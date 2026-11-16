package com.weekweeks.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Completeness and identity checks with Android JSON/Keystore and actual save guards. */
final class StoredDataDeviceChecks {
    private static final SecureStorage STORAGE = new SecureStorage("week_weeks_courses_key");
    private static int checks;

    static void run(Instrumentation instrumentation) throws Throwable {
        Context context = instrumentation.getTargetContext();
        check("com.weekweeks.app.audit".equals(context.getPackageName()), "Refusing non-audit target");
        SharedPreferences fixture = context.getSharedPreferences("stored_data_validation_audit", Context.MODE_PRIVATE);
        SharedPreferences prefs = context.getSharedPreferences(StoredTimetableSnapshot.PREFS_NAME, Context.MODE_PRIVATE);
        Map<String, ?> beforeFixture = new HashMap<>(fixture.getAll());
        Map<String, ?> beforeApp = new HashMap<>(prefs.getAll());
        Activity[] activity = {null};
        Throwable failure = null;
        checks = 0;
        try {
            ui(instrumentation, () -> {
                invalidEvents(fixture, new JSONArray().put(event(1)).put(new JSONObject()), "partial invalid event");
                invalidEvents(fixture, new JSONArray().put(event(1)).put(JSONObject.NULL), "null event");
                invalidEvents(fixture, new JSONArray().put(event(1)).put(event(1)), "duplicate event identity");
                invalidEvents(fixture, new JSONArray().put(event(0)), "zero event identity");
                invalidEvents(fixture, new JSONArray().put(event(1).put("start", 600.5)), "fractional event time");
                invalidEvents(fixture, new JSONArray().put(event(1).put("day", 32)), "invalid event date");
                invalidEvents(fixture, new JSONArray().put(event(1).put("title", new JSONObject())), "non-text event title");
                JSONArray manyEvents = new JSONArray();
                for (int i = 1; i <= PersonalEvent.MAX_EVENTS + 1; i++) manyEvents.put(event(i));
                invalidEvents(fixture, manyEvents, "event capacity overflow");
                invalidCourses(fixture, new JSONArray().put(course(1)).put(course(2).put("name", "")), "unnamed course");
                invalidCourses(fixture, new JSONArray().put(course(1)).put(course(1)), "duplicate course identity");
                invalidCourses(fixture, new JSONArray().put(course(0)), "zero course identity");
                invalidCourses(fixture, new JSONArray().put(course(1).put("day", 7)), "out-of-range weekday");
                invalidCourses(fixture, new JSONArray().put(course(1).put("period", 12).put("endPeriod", 12)), "out-of-range period");
                invalidCourses(fixture, new JSONArray().put(course(1).put("period", 1.5)), "fractional course period");
                StringBuilder weeks = new StringBuilder();
                for (int i = 0; i < 20; i++) weeks.append("1周,");
                weeks.append("30周");
                invalidCourses(fixture, new JSONArray().put(course(1).put("weeks", weeks.toString())), "oversized teaching weeks");
                JSONArray manyCourses = new JSONArray();
                for (int i = 1; i <= StoredTimetableSnapshot.MAX_COURSES + 1; i++) manyCourses.put(course(i));
                invalidCourses(fixture, manyCourses, "course capacity overflow");

                String validCourses = STORAGE.encrypt(new JSONArray().put(course(1))
                        .put(course(2).put("weeks", "repair this week expression")).toString());
                write(fixture, new PreferenceTransaction().put(StoredTimetableSnapshot.COURSES_KEY, validCourses));
                StoredTimetableSnapshot.Courses loaded = StoredTimetableSnapshot.readCourses(fixture, 11, null);
                check(loaded.readable && loaded.items.size() == 2 && loaded.invalidWeekCount == 1,
                        "Repairable invalid weeks were removed or locked");
                JSONArray legacy = new JSONArray().put(new JSONObject().put("name", "Legacy without ID"))
                        .put(course(1));
                write(fixture, new PreferenceTransaction().remove(StoredTimetableSnapshot.COURSES_KEY)
                        .put(StoredTimetableSnapshot.LEGACY_COURSES_KEY, legacy.toString()));
                loaded = StoredTimetableSnapshot.readCourses(fixture, 11, null);
                check(loaded.readable && loaded.legacyNeedsMigration && loaded.items.size() == 2
                                && loaded.items.get(0).id != 0 && loaded.items.get(0).id != 1,
                        "Missing legacy ID collided with existing ID");
                check(loaded.items.get(0).id == StoredTimetableSnapshot.readCourses(fixture, 11, null).items.get(0).id,
                        "Legacy fallback IDs changed between reads");
                write(fixture, new PreferenceTransaction().put(StoredTimetableSnapshot.PERIOD_TIMES_KEY,
                        new JSONArray().put("08:00-08:45").put("").put("10:00-10:45").toString()));
                check(StoredTimetableSnapshot.readPeriodTimes(fixture)
                        .equals(Arrays.asList(StoredTimetableSnapshot.DEFAULT_PERIOD_TIMES)),
                        "Blank period renumbered later time slots");
                write(fixture, new PreferenceTransaction().put(StoredTimetableSnapshot.EVENTS_KEY, ""));
                check(!StoredTimetableSnapshot.readEvents(fixture).readable, "Empty ciphertext became writable empty events");
                write(fixture, new PreferenceTransaction().put(StoredTimetableSnapshot.COURSES_KEY, 1)
                        .put(StoredTimetableSnapshot.EVENTS_KEY, 1));
                check(!StoredTimetableSnapshot.readCourses(fixture, 11, null).readable
                                && !StoredTimetableSnapshot.readEvents(fixture).readable,
                        "Wrong preference types crashed or became writable");
            });

            String badCourseText = new JSONArray().put(course(101)).put(course(102).put("name", "")).toString();
            String badCourseCipher = STORAGE.encrypt(badCourseText);
            String badEventCipher = STORAGE.encrypt(new JSONArray().put(event(201)).put(new JSONObject()).toString());
            ui(instrumentation, () -> write(prefs, new PreferenceTransaction()
                    .put("notifications_enabled", false)
                    .put(StoredTimetableSnapshot.COURSES_KEY, badCourseCipher)
                    .put(StoredTimetableSnapshot.EVENTS_KEY, badEventCipher)));
            activity[0] = instrumentation.startActivitySync(new Intent(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            instrumentation.waitForIdleSync();
            ui(instrumentation, () -> {
                check((Boolean) field(activity[0], "storageLocked") && (Boolean) field(activity[0], "eventsLocked"),
                        "Activity did not lock incomplete stores");
                check(!(Boolean) call(activity[0], "saveCourses", new Class<?>[]{java.util.List.class}, new ArrayList<Course>()),
                        "Incomplete course source could be overwritten");
                check(call(activity[0], "savePersonalEvents", new Class<?>[]{java.util.List.class},
                        new ArrayList<PersonalEvent>()) != null, "Incomplete event source could be overwritten");
                check(badCourseCipher.equals(prefs.getString(StoredTimetableSnapshot.COURSES_KEY, null))
                                && badEventCipher.equals(prefs.getString(StoredTimetableSnapshot.EVENTS_KEY, null)),
                        "Activity rewrote incomplete ciphertext");
                write(prefs, new PreferenceTransaction().remove(StoredTimetableSnapshot.COURSES_KEY)
                        .put(StoredTimetableSnapshot.LEGACY_COURSES_KEY, badCourseText));
                call(activity[0], "loadCourses", new Class<?>[0]);
                check((Boolean) field(activity[0], "storageLocked")
                                && !prefs.contains(StoredTimetableSnapshot.COURSES_KEY)
                                && badCourseText.equals(prefs.getString(StoredTimetableSnapshot.LEGACY_COURSES_KEY, null)),
                        "Legacy migration published or removed a partial course set");
            });
        } catch (Throwable error) {
            failure = error;
        } finally {
            try {
                ui(instrumentation, () -> { if (activity[0] != null && !activity[0].isDestroyed()) activity[0].finish(); });
                instrumentation.waitForIdleSync();
                ui(instrumentation, () -> {
                    restore(prefs, beforeApp); restore(fixture, beforeFixture);
                    check(beforeApp.equals(prefs.getAll()) && beforeFixture.equals(fixture.getAll()),
                            "Stored-data audit preferences were not restored");
                });
            } catch (Throwable cleanup) {
                if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup);
            }
        }
        if (failure != null) throw failure;
        Bundle result = new Bundle();
        result.putString("stream", "PASS stored-data completeness/identity: " + checks
                + " checks with Android JSON, Keystore and Activity save/migration guards\n");
        instrumentation.sendStatus(0, result);
    }

    private static JSONObject course(long id) throws Exception {
        return new JSONObject().put("id", id).put("name", "Audit course " + id).put("day", 0)
                .put("period", 1).put("endPeriod", 2).put("weeks", "1-16");
    }
    private static JSONObject event(long id) throws Exception {
        return new JSONObject().put("id", id).put("title", "Audit event " + id).put("year", 2030)
                .put("month", 1).put("day", 8).put("start", 600).put("end", 660).put("remind", 15);
    }
    private static void invalidEvents(SharedPreferences prefs, JSONArray rows, String reason) throws Exception {
        String cipher = STORAGE.encrypt(rows.toString());
        write(prefs, new PreferenceTransaction().put(StoredTimetableSnapshot.EVENTS_KEY, cipher));
        StoredTimetableSnapshot.Events result = StoredTimetableSnapshot.readEvents(prefs);
        check(!result.readable && result.items.isEmpty(), "Event source accepted: " + reason);
        check(cipher.equals(prefs.getString(StoredTimetableSnapshot.EVENTS_KEY, null)), "Event ciphertext mutated");
    }
    private static void invalidCourses(SharedPreferences prefs, JSONArray rows, String reason) throws Exception {
        for (boolean encrypted : new boolean[]{true, false}) {
            String source = encrypted ? STORAGE.encrypt(rows.toString()) : rows.toString();
            String key = encrypted ? StoredTimetableSnapshot.COURSES_KEY : StoredTimetableSnapshot.LEGACY_COURSES_KEY;
            PreferenceTransaction fixture = new PreferenceTransaction().remove(StoredTimetableSnapshot.COURSES_KEY)
                    .remove(StoredTimetableSnapshot.LEGACY_COURSES_KEY).put(key, source);
            write(prefs, fixture);
            StoredTimetableSnapshot.Courses result = StoredTimetableSnapshot.readCourses(prefs, 11, null);
            check(!result.readable && result.items.isEmpty() && !result.legacyNeedsMigration,
                    "Course source accepted: " + reason + " encrypted=" + encrypted);
            check(source.equals(prefs.getString(key, null)), "Course source mutated");
        }
    }
    private static void write(SharedPreferences prefs, PreferenceTransaction transaction) {
        check(SafePreferences.commit(prefs, transaction), "Fixture preference write failed");
    }
    private static void restore(SharedPreferences prefs, Map<String, ?> snapshot) {
        PreferenceTransaction transaction = new PreferenceTransaction();
        for (String key : prefs.getAll().keySet()) if (!snapshot.containsKey(key)) transaction.remove(key);
        for (Map.Entry<String, ?> entry : snapshot.entrySet()) transaction.put(entry.getKey(), entry.getValue());
        write(prefs, transaction);
    }
    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static Object call(Object owner, String name, Class<?>[] types, Object... values) throws Throwable {
        Method method = owner.getClass().getDeclaredMethod(name, types); method.setAccessible(true);
        try { return method.invoke(owner, values); } catch (InvocationTargetException error) { throw error.getCause(); }
    }
    private interface Checked { void run() throws Throwable; }
    private static void ui(Instrumentation instrumentation, Checked action) throws Throwable {
        Throwable[] error = {null};
        instrumentation.runOnMainSync(() -> { try { action.run(); } catch (Throwable failure) { error[0] = failure; } });
        if (error[0] != null) throw error[0];
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private StoredDataDeviceChecks() { }
}
