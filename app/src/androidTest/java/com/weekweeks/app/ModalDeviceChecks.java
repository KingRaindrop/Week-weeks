package com.weekweeks.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Real-window regression checks. Run only in the isolated audit application. */
final class ModalDeviceChecks {
    private static Object field(Object owner, String name) throws Exception {
        Class<?> type = owner.getClass();
        while (type != null) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f.get(owner); }
            catch (NoSuchFieldException missing) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
    private static void set(Object owner, String name, Object value) throws Exception {
        Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true); f.set(owner, value);
    }
    private static Object call(Object owner, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = owner.getClass().getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(owner, args);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private interface Checked { void run() throws Exception; }
    private static void ui(Instrumentation i, Checked step) throws Throwable {
        Throwable[] failure = {null};
        i.runOnMainSync(() -> { try { step.run(); } catch (Throwable t) { failure[0] = t; } });
        if (failure[0] != null) throw failure[0];
    }
    private static TextView button(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView)root).getText())) return (TextView)root;
        if (root instanceof ViewGroup) for (int n = 0; n < ((ViewGroup)root).getChildCount(); n++) {
            TextView found = button(((ViewGroup)root).getChildAt(n), text);
            if (found != null) return found;
        }
        return null;
    }
    private static BottomSheetDialog dialog(PastelCourseSheet sheet) throws Exception {
        return (BottomSheetDialog)field(sheet, "mainDialog");
    }
    private static CountDownLatch observeDismiss(PastelCourseSheet sheet) {
        CountDownLatch latch = new CountDownLatch(1);
        Runnable previous = sheet.onDismissed;
        sheet.onDismissed = () -> { if (previous != null) previous.run(); latch.countDown(); };
        return latch;
    }
    private static void await(Instrumentation i, CountDownLatch latch) throws Exception {
        check(latch.await(10, TimeUnit.SECONDS), "Window did not finish dismissal");
        i.waitForIdleSync();
    }
    @SuppressWarnings("unchecked")
    static void run(Instrumentation i) throws Throwable {
        check("com.weekweeks.app.audit".equals(i.getTargetContext().getPackageName()), "Use isolated audit package");
        Intent intent = new Intent(i.getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Activity activity = i.startActivitySync(intent);
        Class<?> courseType = Course.class;
        java.lang.reflect.Constructor<?> constructor = courseType.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object course = constructor.newInstance();
        set(course, "id", 991001L);
        set(course, "name", "Modal audit course");
        set(course, "day", 0);
        set(course, "period", 1);
        set(course, "endPeriod", 1);
        set(course, "weeks", "1-16");
        set(course, "color", 0xFF446FA6);
        PersonalEvent event = new PersonalEvent();
        Calendar today = Calendar.getInstance();
        event.id = 991002; event.title = "Modal audit event";
        event.year = today.get(Calendar.YEAR); event.month = today.get(Calendar.MONTH) + 1;
        event.day = today.get(Calendar.DAY_OF_MONTH); event.startMinutes = 600;
        try {
            ui(i, () -> {
                ((List<Object>)field(activity, "courses")).add(course);
                ((List<PersonalEvent>)field(activity, "personalEvents")).add(event);
                // Long-press edit followed immediately by tap, another long press and bottom add.
                call(activity, "showPastelCourseEditor", new Class<?>[]{courseType,int.class,int.class}, course, 0, 1);
                PastelCourseSheet editor = (PastelCourseSheet)field(activity, "activeCourseEditor");
                check(editor != null && editor.isShowing(), "Course editor missing");
                call(activity, "showPastelCourseDetail", new Class<?>[]{courseType}, course);
                call(activity, "showPastelCourseEditor", new Class<?>[]{courseType,int.class,int.class}, course, 0, 1);
                call(activity, "showPersonalEventDetail", new Class<?>[]{PersonalEvent.class}, event);
                call(activity, "showPastelCourseEditor", new Class<?>[]{courseType,int.class,int.class}, null, 0, 1);
                check(field(activity, "activeCourseEditor") == editor, "Repeated entry replaced course editor");
                check(field(activity, "activeCourseDetail") == null, "Tap overlapped course editor");
                check(field(activity, "activeEventDetail") == null, "Event overlapped course editor");
                EditText[] inputs = (EditText[])field(editor, "activeCourseInputs");
                inputs[0].setText("unsaved audit draft");
                dialog(editor).cancel();
                check(editor.isShowing(), "Dirty course editor vanished before discard decision");
                BottomSheetDialog confirmation = (BottomSheetDialog)field(editor, "confirmation");
                check(confirmation != null && confirmation.isShowing(), "No discard confirmation");
                dialog(editor).cancel();
                check(field(editor, "confirmation") == confirmation, "Duplicate discard confirmation");
                confirmation.dismiss();
            });
            closeAndReject(i, activity, (PastelCourseSheet)field(activity, "activeCourseEditor"), event);
            // Detail -> edit must be sequenced by actual dismissal, not by elapsed time.
            PastelCourseSheet[] detail = {null}; CountDownLatch[] transition = {null};
            ui(i, () -> {
                call(activity, "showPastelCourseDetail", new Class<?>[]{courseType}, course);
                detail[0] = (PastelCourseSheet)field(activity, "activeCourseDetail");
                transition[0] = observeDismiss(detail[0]);
                dialog(detail[0]).setDismissWithAnimation(true);
                TextView edit = button(dialog(detail[0]).getWindow().getDecorView(), activity.getString(R.string.sheet_edit_course));
                check(edit != null, "Course edit action missing"); edit.performClick();
                call(activity, "showPersonalEventDetail", new Class<?>[]{PersonalEvent.class}, event);
                check(field(activity, "activeEventDetail") == null, "Underlying click entered handoff gap");
            });
            await(i, transition[0]);
            ui(i, () -> check(field(activity, "activeCourseEditor") != null, "Course detail did not switch to edit"));
            closeAndReject(i, activity, (PastelCourseSheet)field(activity, "activeCourseEditor"), event);
            ui(i, () -> {
                set(activity, "eventFromMine", true);
                call(activity, "showMineSheet", new Class<?>[0]);
                call(activity, "showPersonalEventDetail", new Class<?>[]{PersonalEvent.class}, event);
                detail[0] = (PastelCourseSheet)field(activity, "activeEventDetail");
                transition[0] = observeDismiss(detail[0]);
                dialog(detail[0]).setDismissWithAnimation(true);
                TextView edit = button(dialog(detail[0]).getWindow().getDecorView(), activity.getString(R.string.sheet_edit_event));
                check(edit != null, "Event edit action missing"); edit.performClick();
                call(activity, "showPersonalEventDetail", new Class<?>[]{PersonalEvent.class}, event);
                call(activity, "showPastelCourseDetail", new Class<?>[]{courseType}, course);
                check(field(activity, "activeCourseDetail") == null, "Course overlapped event transition");
            });
            await(i, transition[0]);
            ui(i, () -> {
                PastelCourseSheet editor = (PastelCourseSheet)field(activity, "activeEventEditor");
                check(editor != null && editor.isShowing(), "Event detail did not switch to editor");
                call(activity, "showPersonalEventEditor", new Class<?>[]{PersonalEvent.class}, event);
                call(activity, "showPersonalEventEditor", new Class<?>[]{PersonalEvent.class}, (Object)null);
                check(field(activity, "activeEventEditor") == editor, "Repeated event edit replaced owner");
                EditText[] inputs = (EditText[])field(editor, "activeEventInputs");
                inputs[0].setText("unsaved event audit draft");
                dialog(editor).cancel();
                check(editor.isShowing(), "Dirty event disappeared");
                check(field(editor, "confirmation") != null, "Event discard guard lost");
            });
            closeAndReject(i, activity, (PastelCourseSheet)field(activity, "activeEventEditor"), event);
            ui(i, () -> check(((PastelTimetableView)field(activity, "pastelTimetable")).isMine(), "Mine tab lost after editor close"));
        } finally { i.runOnMainSync(activity::finish); i.waitForIdleSync(); }
        verifyRestore(i, true, courseType);
        verifyRestore(i, false, courseType);
    }

    private static void verifyRestore(Instrumentation i, boolean course, Class<?> courseType) throws Throwable {
        Intent intent = new Intent(i.getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Activity activity = i.startActivitySync(intent);
        Activity[] restored = {null};
        Instrumentation.ActivityMonitor monitor = i.addMonitor(MainActivity.class.getName(), null, false);
        try {
            ui(i, () -> {
                if (course) {
                    // A new course in-term uses the real bottom-add path; bypass only the outside-term choice.
                    call(activity, "openPastelCourseEditor", new Class<?>[]{courseType,int.class,int.class}, null, 0, 1);
                } else {
                    set(activity, "eventFromMine", true);
                    call(activity, "showMineSheet", new Class<?>[0]);
                    call(activity, "showPersonalEventEditor", new Class<?>[]{PersonalEvent.class}, (Object)null);
                }
                PastelCourseSheet sheet = (PastelCourseSheet)field(activity, course ? "activeCourseEditor" : "activeEventEditor");
                EditText[] inputs = (EditText[])field(sheet, course ? "activeCourseInputs" : "activeEventInputs");
                inputs[0].setText("draft survives modal recreation");
                activity.recreate();
            });
            restored[0] = i.waitForMonitorWithTimeout(monitor, 10000);
            check(restored[0] != null && restored[0] != activity, "Editor Activity did not recreate");
            i.waitForIdleSync();
            ui(i, () -> {
                PastelCourseSheet sheet = (PastelCourseSheet)field(restored[0], course ? "activeCourseEditor" : "activeEventEditor");
                check(sheet != null && sheet.isShowing(), "Editor was not restored");
                EditText[] inputs = (EditText[])field(sheet, course ? "activeCourseInputs" : "activeEventInputs");
                check("draft survives modal recreation".contentEquals(inputs[0].getText()), "Draft was lost on recreation");
                ModalCoordinator coordinator = (ModalCoordinator)field(restored[0], "modalCoordinator");
                check(!coordinator.canOpen(), "Restored editor has no ownership");
                dialog(sheet).cancel();
                check(sheet.isShowing() && field(sheet, "confirmation") != null, "Restored dirty draft lost discard guard");
                if (!course) check(((PastelTimetableView)field(restored[0], "pastelTimetable")).isMine(), "Mine tab lost on recreation");
            });
        } finally {
            i.removeMonitor(monitor);
            i.runOnMainSync(() -> { if (restored[0] != null) restored[0].finish(); else activity.finish(); });
            i.waitForIdleSync();
        }
    }

    private static void closeAndReject(Instrumentation i, Activity activity, PastelCourseSheet owner, PersonalEvent event) throws Throwable {
        CountDownLatch latch = observeDismiss(owner);
        ui(i, () -> {
            BottomSheetDialog d = dialog(owner); d.setDismissWithAnimation(true); d.dismiss();
            ModalCoordinator coordinator = (ModalCoordinator)field(activity, "modalCoordinator");
            check(coordinator.state() == ModalCoordinator.State.CLOSING, "Animated close released token early");
            call(activity, "showPersonalEventDetail", new Class<?>[]{PersonalEvent.class}, event);
            check(field(activity, "activeEventDetail") == null, "Click during closing opened another detail");
        });
        await(i, latch);
        ui(i, () -> check(((ModalCoordinator)field(activity, "modalCoordinator")).state() == ModalCoordinator.State.IDLE, "Dismiss did not release token"));
    }
}
