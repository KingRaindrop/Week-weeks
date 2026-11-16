package com.weekweeks.app;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import androidx.core.widget.NestedScrollView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;

/** Real controls with fictional in-memory state. Never writes damaged preferences or user data. */
final class EventStorageIsolationDeviceChecks {
    private EventStorageIsolationDeviceChecks() { }

    static void run(Instrumentation instrumentation) throws Throwable {
        check("com.weekweeks.app.audit".equals(instrumentation.getTargetContext().getPackageName()),
                "Event isolation checks require the audit package");
        Activity activity = instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),
                MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        Dialog[] window = {null};
        PastelCourseSheet[] editor = {null};
        try {
            instrumentation.waitForIdleSync();
            verifyEventEntrypoints(instrumentation, activity, window);
            verifyHostLock(instrumentation, activity);
            verifyEventScroll(instrumentation, activity, editor);
            Bundle status = new Bundle();
            status.putString("stream", "PASS event storage isolation: course lock preserves event entrypoints; "
                    + "event lock still protects editing; event editor scroll survives capture/restore.\n");
            instrumentation.sendStatus(0, status);
        } finally {
            ui(instrumentation, () -> {
                if (editor[0] != null) editor[0].dismissForDestroy();
                if (window[0] != null) window[0].dismiss();
                activity.finish();
            });
            instrumentation.waitForIdleSync();
        }
    }

    private static void verifyEventEntrypoints(Instrumentation test, Activity activity, Dialog[] window)
            throws Throwable {
        int[] addEvent = {0}, addCourse = {0};
        long[] selectedDate = {Long.MIN_VALUE};
        PastelTimetableView[] view = {null};
        Calendar day = Calendar.getInstance();
        day.clear(); day.set(2026, Calendar.AUGUST, 17); // Monday, fictional holiday week.
        long[] dates = AgendaDates.weekdays(day.getTimeInMillis());
        ui(test, () -> {
            PastelTimetableView.Actions actions = (PastelTimetableView.Actions) Proxy.newProxyInstance(
                    PastelTimetableView.Actions.class.getClassLoader(),
                    new Class<?>[]{PastelTimetableView.Actions.class}, (proxy, method, args) -> {
                        if ("onAddEvent".equals(method.getName())) addEvent[0]++;
                        if ("onAdd".equals(method.getName())) addCourse[0]++;
                        if ("onAddEventOnDate".equals(method.getName())) selectedDate[0] = (Long) args[0];
                        return null;
                    });
            view[0] = new PastelTimetableView(activity, false, null, actions);
            bind(view[0], true, true);
            view[0].setEvents(Collections.emptyList(), null, dates);
            window[0] = new Dialog(activity);
            window[0].setContentView(view[0]);
            window[0].show();
            view[0].showAgenda();
        });
        test.waitForIdleSync();
        ui(test, () -> {
            View add = findDescription(view[0], activity.getString(R.string.view_add_event));
            check(add != null && add.isShown() && add.performClick(), "Add-event button is unreachable");
            check(addEvent[0] == 1, "Course storage lock incorrectly blocked the independent event store");
            view[0].restoreUiState(false, 0);
        });
        test.waitForIdleSync();
        ui(test, () -> {
            String tuesday = activity.getResources().getStringArray(R.array.view_days)[1];
            View cell = findDescription(view[0], activity.getString(R.string.view_cell_outside_term_desc, tuesday));
            check(cell != null && cell.isShown() && cell.performLongClick(), "Holiday cell is unreachable");
            check(selectedDate[0] == dates[1], "Course lock blocked or changed the holiday event date");
            bind(view[0], true, false);
            view[0].restoreUiState(false, 0);
        });
        test.waitForIdleSync();
        ui(test, () -> {
            String tuesday = activity.getResources().getStringArray(R.array.view_days)[1];
            View cell = findDescription(view[0], activity.getString(R.string.view_cell_desc, tuesday, 1));
            check(cell != null && cell.performLongClick(), "Term course cell is unreachable");
            check(addCourse[0] == 0, "Course storage lock no longer protects new courses");
            bind(view[0], false, false);
        });
        test.waitForIdleSync();
        ui(test, () -> {
            String tuesday = activity.getResources().getStringArray(R.array.view_days)[1];
            View cell = findDescription(view[0], activity.getString(R.string.view_cell_desc, tuesday, 1));
            check(cell != null && cell.performLongClick(), "Unlocked course cell is unreachable");
            check(addCourse[0] == 1, "Unlocked course entrypoint stopped working");
            window[0].dismiss();
            window[0] = null;
        });
        test.waitForIdleSync();
    }

    private static void bind(PastelTimetableView view, boolean courseLocked, boolean holiday) {
        view.bind(Collections.emptyList(), Arrays.asList("08:00-08:45", "08:55-09:40"),
                "Audit week", true, true, true, null, "", "", courseLocked, holiday);
    }

    private static void verifyHostLock(Instrumentation test, Activity activity) throws Throwable {
        Field courseLock = field(MainActivity.class, "storageLocked");
        Field eventLock = field(MainActivity.class, "eventsLocked");
        Field activeEditor = field(MainActivity.class, "activeEventEditor");
        Method open = MainActivity.class.getDeclaredMethod("showPersonalEventEditor", PersonalEvent.class);
        open.setAccessible(true);
        boolean oldCourse = courseLock.getBoolean(activity), oldEvent = eventLock.getBoolean(activity);
        try {
            ui(test, () -> {
                courseLock.setBoolean(activity, true);
                eventLock.setBoolean(activity, false);
                open.invoke(activity, new Object[]{null});
                PastelCourseSheet editor = (PastelCourseSheet) activeEditor.get(activity);
                check(editor != null && editor.isShowing(), "Healthy event storage must remain editable");
                editor.dismissForDestroy();
            });
            test.waitForIdleSync();
            ui(test, () -> {
                check(activeEditor.get(activity) == null, "Event editor did not close");
                eventLock.setBoolean(activity, true);
                open.invoke(activity, new Object[]{null});
                check(activeEditor.get(activity) == null, "Locked event store must reject the editor");
            });
        } finally {
            ui(test, () -> {
                PastelCourseSheet editor = (PastelCourseSheet) activeEditor.get(activity);
                if (editor != null) editor.dismissForDestroy();
                courseLock.setBoolean(activity, oldCourse);
                eventLock.setBoolean(activity, oldEvent);
            });
            test.waitForIdleSync();
        }
    }

    private static void verifyEventScroll(Instrumentation test, Activity activity, PastelCourseSheet[] editor)
            throws Throwable {
        PersonalEvent event = new PersonalEvent();
        event.year = 2026; event.month = 12; event.day = 1;
        event.title = "Audit event scroll"; event.startMinutes = 600;
        Bundle[] state = {null};
        ui(test, () -> {
            editor[0] = new PastelCourseSheet(activity, false, null);
            editor[0].showEventEditor(false, event.copy(), ignored -> {
                throw new AssertionError("Scroll regression must never save an event");
            }, null);
            constrainEditorWindow(activity, editor[0]);
        });
        awaitScrollableEditor(test, editor);
        ui(test, () -> {
            NestedScrollView scroll = (NestedScrollView) field(PastelCourseSheet.class, "activeEventScroll").get(editor[0]);
            scroll.scrollTo(0, scroll.getChildAt(0).getHeight());
            check(scroll.getScrollY() > 0, "Laid-out constrained event form must actually scroll");
            int selectedOffset = scroll.getScrollY();
            state[0] = editor[0].captureEventEditorState();
            check(state[0].getInt("scroll", -1) == selectedOffset, "Editor capture lost the scroll position");
            editor[0].dismissForDestroy();
        });
        test.waitForIdleSync();
        ui(test, () -> {
            editor[0] = new PastelCourseSheet(activity, false, null);
            editor[0].showEventEditor(false, event.copy(), ignored -> {
                throw new AssertionError("Restoring scroll must never save an event");
            }, null, state[0], null);
            constrainEditorWindow(activity, editor[0]);
        });
        awaitScrollableEditor(test, editor);
        ui(test, () -> {
            NestedScrollView scroll = (NestedScrollView) field(PastelCourseSheet.class, "activeEventScroll").get(editor[0]);
            check(scroll.getScrollY() == state[0].getInt("scroll"), "Restored event form jumped to the top");
            editor[0].dismissForDestroy();
            editor[0] = null;
        });
        test.waitForIdleSync();
    }

    private static void constrainEditorWindow(Activity activity, PastelCourseSheet editor) throws Exception {
        Dialog dialog = (Dialog) field(PastelCourseSheet.class, "activeEventDialog").get(editor);
        int height = Math.round(440 * activity.getResources().getDisplayMetrics().density);
        dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                Math.min(height, activity.getResources().getDisplayMetrics().heightPixels));
        dialog.getWindow().setGravity(Gravity.CENTER);
    }

    private static void awaitScrollableEditor(Instrumentation test, PastelCourseSheet[] editor) throws Throwable {
        long deadline = SystemClock.uptimeMillis() + 5000;
        boolean[] ready = {false};
        int[] dimensions = {0, 0};
        do {
            ui(test, () -> {
                NestedScrollView scroll = (NestedScrollView) field(PastelCourseSheet.class, "activeEventScroll").get(editor[0]);
                dimensions[0] = scroll.getHeight();
                dimensions[1] = scroll.getChildCount() == 0 ? 0 : scroll.getChildAt(0).getHeight();
                ready[0] = scroll.isAttachedToWindow() && dimensions[0] > 0 && dimensions[1] > dimensions[0]
                        && !scroll.isLayoutRequested();
            });
            if (ready[0]) return;
            SystemClock.sleep(16);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Constrained event editor never became scrollable: viewport="
                + dimensions[0] + ", content=" + dimensions[1]);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); return field;
    }

    private static View findDescription(View root, String description) {
        if (description.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findDescription(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    private interface CheckedAction { void run() throws Throwable; }

    private static void ui(Instrumentation test, CheckedAction action) throws Throwable {
        Throwable[] error = {null};
        test.runOnMainSync(() -> { try { action.run(); } catch (Throwable failure) { error[0] = failure; } });
        if (error[0] != null) throw error[0];
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
