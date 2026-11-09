package com.weekweeks.app;

import java.util.Calendar;
import java.util.TimeZone;

/** Calendar-day arithmetic for agenda browsing, independent of semester bounds and DST. */
final class AgendaDates {
    private AgendaDates() { }

    private static long ordinal(long millis) {
        Calendar local = Calendar.getInstance();
        local.setTimeInMillis(millis);
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH));
        return utc.getTimeInMillis() / 86_400_000L;
    }

    static long shift(long date, int days) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(date);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DAY_OF_YEAR, days);
        return c.getTimeInMillis();
    }

    /** Teaching week of date; weeks run Monday–Sunday, week 1 contains the term start. */
    static int week(long semesterStart, long date) {
        long days = ordinal(date) - ordinal(TimetableRules.weekAnchorMillis(semesterStart));
        long quotient = days / 7;
        if (days < 0 && days % 7 != 0) quotient--;
        return (int) quotient + 1;
    }

    /** Monday 00:00 of the week containing date. */
    static long weekStart(long semesterStart, long date) {
        return shift(TimetableRules.weekAnchorMillis(semesterStart), (week(semesterStart, date) - 1) * 7);
    }

    static long[] weekdays(long weekStart) {
        long[] result = new long[7];
        Calendar c = Calendar.getInstance();
        for (int i = 0; i < 7; i++) {
            long date = shift(weekStart, i);
            c.setTimeInMillis(date);
            result[(c.get(Calendar.DAY_OF_WEEK) + 5) % 7] = date;
        }
        return result;
    }
}
