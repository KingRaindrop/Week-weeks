package com.weekweeks.app;

import android.app.Instrumentation;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Uses the real AlarmManager/receiver/notification path, only in the isolated audit package. */
final class RoundTwoDiagnosticsDeviceChecks {
    static void run(Instrumentation test) throws Throwable {
        Context context = test.getTargetContext();
        check("com.weekweeks.app.audit".equals(context.getPackageName()), "Refusing production target");
        SharedPreferences app = context.getSharedPreferences("week_weeks", 0);
        SharedPreferences diagnostics = context.getSharedPreferences("week_weeks_reminder_diagnostics_v1", 0);
        Map<String, ?> before = new HashMap<>(diagnostics.getAll());
        boolean hadEnabled = app.contains("notifications_enabled");
        boolean enabled = app.getBoolean("notifications_enabled", true);
        try {
            check(SafePreferences.commit(app, new PreferenceTransaction().put("notifications_enabled", true)), "Fixture save failed");
            check(ReminderDiagnostics.cancelTest(context).code != ReminderDiagnosticRules.Code.STORE_FAILED, "Test cleanup failed");
            long started = SystemClock.elapsedRealtime();
            ReminderDiagnostics.Outcome outcome = ReminderDiagnostics.scheduleTest(context, 10_000L);
            check(outcome.accepted(), "Real diagnostic alarm rejected: " + outcome.code);
            long deadline = diagnostics.getLong("pending_test_elapsed", 0L);
            check(deadline >= started + 10_000L && deadline <= SystemClock.elapsedRealtime() + 10_000L,
                    "Diagnostic alarm did not store an elapsed-realtime deadline");
            long timeout = SystemClock.elapsedRealtime() + 30_000L;
            while (diagnostics.contains("pending_test_token") && SystemClock.elapsedRealtime() < timeout) SystemClock.sleep(250L);
            check(!diagnostics.contains("pending_test_token"), "Real diagnostic alarm did not arrive within 30 seconds");
            ReminderDiagnostics.Snapshot snapshot = ReminderDiagnostics.snapshot(context);
            check(snapshot.testReceive.code == ReminderDiagnosticRules.Code.RECEIVE_ACCEPTED,
                    "Real receiver rejected diagnostic alarm: " + snapshot.testReceive.code);
            check(snapshot.testPost.code == ReminderDiagnosticRules.Code.POST_SUBMITTED
                    || snapshot.testPost.code == ReminderDiagnosticRules.Code.POST_SUBMITTED_QUIET,
                    "Real notification was not submitted: " + snapshot.testPost.code);
            check(SystemClock.elapsedRealtime() >= deadline - 2000L, "Real diagnostic alarm arrived too early");
            check(ReminderDiagnostics.scheduleTest(context, 60_000L).accepted(), "Second diagnostic alarm failed");
            check(ReminderDiagnostics.cancelTest(context).code == ReminderDiagnosticRules.Code.TEST_CANCELED,
                    "Pending diagnostic alarm did not cancel");
            check(!diagnostics.contains("pending_test_token") && !diagnostics.contains("pending_test_elapsed"),
                    "Cancellation left a pending elapsed deadline");
            Bundle result = new Bundle();
            result.putString("stream", "PASS real elapsed-time diagnostic alarm delivered through receiver/notification and canceled cleanly\n");
            test.sendStatus(0, result);
        } finally {
            ReminderDiagnostics.cancelTest(context);
            ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).cancel("week_weeks_diagnostic_test", 1);
            restore(diagnostics, before);
            check(SafePreferences.commit(app, hadEnabled
                    ? new PreferenceTransaction().put("notifications_enabled", enabled)
                    : new PreferenceTransaction().remove("notifications_enabled")), "Fixture switch restore failed");
        }
    }

    @SuppressWarnings("unchecked") private static void restore(SharedPreferences prefs, Map<String, ?> saved) {
        SharedPreferences.Editor editor = prefs.edit().clear();
        for (Map.Entry<String, ?> entry : saved.entrySet()) {
            Object value = entry.getValue(); String key = entry.getKey();
            if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
        }
        check(editor.commit(), "Diagnostic fixture restore failed");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
