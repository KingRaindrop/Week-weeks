package com.weekweeks.app;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class ImportPlannerTest {
    private static final class C implements ImportPlanner.Item {
        long id; int day, start, end; String name, weeks, room, teacher;
        C(int day, int start, int end, String name, String weeks, String room, String teacher) {
            this.day = day; this.start = start; this.end = end;
            this.name = name; this.weeks = weeks; this.room = room; this.teacher = teacher;
        }
        public int importDay() { return day; }
        public int importStart() { return start; }
        public int importEnd() { return end; }
        public String importName() { return name; }
        public String importWeeks() { return weeks; }
        public String importRoom() { return room; }
        public String importTeacher() { return teacher; }
        public void applyImportedLocation(String r, String t) { room = r; teacher = t; }
    }

    @Test public void newCoursesArePreparedInSequenceAndAppended() {
        List<C> candidate = new ArrayList<>();
        List<C> parsed = Arrays.asList(new C(0, 1, 2, "Math", "1-16", "A1", "Li"),
                new C(1, 3, 4, "Art", "1-8", "B2", ""));
        ImportPlanner.Result r = ImportPlanner.merge(candidate, parsed, 200, 60,
                (item, staged, sequence) -> item.id = 1000 + sequence);
        assertEquals(2, r.imported);
        assertEquals(0, r.updated);
        assertTrue(r.skipped.isEmpty());
        assertTrue(r.changed());
        assertEquals(1000, candidate.get(0).id);
        assertEquals(1001, candidate.get(1).id);
    }

    @Test public void identicalCourseIsSkippedAndChangedLocationUpdatesInPlace() {
        C existing = new C(0, 1, 2, "Math", "1-16周", "A1", "Li");
        List<C> candidate = new ArrayList<>(Arrays.asList(existing));
        ImportPlanner.Result same = ImportPlanner.merge(candidate,
                Arrays.asList(new C(0, 1, 2, " math ", "1-16", "A1 ", "Li")), 200, 60, null);
        assertEquals(1, same.skipped.size());
        assertEquals(ImportPlanner.SkipReason.EXISTS, same.skipped.get(0).reason);
        assertFalse(same.changed());

        ImportPlanner.Result moved = ImportPlanner.merge(candidate,
                Arrays.asList(new C(0, 1, 2, "Math", "1-16", "C9", "Wang")), 200, 60, null);
        assertEquals(1, moved.updated);
        assertEquals(1, candidate.size());
        assertEquals("C9", existing.room);
        assertEquals("Wang", existing.teacher);
    }

    @Test public void differentWeeksOrPeriodsAreNotMergedAndLimitSkips() {
        List<C> candidate = new ArrayList<>(Arrays.asList(new C(0, 1, 2, "Math", "1-8", "A1", "")));
        ImportPlanner.Result r = ImportPlanner.merge(candidate, Arrays.asList(
                new C(0, 1, 2, "Math", "9-16", "A1", ""),
                new C(0, 3, 4, "Math", "1-8", "A1", ""),
                new C(2, 1, 1, "Overflow", "", "", "")), 3, 60, null);
        assertEquals(2, r.imported);
        assertEquals(1, r.skipped.size());
        assertEquals(ImportPlanner.SkipReason.LIMIT, r.skipped.get(0).reason);
        assertEquals("Overflow", r.skipped.get(0).name);
        assertEquals(3, candidate.size());
    }

    @Test public void capacityBlocksOnlyNewRowsNotUpdatesOrDuplicateClassification() {
        C existing = new C(0, 1, 2, "Math", "1-16", "A1", "Li");
        List<C> candidate = new ArrayList<>(Arrays.asList(existing));
        ImportPlanner.Result r = ImportPlanner.merge(candidate, Arrays.asList(
                new C(0, 1, 2, "Math", "1-16", "C9", "Wang"),
                new C(0, 1, 2, "Math", "1-16", "C9", "Wang"),
                new C(1, 1, 2, "New", "1-16", "", "")), 1, 60,
                (item, staged, sequence) -> fail("Capacity-blocked items must not be prepared"));
        assertEquals(1, r.updated);
        assertEquals(0, r.imported);
        assertEquals(1, candidate.size());
        assertEquals("C9", existing.room);
        assertEquals(ImportPlanner.SkipReason.EXISTS, r.skipped.get(0).reason);
        assertEquals(ImportPlanner.SkipReason.LIMIT, r.skipped.get(1).reason);
    }

    @Test public void requiredPeriodsUsesLatestEnd() {
        PdfCourseParser.ParsedCourse a = new PdfCourseParser.ParsedCourse();
        a.endPeriod = 4;
        PdfCourseParser.ParsedCourse b = new PdfCourseParser.ParsedCourse();
        b.endPeriod = 11;
        assertEquals(11, ImportPlanner.requiredPeriods(Arrays.asList(a, b)));
        assertEquals(0, ImportPlanner.requiredPeriods(new ArrayList<>()));
    }

    @Test public void minimalTextReimportDoesNotEraseSavedRoomOrTeacher() {
        C existing = new C(0, 1, 2, "Math", "每周", "A101", "Li");
        TextTimetableParser.Result parsed = TextTimetableParser.parse("Math | Mon | 1-2", 20, 200);
        assertTrue(parsed.errors.toString(), parsed.errors.isEmpty());
        PdfCourseParser.ParsedCourse row = parsed.courses.get(0);
        C incoming = new C(row.day, row.period, row.endPeriod, row.name, row.weeks, row.room, row.teacher);
        ImportPlanner.Result result = ImportPlanner.merge(new ArrayList<>(Arrays.asList(existing)),
                Arrays.asList(incoming), 200, 60, null);
        assertEquals("A101", existing.room);
        assertEquals("Li", existing.teacher);
        assertFalse(result.changed());
        assertEquals(ImportPlanner.SkipReason.EXISTS, result.skipped.get(0).reason);
    }

    @Test public void suppliedOptionalFieldUpdatesIndependentlyWithoutClearingTheOther() {
        C existing = new C(0, 1, 2, "Math", "1-16", "A101", "Li");
        List<C> candidate = new ArrayList<>(Arrays.asList(existing));
        ImportPlanner.Result room = ImportPlanner.merge(candidate,
                Arrays.asList(new C(0, 1, 2, "Math", "1-16", "B202", "")), 1, 60, null);
        assertEquals(1, room.updated);
        assertEquals("B202", existing.room);
        assertEquals("Li", existing.teacher);
        ImportPlanner.Result teacher = ImportPlanner.merge(candidate,
                Arrays.asList(new C(0, 1, 2, "Math", "1-16", "", "Wang")), 1, 60, null);
        assertEquals(1, teacher.updated);
        assertEquals("B202", existing.room);
        assertEquals("Wang", existing.teacher);
    }

    @Test public void nullWhitespaceAndExplicitEmptyOptionalValuesKeepSavedMetadata() {
        C existing = new C(0, 1, 2, "Math", "", "A101", "Li");
        List<C> candidate = new ArrayList<>(Arrays.asList(existing));
        ImportPlanner.Result result = ImportPlanner.merge(candidate, Arrays.asList(
                new C(0, 1, 2, "Math", "", null, " \t"),
                new C(0, 1, 2, "Math", "", "", "")), 200, 60, null);
        assertFalse(result.changed());
        assertEquals(2, result.skipped.size());
        assertEquals("A101", existing.room);
        assertEquals("Li", existing.teacher);
    }

    @Test public void newCourseCanStillHaveEmptyOptionalValues() {
        C existing = new C(0, 1, 2, "Math", "", "A101", "Li");
        C incoming = new C(1, 3, 4, "Art", "", "", "");
        List<C> candidate = new ArrayList<>(Arrays.asList(existing));
        ImportPlanner.Result result = ImportPlanner.merge(candidate, Arrays.asList(incoming), 200, 60, null);
        assertEquals(1, result.imported);
        assertEquals("", incoming.room);
        assertEquals("", incoming.teacher);
        assertEquals("A101", existing.room);
    }

    @Test public void previewFindsExistingAndWithinImportConflictsWithoutMutatingRows() {
        C existing = new C(0, 1, 2, "Math", "1-16", "A1", "Li");
        C incoming = new C(0, 2, 3, "English", "8-16", "B1", "Wu");
        C third = new C(0, 3, 4, "Art", "8-16", "C1", "");
        List<C> saved = new ArrayList<>(Arrays.asList(existing));
        List<C> parsed = Arrays.asList(incoming, third);
        List<ImportPlanner.Conflict<C>> found = ImportPlanner.conflicts(saved, parsed, 200, 60);
        assertEquals(2, found.size());
        assertSame(existing, found.get(0).existing);
        assertSame(incoming, found.get(0).incoming);
        assertSame(third, found.get(1).incoming);
        assertEquals(1, saved.size());
        assertEquals("A1", existing.room);
    }

    @Test public void differentDaysAdjacentPeriodsAndDisjointWeeksAreNotConflicts() {
        List<C> existing = Arrays.asList(new C(0, 1, 2, "Math", "1-16单周", "A1", ""));
        List<C> parsed = Arrays.asList(new C(1, 1, 2, "English", "1-16", "", ""),
                new C(0, 3, 4, "Art", "1-16", "", ""),
                new C(0, 1, 2, "Physics", "1-16双周", "", ""));
        assertTrue(ImportPlanner.conflicts(existing, parsed, 200, 60).isEmpty());
    }

    @Test public void duplicatesLocationUpdatesAndCapacityBlockedRowsAreNotNewConflicts() {
        C existing = new C(0, 1, 2, "Math", "1-16", "A1", "Li");
        List<C> parsed = Arrays.asList(new C(0, 1, 2, " math ", "1-16周", "B2", "Wang"),
                new C(0, 1, 2, "English", "1-16", "", ""));
        assertTrue(ImportPlanner.conflicts(Arrays.asList(existing), parsed, 1, 60).isEmpty());
        assertEquals("A1", existing.room);
        assertEquals("Li", existing.teacher);
        assertEquals(1, ImportPlanner.conflicts(Arrays.asList(existing), parsed, 2, 60).size());
    }

    @Test public void everyWeekRowsConflictWithExplicitWeeksAndDuplicatesAreOnlyCountedOnce() {
        C a = new C(0, 1, 2, "Math", "", "", "");
        C b = new C(0, 1, 2, "English", "18", "", "");
        assertEquals(1, ImportPlanner.conflicts(new ArrayList<C>(), Arrays.asList(a, b, b), 200, 60).size());
    }
}
