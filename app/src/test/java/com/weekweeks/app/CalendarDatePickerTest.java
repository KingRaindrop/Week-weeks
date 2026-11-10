package com.weekweeks.app;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Wiring checks for the system calendar; JVM tests cannot instantiate Android dialogs. */
public class CalendarDatePickerTest {
    private String activitySource() throws Exception {
        Path source = Path.of("src/main/java/com/weekweeks/app/MainActivity.java");
        if (!Files.exists(source)) {
            source = Path.of("app/src/main/java/com/weekweeks/app/MainActivity.java");
        }
        return Files.readString(source, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test
    public void calendarFieldsRequireSelectionAndRetainSaveValidation() throws Exception {
        String source = activitySource();
        String dialog = source.substring(source.indexOf("private void showCalendarImportDialog("),
                source.indexOf("private EditText calendarDateInput("));
        assertTrue(dialog.contains("calendarDateInput(firstRun ? \"\" : formatDate(calendarStartMillis())"));
        assertTrue(dialog.contains("calendarDateInput(firstRun ? \"\" : formatDate(calendarEndMillis())"));
        assertFalse(dialog.contains("input(\"YYYY-MM-DD\""));
        assertTrue(dialog.indexOf("if (start == null || end == null)") < dialog.indexOf("saveAcademicCalendar("));
        assertTrue(dialog.indexOf("if (end < start)") < dialog.indexOf("saveAcademicCalendar("));
    }

    @Test
    public void pickerDisablesTypingAndLimitsDatesTo2000Through2100() throws Exception {
        String source = activitySource();
        String helper = source.substring(source.indexOf("private EditText calendarDateInput("),
                source.indexOf("private void saveAcademicCalendar("));
        assertTrue(helper.contains("field.setKeyListener(null)"));
        assertTrue(helper.contains("field.setFocusable(false)"));
        assertTrue(helper.contains("new android.app.DatePickerDialog(this,"));
        assertTrue(helper.contains("min.set(2000, Calendar.JANUARY, 1)"));
        assertTrue(helper.contains("max.set(2100, Calendar.DECEMBER, 31)"));
        assertTrue(helper.contains("setMinDate(min.getTimeInMillis())"));
        assertTrue(helper.contains("setMaxDate(max.getTimeInMillis())"));
        assertTrue(helper.contains("field.setText(formatDate(chosen.getTimeInMillis()))"));
        assertTrue(helper.contains("AppFonts.install(picker)"));
    }
}
