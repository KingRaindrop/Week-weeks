package com.weekweeks.app;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

/** Source wiring checks only; rotation/touch behavior still needs Android device verification. */
public class ThemeModeFlowTest {
    private String source() throws Exception {
        Path path = Path.of("src/main/java/com/weekweeks/app/MainActivity.java");
        if (!Files.exists(path)) path = Path.of("app").resolve(path);
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private String themeDialog() throws Exception {
        String source = source();
        return source.substring(source.indexOf("private void showThemeModeDialog()"),
                source.indexOf("private void showAccentColorDialog()"));
    }

    @Test public void themeDraftIsCapturedAndDispatchedAfterRecreation() throws Exception {
        String source = source();
        String dialog = themeDialog();
        assertTrue(source.contains("else if (\"theme_mode\".equals(kind)) showThemeModeDialog();"));
        assertTrue(source.contains("outState.putBundle(\"settings_draft\", settingsDraftCapture.capture())"));
        assertTrue(source.contains("pendingSettingsRestore = savedInstanceState.getBundle(\"settings_draft\")"));
        assertTrue(dialog.contains("\"theme_mode\".equals(pendingSettingsRestore.getString(\"kind\"))"));
        assertTrue(dialog.contains("restored.getInt(\"selected\", checked)"));
        assertTrue(dialog.contains("clamp(restored.getInt(\"selected\", checked), 0, 2)"));
        assertTrue(dialog.contains(".setSingleChoiceItems(options, selected[0],"));
        assertTrue(dialog.contains("state.putString(\"kind\", \"theme_mode\")"));
        assertTrue(dialog.contains("state.putInt(\"selected\", selected[0])"));
    }

    @Test public void closingOrFailingToShowReleasesDraftCapture() throws Exception {
        String dialog = themeDialog();
        assertTrue(dialog.contains("Runnable draftCleanup = beginSettingsDraft("));
        assertTrue(dialog.contains("showOwnedDialog(token, dialog, true, draftCleanup, null)"));
        assertTrue(dialog.contains("catch (RuntimeException failure)"));
        assertTrue(dialog.contains("draftCleanup.run();"));
        assertTrue(dialog.indexOf("if (token == null) return;")
                < dialog.indexOf("pendingSettingsRestore = null;"));
    }

    @Test public void restoredChoiceIsNotSavedUntilConfirmedAndUsesPersistedBaseline() throws Exception {
        String dialog = themeDialog();
        int confirm = dialog.indexOf("dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener");
        int save = dialog.indexOf(".putInt(THEME_KEY, chosen).apply()");
        assertTrue(confirm >= 0 && save > confirm);
        assertTrue(dialog.contains("int checked = (themeMode == 1 || themeMode == 2) ? themeMode : 0;"));
        assertTrue(dialog.contains("if (chosen == checked)"));
        assertTrue(dialog.contains(".setNegativeButton(R.string.action_cancel, null)"));
        assertFalse(dialog.substring(0, confirm).contains("themeMode = chosen"));
    }
}
