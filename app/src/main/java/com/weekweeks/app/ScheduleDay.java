package com.weekweeks.app;

import java.util.Calendar;
import java.util.Locale;

/** A local civil date (YYYYMMDD), stable across time-zone changes. Zero means no date. */
final class ScheduleDay {
    static final int LAST = 21001231;
    private ScheduleDay() { }
    static int of(int y, int m, int d) { return y * 10000 + m * 100 + d; }
    static int of(Calendar c) { return of(c.get(Calendar.YEAR), c.get(Calendar.MONTH)+1, c.get(Calendar.DAY_OF_MONTH)); }
    static int at(long millis) { Calendar c = Calendar.getInstance(); c.setTimeInMillis(millis); return of(c); }
    static int of(PersonalEvent e) { return of(e.year, e.month, e.day); }
    static void set(PersonalEvent e, int date) { e.year = date / 10000; e.month = date / 100 % 100; e.day = date % 100; }
    static boolean valid(int date) { return PersonalEvent.validDate(date / 10000, date / 100 % 100, date % 100); }
    static Calendar calendar(int date) {
        Calendar c = Calendar.getInstance(); c.clear(); c.set(date / 10000, date / 100 % 100 - 1, date % 100); return c;
    }
    static long millis(int date) { return calendar(date).getTimeInMillis(); }
    static int weekday(int date) { return (calendar(date).get(Calendar.DAY_OF_WEEK) + 5) % 7; }
    static int shift(int date, int days) { Calendar c = calendar(date); c.add(Calendar.DATE, days); return of(c); }
    static String label(int date) { return String.format(Locale.ROOT, "%04d-%02d-%02d", date / 10000, date / 100 % 100, date % 100); }
    static long time(int date, int minutes) {
        Calendar c = calendar(date); c.setLenient(false); c.set(Calendar.HOUR_OF_DAY, minutes / 60); c.set(Calendar.MINUTE, minutes % 60);
        try { return c.getTimeInMillis(); } catch (IllegalArgumentException gap) { return -1; }
    }
}
