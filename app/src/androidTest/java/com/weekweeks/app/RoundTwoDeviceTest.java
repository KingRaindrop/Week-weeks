package com.weekweeks.app;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Lifecycle and storage regression checks, restricted to the separate audit application. */
public final class RoundTwoDeviceTest extends Instrumentation {
    private Activity current;
    private final List<Activity> activities = new ArrayList<>();
    private boolean probe;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        probe = args != null && "true".equals(args.getString("probe"));
        start();
    }

    @Override public void onStart() {
        Throwable failure = null;
        try {
            check("com.weekweeks.app.audit".equals(getTargetContext().getPackageName()), "Refusing production target");
            if (!probe) {
                StoredDataDeviceChecks.run(this);
                RoundTwoDiagnosticsDeviceChecks.run(this);
                RoundTwoUiDeviceChecks.run(this);
            }
            current = startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            activities.add(current);
            settle();
            visibleReminderSurvives();
            waitingReminderSurvives();
            if (!probe) {
                settingsTestReminderStaysVisible();
                remindersStayOrdered();
                launchIntentReplayIsIgnored();
                forgedReminderIsRejected();
                calendarRangeIsValidated();
                courseDiscardSurvivesRotation();
            }
        } catch (Throwable error) { failure = error; }
        finally {
            try {
                ui(() -> { for (Activity activity : activities) if (!activity.isDestroyed()) activity.finish(); });
                waitForIdleSync();
            } catch (Throwable cleanup) {
                if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup);
            }
        }
        Bundle result = new Bundle();
        result.putString("stream", failure == null
                ? (probe ? "ROUND TWO PROBES COMPLETE\n" : "ROUND TWO FIXES PASS\n")
                : "ROUND TWO FIXES FAILED\n" + android.util.Log.getStackTraceString(failure));
        finish(failure == null ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    private void visibleReminderSurvives() throws Throwable {
        ui(() -> deliver("Audit visible reminder", true));
        settle();
        ui(() -> check(reminder("Audit visible reminder") != null, "Trusted reminder failed to open"));
        rebuild(true, null);
        boolean[] missing = {false};
        ui(() -> {
            Dialog dialog = reminder("Audit visible reminder");
            missing[0] = dialog == null;
            if (probe && missing[0]) { status("BUG visible reminder disappears after rotation"); return; }
            check(dialog != null, "Visible reminder disappeared after rotation");
            dialog.cancel();
        });
        if (probe && missing[0]) return;
        settle();
        rebuild(false, null);
        ui(() -> check(reminder("Audit visible reminder") == null, "Dismissed reminder reappeared"));
        status("PASS visible reminder survives rotation; dismissal remains final after recreation");
    }

    private void waitingReminderSurvives() throws Throwable {
        ui(() -> invoke("showLanguageDialog"));
        settle();
        // Rebuild on the same main-loop turn as delivery, before the old 350ms callback can run.
        rebuild(false, () -> deliver("Audit pending reminder", true));
        ui(() -> {
            check(reminder("Audit pending reminder") == null, "Reminder interrupted restored settings");
            Dialog settings = firstDialog();
            check(settings != null, "Settings dialog failed to restore");
            settings.cancel();
        });
        settle();
        ui(() -> {
            Dialog dialog = reminder("Audit pending reminder");
            if (probe && dialog == null) { status("BUG reminder received just before recreation is lost"); return; }
            check(dialog != null, "Pending reminder was lost during recreation");
            dialog.cancel();
        });
        settle();
        if (!probe) status("PASS pending reminder survives immediate recreation and waits for settings to close");
    }

    private void remindersStayOrdered() throws Throwable {
        ui(() -> {
            invoke("showLanguageDialog");
            deliver("Audit queue first", true);
            deliver("Audit queue second", true);
            deliver("Audit queue third", true);
        });
        settle();
        ui(() -> firstDialog().cancel());
        settle();
        rebuild(true, null);
        for (String title : new String[]{"Audit queue first", "Audit queue second", "Audit queue third"}) {
            ui(() -> {
                Dialog dialog = reminder(title);
                check(dialog != null, "Queued reminder missing or out of order: " + title);
                check(showingDialogs() == 1, "Reminders overlapped");
                dialog.cancel();
            });
            settle();
        }
        rebuild(false, null);
        ui(() -> check(showingDialogs() == 0, "Dismissed reminder queue reappeared"));
        status("PASS three reminders remain ordered across rotation, never overlap, and are acknowledged once");
    }

    private void settingsTestReminderStaysVisible() throws Throwable {
        android.content.Context audit = getTargetContext();
        check("com.weekweeks.app.audit".equals(audit.getPackageName()), "Refusing non-audit test notification");
        check(BuildConfig.ENABLE_TEST_COURSE, "Settings reminder regression requires the debug audit build");
        android.content.SharedPreferences prefs = audit.getSharedPreferences("week_weeks", 0);
        boolean hadEnabled = prefs.contains("notifications_enabled");
        boolean wasEnabled = prefs.getBoolean("notifications_enabled", true);
        try {
            for (boolean initiallyEnabled : new boolean[]{true, false}) {
                check(SafePreferences.commit(prefs,
                        new PreferenceTransaction().put("notifications_enabled", initiallyEnabled)),
                        "Could not prepare test reminder preference");
                ui(() -> invoke("showSettingsDialogV2"));
                settle();
                ui(() -> {
                    View overlay = (View) field(current, "settingsOverlay");
                    check(overlay != null && overlay.getParent() != null, "Settings parent failed to open");
                    View row = overlay.findViewWithTag("settings:row:test_reminder");
                    check(row != null, "Real test-reminder settings row is missing");
                    row.requestRectangleOnScreen(new android.graphics.Rect(0, 0, row.getWidth(), row.getHeight()), true);
                });
                settle();
                ui(() -> {
                    View overlay = (View) field(current, "settingsOverlay");
                    View row = overlay.findViewWithTag("settings:row:test_reminder");
                    android.graphics.Rect visible = new android.graphics.Rect();
                    check(row.isShown() && row.getGlobalVisibleRect(visible) && !visible.isEmpty(),
                            "Test-reminder settings row is not reachable");
                    check(row.performClick(), "Test-reminder row did not handle its click");
                });
                settle();
                if (!initiallyEnabled) {
                    ui(() -> {
                        Dialog prompt = reminder(current.getString(R.string.main_reminders_off_title));
                        check(prompt instanceof androidx.appcompat.app.AlertDialog,
                                "Disabled reminder did not request confirmation before enabling");
                        check(!prefs.getBoolean("notifications_enabled", true), "Opening test reminder enabled it before confirmation");
                        check(reminder(current.getString(R.string.main_test_reminder_course)) == null,
                                "Test preview appeared before enabling reminders");
                        check(showingDialogs() == 1 && !(Boolean) invoke("settingsOverlayVisible"),
                                "Settings parent overlapped the enable-reminders prompt");
                        ((androidx.appcompat.app.AlertDialog) prompt)
                                .getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
                    });
                    settle();
                }
                ui(() -> {
                    check(prefs.getBoolean("notifications_enabled", false), "Confirmed reminder switch is not enabled");
                    Dialog preview = reminder(current.getString(R.string.main_test_reminder_course));
                    check(preview != null, "Settings test reminder preview was lost or blocked after settling");
                    check(showingDialogs() == 1 && !(Boolean) invoke("settingsOverlayVisible"),
                            "Settings parent blocked the test reminder preview");
                    preview.cancel();
                });
                settle();
                ui(() -> check(showingDialogs() == 0 && !(Boolean) invoke("settingsOverlayVisible"),
                        "Acknowledged test reminder reopened a modal or settings parent"));
            }
        } finally {
            try {
                check(SafePreferences.commit(prefs, hadEnabled
                        ? new PreferenceTransaction().put("notifications_enabled", wasEnabled)
                        : new PreferenceTransaction().remove("notifications_enabled")),
                        "Could not restore the original reminder preference");
                ui(() -> invoke("scheduleClassReminderNotifications"));
            } finally {
                // The debug sender has no request-code extra, so its untagged notification uses ID 1.
                // This manager belongs only to the audit target verified above; never cancel all notifications.
                android.app.NotificationManager manager = (android.app.NotificationManager)
                        audit.getSystemService(android.content.Context.NOTIFICATION_SERVICE);
                if (manager != null) manager.cancel(1);
            }
        }
        status("PASS real settings test-reminder row shows its preview directly and after confirming enable; preference restored");
    }

    private void forgedReminderIsRejected() throws Throwable {
        rebuild(false, () -> deliver("Audit untrusted reminder", false));
        ui(() -> check(showingDialogs() == 0, "Untrusted reminder entered saved state"));
        status("PASS untrusted reminder cannot appear or enter restored state");
    }

    private void launchIntentReplayIsIgnored() throws Throwable {
        Intent[] original = {null};
        ui(() -> {
            original[0] = reminderIntent("Audit replayed launch", true);
            ((MainActivity) current).onNewIntent(new Intent(original[0]));
        });
        settle();
        // Supply the original untouched launch Intent, as the system does after process death.
        rebuild(false, () -> current.setIntent(new Intent(original[0])));
        ui(() -> {
            Dialog dialog = reminder("Audit replayed launch");
            check(dialog != null, "Restoration lost the active reminder");
            dialog.cancel();
        });
        settle();
        ui(() -> check(showingDialogs() == 0, "Original launch Intent duplicated the restored reminder"));
        rebuild(false, () -> current.setIntent(new Intent(original[0])));
        ui(() -> {
            check(showingDialogs() == 0, "Acknowledged reminder revived from original launch Intent");
            deliver("Audit new notification", true);
        });
        settle();
        ui(() -> {
            Dialog dialog = reminder("Audit new notification");
            check(dialog != null, "Launch replay guard blocked a real new notification");
            dialog.cancel();
        });
        settle();
        status("PASS saved-state restoration ignores original launch replay and still accepts new notification intents");
    }

    private void calendarRangeIsValidated() throws Throwable {
        ui(() -> {
            android.content.SharedPreferences prefs = current.getSharedPreferences("week_weeks", 0);
            java.util.Map<String, ?> before = new java.util.HashMap<>(prefs.getAll());
            java.util.Calendar start = java.util.Calendar.getInstance();
            start.clear(); start.set(2026, java.util.Calendar.SEPTEMBER, 7);
            Method save = MainActivity.class.getDeclaredMethod("saveAcademicCalendar", String.class, long.class, long.class);
            save.setAccessible(true);
            boolean accepted = (Boolean) save.invoke(current, "Audit invalid calendar", start.getTimeInMillis(),
                    AgendaDates.shift(start.getTimeInMillis(), TimetableRules.MAX_WEEK * 7));
            check(!accepted, "Calendar accepted weeks the course model cannot represent");
            check(before.equals(prefs.getAll()), "Rejected calendar changed stored preferences");
        });
        status("PASS unsupported calendar range is rejected without changing existing data");
    }

    private void courseDiscardSurvivesRotation() throws Throwable {
        ui(() -> {
            Method open = MainActivity.class.getDeclaredMethod("openPastelCourseEditor", Course.class, int.class, int.class);
            open.setAccessible(true); open.invoke(current, null, 0, 1);
            PastelCourseSheet sheet = (PastelCourseSheet) field(current, "activeCourseEditor");
            check(sheet != null && sheet.isShowing(), "Course editor failed to open");
            android.widget.EditText[] inputs = (android.widget.EditText[]) field(sheet, "activeCourseInputs");
            inputs[0].setText("Audit unsaved rotation");
            ((Dialog) field(sheet, "activeCourseDialog")).cancel();
            check(((Dialog) field(sheet, "confirmation")).isShowing(), "Discard prompt failed to open");
        });
        rebuild(true, null);
        ui(() -> {
            PastelCourseSheet sheet = (PastelCourseSheet) field(current, "activeCourseEditor");
            check(sheet != null && sheet.isShowing(), "Course editor failed to restore through Activity");
            Dialog confirmation = (Dialog) field(sheet, "confirmation");
            check(confirmation != null && confirmation.isShowing(), "Discard prompt was lost on actual rotation");
            android.widget.EditText[] inputs = (android.widget.EditText[]) field(sheet, "activeCourseInputs");
            check("Audit unsaved rotation".contentEquals(inputs[0].getText()), "Unsaved name changed during rotation");
            confirmation.cancel();
        });
        settle();
        rebuild(false, null);
        ui(() -> {
            PastelCourseSheet sheet = (PastelCourseSheet) field(current, "activeCourseEditor");
            check(sheet != null && sheet.isShowing(), "Declining discard lost the editor");
            check(field(sheet, "confirmation") == null, "Declined discard prompt reappeared after recreation");
            sheet.dismissForDestroy();
        });
        settle();
        status("PASS Activity rotation restores the course discard prompt and draft; declining does not revive the prompt");
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }

    private void deliver(String name, boolean trusted) {
        ((MainActivity) current).onNewIntent(reminderIntent(name, trusted));
    }

    private Intent reminderIntent(String name, boolean trusted) {
        return new Intent(current, MainActivity.class)
                .putExtra(MainActivity.EXTRA_SHOW_REMINDER_POPUP, true)
                .putExtra(MainActivity.EXTRA_REMINDER_COURSE_NAME, name)
                .putExtra(MainActivity.EXTRA_REMINDER_COURSE_TIME, "08:00-09:00")
                .putExtra(MainActivity.EXTRA_REMINDER_COURSE_ROOM, "Audit room")
                .putExtra(ClassReminderReceiver.EXTRA_POPUP_TOKEN,
                        trusted ? ClassReminderReceiver.popupToken(current) : "invalid-audit-token");
    }

    private void rebuild(boolean rotate, Checked before) throws Throwable {
        Activity old = current;
        ActivityMonitor monitor = addMonitor(MainActivity.class.getName(), null, false);
        try {
            ui(() -> {
                if (before != null) before.run();
                if (rotate) old.setRequestedOrientation(old.getResources().getConfiguration().orientation
                        == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                        ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                else old.recreate();
            });
            current = waitForMonitorWithTimeout(monitor, 10000);
            check(current != null && current != old, "Activity did not recreate");
            activities.add(current);
            settle();
        } finally { removeMonitor(monitor); }
    }

    private void settle() { waitForIdleSync(); android.os.SystemClock.sleep(450); waitForIdleSync(); }
    private Object invoke(String name) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(name); method.setAccessible(true);
        return method.invoke(current);
    }
    @SuppressWarnings("unchecked") private List<Dialog> dialogs() throws Exception {
        Field field = MainActivity.class.getDeclaredField("ownedDialogs"); field.setAccessible(true);
        return (List<Dialog>) field.get(current);
    }
    private Dialog firstDialog() throws Exception {
        for (Dialog dialog : dialogs()) if (dialog.isShowing()) return dialog;
        return null;
    }
    private int showingDialogs() throws Exception {
        int count = 0; for (Dialog dialog : dialogs()) if (dialog.isShowing()) count++; return count;
    }
    private Dialog reminder(String title) throws Exception {
        for (Dialog dialog : dialogs()) if (dialog.isShowing() && hasText(dialog.getWindow().getDecorView(), title)) return dialog;
        return null;
    }
    private static boolean hasText(View view, String title) {
        if (view instanceof TextView && title.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            if (hasText(((ViewGroup) view).getChildAt(i), title)) return true;
        return false;
    }
    private interface Checked { void run() throws Throwable; }
    private void ui(Checked action) throws Throwable {
        Throwable[] failure = {null};
        runOnMainSync(() -> { try { action.run(); } catch (Throwable error) { failure[0] = error; } });
        if (failure[0] != null) throw failure[0];
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private void status(String message) { Bundle result = new Bundle(); result.putString("stream", message + "\n"); sendStatus(0, result); }
}
