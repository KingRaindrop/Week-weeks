package com.weekweeks.app;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Isolated real-window and Bundle checks; never runs on the user's normal application data. */
final class ImportRecoveryDeviceChecks {
    private interface Checked { void run() throws Exception; }
    private static void ui(Instrumentation i, Checked action) throws Throwable {
        Throwable[] failure = {null};
        i.runOnMainSync(() -> { try { action.run(); } catch (Throwable t) { failure[0] = t; } });
        if (failure[0] != null) throw failure[0];
    }
    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value);
    }
    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(target, args);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    @SuppressWarnings("unchecked") private static List<Dialog> dialogs(Activity a) throws Exception {
        return (List<Dialog>) field(a, "ownedDialogs");
    }
    static void run(Instrumentation i) throws Throwable {
        check("com.weekweeks.app.audit".equals(i.getTargetContext().getPackageName()), "Isolated audit package required");
        Activity a = i.startActivitySync(new Intent(i.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        Activity[] restored = {null};
        Instrumentation.ActivityMonitor monitor = null;
        try {
            i.waitForIdleSync();
            ui(i, () -> {
                PdfCourseParser.ParsedCourse c = new PdfCourseParser.ParsedCourse();
                c.day = 0; c.period = 1; c.endPeriod = 1; c.name = "Recovery audit"; c.weeks = "1-16";
                ImportFlowState flow = new ImportFlowState(Arrays.asList(c), "Text", null);
                ImportFlowState copy = ImportFlowStateCodec.restore(ImportFlowStateCodec.save(flow));
                check(copy != null && copy.canSubmit() && copy.items().size() == 1, "Text payload Bundle roundtrip failed");
                copy.committed(new ImportPlanner.Result(1, 0, new ArrayList<>()));
                copy = ImportFlowStateCodec.restore(ImportFlowStateCodec.save(copy));
                check(copy != null && !copy.canSubmit() && copy.items().isEmpty(), "Committed result can replay after roundtrip");
                call(a, "showSettingsDialogV2", new Class<?>[0]);
                call(a, "parsedAndShowCourses", new Class<?>[]{List.class, String.class, android.net.Uri.class}, Arrays.asList(c), "Text", null);
                check(dialogs(a).isEmpty(), "First result covered settings overlay");
                call(a, "dismissSettingsOverlay", new Class<?>[0]);
            });
            i.waitForIdleSync();
            ui(i, () -> {
                check(dialogs(a).size() == 1, "Queued review not shown");
                AlertDialog review = (AlertDialog) dialogs(a).get(0);
                // Force the existing guarded failure path without touching storage or publishing courses.
                set(a, "storageLocked", true);
                review.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                check(review.isShowing(), "Failed import dismissed its review");
                check(((ImportFlowState) field(a, "importFlow")).canSubmit(), "Failed import erased payload");
                set(a, "storageLocked", false);
            });
            monitor = i.addMonitor(MainActivity.class.getName(), null, false);
            ui(i, a::recreate);
            restored[0] = i.waitForMonitorWithTimeout(monitor, 10000);
            check(restored[0] != null && restored[0] != a, "No recreated Activity");
            i.waitForIdleSync();
            ui(i, () -> {
                Activity r = restored[0];
                check(dialogs(r).size() == 1 && dialogs(r).get(0).isShowing(), "Visible review not restored");
                check(((ImportFlowState)field(r, "importFlow")).items().get(0).name.equals("Recovery audit"), "Review payload lost");
                EditText start = new EditText(r); start.setText("2026-09-01");
                EditText end = new EditText(r); end.setText("2027-01-31");
                CalendarSetupDialog.State state = new CalendarSetupDialog.State(true, "2026-09-01", "2027-01-31");
                CalendarSetupDialog editor = new CalendarSetupDialog(r, state, start, end,
                        new TextView(r), new TextView(r), System.currentTimeMillis(), System.currentTimeMillis(), (s, e) -> false);
                editor.dialog.show();
                try {
                    editor.dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                    check(editor.dialog.isShowing() && !state.finished, "Calendar failure closed required form");
                    CalendarSetupDialog.State roundtrip = CalendarSetupDialog.State.restore(editor.capture().save());
                    check(roundtrip != null && "2026-09-01".equals(roundtrip.start) && "2027-01-31".equals(roundtrip.end), "Calendar dates not saved");
                } finally { editor.closeChild(); editor.dialog.dismiss(); }
            });
        } finally {
            if (monitor != null) i.removeMonitor(monitor);
            i.runOnMainSync(() -> { if (restored[0] != null) restored[0].finish(); else a.finish(); });
            i.waitForIdleSync();
        }
    }
}
