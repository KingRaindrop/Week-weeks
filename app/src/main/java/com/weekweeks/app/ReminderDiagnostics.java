package com.weekweeks.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import androidx.core.app.NotificationManagerCompat;
import java.util.Date;
import java.util.UUID;

/** Release-usable diagnostics. Test alarms never touch the encrypted production schedule. */
final class ReminderDiagnostics {
    static final long DEFAULT_TEST_DELAY_MILLIS = 60_000L;
    private static final String PREFS = "week_weeks_reminder_diagnostics_v1";
    private static final String ACTION_TEST = "com.weekweeks.app.action.REMINDER_DIAGNOSTIC_TEST";
    private static final String EXTRA_TEST_TOKEN = "diagnostic_test_token";
    private static final String EXTRA_TEST_AT = "diagnostic_test_at";
    private static final String EXTRA_TEST_ELAPSED = "diagnostic_test_elapsed";
    private static final String TEST_TAG = "week_weeks_diagnostic_test";
    private static final int TEST_ID = 1;
    private static final long TEST_EXPIRY = 24 * 60 * 60_000L;

    private ReminderDiagnostics() { }

    static final class Outcome {
        final ReminderDiagnosticRules.Code code;
        final long triggerAt;
        Outcome(ReminderDiagnosticRules.Code code, long triggerAt) { this.code = code; this.triggerAt = triggerAt; }
        boolean accepted() {
            return code == ReminderDiagnosticRules.Code.POST_SUBMITTED
                    || code == ReminderDiagnosticRules.Code.POST_SUBMITTED_QUIET
                    || code == ReminderDiagnosticRules.Code.SCHEDULE_EXACT
                    || code == ReminderDiagnosticRules.Code.SCHEDULE_INEXACT;
        }
        String message(Context context) {
            String text = describe(context, code);
            return triggerAt > 0 && accepted() ? text + "\n" + context.getString(R.string.reminder_diag_test_expected, time(context, triggerAt)) : text;
        }
    }

    static final class Record {
        final long at;
        final ReminderDiagnosticRules.Code code;
        Record(long at, ReminderDiagnosticRules.Code code) { this.at = at; this.code = code; }
    }

    static final class Snapshot {
        final boolean appRemindersEnabled, permissionGranted, appNotificationsEnabled;
        final boolean channelExists, channelBlocked, channelHasSound, channelVibrates;
        final boolean exactAlarmAllowed, batteryUnrestricted, doNotDisturb;
        final int channelImportance, pendingReminders;
        final long nextReminderAt, pendingTestAt;
        final boolean scheduleReadable;
        final Record schedule, receive, post, testSchedule, testReceive, testPost;
        Snapshot(Capabilities c, ReminderScheduler.Status status, SharedPreferences p) {
            appRemindersEnabled = c.appEnabled; permissionGranted = c.permission;
            appNotificationsEnabled = c.systemEnabled; channelExists = c.channelExists;
            channelBlocked = c.channelBlocked; channelHasSound = c.sound; channelVibrates = c.vibration;
            channelImportance = c.importance; exactAlarmAllowed = c.exact;
            batteryUnrestricted = c.batteryUnrestricted; doNotDisturb = c.doNotDisturb;
            pendingReminders = status.pendingCourses; nextReminderAt = status.nextTriggerAt;
            scheduleReadable = status.scheduleReadable;
            pendingTestAt = pendingTestTime(p);
            schedule = read(p, "schedule"); receive = read(p, "receive"); post = read(p, "post");
            testSchedule = read(p, "test_schedule"); testReceive = read(p, "test_receive"); testPost = read(p, "test_post");
        }
    }

    private static final class Capabilities {
        boolean appEnabled, permission, systemEnabled, managerAvailable, channelExists, channelBlocked;
        boolean sound, vibration, exact, batteryUnrestricted, doNotDisturb;
        int importance = -1;
    }

    static Snapshot snapshot(Context context) {
        return new Snapshot(capabilities(context), ReminderScheduler.status(context), prefs(context));
    }

    /** Localized, privacy-safe report suitable for a selectable TextView or support copy action. */
    static String report(Context context) {
        Snapshot s = snapshot(context);
        StringBuilder out = new StringBuilder();
        line(out, context.getString(R.string.reminder_diag_app_switch), yes(context, s.appRemindersEnabled));
        line(out, context.getString(R.string.reminder_diag_permission), yes(context, s.permissionGranted));
        line(out, context.getString(R.string.reminder_diag_system_switch), yes(context, s.appNotificationsEnabled));
        String channel = !s.channelExists ? context.getString(R.string.reminder_diag_channel_missing)
                : s.channelBlocked ? context.getString(R.string.reminder_diag_channel_blocked)
                : context.getString(R.string.reminder_diag_channel_details, s.channelImportance,
                        yes(context, s.channelHasSound), yes(context, s.channelVibrates));
        line(out, context.getString(R.string.reminder_diag_channel), channel);
        line(out, context.getString(R.string.reminder_diag_exact), context.getString(s.exactAlarmAllowed
                ? R.string.reminder_diag_exact_allowed : R.string.reminder_diag_exact_inexact));
        line(out, context.getString(R.string.reminder_diag_next), !s.scheduleReadable
                ? context.getString(R.string.reminder_diag_store_failed) : time(context, s.nextReminderAt));
        line(out, context.getString(R.string.reminder_diag_pending_count), String.valueOf(s.pendingReminders));
        line(out, context.getString(R.string.reminder_diag_dnd), yes(context, s.doNotDisturb));
        line(out, context.getString(R.string.reminder_diag_battery), yes(context, s.batteryUnrestricted));
        appendRecord(context, out, R.string.reminder_diag_last_schedule, s.schedule);
        appendRecord(context, out, R.string.reminder_diag_last_receive, s.receive);
        appendRecord(context, out, R.string.reminder_diag_last_post, s.post);
        line(out, context.getString(R.string.reminder_diag_pending_test), time(context, s.pendingTestAt));
        appendRecord(context, out, R.string.reminder_diag_test_schedule, s.testSchedule);
        appendRecord(context, out, R.string.reminder_diag_test_receive, s.testReceive);
        appendRecord(context, out, R.string.reminder_diag_test_post, s.testPost);
        out.append('\n').append(context.getString(R.string.reminder_diag_disclaimer));
        return out.toString();
    }

    private static Capabilities capabilities(Context context) {
        Capabilities c = new Capabilities();
        c.appEnabled = ReminderScheduler.notificationsEnabled(context);
        c.permission = Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        NotificationManager manager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        c.managerAvailable = manager != null;
        try { c.systemEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(); }
        catch (RuntimeException unavailable) { c.systemEnabled = false; }
        c.channelExists = Build.VERSION.SDK_INT < 26;
        c.sound = Build.VERSION.SDK_INT < 26;
        if (manager != null) {
            try {
                c.doNotDisturb = manager.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL
                        && manager.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_UNKNOWN;
                if (Build.VERSION.SDK_INT >= 26) {
                    NotificationChannel channel = manager.getNotificationChannel(ClassReminderReceiver.CHANNEL_ID);
                    c.channelExists = channel != null;
                    if (channel != null) {
                        c.importance = channel.getImportance();
                        c.channelBlocked = c.importance == NotificationManager.IMPORTANCE_NONE;
                        if (Build.VERSION.SDK_INT >= 28 && channel.getGroup() != null) {
                            NotificationChannelGroup group = manager.getNotificationChannelGroup(channel.getGroup());
                            c.channelBlocked |= group != null && group.isBlocked();
                        }
                        c.sound = channel.getSound() != null && c.importance >= NotificationManager.IMPORTANCE_DEFAULT;
                        c.vibration = channel.shouldVibrate() && c.importance >= NotificationManager.IMPORTANCE_DEFAULT;
                    }
                }
            } catch (RuntimeException unavailable) { c.channelExists = false; }
        }
        c.exact = ReminderScheduler.canScheduleExactAlarms(context);
        PowerManager power = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        try { c.batteryUnrestricted = power != null && power.isIgnoringBatteryOptimizations(context.getPackageName()); }
        catch (RuntimeException ignored) { c.batteryUnrestricted = false; }
        return c;
    }

    /** Call only after ensureChannel when posting; does not create/change a channel during inspection. */
    static ReminderDiagnosticRules.Code postBlocker(Context context) {
        Capabilities c = capabilities(context);
        return ReminderDiagnosticRules.notificationBlocker(c.appEnabled, c.permission, c.systemEnabled,
                c.managerAvailable, c.channelExists, c.channelBlocked);
    }

    static boolean channelQuiet(Context context) {
        Capabilities c = capabilities(context);
        return Build.VERSION.SDK_INT >= 26 && (!c.sound || !c.vibration || c.importance < NotificationManager.IMPORTANCE_HIGH);
    }

    static Outcome sendImmediateTest(Context context) {
        Context localized = AppLanguage.wrap(context);
        ReminderDiagnosticRules.Code code = postTest(localized);
        return new Outcome(code, 0);
    }

    /** User-initiated real alarm; 10 seconds–5 minutes. At most one pending test, isolated by action+data. */
    @android.annotation.SuppressLint("ApplySharedPref")
    static synchronized Outcome scheduleTest(Context context, long delayMillis) {
        long at = ReminderDiagnosticRules.testTriggerAt(System.currentTimeMillis(), delayMillis);
        long elapsedNow = SystemClock.elapsedRealtime();
        if (at <= 0 || elapsedNow < 0 || elapsedNow > Long.MAX_VALUE - delayMillis)
            return new Outcome(ReminderDiagnosticRules.Code.INVALID_DELAY, 0);
        long elapsedAt = elapsedNow + delayMillis;
        ReminderDiagnosticRules.Code blocker = prepareChannel(context);
        if (blocker != ReminderDiagnosticRules.Code.NONE) {
            record(context, "test_schedule", blocker);
            return new Outcome(blocker, 0);
        }
        AlarmManager alarm = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return testFailure(context, ReminderDiagnosticRules.Code.ALARM_UNAVAILABLE);
        Outcome canceled = cancelTest(context);
        if (canceled.code == ReminderDiagnosticRules.Code.STORE_FAILED
                || canceled.code == ReminderDiagnosticRules.Code.SCHEDULE_ERROR) return canceled;
        String token = UUID.randomUUID().toString();
        // Failed commits also publish their values in memory. Roll the token back so the
        // report never advertises an alarm that was not submitted to AlarmManager.
        if (!SafePreferences.commit(prefs(context), new PreferenceTransaction()
                .put("pending_test_token", token).put("pending_test_at", at).put("pending_test_elapsed", elapsedAt)))
            return testFailure(context, ReminderDiagnosticRules.Code.STORE_FAILED);
        try {
            PendingIntent pending = PendingIntent.getBroadcast(context, TEST_ID, testIntent(context, token, at, elapsedAt),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            boolean exact = ReminderScheduler.canScheduleExactAlarms(context);
            if (exact) {
                try { alarm.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, elapsedAt, pending); }
                catch (SecurityException permissionChanged) {
                    alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, elapsedAt, pending);
                    exact = false;
                }
            } else alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, elapsedAt, pending);
            ReminderDiagnosticRules.Code code = exact ? ReminderDiagnosticRules.Code.SCHEDULE_EXACT : ReminderDiagnosticRules.Code.SCHEDULE_INEXACT;
            record(context, "test_schedule", code);
            return new Outcome(code, at);
        } catch (SecurityException denied) {
            clearPending(context);
            return testFailure(context, ReminderDiagnosticRules.Code.SCHEDULE_SECURITY_ERROR);
        } catch (RuntimeException failed) {
            clearPending(context);
            return testFailure(context, ReminderDiagnosticRules.Code.SCHEDULE_ERROR);
        }
    }

    static synchronized Outcome cancelTest(Context context) {
        boolean existed = prefs(context).getLong("pending_test_at", 0) > 0;
        boolean cleared = clearPending(context); // Invalidate first; even cancellation failure cannot deliver it.
        try {
            PendingIntent p = PendingIntent.getBroadcast(context, TEST_ID, testIntent(context, "", 0, 0),
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (p != null) {
                AlarmManager alarm = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
                if (alarm != null) alarm.cancel(p);
                p.cancel();
            }
        } catch (RuntimeException failed) { return testFailure(context, ReminderDiagnosticRules.Code.SCHEDULE_ERROR); }
        if (!cleared) return testFailure(context, ReminderDiagnosticRules.Code.STORE_FAILED);
        ReminderDiagnosticRules.Code code = existed ? ReminderDiagnosticRules.Code.TEST_CANCELED : ReminderDiagnosticRules.Code.TEST_NOT_PENDING;
        if (existed) record(context, "test_schedule", code);
        return new Outcome(code, 0);
    }

    static boolean isTestIntent(Intent intent) { return intent != null && ACTION_TEST.equals(intent.getAction()); }

    /** Separate one-shot validation path. Production generation validation is never bypassed for course/event intents. */
    static synchronized void receiveTest(Context context, Intent intent) {
        SharedPreferences p = prefs(context);
        long at = p.getLong("pending_test_at", 0);
        long elapsedAt = p.getLong("pending_test_elapsed", 0);
        // Older pending tests did not store elapsed time. Keep their wall-clock validation.
        long deadline = elapsedAt > 0 ? elapsedAt : at;
        long now = elapsedAt > 0 ? SystemClock.elapsedRealtime() : System.currentTimeMillis();
        String token = p.getString("pending_test_token", "");
        boolean valid = intent != null && testUri(context).equals(intent.getData())
                && at > 0 && intent.getLongExtra(EXTRA_TEST_AT, 0) == at
                && ReminderDiagnosticRules.validTestDelivery(token, intent.getStringExtra(EXTRA_TEST_TOKEN), deadline,
                intent.getLongExtra(elapsedAt > 0 ? EXTRA_TEST_ELAPSED : EXTRA_TEST_AT, 0), now,
                deadline > Long.MAX_VALUE - TEST_EXPIRY ? Long.MAX_VALUE : deadline + TEST_EXPIRY);
        if (!valid) {
            boolean expired = deadline > 0 && deadline <= Long.MAX_VALUE - TEST_EXPIRY && now > deadline + TEST_EXPIRY;
            if (expired && !clearPending(context)) {
                record(context, "test_receive", ReminderDiagnosticRules.Code.STORE_FAILED);
                return;
            }
            record(context, "test_receive", expired
                    ? ReminderDiagnosticRules.Code.TEST_EXPIRED : ReminderDiagnosticRules.Code.TEST_INVALID);
            return;
        }
        if (!clearPending(context)) { record(context, "test_receive", ReminderDiagnosticRules.Code.STORE_FAILED); return; }
        record(context, "test_receive", ReminderDiagnosticRules.Code.RECEIVE_ACCEPTED);
        postTest(AppLanguage.wrap(context));
    }

    /** A reboot/package update cancels an old diagnostic test rather than reusing production schedule data. */
    static void onSystemRestore(Context context) {
        if (prefs(context).getLong("pending_test_at", 0) > 0) cancelTest(context);
    }

    private static Intent testIntent(Context context, String token, long at, long elapsedAt) {
        return new Intent(context, ClassReminderReceiver.class).setAction(ACTION_TEST).setData(testUri(context))
                .putExtra(EXTRA_TEST_TOKEN, token).putExtra(EXTRA_TEST_AT, at).putExtra(EXTRA_TEST_ELAPSED, elapsedAt);
    }
    private static Uri testUri(Context context) { return Uri.parse("weekweeks://" + context.getPackageName() + "/diagnostics/alarm-test"); }

    private static ReminderDiagnosticRules.Code prepareChannel(Context context) {
        try {
            NotificationManager manager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return ReminderDiagnosticRules.Code.NOTIFICATION_SERVICE_UNAVAILABLE;
            ClassReminderReceiver.ensureChannel(context, manager);
            return postBlocker(context);
        } catch (SecurityException denied) { return ReminderDiagnosticRules.Code.POST_SECURITY_ERROR; }
        catch (RuntimeException failed) { return ReminderDiagnosticRules.Code.POST_ERROR; }
    }

    private static ReminderDiagnosticRules.Code postTest(Context context) {
        ReminderDiagnosticRules.Code blocker = prepareChannel(context);
        if (blocker != ReminderDiagnosticRules.Code.NONE) { record(context, "test_post", blocker); return blocker; }
        try {
            NotificationManager manager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            Intent open = new Intent(context, MainActivity.class).setData(Uri.parse("weekweeks://diagnostics/test-notification"))
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent content = PendingIntent.getActivity(context, TEST_ID, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(context, ClassReminderReceiver.CHANNEL_ID) : new Notification.Builder(context);
            Notification notification = builder.setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(context.getString(R.string.reminder_diag_test_notification_title))
                    .setContentText(context.getString(R.string.reminder_diag_test_notification_body))
                    .setContentIntent(content).setAutoCancel(true).setCategory(Notification.CATEGORY_REMINDER)
                    .setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                    .setVisibility(Notification.VISIBILITY_PRIVATE).setColor(MainActivity.accentColorFor(context)).build();
            manager.notify(TEST_TAG, TEST_ID, notification);
            ReminderDiagnosticRules.Code code = channelQuiet(context) ? ReminderDiagnosticRules.Code.POST_SUBMITTED_QUIET : ReminderDiagnosticRules.Code.POST_SUBMITTED;
            record(context, "test_post", code);
            return code;
        } catch (SecurityException denied) { record(context, "test_post", ReminderDiagnosticRules.Code.POST_SECURITY_ERROR); return ReminderDiagnosticRules.Code.POST_SECURITY_ERROR; }
        catch (RuntimeException failed) { record(context, "test_post", ReminderDiagnosticRules.Code.POST_ERROR); return ReminderDiagnosticRules.Code.POST_ERROR; }
    }

    static Intent appNotificationSettings(Context context) {
        return new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
    }
    static Intent channelSettings(Context context) {
        if (Build.VERSION.SDK_INT < 26) return appNotificationSettings(context);
        return new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName())
                .putExtra(Settings.EXTRA_CHANNEL_ID, ClassReminderReceiver.CHANNEL_ID);
    }
    static Intent exactAlarmSettings(Context context) {
        return Build.VERSION.SDK_INT >= 31
                ? new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.getPackageName()))
                : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.getPackageName()));
    }
    static Intent appDetailsSettings(Context context) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.getPackageName()));
    }

    static void recordSchedule(Context c, ReminderDiagnosticRules.Code code) { record(c, "schedule", code); }
    static void recordReceive(Context c, ReminderDiagnosticRules.Code code) { record(c, "receive", code); }
    static void recordPost(Context c, ReminderDiagnosticRules.Code code) { record(c, "post", code); }
    private static Outcome testFailure(Context c, ReminderDiagnosticRules.Code code) { record(c, "test_schedule", code); return new Outcome(code, 0); }
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    @android.annotation.SuppressLint("ApplySharedPref")
    private static boolean clearPending(Context c) {
        return prefs(c).edit().remove("pending_test_token").remove("pending_test_at").remove("pending_test_elapsed").commit();
    }
    private static long pendingTestTime(SharedPreferences p) {
        long at = p.getLong("pending_test_at", 0);
        long elapsedAt = p.getLong("pending_test_elapsed", 0);
        if (at <= 0 || elapsedAt <= 0) return at;
        // Show the current wall-clock equivalent without changing the broadcast's identity.
        long remaining = elapsedAt - SystemClock.elapsedRealtime();
        long now = System.currentTimeMillis();
        if (remaining > 0 && now > Long.MAX_VALUE - remaining) return Long.MAX_VALUE;
        if (remaining < 0 && now < Long.MIN_VALUE - remaining) return 0;
        return Math.max(0, now + remaining);
    }
    @android.annotation.SuppressLint("ApplySharedPref")
    private static void record(Context c, String stage, ReminderDiagnosticRules.Code code) {
        prefs(c).edit().putString(stage + "_code", code.name()).putLong(stage + "_at", System.currentTimeMillis()).commit();
    }
    private static Record read(SharedPreferences p, String stage) {
        ReminderDiagnosticRules.Code code;
        try { code = ReminderDiagnosticRules.Code.valueOf(p.getString(stage + "_code", "NONE")); }
        catch (IllegalArgumentException invalid) { code = ReminderDiagnosticRules.Code.NONE; }
        return new Record(p.getLong(stage + "_at", 0), code);
    }
    private static void line(StringBuilder b, String label, String value) { b.append(label).append(": ").append(value).append('\n'); }
    private static String yes(Context c, boolean value) { return c.getString(value ? R.string.reminder_diag_yes : R.string.reminder_diag_no); }
    private static void appendRecord(Context c, StringBuilder b, int label, Record record) {
        line(b, c.getString(label), record.at <= 0 ? c.getString(R.string.reminder_diag_no_record)
                : time(c, record.at) + " · " + describe(c, record.code));
    }
    private static String time(Context c, long at) {
        if (at <= 0) return c.getString(R.string.reminder_diag_none);
        return android.text.format.DateFormat.getDateFormat(c).format(new Date(at)) + " "
                + android.text.format.DateFormat.getTimeFormat(c).format(new Date(at));
    }
    static String describe(Context c, ReminderDiagnosticRules.Code code) {
        int id;
        switch (code) {
            case APP_REMINDERS_OFF: id=R.string.reminder_diag_app_off; break;
            case PERMISSION_DENIED: id=R.string.reminder_diag_permission_denied; break;
            case NOTIFICATIONS_BLOCKED: id=R.string.reminder_diag_notifications_blocked; break;
            case CHANNEL_BLOCKED: id=R.string.reminder_diag_channel_blocked; break;
            case CHANNEL_UNAVAILABLE: id=R.string.reminder_diag_channel_unavailable; break;
            case NOTIFICATION_SERVICE_UNAVAILABLE: id=R.string.reminder_diag_notification_service_unavailable; break;
            case POST_SUBMITTED: id=R.string.reminder_diag_post_submitted; break;
            case POST_SUBMITTED_QUIET: id=R.string.reminder_diag_post_quiet; break;
            case POST_SECURITY_ERROR: id=R.string.reminder_diag_post_security_error; break;
            case POST_ERROR: id=R.string.reminder_diag_post_error; break;
            case SCHEDULE_EXACT: id=R.string.reminder_diag_schedule_exact; break;
            case SCHEDULE_INEXACT: id=R.string.reminder_diag_schedule_inexact; break;
            case NO_FUTURE: id=R.string.reminder_diag_no_future; break;
            case STORE_FAILED: id=R.string.reminder_diag_store_failed; break;
            case ALARM_UNAVAILABLE: id=R.string.reminder_diag_alarm_unavailable; break;
            case SCHEDULE_SECURITY_ERROR: id=R.string.reminder_diag_schedule_security_error; break;
            case SCHEDULE_ERROR: id=R.string.reminder_diag_schedule_error; break;
            case RECEIVE_ACCEPTED: id=R.string.reminder_diag_receive_accepted; break;
            case RECEIVE_INVALID: id=R.string.reminder_diag_receive_invalid; break;
            case RECEIVE_STALE: id=R.string.reminder_diag_receive_stale; break;
            case RECEIVE_UNKNOWN_ENTRY: id=R.string.reminder_diag_receive_unknown; break;
            case TEST_INVALID: id=R.string.reminder_diag_test_invalid; break;
            case TEST_EXPIRED: id=R.string.reminder_diag_test_expired; break;
            case TEST_CANCELED: id=R.string.reminder_diag_test_canceled; break;
            case TEST_NOT_PENDING: id=R.string.reminder_diag_test_not_pending; break;
            case INVALID_DELAY: id=R.string.reminder_diag_invalid_delay; break;
            case RESTORE_FAILED: id=R.string.reminder_diag_restore_failed; break;
            default: id=R.string.reminder_diag_no_record;
        }
        return c.getString(id);
    }
}
