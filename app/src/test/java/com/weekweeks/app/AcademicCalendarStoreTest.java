package com.weekweeks.app;

import static org.junit.Assert.*;
import java.util.Calendar;
import java.util.TimeZone;
import org.junit.Test;

public class AcademicCalendarStoreTest {
    private static final TimeZone SHANGHAI = TimeZone.getTimeZone("Asia/Shanghai");
    private static final TimeZone BERLIN = TimeZone.getTimeZone("Europe/Berlin");
    private static final TimeZone TOKYO = TimeZone.getTimeZone("Asia/Tokyo");

    @Test public void travelingWestPreservesSemesterMondayAndWeekNumber() {
        TimeZone before = TimeZone.getDefault();
        try {
            long saved = date(SHANGHAI, 2026, 3, 2);
            TimeZone.setDefault(BERLIN);
            long start = AcademicCalendarStore.rebase(saved, SHANGHAI, BERLIN);
            assertEquals(date(BERLIN, 2026, 3, 2), start);
            assertEquals(1, AgendaDates.week(start, date(BERLIN, 2026, 3, 2)));
            assertEquals(date(BERLIN, 2026, 3, 9), TimetableRules.courseDateMillisForWeek(start, 2, 0));
        } finally { TimeZone.setDefault(before); }
    }

    @Test public void travelingEastKeepsFirstMondayReminder() {
        TimeZone before = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TOKYO);
            long start = AcademicCalendarStore.rebase(date(SHANGHAI, 2026, 3, 2), SHANGHAI, TOKYO);
            long[] reminders = TimetableRules.reminderTriggerTimes(start, start, 1, 0, 480, 30, null, start);
            assertEquals(1, reminders.length);
            assertEquals(start + 450 * 60_000L, reminders[0]);
        } finally { TimeZone.setDefault(before); }
    }

    @Test public void leapDayAndYearBoundarySurviveTravel() {
        TimeZone west = TimeZone.getTimeZone("Pacific/Honolulu");
        TimeZone east = TimeZone.getTimeZone("Pacific/Kiritimati");
        assertEquals(date(east, 2028, 2, 29), AcademicCalendarStore.rebase(date(west, 2028, 2, 29), west, east));
        assertEquals(date(west, 2027, 1, 1), AcademicCalendarStore.rebase(date(east, 2027, 1, 1), east, west));
    }

    @Test public void dstUsesEachDatesOffsetInsteadOfOneFixedShift() {
        TimeZone before = TimeZone.getDefault();
        try {
            TimeZone.setDefault(BERLIN);
            long start = AcademicCalendarStore.rebase(date(SHANGHAI, 2026, 3, 23), SHANGHAI, BERLIN);
            long end = AcademicCalendarStore.rebase(date(SHANGHAI, 2026, 3, 30), SHANGHAI, BERLIN);
            long[] reminders = TimetableRules.reminderTriggerTimes(start, end, 2, 0, 480, 30, null, start);
            assertEquals(2, reminders.length);
            assertEquals(167 * 3_600_000L, reminders[1] - reminders[0]);
            for (long reminder : reminders) {
                Calendar calendar = Calendar.getInstance(); calendar.setTimeInMillis(reminder);
                assertEquals(7, calendar.get(Calendar.HOUR_OF_DAY));
                assertEquals(30, calendar.get(Calendar.MINUTE));
            }
        } finally { TimeZone.setDefault(before); }
    }

    @Test public void repeatedTripsDoNotAccumulateDateDrift() {
        long original = date(SHANGHAI, 2026, 10, 26);
        long berlin = AcademicCalendarStore.rebase(original, SHANGHAI, BERLIN);
        long tokyo = AcademicCalendarStore.rebase(berlin, BERLIN, TOKYO);
        assertEquals(original, AcademicCalendarStore.rebase(tokyo, TOKYO, SHANGHAI));
    }

    private static long date(TimeZone zone, int year, int month, int day) {
        Calendar calendar = Calendar.getInstance(zone); calendar.clear(); calendar.set(year, month - 1, day);
        return calendar.getTimeInMillis();
    }
}
