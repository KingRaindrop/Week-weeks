package com.weekweeks.app;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

/** Source wiring guards; real touch behavior is checked by DarkUiDeviceTest. */
public class DialogSurfaceStyleTest {
    private String source(String name) throws Exception {
        Path path = Path.of("src/main/java/com/weekweeks/app/" + name + ".java");
        if (!Files.exists(path)) path = Path.of("app").resolve(path);
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test public void preservesMaterialWrapperAndDoesNotOverrideTouchOrDismiss() throws Exception {
        String helper = source("DialogSurfaceStyle");
        assertTrue(helper.contains("background instanceof InsetDrawable"));
        assertTrue(helper.contains("inset.setDrawable(surface)"));
        assertTrue(helper.contains("surface.setBounds(bounds)"));
        assertFalse(helper.contains("setBackgroundDrawable("));
        assertFalse(helper.contains("setOnTouchListener("));
        assertFalse(helper.contains("setOnDismissListener("));
        assertFalse(helper.contains("setCanceledOnTouchOutside("));
    }

    @Test public void bothDarkDialogPathsAndEditorOverrideUseInsetSafeStyling() throws Exception {
        String main = source("MainActivity");
        String reminder = source("ReminderStatusDialog");
        assertTrue(main.contains("DialogSurfaceStyle.apply(dialog,\n                    roundedSurface(DarkUiPalette.DIALOG_SURFACE"));
        assertTrue(main.contains("dialog.getWindow().setBackgroundDrawable(editorStyle.getBackground())"));
        assertTrue(reminder.contains("DialogSurfaceStyle.apply(dialog,"));
        assertFalse(main.contains("getWindow().setBackgroundDrawable(\n                    roundedSurface(DarkUiPalette.DIALOG_SURFACE"));
        assertFalse(reminder.contains("getWindow().setBackgroundDrawable("));
    }
}
