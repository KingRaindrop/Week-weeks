package com.weekweeks.app;

import android.content.SharedPreferences;
import java.util.Calendar;
import java.util.TimeZone;

/** Semester boundaries are local calendar dates, not fixed instants when the user travels. */
final class AcademicCalendarStore {
    static final String TIME_ZONE_KEY = "calendar_time_zone_id";
    private static final String START_KEY = "calendar_start_millis";
    private static final String END_KEY = "calendar_end_millis";

    private AcademicCalendarStore() { }

    static long startMillis(SharedPreferences prefs) {
        return readDate(prefs, START_KEY, 2026, Calendar.MARCH, 2);
    }

    static long endMillis(SharedPreferences prefs) {
        return readDate(prefs, END_KEY, 2026, Calendar.JULY, 19);
    }

    private static long readDate(SharedPreferences prefs, String key, int year, int month, int day) {
        TimeZone local = TimeZone.getDefault();
        if (!prefs.contains(key)) {
            Calendar fallback = Calendar.getInstance(local);
            fallback.clear(); fallback.set(year, month, day);
            return fallback.getTimeInMillis();
        }
        TimeZone saved = TimeZone.getTimeZone(prefs.getString(TIME_ZONE_KEY, local.getID()));
        return rebase(prefs.getLong(key, 0L), saved, local);
    }

    /** First observed legacy dates have no recoverable original zone; freeze their local meaning. */
    static boolean initializeLegacyZone(SharedPreferences prefs) {
        if (prefs.contains(TIME_ZONE_KEY) || (!prefs.contains(START_KEY) && !prefs.contains(END_KEY))) return true;
        return SafePreferences.commit(prefs,
                new PreferenceTransaction().put(TIME_ZONE_KEY, TimeZone.getDefault().getID()));
    }

    static PreferenceTransaction putDates(PreferenceTransaction transaction, long start, long end) {
        return transaction.put(START_KEY, start).put(END_KEY, end)
                .put(TIME_ZONE_KEY, TimeZone.getDefault().getID());
    }

    /** Preserve year/month/day while rebuilding midnight in the destination zone, including DST. */
    static long rebase(long millis, TimeZone source, TimeZone destination) {
        Calendar saved = Calendar.getInstance(source);
        saved.setTimeInMillis(millis);
        Calendar local = Calendar.getInstance(destination);
        local.clear();
        local.set(saved.get(Calendar.YEAR), saved.get(Calendar.MONTH), saved.get(Calendar.DAY_OF_MONTH));
        return local.getTimeInMillis();
    }
}
