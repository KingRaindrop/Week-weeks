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
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Actual Activity/storage integration, fictional fixtures and reversible audit preferences only. */
final class ReminderIsolationDeviceChecks {
    private static final String PREFS = "week_weeks";
    private static final String COURSE = "courses_encrypted_v2";
    private static final String LEGACY = "courses";
    private static final String EVENTS = "personal_events_encrypted_v1";
    private static final String BROKEN = "audit-intentionally-unreadable";
    private static final String DIAGNOSTICS = "week_weeks_reminder_diagnostics_v1";

    static void run(Instrumentation instrumentation) throws Throwable {
        Context context = instrumentation.getTargetContext();
        check("com.weekweeks.app.audit".equals(context.getPackageName()), "Refusing non-audit target");
        check(ReminderScheduler.status(context).scheduleReadable, "Baseline audit schedule must be restorable");
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences diagnostics = context.getSharedPreferences(DIAGNOSTICS, Context.MODE_PRIVATE);
        Map<String, ?> before = new HashMap<>(prefs.getAll());
        Map<String, ?> beforeDiagnostics = new HashMap<>(diagnostics.getAll());
        Activity[] activity = {null};
        Throwable failure = null;
        try {
            long eventAt = System.currentTimeMillis() + 3L * 24 * 60 * 60_000L;
            ReminderScheduler.Entry lockedEvent = new ReminderScheduler.Entry(91701,
                    "Audit locked event", "10:00", "", 0, new long[]{eventAt}, ReminderScheduler.KIND_EVENT);
            ui(instrumentation, () -> {
                prepare(context, new PreferenceTransaction().put(LEGACY, legacyCourseJson()).put(EVENTS, BROKEN));
                ReminderScheduler.replaceSchedule(context, Arrays.asList(lockedEvent));
            });
            activity[0] = launch(instrumentation);
            ui(instrumentation, () -> {
                check((Boolean) field(activity[0], "eventsLocked"), "Unreadable event source was not locked");
                check(!(Boolean) field(activity[0], "storageLocked"), "Legacy course migration failed");
                check(prefs.contains(COURSE) && !prefs.contains(LEGACY), "Startup did not exercise legacy migration");
                assertRetained(context, lockedEvent);
                call(activity[0], "deleteCurrentConfiguration", new Class<?>[0]);
                check(BROKEN.equals(prefs.getString(EVENTS, null)), "Deleting timetable rewrote unreadable events");
                assertRetained(context, lockedEvent);
                check(plan(context).size() == 1, "Deleted timetable still has course reminders");
            });
            status(instrumentation, "PASS startup migration and timetable deletion retain the unreadable event partition");
            finish(instrumentation, activity);

            long courseAt = System.currentTimeMillis() + 2L * 24 * 60 * 60_000L;
            ReminderScheduler.Entry lockedCourse = new ReminderScheduler.Entry(91702,
                    "Audit locked course", "08:00", "", 0, new long[]{courseAt}, ReminderScheduler.KIND_COURSE);
            ui(instrumentation, () -> {
                // An empty readable event source plus an unreadable course source.
                prepare(context, new PreferenceTransaction().put(COURSE, BROKEN).remove(EVENTS));
                ReminderScheduler.replaceSchedule(context, Arrays.asList(lockedCourse));
            });
            activity[0] = launch(instrumentation);
            PersonalEvent event = futureEvent();
            ui(instrumentation, () -> {
                check((Boolean) field(activity[0], "storageLocked"), "Unreadable course source was not locked");
                check(!(Boolean) field(activity[0], "eventsLocked"), "Healthy event source was locked");
                assertRetained(context, lockedCourse);
                Object result = call(activity[0], "savePersonalEvents", new Class<?>[]{List.class}, Arrays.asList(event));
                check(result == null, "Saving an independent event failed");
                check(BROKEN.equals(prefs.getString(COURSE, null)), "Saving event overwrote locked course source");
                assertRetained(context, lockedCourse);
                check(plan(context).size() == 2 && contains(context, event.title, ReminderScheduler.KIND_EVENT),
                        "Healthy event plan did not coexist with locked course plan");
                result = call(activity[0], "savePersonalEvents", new Class<?>[]{List.class}, new ArrayList<PersonalEvent>());
                check(result == null && plan(context).size() == 1, "Deleting healthy event changed locked course reminders");
                assertRetained(context, lockedCourse);
                result = call(activity[0], "savePersonalEvents", new Class<?>[]{List.class}, Arrays.asList(event));
                check(result == null, "Restoring fictional event failed");
                call(activity[0], "deleteCurrentConfiguration", new Class<?>[0]);
                check(!prefs.contains(COURSE) && plan(context).size() == 1
                                && contains(context, event.title, ReminderScheduler.KIND_EVENT),
                        "Deleting locked timetable lost independent healthy event reminders");
            });
            status(instrumentation, "PASS healthy event save/delete retains locked courses; timetable deletion keeps healthy events");
        } catch (Throwable error) {
            failure = error;
        } finally {
            try {
                finish(instrumentation, activity);
                ui(instrumentation, () -> {
                    ReminderScheduler.clear(context);
                    restore(prefs, before);
                    ReminderScheduler.rescheduleStored(context);
                    // Resubmission records diagnostics; restore the exact preference snapshot afterwards.
                    restore(prefs, before);
                    restore(diagnostics, beforeDiagnostics);
                    check(before.equals(prefs.getAll()), "Audit preferences were not fully restored");
                    check(beforeDiagnostics.equals(diagnostics.getAll()), "Audit diagnostics were not fully restored");
                });
            } catch (Throwable cleanup) {
                if (failure == null) failure = cleanup;
                else failure.addSuppressed(cleanup);
            }
        }
        if (failure != null) throw failure;
    }

    private static void prepare(Context context, PreferenceTransaction fixture) {
        ReminderScheduler.clear(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        PreferenceTransaction reset = new PreferenceTransaction()
                .remove(COURSE).remove(LEGACY).remove(EVENTS).remove("class_reminder_codes")
                .remove("period_times_v1").remove("calendar_start_millis").remove("calendar_end_millis")
                .remove("calendar_total_weeks").remove("calendar_initialized").remove("calendar_name")
                .put("notifications_enabled", true);
        check(SafePreferences.commit(prefs, reset), "Fixture reset failed");
        check(SafePreferences.commit(prefs, fixture), "Fixture preferences failed");
    }

    private static String legacyCourseJson() throws Exception {
        JSONObject course = new JSONObject();
        course.put("id", 91801L); course.put("name", "Audit migration course");
        course.put("day", 0); course.put("period", 1); course.put("endPeriod", 1);
        course.put("weeks", "1-30"); course.put("color", 0xFFE2E9FC);
        return new JSONArray().put(course).toString();
    }

    private static PersonalEvent futureEvent() {
        Calendar date = Calendar.getInstance(); date.add(Calendar.DAY_OF_YEAR, 4);
        PersonalEvent event = new PersonalEvent(); event.id = 91802L;
        event.year = date.get(Calendar.YEAR); event.month = date.get(Calendar.MONTH) + 1;
        event.day = date.get(Calendar.DAY_OF_MONTH); event.startMinutes = 10 * 60;
        event.endMinutes = 11 * 60; event.remindMinutes = 15;
        event.title = "Audit independent event";
        return event;
    }

    private static Activity launch(Instrumentation instrumentation) {
        Activity activity = instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        instrumentation.waitForIdleSync();
        return activity;
    }

    private static void finish(Instrumentation instrumentation, Activity[] activity) throws Throwable {
        if (activity[0] == null) return;
        ui(instrumentation, () -> { if (!activity[0].isDestroyed()) activity[0].finish(); });
        instrumentation.waitForIdleSync();
        activity[0] = null;
    }

    private static void assertRetained(Context context, ReminderScheduler.Entry expected) throws Exception {
        for (ReminderScheduler.Entry actual : plan(context)) {
            if (actual.name.equals(expected.name) && actual.kind.equals(expected.kind)) {
                check(actual.requestCode == expected.requestCode && Arrays.equals(actual.triggerTimes, expected.triggerTimes),
                        "Retained partition changed its code or trigger times");
                return;
            }
        }
        throw new AssertionError("Locked reminder partition disappeared");
    }

    private static boolean contains(Context context, String name, String kind) throws Exception {
        for (ReminderScheduler.Entry entry : plan(context)) if (entry.name.equals(name) && entry.kind.equals(kind)) return true;
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<ReminderScheduler.Entry> plan(Context context) throws Exception {
        Method load = ReminderScheduler.class.getDeclaredMethod("loadSchedule", Context.class);
        load.setAccessible(true);
        Object data = load.invoke(null, context);
        List<ReminderScheduler.Entry> entries = (List<ReminderScheduler.Entry>) field(data, "entries");
        Set<Integer> codes = new HashSet<>();
        for (ReminderScheduler.Entry entry : entries) check(codes.add(entry.requestCode), "Duplicate request code in combined plan");
        return entries;
    }

    private static void restore(SharedPreferences preferences, Map<String, ?> snapshot) {
        PreferenceTransaction transaction = new PreferenceTransaction();
        for (String key : preferences.getAll().keySet()) if (!snapshot.containsKey(key)) transaction.remove(key);
        for (Map.Entry<String, ?> entry : snapshot.entrySet()) transaction.put(entry.getKey(), entry.getValue());
        check(SafePreferences.commit(preferences, transaction), "Audit preference restoration failed");
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static Object call(Object owner, String name, Class<?>[] parameters, Object... arguments) throws Throwable {
        Method method = owner.getClass().getDeclaredMethod(name, parameters); method.setAccessible(true);
        try { return method.invoke(owner, arguments); }
        catch (InvocationTargetException error) { throw error.getCause(); }
    }
    private interface Checked { void run() throws Throwable; }
    private static void ui(Instrumentation instrumentation, Checked action) throws Throwable {
        Throwable[] failure = {null};
        instrumentation.runOnMainSync(() -> { try { action.run(); } catch (Throwable error) { failure[0] = error; } });
        if (failure[0] != null) throw failure[0];
    }
    private static void status(Instrumentation instrumentation, String message) {
        Bundle result = new Bundle(); result.putString("stream", message + "\n"); instrumentation.sendStatus(0, result);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private ReminderIsolationDeviceChecks() { }
}
