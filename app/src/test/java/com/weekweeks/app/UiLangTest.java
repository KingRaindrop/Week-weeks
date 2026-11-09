package com.weekweeks.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class UiLangTest {
    @After
    public void reset() {
        UiLang.set(UiLang.HANS);
    }

    @Test
    public void defaultIsSimplified() {
        assertEquals("提前 10 分钟", PersonalEvent.remindLabel(10));
        assertEquals("珊瑚", UiAccentPalette.name(UiAccentPalette.CORAL_INDEX));
        assertEquals("雾霾蓝", CoursePalette.name(0));
        assertEquals(TextTimetableParser.EXAMPLE, TextTimetableParser.example());
    }

    @Test
    public void englishLabels() {
        UiLang.set(UiLang.EN);
        assertEquals("No reminder", PersonalEvent.remindLabel(-1));
        assertEquals("10 min before", PersonalEvent.remindLabel(10));
        assertEquals("1 day before", PersonalEvent.remindLabel(1440));
        assertEquals("Starts in 1 hr 30 min", PersonalEvent.startsInLabel(90));
        assertEquals("Coral", UiAccentPalette.name(UiAccentPalette.CORAL_INDEX));
        assertEquals("Haze Blue", CoursePalette.name(0));
        assertEquals("Event reminder", ReminderTiming.statusText(true, -1, -1, 0));
        assertEquals("Class starts in 5 min", ReminderTiming.statusText(false, 300_000L, -1, 0));
        assertTrue(TimetableRules.parseWeeks("abc").error.startsWith("Unrecognized weeks"));
        TextTimetableParser.Result result = TextTimetableParser.parse("高数 | 周八 | 1-2", 10, 200);
        assertTrue(result.errors.get(0).startsWith("Line 1: Unrecognized day"));
    }

    @Test
    public void englishExampleStillParses() {
        UiLang.set(UiLang.EN);
        TextTimetableParser.Result result = TextTimetableParser.parse(TextTimetableParser.example(), 10, 200);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(5, result.courses.size());
    }

    @Test
    public void traditionalLabels() {
        UiLang.set(UiLang.HANT);
        assertEquals("10 分鐘後開始", PersonalEvent.startsInLabel(10));
        assertEquals("已下課", ReminderTiming.statusText(false, 1000L, 2000L, 3000L));
        assertEquals("藍色", UiAccentPalette.name(0));
    }
}
