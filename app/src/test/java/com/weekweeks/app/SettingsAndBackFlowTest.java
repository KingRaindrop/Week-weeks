package com.weekweeks.app;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

public class SettingsAndBackFlowTest {
    private String source(String name) throws Exception {
        Path root = Path.of("src/main");
        if (!Files.exists(root)) root = Path.of("app/src/main");
        return Files.readString(root.resolve(name)).replace("\r\n", "\n");
    }

    @Test public void cardFitsSmallPhoneLandscapeAndTablet() {
        assertEquals(320, SettingsPanelBounds.width(320, 430));
        assertEquals(430, SettingsPanelBounds.width(1000, 430));
        assertEquals(480, SettingsPanelBounds.height(600));
        assertEquals(224, SettingsPanelBounds.height(280));
        assertEquals(0, SettingsPanelBounds.height(0));
        assertEquals(0, SettingsPanelBounds.width(-1, 430));
    }

    @Test public void gestureBackClosesSettingsBeforeTabsOrActivity() throws Exception {
        String activity = source("java/com/weekweeks/app/MainActivity.java");
        String handler = activity.substring(activity.indexOf("@Override public void handleOnBackPressed()"),
                activity.indexOf("        render();"));
        assertTrue(handler.indexOf("if (dismissSettingsOverlay()) return;")
                < handler.indexOf("pastelTimetable.isMine()"));
        assertTrue(handler.indexOf("pastelTimetable.isMine()")
                < handler.indexOf("setEnabled(false)"));
        assertTrue(source("AndroidManifest.xml").contains("android:enableOnBackInvokedCallback=\"true\""));
    }

    @Test public void settingsUsesSafeWindowBoundsAndOnlyBodyScrolls() throws Exception {
        String activity = source("java/com/weekweeks/app/MainActivity.java");
        String settings = activity.substring(activity.indexOf("private void showSettingsDialogV2()"),
                activity.indexOf("private void confirmRandomizeCourseColors()"));
        assertTrue(settings.contains("new AdaptiveSettingsPanel(this)"));
        assertTrue(settings.contains("Type.systemBars()"));
        assertTrue(settings.contains("Type.displayCutout()"));
        assertFalse(settings.contains("getDisplayMetrics().widthPixels"));
        assertTrue(settings.indexOf("glassPanel.addView(titleRow)") < settings.indexOf("new ScrollView(this)"));
        assertTrue(settings.contains("ViewGroup.LayoutParams.WRAP_CONTENT,\n                1f"));
    }
}
