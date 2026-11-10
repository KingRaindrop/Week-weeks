package com.weekweeks.app;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

/** Source-wiring regression checks; real system gestures still require device coverage. */
public class GuardedBottomSheetFlowTest {
    private String source(String name) throws Exception {
        Path root = Path.of("src/main/java/com/weekweeks/app");
        if (!Files.exists(root)) root = Path.of("app/src/main/java/com/weekweeks/app");
        return Files.readString(root.resolve(name)).replace("\r\n", "\n");
    }

    @Test public void everyDirtyEditorUsesGuardButOrdinarySheetsKeepMaterialBehavior() throws Exception {
        String sheets = source("PastelCourseSheet.java");
        assertEquals(3, sheets.split("new GuardedBottomSheetDialog\\(context\\) ", -1).length - 1);
        assertEquals(3, sheets.split("@Override public void cancel\\(\\)", -1).length - 1);
        assertFalse(sheets.contains("new BottomSheetDialog(context) {"));
        assertTrue(sheets.contains("BottomSheetDialog dialog = new BottomSheetDialog(context);"));
        // Existing dirty checks and discard callbacks must not be removed by the wrapper.
        assertTrue(sheets.contains("snapshot(draft).equals(initial)"));
        assertTrue(sheets.contains("allowClose[0] || typed.isEmpty()"));
        assertTrue(sheets.contains("eventSnapshot(inputs, draft, customReminder[0]).equals(initial[0])"));
        assertTrue(sheets.contains("allowClose[0] = true; holder[0].cancel();"));
    }

    @Test public void api33GuardRegistersAfterMaterialAndUnregistersBeforeDetach() throws Exception {
        String guard = source("GuardedBottomSheetDialog.java");
        String attach = guard.substring(guard.indexOf("@Override public void onAttachedToWindow()"),
                guard.indexOf("@Override public void onDetachedFromWindow()"));
        assertTrue(attach.indexOf("super.onAttachedToWindow();") < attach.indexOf("updateBackRegistration();"));
        String detach = guard.substring(guard.indexOf("@Override public void onDetachedFromWindow()"),
                guard.indexOf("@Override public void setCancelable"));
        assertTrue(detach.indexOf("clearBackRegistration();") < detach.indexOf("super.onDetachedFromWindow();"));
        assertTrue(guard.contains("Build.VERSION.SDK_INT >= 33 && attached && editorCancelable"));
        assertTrue(guard.contains("OnBackInvokedDispatcher.PRIORITY_OVERLAY"));
        assertTrue(guard.contains("unregisterOnBackInvokedCallback(registration.callback)"));
        assertTrue(guard.contains("this::handleSystemBack"));
        assertFalse(guard.contains("STATE_HIDDEN"));
    }

    @Test public void keyboardBackPrecedesVirtualCancelAndNeverDismissesDraft() throws Exception {
        String guard = source("GuardedBottomSheetDialog.java");
        String handler = guard.substring(guard.indexOf("private void handleSystemBack()"),
                guard.indexOf("/** Loaded only on Android 13+"));
        assertTrue(handler.contains("insets.isVisible(WindowInsetsCompat.Type.ime())"));
        assertTrue(handler.contains("controller.hide(WindowInsetsCompat.Type.ime());\n            return;"));
        assertTrue(handler.indexOf("controller.hide(") < handler.indexOf("        cancel();"));
        assertFalse(handler.contains("dismiss()"));
        assertFalse(handler.contains("super.cancel()"));
    }

    @Test public void api23OuterClassDoesNotReferenceFrameworkBackTypes() throws Exception {
        String guard = source("GuardedBottomSheetDialog.java");
        String outer = guard.substring(0, guard.indexOf("private static final class Api33"));
        assertFalse(outer.contains("import android.window."));
        assertFalse(outer.contains("android.window.OnBackInvoked"));
        assertTrue(outer.contains("private Object backRegistration;"));
        assertTrue(outer.contains("@androidx.annotation.RequiresApi(33)"));
    }
}
