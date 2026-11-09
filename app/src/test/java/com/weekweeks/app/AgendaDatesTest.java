package com.weekweeks.app;

import org.junit.Test;
import java.util.Calendar;
import java.util.TimeZone;
import static org.junit.Assert.*;

public class AgendaDatesTest {
    private static long day(int y, int m, int d) {
        Calendar c = Calendar.getInstance(); c.clear(); c.set(y, m - 1, d); return c.getTimeInMillis();
    }
    @Test public void midweekSemesterUsesMondayToSundayWeeks() {
        long anchor = day(2026, 9, 1); // Tuesday: week 1 is 8.31–9.6
        assertEquals(1, AgendaDates.week(anchor, day(2026, 8, 31)));
        assertEquals(day(2026, 11, 2), AgendaDates.weekStart(anchor, day(2026, 11, 8)));
        assertEquals(10, AgendaDates.week(anchor, day(2026, 11, 2)));
        assertEquals(10, AgendaDates.week(anchor, day(2026, 11, 8)));
        assertEquals(11, AgendaDates.week(anchor, day(2026, 11, 9)));
        long[] days = AgendaDates.weekdays(day(2026, 11, 2));
        assertEquals(day(2026, 11, 2), days[0]);
        assertEquals(day(2026, 11, 8), days[6]);
        assertEquals(day(2026, 11, 2), TimetableRules.courseDateMillisForWeek(anchor, 10, 0));
    }
    @Test public void sundayStartIsWeekOneAndMondayIsWeekTwo() {
        long anchor = day(2026, 9, 6); // Sunday
        assertEquals(1, AgendaDates.week(anchor, day(2026, 9, 6)));
        assertEquals(2, AgendaDates.week(anchor, day(2026, 9, 7)));
        assertEquals(day(2026, 8, 31), AgendaDates.weekStart(anchor, day(2026, 9, 6)));
        assertEquals(day(2026, 9, 7), TimetableRules.courseDateMillisForWeek(anchor, 2, 0));
    }
    @Test public void datesBeforeSemesterUseFloorNotTruncation() {
        long anchor = day(2026, 9, 1);
        assertEquals(0, AgendaDates.week(anchor, day(2026, 8, 30)));
        assertEquals(-1, AgendaDates.week(anchor, day(2026, 8, 23)));
        assertEquals(day(2026, 8, 24), AgendaDates.weekStart(anchor, day(2026, 8, 30)));
    }
    @Test public void holidaysAndNextYearRemainBrowsable() {
        long anchor = day(2026, 9, 1);
        long date = day(2027, 7, 15);
        long start = AgendaDates.weekStart(anchor, date);
        assertTrue(AgendaDates.week(anchor, date) > 30);
        assertTrue(date >= start && date < AgendaDates.shift(start, 7));
        assertEquals(date, AgendaDates.weekdays(start)[3]);
    }
    @Test public void localMidnightSurvivesDaylightSaving() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
            long anchor = day(2026, 3, 23);
            assertEquals(2, AgendaDates.week(anchor, day(2026, 3, 30)));
            assertEquals(day(2026, 3, 30), AgendaDates.shift(anchor, 7));
            assertEquals(day(2026, 3, 23), AgendaDates.weekStart(anchor, day(2026, 3, 29)));
        } finally { TimeZone.setDefault(original); }
    }
}
