package com.weekweeks.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Exercises a real Activity recreation, using only the isolated audit application. */
final class PasteImportDeviceChecks {
    private static Object field(Object owner, String name) throws Exception {
        Field f = owner.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(owner);
    }
    private static Object call(Object owner, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = owner.getClass().getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(owner, args);
    }
    static void run(Instrumentation instrumentation) throws Throwable {
        if (!"com.weekweeks.app.audit".equals(instrumentation.getTargetContext().getPackageName()))
            throw new AssertionError("Lifecycle checks must run in the isolated audit package");
        Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Activity activity = instrumentation.startActivitySync(intent);
        final Throwable[] failure = {null};
        instrumentation.runOnMainSync(() -> {
            try {
                call(activity, "showPasteImport", new Class<?>[0]);
                Bundle state = new Bundle();
                call(activity, "onSaveInstanceState", new Class<?>[]{Bundle.class}, state);
                if (!state.containsKey("paste_import"))
                    throw new AssertionError("Paste-import sheet is lost on Activity recreation: no saved draft");
                Object sheet = field(activity, "activePasteImport");
                EditText input = (EditText)field(sheet, "activePasteInput");
                input.setText("数学 | 周一 | 1-2节 | 1-16周 | A101\n未输入完成的第二行");
                input.setSelection(3, 8);
            } catch (Throwable t) { failure[0] = t; }
        });
        if (failure[0] != null) { instrumentation.runOnMainSync(activity::finish); throw failure[0]; }
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(MainActivity.class.getName(), null, false);
        try {
            instrumentation.runOnMainSync(activity::recreate);
            Activity restored = instrumentation.waitForMonitorWithTimeout(monitor, 10000);
            if (restored == null || restored == activity) throw new AssertionError("No recreated Activity");
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                try {
                    Object sheet = field(restored, "activePasteImport");
                    if (sheet == null) throw new AssertionError("Paste-import sheet not restored");
                    EditText input = (EditText)field(sheet, "activePasteInput");
                    if (!"数学 | 周一 | 1-2节 | 1-16周 | A101\n未输入完成的第二行".contentEquals(input.getText()))
                        throw new AssertionError("Raw pasted text was not preserved");
                    if (input.getSelectionStart() != 3 || input.getSelectionEnd() != 8)
                        throw new AssertionError("Selection was not preserved");
                    BottomSheetDialog dialog = (BottomSheetDialog)field(sheet, "activePasteDialog");
                    dialog.cancel();
                    if (!dialog.isShowing()) throw new AssertionError("Restored text discarded without confirmation");
                    dialog.dismiss();
                    Bundle afterClose = new Bundle();
                    call(restored, "onSaveInstanceState", new Class<?>[]{Bundle.class}, afterClose);
                    if (afterClose.containsKey("paste_import")) throw new AssertionError("Closed paste draft resurrects");
                } catch (Throwable t) { failure[0] = t; }
                finally { restored.finish(); }
            });
            if (failure[0] != null) throw failure[0];
        } finally { instrumentation.removeMonitor(monitor); }
    }
}
