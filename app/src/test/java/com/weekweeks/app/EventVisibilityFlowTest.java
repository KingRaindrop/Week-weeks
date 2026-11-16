package com.weekweeks.app;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.assertTrue;

/** JVM wiring checks; Android dialogs and encrypted preferences require a device. */
public class EventVisibilityFlowTest {
    private String source(String name) throws Exception {
        Path path = Path.of("src/main/java/com/weekweeks/app/" + name + ".java");
        if (!Files.exists(path)) path = Path.of("app").resolve(path);
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test public void visibilityIsPersistedAndLegacyEventsRemainVisible() throws Exception {
        String activity = source("MainActivity");
        assertTrue(activity.contains("StoredTimetableSnapshot.readEvents("));
        assertTrue(source("StoredTimetableSnapshot").contains("event.showInTimetable = o.optBoolean(\"showInTimetable\", true)"));
        assertTrue(activity.contains("o.put(\"showInTimetable\", e.showInTimetable)"));
    }

    @Test public void individualVisibilityFiltersTimetableWhileDisplayModeAlsoControlsMine() throws Exception {
        String activity = source("MainActivity");
        String binding = activity.substring(activity.indexOf("private void bindPastelEvents("),
                activity.indexOf("private void showPersonalEventDetail("));
        assertTrue(binding.contains("if (!displayMode.showsEvents() || !e.showInTimetable) continue;"));
        assertTrue(binding.contains("refreshMinePage();"));
        String mine = activity.substring(activity.indexOf("private void refreshMinePage("),
                activity.indexOf("private String cleanField("));
        assertTrue(mine.contains("minePage.setEvents(displayMode.showsEvents() ? new ArrayList<>(personalEvents) : Collections.emptyList())"));
        assertTrue(activity.contains("displayMode.showsEvents() && !eventFromMine && candidate.showInTimetable"));
    }

    @Test public void editorRestoresVisibilityAndDetectsUnsavedToggle() throws Exception {
        String sheet = source("PastelCourseSheet");
        assertTrue(sheet.contains("state.putBoolean(\"showInTimetable\", activeEventDraft.showInTimetable)"));
        assertTrue(sheet.contains("restoredState.getBoolean(\"showInTimetable\", draft.showInTimetable)"));
        assertTrue(sheet.contains("showInTimetable.setChecked(draft.showInTimetable)"));
        assertTrue(sheet.contains("draft.showInTimetable = checked"));
        String snapshot = sheet.substring(sheet.indexOf("private static String eventSnapshot("),
                sheet.indexOf("private static String pastEventLabel("));
        assertTrue(snapshot.contains("d.showInTimetable"));
    }
}
