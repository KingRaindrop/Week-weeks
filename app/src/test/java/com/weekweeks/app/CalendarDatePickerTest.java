package com.weekweeks.app;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

/** Wiring checks only: real picker/restoration behavior is covered by device checks. */
public class CalendarDatePickerTest {
    private String source(String file) throws Exception {
        Path root = Path.of("src/main/java/com/weekweeks/app");
        if (!Files.exists(root)) root = Path.of("app/src/main/java/com/weekweeks/app");
        return Files.readString(root.resolve(file)).replace("\r\n", "\n");
    }
    @Test public void calendarFieldsRetainValidationAndOnlyCloseAfterSuccessfulSave() throws Exception {
        String dialog = source("CalendarSetupDialog.java");
        assertTrue(dialog.contains("if (start == null || end == null)"));
        assertTrue(dialog.contains("if (end < start)"));
        assertTrue(dialog.indexOf("if (end < start)") < dialog.indexOf("if (!saver.save(start, end)) return;"));
        assertTrue(dialog.indexOf("if (!saver.save(start, end)) return;") < dialog.indexOf("state.finished = true;\n                dialog.dismiss();"));
        assertTrue(dialog.contains("setOnClickListener(v -> {"));
        String activity = source("MainActivity.java");
        assertTrue(activity.contains("private boolean saveAcademicCalendar("));
        assertTrue(activity.contains("if (!saveAcademicCalendar(getString(R.string.main_calendar_manual_name), start, end)) return false;"));
    }
    @Test public void pickerIsReadOnlyBoundedAndOwnedByParent() throws Exception {
        String dialog = source("CalendarSetupDialog.java");
        // Read-only picker actions remain reachable by keyboard without requesting touch focus or an IME.
        assertTrue(dialog.contains("field.setKeyListener(null);"));
        assertTrue(dialog.contains("field.setFocusable(true);"));
        assertTrue(dialog.contains("field.setFocusableInTouchMode(false);"));
        assertTrue(dialog.contains("field.setShowSoftInputOnFocus(false);"));
        assertTrue(dialog.contains("min.set(2000, Calendar.JANUARY, 1)"));
        assertTrue(dialog.contains("max.set(2100, Calendar.DECEMBER, 31)"));
        assertTrue(dialog.contains("if (closed || (picker != null && picker.isShowing())) return;"));
        assertTrue(dialog.contains("if (picker != null) picker.dismiss();"));
        assertTrue(dialog.contains("picker.getDatePicker().getYear()"));
        assertTrue(dialog.contains("openPicker(state.pickerField, true)"));
        assertTrue(source("MainActivity.java").contains("editor.closeChild();"));
    }
}
