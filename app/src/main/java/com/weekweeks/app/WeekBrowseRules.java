package com.weekweeks.app;

/** Pure-Java week navigation. Reuses the existing Monday anchor and calendar-day arithmetic. */
final class WeekBrowseRules {
    private WeekBrowseRules() { }

    /** Raw week numbers may be outside 1..totalWeeks: holiday/date browsing stays representable. */
    static final class Snapshot {
        final int totalWeeks, viewedWeek, realWeek;
        final long displayedStartMillis;

        private Snapshot(int totalWeeks, int viewedWeek, int realWeek, long displayedStartMillis) {
            this.totalWeeks = totalWeeks;
            this.viewedWeek = viewedWeek;
            this.realWeek = realWeek;
            this.displayedStartMillis = displayedStartMillis;
        }

        boolean isViewedWeek(int week) { return inSemester(week, totalWeeks) && viewedWeek == week; }
        boolean isRealWeek(int week) { return inSemester(week, totalWeeks) && realWeek == week; }
        boolean outsideSemester() { return !inSemester(viewedWeek, totalWeeks); }

        /** Scroll to the viewed week; outside the term, position at the nearest semester edge. */
        int initialWeek() { return Math.max(1, Math.min(totalWeeks, viewedWeek)); }
    }

    /**
     * Atomic replacement for the host's two navigation fields. Setting only viewingWeek is wrong
     * for the pastel timetable: its visible dates come from pastelBrowseDate.
     * targetDateMillis can alternatively be passed to MainActivity.browsePastelDate(...).
     */
    static final class Selection {
        final long targetDateMillis, pastelBrowseDate, displayedStartMillis;
        final int viewingWeek, displayedWeek;
        final boolean outsideSemester;

        private Selection(long targetDateMillis, long pastelBrowseDate, long displayedStartMillis,
                          int viewingWeek, int displayedWeek, boolean outsideSemester) {
            this.targetDateMillis = targetDateMillis;
            this.pastelBrowseDate = pastelBrowseDate;
            this.displayedStartMillis = displayedStartMillis;
            this.viewingWeek = viewingWeek;
            this.displayedWeek = displayedWeek;
            this.outsideSemester = outsideSemester;
        }
    }

    static Snapshot snapshot(long semesterStartMillis, int totalWeeks, long pastelBrowseDate, long nowMillis) {
        requireTotal(totalWeeks);
        long shown = pastelBrowseDate == 0L ? nowMillis : pastelBrowseDate;
        return new Snapshot(totalWeeks, AgendaDates.week(semesterStartMillis, shown),
                AgendaDates.week(semesterStartMillis, nowMillis), AgendaDates.weekStart(semesterStartMillis, shown));
    }

    static boolean inSemester(int week, int totalWeeks) { return week >= 1 && week <= totalWeeks; }

    static Selection selectWeek(long semesterStartMillis, int totalWeeks, int week, long nowMillis) {
        requireTotal(totalWeeks);
        if (!inSemester(week, totalWeeks)) throw new IllegalArgumentException("Week is outside 1..totalWeeks");
        // Calendar.add (inside AgendaDates.shift), not fixed 168-hour arithmetic, survives DST.
        long dayOffset = (week - 1L) * 7L;
        if (dayOffset > Integer.MAX_VALUE) throw new IllegalArgumentException("Week offset is too large");
        long target = AgendaDates.shift(TimetableRules.weekAnchorMillis(semesterStartMillis), (int) dayOffset);
        return selectDate(semesterStartMillis, totalWeeks, target, nowMillis);
    }

    /** Secondary date lookup may intentionally navigate outside the semester. */
    static Selection selectDate(long semesterStartMillis, int totalWeeks, long dateMillis, long nowMillis) {
        requireTotal(totalWeeks);
        long start = AgendaDates.weekStart(semesterStartMillis, dateMillis);
        long realStart = AgendaDates.weekStart(semesterStartMillis, nowMillis);
        long browseDate = start == realStart ? 0L : dateMillis;
        int week = AgendaDates.week(semesterStartMillis, dateMillis);
        boolean outside = !inSemester(week, totalWeeks);
        return new Selection(dateMillis, browseDate, start,
                browseDate == 0L || outside ? 0 : week, week, outside);
    }

    static Selection currentWeek(long semesterStartMillis, int totalWeeks, long nowMillis) {
        return selectDate(semesterStartMillis, totalWeeks, nowMillis, nowMillis);
    }

    private static void requireTotal(int totalWeeks) {
        if (totalWeeks < 1) throw new IllegalArgumentException("totalWeeks must be positive");
    }
}
