package com.weekweeks.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Android-free merge rules for a reviewed timetable import. Callers persist the staged candidate copy. */
final class ImportPlanner {
    interface Item {
        int importDay();
        int importStart();
        int importEnd();
        String importName();
        String importWeeks();
        String importRoom();
        String importTeacher();
        void applyImportedLocation(String room, String teacher);
    }

    /** Assigns id/color to a new item; {@code sequence} counts items handled before it. */
    interface Preparer<T> { void prepare(T item, List<T> candidate, int sequence); }

    enum SkipReason { LIMIT, EXISTS }

    static final class Skip {
        final SkipReason reason;
        final String name;
        Skip(SkipReason reason, String name) { this.reason = reason; this.name = name; }
    }

    static final class Result {
        final int imported;
        final int updated;
        final List<Skip> skipped;
        Result(int imported, int updated, List<Skip> skipped) {
            this.imported = imported;
            this.updated = updated;
            this.skipped = Collections.unmodifiableList(skipped);
        }
        boolean changed() { return imported > 0 || updated > 0; }
    }

    private ImportPlanner() { }

    static final class Conflict<T extends Item> {
        final T existing, incoming;
        Conflict(T existing, T incoming) { this.existing = existing; this.incoming = incoming; }
    }

    /** Preview only: matches merge's duplicate/capacity rules without changing either input. */
    static <T extends Item> List<Conflict<T>> conflicts(List<T> existing, List<T> parsed,
                                                       int maxCourses, int maxFieldLength) {
        List<T> staged = new ArrayList<>(existing);
        List<Conflict<T>> result = new ArrayList<>();
        for (T incoming : parsed) {
            if (findMatch(staged, incoming, maxFieldLength) != null || staged.size() >= maxCourses) continue;
            for (T other : staged) {
                if (other.importDay() == incoming.importDay()
                        && other.importStart() <= incoming.importEnd()
                        && incoming.importStart() <= other.importEnd()
                        && TimetableRules.weeksOverlap(other.importWeeks(), incoming.importWeeks()))
                    result.add(new Conflict<>(other, incoming));
            }
            staged.add(incoming);
        }
        return result;
    }

    /**
     * Same name, day, periods and weeks count as one course: a changed room/teacher updates
     * it in place instead of creating a conflicting duplicate. Blank optional import values
     * preserve saved metadata; clearing a room or teacher is an explicit editor action.
     */
    static <T extends Item> Result merge(List<T> candidate, List<T> parsed, int maxCourses,
                                         int maxFieldLength, Preparer<T> preparer) {
        int imported = 0;
        int updated = 0;
        List<Skip> skipped = new ArrayList<>();
        for (T item : parsed) {
            // Existing rows do not consume capacity: allow updates and accurate duplicate reasons at the limit.
            T existing = findMatch(candidate, item, maxFieldLength);
            if (existing != null) {
                String room = importedOrSaved(item.importRoom(), existing.importRoom());
                String teacher = importedOrSaved(item.importTeacher(), existing.importTeacher());
                if (sameText(existing.importRoom(), room, maxFieldLength)
                        && sameText(existing.importTeacher(), teacher, maxFieldLength)) {
                    skipped.add(new Skip(SkipReason.EXISTS, item.importName()));
                } else {
                    existing.applyImportedLocation(room, teacher);
                    updated++;
                }
                continue;
            }
            if (candidate.size() >= maxCourses) {
                skipped.add(new Skip(SkipReason.LIMIT, item.importName()));
                continue;
            }
            if (preparer != null) preparer.prepare(item, candidate, imported + updated + skipped.size());
            candidate.add(item);
            imported++;
        }
        return new Result(imported, updated, skipped);
    }

    private static String importedOrSaved(String incoming, String saved) {
        return incoming == null || incoming.trim().isEmpty() ? saved : incoming;
    }

    static <T extends Item> T findMatch(List<T> source, Item draft, int maxFieldLength) {
        for (T item : source) {
            if (item.importDay() == draft.importDay()
                    && item.importStart() == draft.importStart()
                    && item.importEnd() == draft.importEnd()
                    && sameText(item.importName(), draft.importName(), maxFieldLength)
                    && TimetableRules.sameWeekExpression(item.importWeeks(), draft.importWeeks())) {
                return item;
            }
        }
        return null;
    }

    static boolean sameText(String left, String right, int maxFieldLength) {
        return TimetableRules.normalizedCourseText(clean(left, maxFieldLength))
                .equals(TimetableRules.normalizedCourseText(clean(right, maxFieldLength)));
    }

    static String clean(String value, int maxFieldLength) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= maxFieldLength ? trimmed : trimmed.substring(0, maxFieldLength);
    }

    static int requiredPeriods(List<PdfCourseParser.ParsedCourse> items) {
        int required = 0;
        for (PdfCourseParser.ParsedCourse item : items) required = Math.max(required, item.endPeriod);
        return required;
    }
}
