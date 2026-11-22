package com.weekweeks.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.TimeZone;

import org.junit.Test;

public class PersonalEventTest {
    private static PersonalEvent library() {
        PersonalEvent e = new PersonalEvent();
        e.id = 1L;
        e.title = "去图书馆";
        e.year = 2026; e.month = 9; e.day = 24;
        e.startMinutes = 10 * 60;
        return e;
    }

    @Test
    public void parsesCommonTimeSpellings() {
        assertEquals(600, PersonalEvent.parseTime("10"));
        assertEquals(600, PersonalEvent.parseTime("10:00"));
        assertEquals(630, PersonalEvent.parseTime("10：30"));
        assertEquals(600, PersonalEvent.parseTime("10点"));
        assertEquals(630, PersonalEvent.parseTime("10点半"));
        assertEquals(615, PersonalEvent.parseTime("10点15分"));
        assertEquals(630, PersonalEvent.parseTime("10.30"));
        assertEquals(630, PersonalEvent.parseTime("1030"));
        assertEquals(-1, PersonalEvent.parseTime("24:00"));
        assertEquals(-1, PersonalEvent.parseTime("10:60"));
        assertEquals(-1, PersonalEvent.parseTime(""));
        assertEquals(-1, PersonalEvent.parseTime("早上"));
    }

    @Test
    public void validatesFieldsAndDates() {
        PersonalEvent e = library();
        assertNull(e.validate());
        e.title = "  ";
        assertNotNull(e.validate());
        e = library();
        e.endMinutes = 9 * 60;
        assertNotNull("end before start", e.validate());
        e.endMinutes = 11 * 60;
        assertNull(e.validate());
        e = library();
        e.month = 2; e.day = 30;
        assertNotNull("no Feb 30", e.validate());
        e = library();
        e.remindMinutes = PersonalEvent.MAX_LEAD_MINUTES + 1;
        assertNotNull(e.validate());
    }

    @Test
    public void editingRejectsSkippedLocalStartAndEndTimes() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
            PersonalEvent event = library();
            event.year = 2027; event.month = 3; event.day = 28;
            event.startMinutes = 150; event.endMinutes = 180;
            assertNull("Stored wall-clock fields remain readable", event.validate());
            assertFalse(event.hasValidLocalTimes());
            assertNotNull("02:30 is skipped on this date", event.validateForEditing());
            event.startMinutes = 90; event.endMinutes = 150;
            assertNull(event.validate());
            assertFalse(event.hasValidLocalTimes());
            assertNotNull("An end time in the skipped hour is invalid too", event.validateForEditing());
            event.startMinutes = 150; event.endMinutes = PersonalEvent.NO_END;
            assertFalse(event.hasValidLocalTimes());
            assertNotNull(event.validateForEditing());
        } finally { TimeZone.setDefault(original); }
    }

    @Test
    public void editingAllowsValidSpringAndAutumnTransitionIntervals() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
            PersonalEvent event = library();
            event.year = 2027; event.month = 3; event.day = 28;
            event.startMinutes = 90; event.endMinutes = 210;
            assertNull(event.validateForEditing());
            assertTrue(event.hasValidLocalTimes());
            assertEquals(60 * 60_000L, event.endMillis() - event.startMillis());
            event.month = 10; event.day = 31;
            assertNull(event.validateForEditing());
            assertTrue(event.hasValidLocalTimes());
            assertEquals(180 * 60_000L, event.endMillis() - event.startMillis());
            event.startMinutes = 135; event.endMinutes = 165;
            assertNull("Repeated local times still exist", event.validateForEditing());
            assertTrue(event.hasValidLocalTimes());
            assertEquals(30 * 60_000L, event.endMillis() - event.startMillis());
        } finally { TimeZone.setDefault(original); }
    }

    @Test
    public void movingTimeZonesDoesNotInvalidateOrMutateStoredFields() {
        TimeZone original = TimeZone.getDefault();
        try {
            PersonalEvent event = library();
            event.year = 2027; event.month = 3; event.day = 28;
            event.startMinutes = 150; event.endMinutes = 180;
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            assertNull(event.validateForEditing());
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
            assertNull("Loader validation must not lock previously saved events", event.validate());
            assertNotNull(event.validateForEditing());
            assertEquals(150, event.startMinutes);
            assertEquals(180, event.endMinutes);
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            assertNull(event.validate());
            assertNull(event.validateForEditing());
        } finally { TimeZone.setDefault(original); }
    }

    @Test
    public void editingRetainsExistingValidationErrors() {
        PersonalEvent event = library();
        event.title = "";
        assertEquals(event.validate(), event.validateForEditing());
        event = library();
        event.startMinutes = -1;
        assertEquals(event.validate(), event.validateForEditing());
    }

    @Test
    public void reminderInstantUsesLeadMinutes() {
        PersonalEvent e = library();
        assertEquals(-1L, e.triggerAtMillis());
        e.remindMinutes = 25;
        Calendar expected = Calendar.getInstance();
        expected.clear();
        expected.set(2026, Calendar.SEPTEMBER, 24, 9, 35, 0);
        assertEquals(expected.getTimeInMillis(), e.triggerAtMillis());
        e.remindMinutes = 0;
        assertEquals(e.startMillis(), e.triggerAtMillis());
    }

    @Test
    public void pastUsesEndTimeWhenPresent() {
        PersonalEvent e = library();
        long start = e.startMillis();
        assertFalse(e.isPast(start - 1));
        assertTrue("no end: over once started", e.isPast(start));
        e.endMinutes = 11 * 60;
        assertFalse(e.isPast(start + 30 * 60_000L));
        assertTrue(e.isPast(start + 60 * 60_000L));
    }

    @Test
    public void expiredReminderIsDistinctFromPastEvent() {
        PersonalEvent e = library();
        e.remindMinutes = 10;
        long fiveMinutesBeforeStart = e.startMillis() - 5 * 60_000L;
        assertFalse(e.isPast(fiveMinutesBeforeStart));
        assertTrue(e.reminderExpired(fiveMinutesBeforeStart));
        assertFalse(e.reminderExpired(e.triggerAtMillis() - 1));
        assertTrue(e.reminderExpired(e.triggerAtMillis()));
        assertNull("historical records remain valid", e.validate());
    }

    @Test
    public void noReminderDoesNotRequireExpiredReminderConfirmation() {
        PersonalEvent e = library();
        assertFalse(e.reminderExpired(e.startMillis() + 86_400_000L));
        e.remindMinutes = 0;
        assertFalse(e.reminderExpired(e.startMillis() - 1));
        assertTrue(e.reminderExpired(e.startMillis()));
        e.remindMinutes = PersonalEvent.NO_REMINDER;
        assertFalse(e.reminderExpired(e.startMillis()));
    }

    @Test
    public void longMultilineNoteRemainsLegal() {
        PersonalEvent e = library();
        StringBuilder note = new StringBuilder("a");
        for (int i = 1; i < 80; i++) note.append("\na");
        e.note = note.toString();
        assertEquals(159, e.note.length());
        assertNull(e.validate());
    }

    @Test
    public void weekdayIsMondayBased() {
        assertEquals(3, library().weekday()); // 2026-09-24 is a Thursday
    }

    @Test
    public void eventsDefaultToVisibleAndCopiesRetainVisibility() {
        PersonalEvent event = library();
        assertTrue(event.showInTimetable);
        assertTrue(event.copy().showInTimetable);
        event.showInTimetable = false;
        assertFalse(event.copy().showInTimetable);
        assertNull(event.copy().validate());
    }

    @Test
    public void hidingEventDoesNotDisableReminderOrChangeItsDate() {
        PersonalEvent event = library();
        event.remindMinutes = 10;
        long trigger = event.triggerAtMillis();
        long day = event.dayMillis();
        event.showInTimetable = false;
        assertEquals(trigger, event.triggerAtMillis());
        assertEquals(day, event.dayMillis());
        assertEquals(10, event.copy().remindMinutes);
    }

    @Test
    public void labels() {
        assertEquals("10:00", library().timeLabel());
        assertEquals("不提醒", PersonalEvent.remindLabel(-1));
        assertEquals("提前 1 天", PersonalEvent.remindLabel(1440));
        assertEquals("提前 2 小时", PersonalEvent.remindLabel(120));
        assertEquals("提前 45 分钟", PersonalEvent.remindLabel(45));
        assertEquals("1 小时 30 分钟后开始", PersonalEvent.startsInLabel(90));
        assertEquals("现在开始", PersonalEvent.startsInLabel(0));
    }
}
