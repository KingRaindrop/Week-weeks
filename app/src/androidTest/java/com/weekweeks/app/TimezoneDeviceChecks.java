package com.weekweeks.app;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/** Real encrypted-source and receiver checks; changes only the isolated app process time zone. */
final class TimezoneDeviceChecks {
    private static final SecureStorage SOURCES = new SecureStorage("week_weeks_courses_key");
    private static final SecureStorage SCHEDULE = new SecureStorage("week_weeks_reminder_schedule_key");
    private static final String SCHEDULE_KEY = "class_reminder_schedule_encrypted_v1";
    private TimezoneDeviceChecks() { }

    static void run(Instrumentation instrumentation) throws Throwable {
        Context context = instrumentation.getTargetContext();
        check("com.weekweeks.app.audit".equals(context.getPackageName()), "Refusing non-audit package");
        Throwable[] failure = {null};
        instrumentation.runOnMainSync(() -> {
            try { checkSourcesAndReceivers(context); } catch (Throwable t) { failure[0] = t; }
        });
        if (failure[0] != null) throw failure[0];
        Bundle result = new Bundle();
        result.putString("stream", "TIMEZONE CHECKS PASS: encrypted sources rebuilt without Activity; west/east changes, clock rollback, locked partitions and source preservation verified.\n");
        instrumentation.sendStatus(0, result);
    }

    private static void checkSourcesAndReceivers(Context context) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences("week_weeks", Context.MODE_PRIVATE);
        SharedPreferences diagnostics = context.getSharedPreferences("week_weeks_reminder_diagnostics_v1", Context.MODE_PRIVATE);
        Map<String, ?> originalPrefs = new HashMap<>(prefs.getAll());
        Map<String, ?> originalDiagnostics = new HashMap<>(diagnostics.getAll());
        TimeZone originalZone = TimeZone.getDefault();
        check(ReminderScheduler.status(context).scheduleReadable, "Baseline audit plan must be restorable");
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            String courses = SOURCES.encrypt(courseJson("Timezone course").toString());
            String events = SOURCES.encrypt(eventJson("Timezone event", 600).toString());
            check(prefs.edit().putString(StoredTimetableSnapshot.COURSES_KEY, courses)
                    .putString(StoredTimetableSnapshot.EVENTS_KEY, events)
                    .putString(StoredTimetableSnapshot.PERIOD_TIMES_KEY, "[\"08:00-08:45\",\"08:55-09:40\"]")
                    .putBoolean("notifications_enabled", true).commit(), "Fixture source commit failed");
            check(SafePreferences.commit(prefs, AcademicCalendarStore.putDates(new PreferenceTransaction(),
                    date(2030, 1, 7, 0, 0), date(2030, 1, 20, 0, 0))), "Fixture calendar commit failed");
            ReminderSourceRebuilder.rebuild(context);
            long oldCourse = firstTrigger(prefs, "course"), oldEvent = firstTrigger(prefs, "event");

            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
            new ReminderBootReceiver().onReceive(context, new Intent(Intent.ACTION_TIMEZONE_CHANGED));
            check(AcademicCalendarStore.startMillis(prefs) == date(2030, 1, 7, 0, 0), "Semester date shifted westward");
            check(firstTrigger(prefs, "course") == date(2030, 1, 7, 7, 30), "Westward course reminder not at local 07:30");
            check(firstTrigger(prefs, "event") == date(2030, 1, 7, 9, 30), "Westward event reminder not at local 09:30");
            check(firstTrigger(prefs, "course") - oldCourse == 7 * 3_600_000L
                    && firstTrigger(prefs, "event") - oldEvent == 7 * 3_600_000L, "Old absolute alarm times were reused");

            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            new ReminderBootReceiver().onReceive(context, new Intent(Intent.ACTION_TIMEZONE_CHANGED));
            check(firstTrigger(prefs, "course") == date(2030, 1, 7, 7, 30), "Eastward shift dropped first semester day");
            check(firstTrigger(prefs, "event") == date(2030, 1, 7, 9, 30), "Eastward event reminder is late");
            check(find(prefs, "course").getJSONArray("triggerTimes").length() == 2, "Course recurrence changed on zone switch");

            long eventTrigger = date(2030, 1, 7, 9, 30);
            ReminderSourceRebuilder.rebuild(context, eventTrigger + 1);
            check(find(prefs, "event") == null, "Synthetic future now did not filter expired event");
            ReminderSourceRebuilder.rebuild(context, eventTrigger - 1);
            check(firstTrigger(prefs, "event") == eventTrigger, "Clock rollback could not recover source occurrence");
            ReminderSourceRebuilder.rebuild(context, eventTrigger + 1);
            new ReminderBootReceiver().onReceive(context, new Intent(Intent.ACTION_TIME_CHANGED));
            check(firstTrigger(prefs, "event") == eventTrigger, "TIME_SET reused filtered reminder plan");
            check(courses.equals(prefs.getString(StoredTimetableSnapshot.COURSES_KEY, null))
                    && events.equals(prefs.getString(StoredTimetableSnapshot.EVENTS_KEY, null)), "Rebuild rewrote source ciphertext");

            String retainedCourse = find(prefs, "course").toString();
            String changedEvents = SOURCES.encrypt(eventJson("Changed healthy event", 660).toString());
            check(prefs.edit().putString(StoredTimetableSnapshot.COURSES_KEY, "invalid-course-fixture")
                    .putString(StoredTimetableSnapshot.EVENTS_KEY, changedEvents).commit(), "Course-lock fixture failed");
            new ReminderBootReceiver().onReceive(context, new Intent(Intent.ACTION_TIME_CHANGED));
            check(retainedCourse.equals(find(prefs, "course").toString()), "Locked course reminder was changed");
            check("Changed healthy event".equals(find(prefs, "event").getString("name")), "Healthy event did not update");
            check("invalid-course-fixture".equals(prefs.getString(StoredTimetableSnapshot.COURSES_KEY, null)), "Locked course source overwritten");

            String retainedEvent = find(prefs, "event").toString();
            String changedCourses = SOURCES.encrypt(courseJson("Changed healthy course").toString());
            check(prefs.edit().putString(StoredTimetableSnapshot.COURSES_KEY, changedCourses)
                    .putString(StoredTimetableSnapshot.EVENTS_KEY, "invalid-event-fixture").commit(), "Event-lock fixture failed");
            new ReminderBootReceiver().onReceive(context, new Intent(Intent.ACTION_TIME_CHANGED));
            check(retainedEvent.equals(find(prefs, "event").toString()), "Locked event reminder was changed");
            check("Changed healthy course".equals(find(prefs, "course").getString("name")), "Healthy course did not update");
            check("invalid-event-fixture".equals(prefs.getString(StoredTimetableSnapshot.EVENTS_KEY, null)), "Locked event source overwritten");

            String beforeLocked = prefs.getString(SCHEDULE_KEY, null);
            check(prefs.edit().putString(StoredTimetableSnapshot.COURSES_KEY, "invalid-course-fixture").commit(), "Both-lock fixture failed");
            new ReminderBootReceiver().onReceive(context, new Intent(Intent.ACTION_TIME_CHANGED));
            check(beforeLocked.equals(prefs.getString(SCHEDULE_KEY, null)), "Both locked stores changed plan");
            check(prefs.edit().putString(StoredTimetableSnapshot.COURSES_KEY, courses)
                    .putString(StoredTimetableSnapshot.EVENTS_KEY, events).putBoolean("notifications_enabled", false).commit(), "Off fixture failed");
            new ReminderBootReceiver().onReceive(context, new Intent(Intent.ACTION_TIMEZONE_CHANGED));
            check(beforeLocked.equals(prefs.getString(SCHEDULE_KEY, null)), "Time-zone receiver bypassed reminder switch");
        } finally {
            TimeZone.setDefault(originalZone);
            ReminderScheduler.clear(context);
            restore(prefs, originalPrefs);
            ReminderScheduler.rescheduleStored(context);
            restore(prefs, originalPrefs);
            restore(diagnostics, originalDiagnostics);
            check(originalPrefs.equals(prefs.getAll()), "Audit preferences were not fully restored");
        }
    }

    private static JSONArray courseJson(String name) throws Exception {
        JSONObject c = new JSONObject(); c.put("id", 710001L); c.put("name", name);
        c.put("day", 0); c.put("period", 1); c.put("endPeriod", 1); c.put("weeks", "1-2");
        c.put("room", "Fictional room"); c.put("teacher", "Fictional teacher");
        return new JSONArray().put(c);
    }

    private static JSONArray eventJson(String title, int start) throws Exception {
        JSONObject e = new JSONObject(); e.put("id", 720001L); e.put("title", title);
        e.put("year", 2030); e.put("month", 1); e.put("day", 7); e.put("start", start);
        e.put("end", -1); e.put("remind", 30); e.put("location", "Fictional location");
        e.put("note", ""); e.put("showInTimetable", true); return new JSONArray().put(e);
    }

    private static JSONObject find(SharedPreferences prefs, String kind) throws Exception {
        JSONObject payload = new JSONObject(SCHEDULE.decrypt(prefs.getString(SCHEDULE_KEY, "")));
        JSONArray entries = payload.getJSONArray("entries");
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            if (kind.equals(entry.optString("kind", "course"))) return entry;
        }
        return null;
    }

    private static long firstTrigger(SharedPreferences prefs, String kind) throws Exception {
        JSONObject entry = find(prefs, kind); check(entry != null, "Missing " + kind + " reminder");
        return entry.getJSONArray("triggerTimes").getLong(0);
    }

    private static long date(int y, int m, int d, int h, int minute) {
        Calendar c = Calendar.getInstance(); c.clear(); c.set(y, m - 1, d, h, minute); return c.getTimeInMillis();
    }

    @SuppressWarnings("unchecked") private static void restore(SharedPreferences prefs, Map<String, ?> saved) {
        SharedPreferences.Editor editor = prefs.edit().clear();
        for (Map.Entry<String, ?> item : saved.entrySet()) {
            Object value = item.getValue(); String key = item.getKey();
            if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof Set) editor.putStringSet(key, new HashSet<>((Set<String>) value));
        }
        check(editor.commit(), "Could not restore audit preferences");
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
