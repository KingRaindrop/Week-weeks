package com.weekweeks.app;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.ScrollView;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Real Activity rotation and navigation checks, using only the isolated audit application. */
public final class NavigationStateDeviceTest extends Instrumentation {
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
                ReminderIsolationDeviceChecks.run(this);
                TimezoneDeviceChecks.run(this);
            }
            current = startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            activities.add(current);
            waitForIdleSync();
            if (!probe) browseDateSurvivesTimeZoneChange();
            dateNavigationSurvives();
            weekNavigationSurvives();
            if (!probe) EventStorageIsolationDeviceChecks.run(this);
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
                ? (probe ? "NAVIGATION PROBES COMPLETE\n" : "NAVIGATION FIXES PASS\n")
                : "NAVIGATION FIXES FAILED\n" + android.util.Log.getStackTraceString(failure));
        finish(failure == null ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    private void dateNavigationSurvives() throws Throwable {
        long[] original = {0};
        ui(() -> {
            original[0] = (Long) field(current, "pastelBrowseDate");
            call("showPastelDatePicker");
            DatePickerDialog dialog = (DatePickerDialog) field(current, "browseDatePickerDialog");
            check(dialog != null && dialog.isShowing(), "Date navigation did not open");
            dialog.getDatePicker().updateDate(2027, Calendar.MARCH, 17);
        });
        rotate();
        final boolean[] missing = {false};
        ui(() -> {
            DatePickerDialog dialog = (DatePickerDialog) field(current, "browseDatePickerDialog");
            missing[0] = dialog == null || !dialog.isShowing();
            if (probe && missing[0]) {
                status("BUG date navigation and tentative selection lost on rotation");
                return;
            }
            check(!missing[0], "Date navigation disappeared on rotation");
            DatePicker picker = dialog.getDatePicker();
            check(picker.getYear() == 2027 && picker.getMonth() == Calendar.MARCH && picker.getDayOfMonth() == 17,
                    "Tentative navigation date was lost");
            check(original[0] == (Long) field(current, "pastelBrowseDate"), "Tentative navigation date applied early");
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        });
        if (probe && missing[0]) return;
        waitForIdleSync();
        rotate();
        ui(() -> {
            check(field(current, "browseDatePickerDialog") == null, "Cancelled date picker reappeared");
            check(original[0] == (Long) field(current, "pastelBrowseDate"), "Cancellation changed viewed date");
            call("showPastelDatePicker");
            DatePickerDialog dialog = (DatePickerDialog) field(current, "browseDatePickerDialog");
            dialog.getDatePicker().updateDate(2027, Calendar.MARCH, 17);
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        });
        waitForIdleSync();
        rotate();
        ui(() -> {
            check(field(current, "browseDatePickerDialog") == null, "Confirmed date picker reappeared");
            Calendar chosen = Calendar.getInstance(); chosen.clear(); chosen.set(2027, Calendar.MARCH, 17);
            check((Long) field(current, "pastelBrowseDate") == chosen.getTimeInMillis(), "Confirmed navigation date lost");
        });
        status("PASS date navigation survives rotation; cancel preserves viewed date; confirm applies once");
    }

    private void browseDateSurvivesTimeZoneChange() throws Throwable {
        java.util.TimeZone before = java.util.TimeZone.getDefault();
        try {
            ui(() -> {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
                call("refreshBrowseTimeZone");
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Tokyo"));
                Calendar chosen = Calendar.getInstance(); chosen.clear(); chosen.set(2027, Calendar.MARCH, 17);
                Method browse = MainActivity.class.getDeclaredMethod("browsePastelDate", long.class);
                browse.setAccessible(true); browse.invoke(current, chosen.getTimeInMillis());
                check((Long) field(current, "pastelBrowseDate") == chosen.getTimeInMillis(),
                        "New date was wrongly converted from the stale time zone");
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Berlin"));
                call("showPastelDatePicker");
                DatePickerDialog picker = (DatePickerDialog) field(current, "browseDatePickerDialog");
                check(picker.getDatePicker().getYear() == 2027
                        && picker.getDatePicker().getMonth() == Calendar.MARCH
                        && picker.getDatePicker().getDayOfMonth() == 17, "Browsing date drifted after travel");
                picker.cancel();
            });
            waitForIdleSync();
            rotate();
            ui(() -> {
                Calendar chosen = Calendar.getInstance(); chosen.clear(); chosen.set(2027, Calendar.MARCH, 17);
                check((Long) field(current, "pastelBrowseDate") == chosen.getTimeInMillis(),
                        "Browsed local date was lost on recreation after travel");
            });
        } finally {
            ui(() -> {
                java.util.TimeZone.setDefault(before);
                call("refreshBrowseTimeZone");
                call("returnToCurrentWeek");
                call("scheduleClassReminderNotifications");
            });
            waitForIdleSync();
        }
        status("PASS browsing and new date selections preserve the local date across time-zone changes and rotation");
    }

    private void weekNavigationSurvives() throws Throwable {
        int[] originalScroll = {0};
        // Use the short real window so this fixture has content below the fold on large emulators too.
        if (current.getResources().getConfiguration().orientation
                != android.content.res.Configuration.ORIENTATION_LANDSCAPE) rotate();
        ui(() -> call("showWeekPickerDialog"));
        waitForIdleSync();
        ui(() -> {
            Dialog dialog = (Dialog) field(current, "weekPickerDialog");
            check(dialog != null && dialog.isShowing(), "Week picker did not open");
            ScrollView scroll = find(dialog.getWindow().getDecorView(), ScrollView.class);
            check(scroll != null, "Week picker has no scroll area");
            scroll.fullScroll(View.FOCUS_DOWN);
        });
        waitForIdleSync();
        ui(() -> {
            Dialog dialog = (Dialog) field(current, "weekPickerDialog");
            ScrollView scroll = find(dialog.getWindow().getDecorView(), ScrollView.class);
            originalScroll[0] = scroll.getScrollY();
            check(originalScroll[0] > 0, "Week fixture must scroll before rotation");
        });
        if (!probe) {
            Activity old = current;
            ActivityMonitor monitor = addMonitor(MainActivity.class.getName(), null, false);
            try {
                ui(old::recreate);
                current = waitForMonitorWithTimeout(monitor, 10000);
                check(current != null && current != old, "Activity did not recreate at the same size");
                activities.add(current); waitForIdleSync();
                ui(() -> {
                    Dialog dialog = (Dialog) field(current, "weekPickerDialog");
                    check(dialog != null && dialog.isShowing(), "Week picker lost on same-size recreation");
                    check(find(dialog.getWindow().getDecorView(), ScrollView.class).getScrollY() == originalScroll[0],
                            "Week scroll position lost on same-size recreation");
                });
            } finally { removeMonitor(monitor); }
        }
        rotate();
        final boolean[] missing = {false};
        ui(() -> {
            Dialog dialog = (Dialog) field(current, "weekPickerDialog");
            missing[0] = dialog == null || !dialog.isShowing();
            if (probe && missing[0]) {
                status("BUG week picker lost on rotation");
                return;
            }
            check(!missing[0], "Week picker disappeared on rotation");
            ScrollView scroll = find(dialog.getWindow().getDecorView(), ScrollView.class);
            int maxScroll = Math.max(0, scroll.getChildAt(0).getHeight() - scroll.getHeight()
                    + scroll.getPaddingTop() + scroll.getPaddingBottom());
            check(scroll.getScrollY() == Math.min(originalScroll[0], maxScroll), "Week picker lost its scroll position");
            Dialog before = dialog;
            call("showWeekPickerDialog");
            check(before == field(current, "weekPickerDialog"), "Duplicate week picker opened");
            TextView date = findText(dialog.getWindow().getDecorView(), current.getString(R.string.week_picker_find_date));
            check(date != null && date.performClick(), "Find date action missing");
        });
        if (probe && missing[0]) return;
        waitForIdleSync();
        ui(() -> {
            check(field(current, "weekPickerDialog") == null, "Week picker failed to close before date picker");
            DatePickerDialog date = (DatePickerDialog) field(current, "browseDatePickerDialog");
            check(date != null && date.isShowing(), "Find date successor failed to open");
            date.cancel();
        });
        waitForIdleSync();
        rotate();
        ui(() -> {
            check(field(current, "weekPickerDialog") == null && field(current, "browseDatePickerDialog") == null,
                    "Cancelled navigation window reappeared");
            call("showWeekPickerDialog");
        });
        waitForIdleSync();
        ui(() -> {
            Dialog dialog = (Dialog) field(current, "weekPickerDialog");
            View target = findDescription(dialog.getWindow().getDecorView(), current.getString(R.string.week_picker_week, 2));
            check(target != null && target.performClick(), "Week 2 selection failed");
        });
        waitForIdleSync();
        rotate();
        ui(() -> {
            check(field(current, "weekPickerDialog") == null, "Chosen week picker reopened");
            long semester = (Long) call("calendarStartMillis");
            long shown = (Long) call("pastelDisplayedStart");
            check(AgendaDates.week(semester, shown) == 2, "Week selection did not persist");
        });
        status("PASS week picker survives rotation; successor opens; cancellation and selection do not reopen windows");
    }

    private void rotate() throws Throwable {
        Activity old = current;
        ActivityMonitor monitor = addMonitor(MainActivity.class.getName(), null, false);
        try {
            boolean landscape = old.getResources().getConfiguration().orientation
                    != android.content.res.Configuration.ORIENTATION_LANDSCAPE;
            ui(() -> old.setRequestedOrientation(landscape
                    ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            current = waitForMonitorWithTimeout(monitor, 10000);
            check(current != null && current != old, "Activity did not recreate");
            activities.add(current);
            waitForIdleSync();
        } finally { removeMonitor(monitor); }
    }

    private Object call(String name) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(name); method.setAccessible(true);
        return method.invoke(current);
    }
    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static <T extends View> T find(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            T result = find(((ViewGroup) view).getChildAt(i), type); if (result != null) return result;
        }
        return null;
    }
    private static TextView findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            TextView result = findText(((ViewGroup) view).getChildAt(i), text); if (result != null) return result;
        }
        return null;
    }
    private static View findDescription(View view, String prefix) {
        if (view.getContentDescription() != null && (view.getContentDescription().toString().equals(prefix)
                || view.getContentDescription().toString().startsWith(prefix + ","))) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View result = findDescription(((ViewGroup) view).getChildAt(i), prefix); if (result != null) return result;
        }
        return null;
    }
    private interface Checked { void run() throws Throwable; }
    private void ui(Checked action) throws Throwable {
        Throwable[] failure = {null};
        runOnMainSync(() -> { try { action.run(); } catch (Throwable error) { failure[0] = error; } });
        if (failure[0] != null) throw failure[0];
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private void status(String text) { Bundle result = new Bundle(); result.putString("stream", text + "\n"); sendStatus(0, result); }
}
