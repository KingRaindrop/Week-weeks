package com.weekweeks.app;

import org.junit.Test;
import java.util.Calendar;
import java.util.TimeZone;
import static org.junit.Assert.*;

public class WeekBrowseRulesTest {
    private static long day(int year, int month, int date) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month - 1, date);
        return c.getTimeInMillis();
    }

    @Test public void midweekSemesterSelectsExistingMondayAnchor() {
        long start = day(2026, 9, 1); // Tuesday; existing week one starts on Monday 8/31.
        WeekBrowseRules.Selection selected = WeekBrowseRules.selectWeek(start, 20, 1, day(2026, 11, 12));
        assertEquals(day(2026, 8, 31), selected.targetDateMillis);
        assertEquals(TimetableRules.weekAnchorMillis(start), selected.displayedStartMillis);
        assertEquals(1, selected.viewingWeek);
        assertEquals(1, selected.displayedWeek);
        assertEquals(selected.targetDateMillis, selected.pastelBrowseDate);
        assertFalse(selected.outsideSemester);
    }

    @Test public void browsingAnotherWeekUpdatesBothHostFields() {
        long start = day(2026, 9, 1), now = day(2026, 11, 12);
        WeekBrowseRules.Selection selected = WeekBrowseRules.selectWeek(start, 20, 5, now);
        assertEquals(5, selected.viewingWeek);
        assertNotEquals(0L, selected.pastelBrowseDate);
        assertEquals(5, AgendaDates.week(start, selected.pastelBrowseDate));
        assertEquals(AgendaDates.weekStart(start, selected.pastelBrowseDate), selected.displayedStartMillis);
    }

    @Test public void choosingRealWeekRestoresFollowTodaySentinels() {
        long start = day(2026, 9, 1), now = day(2026, 11, 12);
        WeekBrowseRules.Selection selected = WeekBrowseRules.selectWeek(start, 20, 11, now);
        assertEquals(0L, selected.pastelBrowseDate);
        assertEquals(0, selected.viewingWeek);
        assertEquals(11, selected.displayedWeek);
    }

    @Test public void viewedAndRealWeeksAreMarkedSeparately() {
        WeekBrowseRules.Snapshot s = WeekBrowseRules.snapshot(day(2026, 9, 1), 20,
                day(2026, 9, 28), day(2026, 11, 12));
        assertEquals(5, s.viewedWeek);
        assertEquals(11, s.realWeek);
        assertTrue(s.isViewedWeek(5));
        assertFalse(s.isRealWeek(5));
        assertTrue(s.isRealWeek(11));
        assertFalse(s.isViewedWeek(11));
        assertEquals(5, s.initialWeek());
    }

    @Test public void sameWeekCanHaveBothMarkers() {
        WeekBrowseRules.Snapshot s = WeekBrowseRules.snapshot(day(2026, 9, 1), 20, 0L, day(2026, 11, 12));
        assertTrue(s.isViewedWeek(11));
        assertTrue(s.isRealWeek(11));
        assertFalse(s.isViewedWeek(0));
        assertFalse(s.isRealWeek(21));
    }

    @Test public void acceptsCalendarTotalRatherThanHardCodedTwentyOrCourseParserLimit() {
        long start = day(2026, 9, 1), now = day(2026, 9, 1);
        for (int total : new int[]{1, 2, 20, 30, 52}) {
            WeekBrowseRules.Selection s = WeekBrowseRules.selectWeek(start, total, total, now);
            assertEquals(total, s.displayedWeek);
            assertFalse(s.outsideSemester);
        }
    }

    @Test public void invalidWeeksAndTotalsAreRejectedInsteadOfClamped() {
        long start = day(2026, 9, 1);
        assertThrows(IllegalArgumentException.class, () -> WeekBrowseRules.selectWeek(start, 20, 0, start));
        assertThrows(IllegalArgumentException.class, () -> WeekBrowseRules.selectWeek(start, 20, 21, start));
        assertThrows(IllegalArgumentException.class, () -> WeekBrowseRules.snapshot(start, 0, 0L, start));
        assertThrows(IllegalArgumentException.class, () -> WeekBrowseRules.selectDate(start, -1, start, start));
        assertThrows(IllegalArgumentException.class,
                () -> WeekBrowseRules.selectWeek(start, Integer.MAX_VALUE, Integer.MAX_VALUE, start));
    }

    @Test public void dateLookupPreservesOutsideSemesterAgendaNavigation() {
        long start = day(2026, 9, 1), outside = day(2027, 7, 15), now = day(2026, 11, 12);
        WeekBrowseRules.Selection s = WeekBrowseRules.selectDate(start, 20, outside, now);
        assertTrue(s.outsideSemester);
        assertEquals(0, s.viewingWeek);
        assertEquals(outside, s.pastelBrowseDate);
        assertEquals(outside, s.targetDateMillis);
        assertTrue(s.displayedWeek > 20);
        assertEquals(AgendaDates.weekStart(start, outside), s.displayedStartMillis);
    }

    @Test public void outsideSemesterDoesNotInventASelectedOrRealSemesterWeek() {
        long start = day(2026, 9, 1);
        WeekBrowseRules.Snapshot before = WeekBrowseRules.snapshot(start, 20,
                day(2026, 8, 20), day(2027, 7, 15));
        assertTrue(before.outsideSemester());
        assertEquals(1, before.initialWeek());
        for (int week = 1; week <= 20; week++) {
            assertFalse(before.isViewedWeek(week));
            assertFalse(before.isRealWeek(week));
        }
        WeekBrowseRules.Snapshot after = WeekBrowseRules.snapshot(start, 20,
                day(2027, 7, 15), day(2027, 7, 15));
        assertEquals(20, after.initialWeek());
    }

    @Test public void backToCurrentWeekAlsoWorksDuringHoliday() {
        long now = day(2027, 7, 15);
        WeekBrowseRules.Selection s = WeekBrowseRules.currentWeek(day(2026, 9, 1), 20, now);
        assertEquals(0L, s.pastelBrowseDate);
        assertEquals(0, s.viewingWeek);
        assertEquals(now, s.targetDateMillis);
        assertTrue(s.outsideSemester);
    }

    @Test public void sundayStartAndYearBoundaryUseExistingWeekArithmetic() {
        long start = day(2026, 12, 27); // Sunday, week one starts 12/21.
        WeekBrowseRules.Selection s = WeekBrowseRules.selectWeek(start, 20, 3, day(2026, 12, 27));
        assertEquals(day(2027, 1, 4), s.targetDateMillis);
        assertEquals(3, AgendaDates.week(start, s.targetDateMillis));
        assertEquals(TimetableRules.courseDateMillisForWeek(start, 3, 0), s.targetDateMillis);
    }

    @Test public void dstUsesCalendarDaysNotFixedHourOffsets() {
        TimeZone old = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
            long start = day(2026, 3, 23);
            WeekBrowseRules.Selection s = WeekBrowseRules.selectWeek(start, 20, 2, start);
            assertEquals(day(2026, 3, 30), s.targetDateMillis);
            assertEquals(167L * 60 * 60 * 1000, s.targetDateMillis - start);
            assertEquals(2, s.viewingWeek);
        } finally { TimeZone.setDefault(old); }
    }
}
