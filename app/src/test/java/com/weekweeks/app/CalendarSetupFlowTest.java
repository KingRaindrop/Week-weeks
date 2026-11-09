package com.weekweeks.app;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Source-level wiring checks: local JVM tests cannot instantiate the Android activity. */
public class CalendarSetupFlowTest {
    private String activitySource() throws Exception {
        Path source = Path.of("src/main/java/com/weekweeks/app/MainActivity.java");
        if (!Files.exists(source)) {
            source = Path.of("app/src/main/java/com/weekweeks/app/MainActivity.java");
        }
        return Files.readString(source, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test
    public void launchDoesNotPromptForCalendar() throws Exception {
        String source = activitySource();
        String startup = source.substring(0, source.indexOf("protected void onSaveInstanceState"));
        assertFalse(startup.contains("maybePromptCalendarSetup();"));
        assertFalse(startup.contains("showCalendarImportDialog("));
    }

    @Test
    public void importPromptsOnlyAfterChangesAreSaved() throws Exception {
        String source = activitySource();
        String flow = source.substring(source.indexOf("final boolean importedChanges"),
                source.indexOf("private byte[] readUriBytes"));
        assertTrue(flow.contains("final boolean importedChanges = imported > 0 || updated > 0;"));
        assertTrue(flow.contains("if (!saveCourses(candidate)) {\n                        return;"));
        assertTrue(flow.indexOf("saveCourses(candidate)") < flow.indexOf("maybePromptCalendarSetup();"));
        assertTrue(flow.contains("if (importedChanges) maybePromptCalendarSetup();"));
        assertTrue(flow.contains(".setOnDismissListener(dismissed -> {"));
    }

    @Test
    public void configuredCalendarIsNotRequestedAgain() throws Exception {
        String source = activitySource();
        String prompt = source.substring(source.indexOf("private void maybePromptCalendarSetup()"),
                source.indexOf("private boolean containsLegacyCalendar()"));
        assertTrue(prompt.contains("getBoolean(CALENDAR_INITIALIZED_KEY, containsLegacyCalendar())"));
        assertTrue(prompt.contains("if (!initialized)"));
    }
}
