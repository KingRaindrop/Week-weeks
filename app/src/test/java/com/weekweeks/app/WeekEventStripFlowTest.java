package com.weekweeks.app;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.assertTrue;

/** Source wiring checks; actual sizing/touch behavior is verified on an Android device. */
public class WeekEventStripFlowTest {
    private String source() throws Exception {
        Path path = Path.of("src/main/java/com/weekweeks/app/PastelTimetableView.java");
        if (!Files.exists(path)) path = Path.of("app").resolve(path);
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test public void eventStripUsesCourseColumnBoundariesAndExistingDetailAction() throws Exception {
        String source = source();
        String strip = source.substring(source.indexOf("private void buildEventStrip()"), source.indexOf("private void place("));
        assertTrue(strip.contains("WeekEventLayout.forDay(events, day)"));
        assertTrue(strip.contains("Math.round(left + day * columnWidth)"));
        assertTrue(strip.contains("Math.round(left + (day + 1) * columnWidth)"));
        assertTrue(strip.contains("actions.onEvent(event.id)"));
        assertTrue(strip.contains("event.startMinutes / 60"));
        assertTrue(strip.contains("event.title"));
    }

    @Test public void emptyStripHasNoHeightAndCourseOverlayMovesTogether() throws Exception {
        String source = source();
        assertTrue(source.contains("eventStripHeight = rowsHeight == 0 ? 0 : rowsHeight + dp(4)"));
        assertTrue(source.contains("place(ruler, 0, courseTop() + p * rowHeight"));
        assertTrue(source.contains("place(card, x, courseTop() + (entry.start - 1) * rowHeight"));
        assertTrue(source.contains("y = courseTop() + (p + (minute - start) / (end - start)) * rowHeight"));
        assertTrue(source.contains("clip.set(0, courseTop(), getWidth(), getHeight())"));
    }

    @Test public void eachDayExpandsAndWeekChangeResetsExpansion() throws Exception {
        String source = source();
        assertTrue(source.contains("expandedEvents[selectedDay] = !expandedEvents[selectedDay]"));
        assertTrue(source.contains("WeekEventLayout.hiddenCount(dayEvents.size(), expandedEvents[day])"));
        assertTrue(source.contains("if (!java.util.Arrays.equals(dayDateMillis, dateMillis)) grid.collapseEvents()"));
        String binding = source.substring(source.indexOf("public void setEvents("), source.indexOf("/** Opens the"));
        assertTrue(binding.contains("grid.rebuild()"));
        assertTrue(binding.contains("rebuildList()"));
    }
}
