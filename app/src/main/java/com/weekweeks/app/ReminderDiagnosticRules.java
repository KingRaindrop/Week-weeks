package com.weekweeks.app;

/** Pure Java outcomes: deliberately contain no names, locations, notification text or exception messages. */
final class ReminderDiagnosticRules {
    enum Code {
        NONE, APP_REMINDERS_OFF, PERMISSION_DENIED, NOTIFICATIONS_BLOCKED, CHANNEL_BLOCKED,
        CHANNEL_UNAVAILABLE, NOTIFICATION_SERVICE_UNAVAILABLE,
        POST_SUBMITTED, POST_SUBMITTED_QUIET, POST_SECURITY_ERROR, POST_ERROR,
        SCHEDULE_EXACT, SCHEDULE_INEXACT, NO_FUTURE, STORE_FAILED,
        ALARM_UNAVAILABLE, SCHEDULE_SECURITY_ERROR, SCHEDULE_ERROR,
        RECEIVE_ACCEPTED, RECEIVE_INVALID, RECEIVE_STALE, RECEIVE_UNKNOWN_ENTRY,
        TEST_INVALID, TEST_EXPIRED, TEST_CANCELED, TEST_NOT_PENDING, INVALID_DELAY, RESTORE_FAILED
    }

    private ReminderDiagnosticRules() { }

    static boolean scheduleSucceeded(Code code) {
        return code == Code.SCHEDULE_EXACT || code == Code.SCHEDULE_INEXACT || code == Code.NO_FUTURE;
    }

    /** Preserve the first failure; an empty entry must never hide a later failed alarm. */
    static Code mergeSchedule(Code previous, Code next) {
        if (!scheduleSucceeded(previous)) return previous;
        if (!scheduleSucceeded(next)) return next;
        if (previous == Code.SCHEDULE_INEXACT || next == Code.SCHEDULE_INEXACT) return Code.SCHEDULE_INEXACT;
        if (previous == Code.SCHEDULE_EXACT || next == Code.SCHEDULE_EXACT) return Code.SCHEDULE_EXACT;
        return Code.NO_FUTURE;
    }

    static Code notificationBlocker(boolean appEnabled, boolean permission, boolean systemEnabled,
                                    boolean managerAvailable, boolean channelAvailable, boolean channelBlocked) {
        if (!appEnabled) return Code.APP_REMINDERS_OFF;
        if (!permission) return Code.PERMISSION_DENIED;
        if (!managerAvailable) return Code.NOTIFICATION_SERVICE_UNAVAILABLE;
        if (!systemEnabled) return Code.NOTIFICATIONS_BLOCKED;
        if (!channelAvailable) return Code.CHANNEL_UNAVAILABLE;
        if (channelBlocked) return Code.CHANNEL_BLOCKED;
        return Code.NONE;
    }

    static boolean validTestDelivery(String expectedToken, String suppliedToken, long expectedAt,
                                     long suppliedAt, long now, long expiresAt) {
        return expectedToken != null && !expectedToken.isEmpty() && expectedToken.equals(suppliedToken)
                && expectedAt > 0 && expectedAt == suppliedAt && now >= expectedAt && now <= expiresAt;
    }

    static long testTriggerAt(long now, long delayMillis) {
        if (delayMillis < 10_000L || delayMillis > 5 * 60_000L || now <= 0
                || now > Long.MAX_VALUE - delayMillis) return -1;
        return now + delayMillis;
    }
}
