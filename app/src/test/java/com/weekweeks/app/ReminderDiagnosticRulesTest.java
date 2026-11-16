package com.weekweeks.app;

import org.junit.Test;
import static org.junit.Assert.*;
import static com.weekweeks.app.ReminderDiagnosticRules.*;

public class ReminderDiagnosticRulesTest {
    @Test public void notificationFailuresAreSpecificAndNeverPretendToBeSubmitted() {
        assertEquals(Code.APP_REMINDERS_OFF, notificationBlocker(false, true, true, true, true, false));
        assertEquals(Code.PERMISSION_DENIED, notificationBlocker(true, false, true, true, true, false));
        assertEquals(Code.NOTIFICATION_SERVICE_UNAVAILABLE, notificationBlocker(true, true, true, false, true, false));
        assertEquals(Code.NOTIFICATIONS_BLOCKED, notificationBlocker(true, true, false, true, true, false));
        assertEquals(Code.CHANNEL_UNAVAILABLE, notificationBlocker(true, true, true, true, false, false));
        assertEquals(Code.CHANNEL_BLOCKED, notificationBlocker(true, true, true, true, true, true));
        assertEquals(Code.NONE, notificationBlocker(true, true, true, true, true, false));
    }

    @Test public void aggregateNeverHidesFailuresOrInexactFallback() {
        assertEquals(Code.NO_FUTURE, mergeSchedule(Code.NO_FUTURE, Code.NO_FUTURE));
        assertEquals(Code.SCHEDULE_EXACT, mergeSchedule(Code.NO_FUTURE, Code.SCHEDULE_EXACT));
        assertEquals(Code.SCHEDULE_INEXACT, mergeSchedule(Code.SCHEDULE_EXACT, Code.SCHEDULE_INEXACT));
        assertEquals(Code.SCHEDULE_INEXACT, mergeSchedule(Code.SCHEDULE_INEXACT, Code.NO_FUTURE));
        assertEquals(Code.SCHEDULE_ERROR, mergeSchedule(Code.NO_FUTURE, Code.SCHEDULE_ERROR));
        assertEquals(Code.STORE_FAILED, mergeSchedule(Code.STORE_FAILED, Code.SCHEDULE_EXACT));
        assertEquals(Code.SCHEDULE_ERROR, mergeSchedule(Code.SCHEDULE_ERROR, Code.NO_FUTURE));
        assertTrue(scheduleSucceeded(Code.NO_FUTURE));
        assertTrue(scheduleSucceeded(Code.SCHEDULE_INEXACT));
        assertFalse(scheduleSucceeded(Code.POST_SUBMITTED));
    }

    @Test public void testAlarmIsBoundedAndOverflowSafe() {
        assertEquals(61_000L, testTriggerAt(1000L, 60_000L));
        assertEquals(11_000L, testTriggerAt(1000L, 10_000L));
        assertEquals(301_000L, testTriggerAt(1000L, 300_000L));
        assertEquals(-1L, testTriggerAt(1000L, 9999L));
        assertEquals(-1L, testTriggerAt(1000L, 300_001L));
        assertEquals(-1L, testTriggerAt(0L, 60_000L));
        assertEquals(-1L, testTriggerAt(Long.MAX_VALUE, 60_000L));
    }

    @Test public void testDeliveryRequiresCurrentTokenExactTimeAndValidityWindow() {
        assertTrue(validTestDelivery("token", "token", 100, 100, 100, 200));
        assertTrue(validTestDelivery("token", "token", 100, 100, 200, 200));
        assertFalse(validTestDelivery("token", "old", 100, 100, 150, 200));
        assertFalse(validTestDelivery("token", "token", 100, 101, 150, 200));
        assertFalse(validTestDelivery("token", "token", 100, 100, 99, 200));
        assertFalse(validTestDelivery("token", "token", 100, 100, 201, 200));
        assertFalse(validTestDelivery("", "", 100, 100, 150, 200));
        assertFalse(validTestDelivery(null, null, 100, 100, 150, 200));
        assertFalse(validTestDelivery("token", "token", 0, 0, 150, 200));
    }
}
