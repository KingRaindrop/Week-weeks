package com.weekweeks.app;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Real-window checks for the import chain. Run only in the isolated audit application. */
final class ImportChainDeviceChecks {
    private static Object field(Object owner, String name) throws Exception {
        Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner);
    }
    private static Object call(Object owner, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = owner.getClass().getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(owner, args);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    @SuppressWarnings("unchecked")
    private static List<Dialog> owned(Activity activity) throws Exception {
        return (List<Dialog>) field(activity, "ownedDialogs");
    }

    static void run(Instrumentation i) throws Throwable {
        check("com.weekweeks.app.audit".equals(i.getTargetContext().getPackageName()), "Use isolated audit package");
        Intent intent = new Intent(i.getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Activity activity = i.startActivitySync(intent);
        Throwable[] failure = {null};
        try {
            i.runOnMainSync(() -> {
                try {
                    // A background PDF result arrives while period settings are open.
                    call(activity, "showPeriodSettingsDialog", new Class<?>[0]);
                    check(owned(activity).size() == 1, "Period settings not owned");
                    Dialog settings = owned(activity).get(0);
                    PdfCourseParser.ParsedCourse parsed = new PdfCourseParser.ParsedCourse();
                    parsed.day = 0; parsed.period = 1; parsed.endPeriod = 1;
                    parsed.name = "Import chain audit"; parsed.weeks = "1-16";
                    List<PdfCourseParser.ParsedCourse> items = new ArrayList<>();
                    items.add(parsed);
                    call(activity, "parsedAndShowCourses", new Class<?>[]{List.class, String.class, Uri.class},
                            items, "PDF", null);
                    check(owned(activity).size() == 1 && owned(activity).get(0) == settings,
                            "Import review covered another window");
                    check(field(activity, "importFlow") != null, "Import result was dropped");
                    settings.dismiss();
                } catch (Throwable t) { failure[0] = t; }
            });
            if (failure[0] != null) throw failure[0];
            i.waitForIdleSync();
            i.runOnMainSync(() -> {
                try {
                    check(((ImportFlowState) field(activity, "importFlow")).canSubmit(), "Deferred review lost its payload");
                    check(owned(activity).size() == 1 && owned(activity).get(0).isShowing(), "Review not shown after close");
                    Dialog review = owned(activity).get(0);
                    // Rapid secondary entries must not stack on the review.
                    call(activity, "showFontDialog", new Class<?>[0]);
                    call(activity, "confirmDeleteCurrentConfiguration", new Class<?>[0]);
                    check(owned(activity).size() == 1 && owned(activity).get(0) == review, "Window stacked on review");
                    review.dismiss();
                } catch (Throwable t) { failure[0] = t; }
            });
            if (failure[0] != null) throw failure[0];
            // Dialog posts its OnDismiss callback: do not assert ownership in the dismissing task.
            i.waitForIdleSync();
            i.runOnMainSync(() -> {
                try {
                    ModalCoordinator coordinator = (ModalCoordinator) field(activity, "modalCoordinator");
                    check(coordinator.canOpen(), "Cancelled review kept ownership");
                    check(field(activity, "importFlow") == null, "Cancelled import could reopen");
                } catch (Throwable t) { failure[0] = t; }
            });
            if (failure[0] != null) throw failure[0];
        } finally {
            i.runOnMainSync(activity::finish);
            i.waitForIdleSync();
        }
    }
}
