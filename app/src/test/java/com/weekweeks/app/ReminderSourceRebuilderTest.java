package com.weekweeks.app;

import org.junit.Test;
import java.util.Arrays;
import java.util.Calendar;
import java.util.TimeZone;
import static org.junit.Assert.*;

public class ReminderSourceRebuilderTest {
    private static long date(int y, int m, int d) {
        Calendar c = Calendar.getInstance(); c.clear(); c.set(y, m - 1, d); return c.getTimeInMillis();
    }
    private static Course course(int period, int end, String weeks) {
        Course c = new Course(); c.name = "Fictional course"; c.day = 0;
        c.period = period; c.endPeriod = end; c.weeks = weeks; return c;
    }
    private static ReminderPlanBuilder.CourseDetails details() {
        return ReminderSourceRebuilder.courseDetails(date(2030, 1, 7), date(2030, 1, 20),
                Arrays.asList("08:00-08:45", "08:55-09:40"));
    }

    @Test public void periodLoaderRulesRejectOverlapsAndMalformedClocks() {
        assertTrue(StoredTimetableSnapshot.validatePeriods(Arrays.asList("08:00-08:45", "08:45-09:30")));
        assertFalse(StoredTimetableSnapshot.validatePeriods(Arrays.asList("08:00-08:45", "08:44-09:30")));
        assertFalse(StoredTimetableSnapshot.validatePeriods(Arrays.asList("08:00-24:00")));
        assertFalse(StoredTimetableSnapshot.validatePeriods(Arrays.asList("08:00-08:00")));
        assertFalse(StoredTimetableSnapshot.validatePeriods(Arrays.asList("oops")));
    }

    @Test public void periodLabelsAndLeadTimesMatchSingleAndSpanningCourses() {
        ReminderPlanBuilder.CourseDetails d = details();
        assertEquals("08:00-08:45", d.timeLabel(course(1, 1, "")));
        assertEquals("08:00-09:40", d.timeLabel(course(1, 2, "")));
        assertEquals(30, d.leadMinutes(course(1, 1, "")));
        assertEquals(30, d.leadMinutes(course(5, 5, "")));
        assertEquals(30, d.leadMinutes(course(9, 9, "")));
        assertEquals(10, d.leadMinutes(course(2, 2, "")));
    }

    @Test public void invalidWeeksNeverProduceBackgroundReminders() {
        assertEquals(0, details().triggerTimes(course(1, 1, "invalid"), date(2030, 1, 1)).length);
    }

    @Test public void oddAndEvenWeeksKeepTheirCorrectLocalDates() {
        ReminderPlanBuilder.CourseDetails d = details();
        long odd = d.triggerTimes(course(1, 1, "1-2周单周"), date(2030, 1, 1))[0];
        long even = d.triggerTimes(course(1, 1, "1-2周双周"), date(2030, 1, 1))[0];
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(odd);
        assertEquals(7, c.get(Calendar.DAY_OF_MONTH)); assertEquals(7, c.get(Calendar.HOUR_OF_DAY));
        assertEquals(30, c.get(Calendar.MINUTE)); c.setTimeInMillis(even);
        assertEquals(14, c.get(Calendar.DAY_OF_MONTH));
    }

    @Test public void rebuildingCanRecoverOccurrencesAfterClockMovesBackwards() {
        ReminderPlanBuilder.CourseDetails d = details(); Course c = course(1, 1, "1周");
        long trigger = d.triggerTimes(c, date(2030, 1, 1))[0];
        assertEquals(0, d.triggerTimes(c, trigger + 1).length);
        assertArrayEquals(new long[]{trigger}, d.triggerTimes(c, trigger - 1));
    }

    @Test public void daylightSavingKeepsWallClockTimeAcrossOccurrences() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
            ReminderPlanBuilder.CourseDetails d = ReminderSourceRebuilder.courseDetails(date(2026, 3, 23),
                    date(2026, 4, 5), Arrays.asList("08:00-08:45"));
            long[] times = d.triggerTimes(course(1, 1, "1-2"), date(2026, 3, 1));
            assertEquals(2, times.length); assertEquals(167L * 3_600_000L, times[1] - times[0]);
            for (long time : times) {
                Calendar c = Calendar.getInstance(); c.setTimeInMillis(time);
                assertEquals(7, c.get(Calendar.HOUR_OF_DAY)); assertEquals(30, c.get(Calendar.MINUTE));
            }
        } finally { TimeZone.setDefault(original); }
    }
}
