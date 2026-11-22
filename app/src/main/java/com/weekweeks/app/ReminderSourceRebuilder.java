package com.weekweeks.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Rebuilds local wall-clock reminders from full source data after time or time-zone changes. */
final class ReminderSourceRebuilder {
    private ReminderSourceRebuilder() { }

    static void rebuild(Context context) { rebuild(context, System.currentTimeMillis()); }

    static void rebuild(Context context, long now) {
        rebuild(context, now, false);
    }

    static void refresh(Context context) { rebuild(context, System.currentTimeMillis(), true); }

    private static void rebuild(Context context, long now, boolean foreground) {
        if (!ReminderScheduler.notificationsEnabled(context)) {
            ReminderDiagnostics.recordSchedule(context, ReminderDiagnosticRules.Code.APP_REMINDERS_OFF);
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(StoredTimetableSnapshot.PREFS_NAME, Context.MODE_PRIVATE);
        if (!AcademicCalendarStore.initializeLegacyZone(prefs)) {
            ReminderDiagnostics.recordSchedule(context, ReminderDiagnosticRules.Code.STORE_FAILED);
            return;
        }
        StoredTimetableSnapshot snapshot = StoredTimetableSnapshot.load(prefs);
        ReminderPlanBuilder.CourseDetails details = courseDetails(AcademicCalendarStore.startMillis(prefs),
                AcademicCalendarStore.endMillis(prefs), snapshot.periodTimes);
        List<ReminderScheduler.Entry> entries = ReminderPlanBuilder.build(snapshot.courses.items,
                snapshot.events.items, now, details);
        if (foreground) ReminderScheduler.refreshSchedule(context, entries, snapshot.courses.readable, snapshot.events.readable);
        else ReminderScheduler.replaceSchedule(context, entries, snapshot.courses.readable, snapshot.events.readable);
    }

    /** Fingerprint source data, never the shrinking set of future occurrences or the current instant. */
    static String sourceFingerprint(Context context) {
        try {
            return sourceFingerprint(context.getSharedPreferences(StoredTimetableSnapshot.PREFS_NAME,
                    Context.MODE_PRIVATE).getAll(), TimeZone.getDefault());
        } catch (RuntimeException unavailable) { return null; }
    }

    static String sourceFingerprint(Map<String, ?> values, TimeZone zone) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            fingerprintPart(digest, "reminder-source-v1");
            for (String key : new String[]{StoredTimetableSnapshot.COURSES_KEY,
                    StoredTimetableSnapshot.LEGACY_COURSES_KEY, StoredTimetableSnapshot.EVENTS_KEY,
                    StoredTimetableSnapshot.PERIOD_TIMES_KEY, "calendar_start_millis", "calendar_end_millis",
                    AcademicCalendarStore.TIME_ZONE_KEY, "notifications_enabled"}) {
                Object value = values.get(key);
                fingerprintPart(digest, value == null ? "absent" : value.getClass().getName());
                fingerprintPart(digest, value == null ? "" : String.valueOf(value));
            }
            fingerprintPart(digest, zone.getID());
            fingerprintPart(digest, Integer.toString(zone.getRawOffset()));
            fingerprintPart(digest, Integer.toString(zone.getDSTSavings()));
            byte[] bytes = digest.digest();
            char[] encoded = new char[bytes.length * 2];
            char[] hex = "0123456789abcdef".toCharArray();
            for (int i = 0; i < bytes.length; i++) {
                encoded[2 * i] = hex[(bytes[i] & 0xff) >>> 4];
                encoded[2 * i + 1] = hex[bytes[i] & 0xf];
            }
            return new String(encoded);
        } catch (Exception unavailable) { return null; }
    }

    private static void fingerprintPart(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        for (int shift = 24; shift >= 0; shift -= 8) digest.update((byte) (bytes.length >>> shift));
        digest.update(bytes);
    }

    /** Same period span and lead-time rules used by the activity, independent of Android services. */
    static ReminderPlanBuilder.CourseDetails courseDetails(long termStart, long termEnd, List<String> periodTimes) {
        final List<String> periods = new ArrayList<>(periodTimes);
        final int totalWeeks = termEnd < termStart ? 20 : Math.max(1, AgendaDates.week(termStart, termEnd));
        final LessonSchedule lessons = new LessonSchedule(termStart,termEnd);
        return new ReminderPlanBuilder.CourseDetails() {
            @Override public boolean activeChange(Course series,LessonSchedule.Change ch) {
                return !series.invalidWeeks && lessons.active(series,ch.source) && ch.target>=lessons.start && ch.target<=lessons.end;
            }
            @Override public long[] triggerTimes(Course course, long now) {
                TimetableRules.WeekParseResult weeks = TimetableRules.parseWeeks(course.weeks);
                if (!weeks.valid || periods.isEmpty()) return new long[0];
                String[] start = periods.get(clamp(course.period, 1, periods.size()) - 1).split("-");
                int minutes = course.customStartMinutes >= 0 ? course.customStartMinutes
                        : start.length == 2 ? StoredTimetableSnapshot.clockMinutes(start[0]) : -1;
                if(course.actualDate!=0) {
                    long at=minutes<0?-1:ScheduleDay.time(course.actualDate,minutes);
                    long trigger=at-leadMinutes(course)*60000L;
                    return trigger>now ? new long[]{trigger} : new long[0];
                }
                long[] times=TimetableRules.reminderTriggerTimes(termStart, termEnd, totalWeeks, course.day,
                        minutes, leadMinutes(course), weeks.explicit ? weeks.weeks : null, now);
                if(course.changes.isEmpty()) return times;
                int count=0; for(long time:times) if(!course.changes.containsKey(ScheduleDay.at(time+leadMinutes(course)*60000L))) times[count++]=time;
                return java.util.Arrays.copyOf(times,count);
            }
            @Override public String timeLabel(Course course) {
                if (course.customStartMinutes >= 0 && course.customEndMinutes >= 0)
                    return PersonalEvent.formatTime(course.customStartMinutes) + "-" + PersonalEvent.formatTime(course.customEndMinutes);
                if (periods.isEmpty()) return "";
                String[] start = periods.get(clamp(course.period, 1, periods.size()) - 1).split("-");
                String[] end = periods.get(clamp(course.endPeriod, 1, periods.size()) - 1).split("-");
                return start.length == 2 && end.length == 2 ? start[0] + "-" + end[1]
                        : periods.get(clamp(course.period, 1, periods.size()) - 1);
            }
            @Override public int leadMinutes(Course course) {
                return course.period == 1 || course.period == 5 || course.period == 9 ? 30 : 10;
            }
        };
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
