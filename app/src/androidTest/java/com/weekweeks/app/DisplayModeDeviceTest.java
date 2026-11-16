package com.weekweeks.app;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Opt-in integration runner for the isolated audit application, never the user package. */
public final class DisplayModeDeviceTest extends Instrumentation {
    private static final String APP_PREFS = "week_weeks";
    private static final String DIAGNOSTIC_PREFS = "week_weeks_reminder_diagnostics_v1";
    private static final String SCHEDULE_KEY = "class_reminder_schedule_encrypted_v1";
    private static final String NOTIFICATIONS_KEY = "notifications_enabled";
    private static final String COURSE_NAME = "Display audit course";
    private static final String VISIBLE_EVENT = "Display audit visible event";
    private static final String HIDDEN_EVENT = "Display audit personal-only event";
    private final List<Activity> activities = new ArrayList<>();
    private MainActivity current;
    private SharedPreferences prefs;
    private long browseDate;
    private int checks;
    private boolean chooserCaptured;
    private String courseCipher, eventCipher, expectedPlan;

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Throwable failure = null;
        Context context = getTargetContext();
        Map<String, ?> appBefore = null, diagnosticsBefore = null;
        SharedPreferences diagnostics = null;
        try {
            check("com.weekweeks.app.audit".equals(context.getPackageName()), "Refusing production target");
            check(!BuildConfig.ENABLE_TEST_COURSE, "Use display-mode-audit.init.gradle to disable synthetic courses");
            check(Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED, "Grant the audit app notification permission before running");
            prefs = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE);
            diagnostics = context.getSharedPreferences(DIAGNOSTIC_PREFS, Context.MODE_PRIVATE);
            appBefore = new HashMap<>(prefs.getAll());
            diagnosticsBefore = new HashMap<>(diagnostics.getAll());
            installFixture();
            current = (MainActivity) startActivitySync(new Intent(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            activities.add(current);
            settle();
            ui(() -> {
                setField(current, "pastelBrowseDate", browseDate);
                invoke(current, "render");
                timetable().restoreUiState(true, 0);
                courseCipher = prefs.getString(StoredTimetableSnapshot.COURSES_KEY, null);
                eventCipher = prefs.getString(StoredTimetableSnapshot.EVENTS_KEY, null);
                expectedPlan = planEntries();
                check(new JSONArray(expectedPlan).length() == 3, "Fixture must schedule its course and both events");
                check(!prefs.contains(DisplayMode.PREF_KEY), "Fixture unexpectedly selected a mode");
                assertMode(DisplayMode.COMBINED);
            });
            status("PASS absent display preference defaults to combined with original course and event sources");
            for (DisplayMode mode : DisplayMode.values()) confirmMode(mode);
            cancelRestoredDraft();
            disabledRemindersStayDisabled();
            chineseLightScreenshots();
            DisplayModeViewDeviceChecks.run(this);
        } catch (Throwable error) {
            failure = error;
        } finally {
            try {
                ui(() -> { for (Activity activity : activities) if (!activity.isDestroyed()) activity.finish(); });
                waitForIdleSync();
                if (appBefore != null) {
                    Map<String, ?> restoreApp = appBefore, restoreDiagnostics = diagnosticsBefore;
                    SharedPreferences diagnosticStore = diagnostics;
                    ui(() -> {
                        ReminderScheduler.clear(context);
                        restore(prefs, restoreApp);
                        ReminderScheduler.rescheduleStored(context);
                        // Rescheduling records its result; restore those audit metadata values too.
                        restore(prefs, restoreApp);
                        restore(diagnosticStore, restoreDiagnostics);
                        check(restoreApp.equals(prefs.getAll()) && restoreDiagnostics.equals(diagnosticStore.getAll()),
                                "Audit preferences were not restored exactly");
                    });
                }
            } catch (Throwable cleanup) {
                if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup);
            }
        }
        Bundle result = new Bundle();
        result.putString("stream", failure == null
                ? "DISPLAY MODE DEVICE PASS: " + checks + " integration checks; audit preferences restored\n"
                : "DISPLAY MODE DEVICE FAILED\n" + android.util.Log.getStackTraceString(failure));
        finish(failure == null ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    private void installFixture() throws Throwable {
        Calendar now = Calendar.getInstance();
        long termStart = TimetableRules.weekAnchorMillis(now.getTimeInMillis());
        browseDate = AgendaDates.shift(termStart, 7);
        Calendar eventDay = Calendar.getInstance(); eventDay.setTimeInMillis(browseDate);
        JSONObject course = new JSONObject().put("id", 77101L).put("day", 0).put("period", 1).put("endPeriod", 1)
                .put("name", COURSE_NAME).put("weeks", "1-20").put("room", "Audit room")
                .put("teacher", "").put("color", Course.DEFAULT_COLOR);
        SecureStorage storage = new SecureStorage("week_weeks_courses_key");
        String courses = storage.encrypt(new JSONArray().put(course).toString());
        String events = storage.encrypt(new JSONArray().put(event(77201L, VISIBLE_EVENT, true, eventDay))
                .put(event(77202L, HIDDEN_EVENT, false, eventDay)).toString());
        ui(() -> {
            ReminderScheduler.clear(getTargetContext());
            restore(prefs, new HashMap<>());
            PreferenceTransaction fixture = AcademicCalendarStore.putDates(new PreferenceTransaction(),
                    termStart, AgendaDates.shift(termStart, 20 * 7 - 1))
                    .put("calendar_total_weeks", 20).put(AppLanguage.KEY, "en")
                    .put(NOTIFICATIONS_KEY, true)
                    .put(StoredTimetableSnapshot.COURSES_KEY, courses)
                    .put(StoredTimetableSnapshot.EVENTS_KEY, events);
            check(SafePreferences.commit(prefs, fixture), "Could not save isolated display fixture");
        });
    }

    private static JSONObject event(long id, String title, boolean visible, Calendar date) throws Exception {
        return new JSONObject().put("id", id).put("year", date.get(Calendar.YEAR))
                .put("month", date.get(Calendar.MONTH) + 1).put("day", date.get(Calendar.DAY_OF_MONTH))
                .put("start", 600).put("end", 660).put("remind", 10).put("title", title)
                .put("location", "Audit event room").put("note", "").put("showInTimetable", visible);
    }

    private void confirmMode(DisplayMode mode) throws Throwable {
        openSettingsMode();
        String[] beforeSchedule = {null};
        DisplayMode[] previous = {null};
        ui(() -> {
            beforeSchedule[0] = prefs.getString(SCHEDULE_KEY, null);
            previous[0] = (DisplayMode) field(current, "displayMode");
            choose(mode);
            check(field(current, "displayMode") == previous[0], "Tentative mode applied before confirmation");
            check(DisplayMode.fromStoredValue(prefs.getString(DisplayMode.PREF_KEY, null)) == previous[0],
                    "Tentative mode persisted before confirmation");
            modeDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        });
        settle();
        closeReturnedSettings();
        ui(() -> {
            check(mode.storedValue.equals(prefs.getString(DisplayMode.PREF_KEY, null)), "Confirmed mode was not persisted");
            check(modeDialogOrNull() == null, "Confirmed mode dialog stayed open");
            assertMode(mode);
            assertSourcesAndPlan(true, beforeSchedule[0]);
        });
        screenshot(mode.storedValue + ".png");
        rebuild(false);
        ui(() -> {
            assertMode(mode);
            check(modeDialogOrNull() == null, "Confirmed mode dialog returned after recreation");
            assertSourcesAndPlan(true, null);
        });
        status("PASS " + mode + ": real settings confirmation, filters, hidden recovery and recreation; data/reminders preserved");
    }

    private void cancelRestoredDraft() throws Throwable {
        openSettingsMode();
        ui(() -> {
            choose(DisplayMode.AGENDA);
            assertMode(DisplayMode.COMBINED);
            check(DisplayMode.COMBINED.storedValue.equals(prefs.getString(DisplayMode.PREF_KEY, null)),
                    "Draft changed committed preference");
        });
        rebuild(true);
        ui(() -> {
            check(modeDialog().getListView().getCheckedItemPosition() == DisplayMode.AGENDA.ordinal(),
                    "Rotation lost the tentative display choice");
            assertMode(DisplayMode.COMBINED);
            assertSourcesAndPlan(true, null);
            modeDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        });
        settle();
        closeReturnedSettings();
        rebuild(false);
        ui(() -> {
            check(modeDialogOrNull() == null, "Canceled display draft resurrected");
            assertMode(DisplayMode.COMBINED);
            assertSourcesAndPlan(true, null);
        });
        status("PASS tentative choice survives rotation; cancel neither applies nor resurrects it");
    }

    private void disabledRemindersStayDisabled() throws Throwable {
        ui(() -> check(SafePreferences.commit(prefs, new PreferenceTransaction().put(NOTIFICATIONS_KEY, false)),
                "Could not prepare disabled-reminder fixture"));
        openSettingsMode();
        String[] schedule = {null};
        ui(() -> {
            schedule[0] = prefs.getString(SCHEDULE_KEY, null);
            choose(DisplayMode.TIMETABLE);
            modeDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        });
        settle();
        closeReturnedSettings();
        ui(() -> { assertMode(DisplayMode.TIMETABLE); assertSourcesAndPlan(false, schedule[0]); });
        status("PASS mode changes also preserve an explicitly disabled reminder switch");
    }

    private void chineseLightScreenshots() throws Throwable {
        ui(() -> check(SafePreferences.commit(prefs, new PreferenceTransaction()
                        .put(AppLanguage.KEY, AppLanguage.ZH_HANS).put("theme_mode", 1)),
                "Could not prepare Chinese light-theme audit screenshots"));
        rebuild(false);
        if (current.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
            rebuild(true);
        ui(() -> {
            check(AppLanguage.ZH_HANS.equals(AppLanguage.current(current)), "Chinese language preference did not apply");
            Configuration configuration = current.getResources().getConfiguration();
            check("zh".equals((Build.VERSION.SDK_INT >= 24
                            ? configuration.getLocales().get(0) : configuration.locale).getLanguage()),
                    "Screenshots still use a non-Chinese resource configuration");
            check(!(Boolean) field(current, "isDarkMode"), "Light-theme screenshot fixture remained dark");
            timetable().setMineSelected(false);
            timetable().restoreUiState(false, 0);
            assertSourcesAndPlan(false, null);
        });
        settle();
        boolean chooserSaved = false;
        for (DisplayMode mode : DisplayMode.values()) {
            openSettingsMode();
            if (!chooserSaved) {
                screenshot("zh-mode-chooser.png");
                chooserSaved = true;
            }
            String[] schedule = {null};
            ui(() -> {
                schedule[0] = prefs.getString(SCHEDULE_KEY, null);
                choose(mode);
                modeDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            });
            settle();
            closeReturnedSettings();
            ui(() -> {
                assertMode(mode, false);
                check(!timetable().isMine(), "Mode confirmation changed the selected week tab");
                assertSourcesAndPlan(false, schedule[0]);
            });
            screenshot("zh-" + mode.storedValue + "-week.png");
            ui(() -> timetable().setMineSelected(true));
            settle();
            ui(() -> {
                assertMode(mode, false);
                check(timetable().isMine(), "My events tab did not open for screenshot");
                assertSourcesAndPlan(false, schedule[0]);
            });
            screenshot("zh-" + mode.storedValue + "-mine.png");
            ui(() -> timetable().setMineSelected(false));
            settle();
        }
        status("PASS Chinese light-theme real mode choices, week and My events screenshots; layout, dates, sources and reminders preserved");
    }

    /** Uses the actual header settings button and the actual settings row. */
    private void openSettingsMode() throws Throwable {
        ui(() -> {
            check(modeDialogOrNull() == null, "A previous display dialog is still open");
            View settings = findDescription(current.getWindow().getDecorView(), current.getString(R.string.view_timetable_settings));
            check(settings != null && settings.performClick(), "Visible timetable settings button was not clickable");
        });
        settle();
        ui(() -> {
            View overlay = (View) field(current, "settingsOverlay");
            check(overlay != null && overlay.getParent() != null, "Settings button did not open its overlay");
            View title = findText(overlay, current.getString(R.string.display_mode_title));
            check(title != null, "Display mode row missing from real settings");
            check(clickAncestor(title), "Display mode settings row was not clickable");
        });
        settle();
        ui(() -> {
            View overlay = (View) field(current, "settingsOverlay");
            check(overlay == null || overlay.getParent() == null, "Settings overlay still covered mode chooser");
            check(modeDialog().getListView().getAdapter().getCount() == 3, "Mode chooser must have exactly three choices");
        });
        if (!chooserCaptured) {
            screenshot("mode-chooser.png");
            chooserCaptured = true;
        }
    }

    /** Child dialogs now return to settings; close that real parent before testing the timetable. */
    private void closeReturnedSettings() throws Throwable {
        ui(() -> {
            View overlay = (View) field(current, "settingsOverlay");
            check(overlay != null && overlay.getParent() != null, "Mode chooser did not return to its parent settings");
            View close = overlay.findViewWithTag("settings:close");
            check(close != null && close.performClick(), "Returned settings close control is unreachable");
        });
        settle();
        ui(() -> check(field(current, "settingsOverlay") == null, "Parent settings remained over the timetable"));
    }

    private void choose(DisplayMode mode) throws Throwable {
        ListView choices = modeDialog().getListView();
        int position = mode.ordinal();
        View row = choices.getChildAt(position - choices.getFirstVisiblePosition());
        check(row != null, "Display choice is not visible");
        choices.performItemClick(row, position, choices.getAdapter().getItemId(position));
        check(choices.getCheckedItemPosition() == position, "Display choice did not become checked");
    }

    private void assertMode(DisplayMode mode) throws Throwable {
        assertMode(mode, true);
    }

    private void assertMode(DisplayMode mode, boolean agendaLayout) throws Throwable {
        check(field(current, "displayMode") == mode, "Activity display mode differs from saved choice");
        check(((List<?>) field(current, "courses")).size() == 1 && ((List<?>) field(current, "personalEvents")).size() == 2,
                "Display filter changed original in-memory data");
        PastelTimetableView view = timetable();
        List<?> courses = (List<?>) field(view, "entries"), events = (List<?>) field(view, "events");
        check(courses.size() == (mode.showsCourses() ? 1 : 0), "Incorrect rendered course subset in " + mode);
        check(events.size() == (mode.showsEvents() ? 1 : 0), "Incorrect rendered timetable event subset in " + mode);
        Object mine = field(current, "minePage");
        check(((List<?>) field(mine, "events")).size() == (mode.showsEvents() ? 2 : 0),
                "My events filter lost private-only events or exposed hidden mode data");
        View mineView = (View) field(mine, "view");
        check((findText(mineView, VISIBLE_EVENT) != null) == mode.showsEvents(), "My events visible title disagrees with mode");
        check((findText(mineView, HIDDEN_EVENT) != null) == mode.showsEvents(), "Private-only event failed to recover in My events");
        check(view.isAgenda() == agendaLayout, "Display filter changed the selected layout");
        check((Long) field(current, "pastelBrowseDate") == browseDate, "Display filter changed the browsed week");
    }

    private void assertSourcesAndPlan(boolean notifications, String exactSchedule) throws Exception {
        check(courseCipher.equals(prefs.getString(StoredTimetableSnapshot.COURSES_KEY, null)), "Mode change rewrote course ciphertext");
        check(eventCipher.equals(prefs.getString(StoredTimetableSnapshot.EVENTS_KEY, null)), "Mode change rewrote event ciphertext");
        check(prefs.getBoolean(NOTIFICATIONS_KEY, !notifications) == notifications, "Mode change altered the reminder switch");
        check(expectedPlan.equals(planEntries()), "Mode change altered reminder occurrences or hidden event reminders");
        if (exactSchedule != null) check(exactSchedule.equals(prefs.getString(SCHEDULE_KEY, null)),
                "Confirming display mode unexpectedly rebuilt the reminder schedule");
    }

    private String planEntries() throws Exception {
        String encrypted = prefs.getString(SCHEDULE_KEY, null);
        check(encrypted != null && !encrypted.isEmpty(), "Reminder fixture schedule missing");
        String decoded = new SecureStorage("week_weeks_reminder_schedule_key").decrypt(encrypted);
        return new JSONObject(decoded).getJSONArray("entries").toString();
    }

    private void rebuild(boolean rotate) throws Throwable {
        MainActivity old = current;
        ActivityMonitor monitor = addMonitor(MainActivity.class.getName(), null, false);
        try {
            ui(() -> {
                if (rotate) old.setRequestedOrientation(old.getResources().getConfiguration().orientation
                        == Configuration.ORIENTATION_LANDSCAPE ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                else old.recreate();
            });
            current = (MainActivity) waitForMonitorWithTimeout(monitor, 10000);
            check(current != null && current != old, "Display-mode activity did not recreate");
            activities.add(current);
            settle();
        } finally { removeMonitor(monitor); }
    }

    private PastelTimetableView timetable() throws Exception { return (PastelTimetableView) field(current, "pastelTimetable"); }
    private AlertDialog modeDialog() throws Exception {
        AlertDialog dialog = modeDialogOrNull();
        check(dialog != null, "Display mode dialog missing");
        return dialog;
    }
    private AlertDialog modeDialogOrNull() throws Exception {
        for (Object item : (List<?>) field(current, "ownedDialogs")) {
            Dialog dialog = (Dialog) item;
            if (dialog instanceof AlertDialog && dialog.isShowing()
                    && findText(dialog.getWindow().getDecorView(), current.getString(R.string.display_mode_title)) != null)
                return (AlertDialog) dialog;
        }
        return null;
    }
    private static View findText(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView) root).getText())) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View found = findText(((ViewGroup) root).getChildAt(i), text);
            if (found != null) return found;
        }
        return null;
    }
    private static View findDescription(View root, String description) {
        if (root.getContentDescription() != null && description.contentEquals(root.getContentDescription())) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View found = findDescription(((ViewGroup) root).getChildAt(i), description);
            if (found != null) return found;
        }
        return null;
    }
    private static boolean clickAncestor(View target) {
        for (View view = target; view != null; view = view.getParent() instanceof View ? (View) view.getParent() : null)
            if (view.isClickable()) return view.performClick();
        return false;
    }
    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static void setField(Object owner, String name, Object value) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
    }
    private static void invoke(Object owner, String name) throws Throwable {
        Method method = owner.getClass().getDeclaredMethod(name); method.setAccessible(true);
        try { method.invoke(owner); } catch (InvocationTargetException failure) { throw failure.getCause(); }
    }
    private void restore(SharedPreferences store, Map<String, ?> snapshot) {
        PreferenceTransaction transaction = new PreferenceTransaction();
        for (String key : store.getAll().keySet()) if (!snapshot.containsKey(key)) transaction.remove(key);
        for (Map.Entry<String, ?> entry : snapshot.entrySet()) transaction.put(entry.getKey(), entry.getValue());
        check(SafePreferences.commit(store, transaction), "Could not restore audit preferences");
    }
    private void screenshot(String name) throws Exception {
        waitForIdleSync();
        android.os.SystemClock.sleep(400);
        waitForIdleSync();
        Bitmap bitmap = getUiAutomation().takeScreenshot();
        check(bitmap != null, "Could not capture display-mode screenshot");
        try {
            File base = getTargetContext().getExternalFilesDir(null);
            check(base != null, "Audit external files directory unavailable");
            File directory = new File(base, "display-mode");
            check(directory.isDirectory() || directory.mkdirs(), "Could not create screenshot directory");
            File output = new File(directory, name);
            try (FileOutputStream stream = new FileOutputStream(output)) {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream), "Could not encode display screenshot");
            }
            status("SCREENSHOT " + output.getAbsolutePath());
        } finally { bitmap.recycle(); }
    }
    private interface Checked { void run() throws Throwable; }
    private void ui(Checked action) throws Throwable {
        Throwable[] failure = {null};
        runOnMainSync(() -> { try { action.run(); } catch (Throwable error) { failure[0] = error; } });
        if (failure[0] != null) throw failure[0];
    }
    private void settle() { waitForIdleSync(); android.os.SystemClock.sleep(150); waitForIdleSync(); }
    private void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private void status(String message) { Bundle result = new Bundle(); result.putString("stream", message + "\n"); sendStatus(0, result); }
}
