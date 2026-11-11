package com.weekweeks.app;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

/** Wiring guards only: these do not substitute for device layout/accessibility checks. */
public class CourseCompletionFlowTest {
    private String read(String relative) throws Exception {
        Path path = Path.of("src/main/" + relative);
        if (!Files.exists(path)) path = Path.of("app").resolve(path);
        return Files.readString(path, StandardCharsets.UTF_8);
    }
    @Test public void bothViewsUseIndependentBackgroundAndSecondaryInk() throws Exception {
        String s = read("java/com/weekweeks/app/PastelTimetableView.java");
        assertTrue(s.contains("card.setBackground(courseBackground(entry, 15))"));
        assertTrue(s.contains("card.setBackground(courseBackground(entry, 10))"));
        assertTrue(s.contains("CoursePalette.finishedSecondaryInk(courseColor(entry), dark)"));
        assertTrue(s.contains("if (!dark || !isFinished(entry)) return base"));
        assertFalse(s.contains("card.setAlpha("));
    }
    @Test public void compactCardsReserveIconAndLargerCardsReserveFooter() throws Exception {
        String s = read("java/com/weekweeks/app/PastelTimetableView.java");
        assertTrue(s.contains("boolean compactFinished = finished && end == entry.start"));
        assertTrue(s.contains("titleRow.addView(finishedBadge(entry, true)"));
        assertTrue(s.contains("finished && !compactFinished ? 28 : 0"));
        assertTrue(s.contains("badge.getPaint().measureText(full)"));
        assertTrue(s.contains("s(R.string.view_desc_suffix, s(R.string.course_finished_label))"));
        assertTrue(s.contains("card.setOnClickListener(v -> openCourse(entry))"));
        assertTrue(s.contains("card.setOnLongClickListener(v -> { editCourse(v, entry); return true; })"));
    }
    @Test public void compactRoomNeverConsumesTwoLines() throws Exception {
        String s = read("java/com/weekweeks/app/PastelTimetableView.java");
        assertTrue(s.contains("room.setMaxLines(end == entry.start ? 1 : 2)"));
        assertTrue(s.contains("CourseColumn card = new CourseColumn()"));
        assertTrue(s.contains("card.title = name"));
    }
    @Test public void completionLabelsAvailableInAllLanguages() throws Exception {
        assertTrue(read("res/values/strings_course_completion.xml").contains("已结束"));
        assertTrue(read("res/values-zh-rTW/strings_course_completion.xml").contains("已結束"));
        assertTrue(read("res/values-en/strings_course_completion.xml").contains("Finished"));
    }
}
