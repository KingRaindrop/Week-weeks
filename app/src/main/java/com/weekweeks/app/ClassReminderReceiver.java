package com.weekweeks.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

public class ClassReminderReceiver extends BroadcastReceiver {
    static final String CHANNEL_ID = "class_reminders_high_v2";
    static final String EXTRA_COURSE_NAME = "course_name";
    static final String EXTRA_COURSE_TIME = "course_time";
    static final String EXTRA_COURSE_ROOM = "course_room";
    static final String EXTRA_REMINDER_MINUTES = "reminder_minutes";
    static final String EXTRA_POPUP_TOKEN = "reminder_popup_token";
    static final String EXTRA_KIND = "reminder_kind";
    /** Epoch milliseconds of this occurrence; -1 means unknown (legacy/debug notification). */
    static final String EXTRA_START_AT = "reminder_start_at";
    static final String EXTRA_END_AT = "reminder_end_at";
    private static final String POPUP_TOKEN_KEY = "reminder_popup_token";

    /**
     * Per-install secret carried only by notifications this app posts. MainActivity is exported
     * (launcher), so any app could otherwise start it with forged reminder extras; without the
     * token it cannot, because the value lives in app-private storage.
     */
    @android.annotation.SuppressLint("ApplySharedPref") // 通知发出前令牌必须已落盘，否则重启后点通知会被误拒
    static synchronized String popupToken(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("week_weeks", Context.MODE_PRIVATE);
        String token = prefs.getString(POPUP_TOKEN_KEY, null);
        if (token == null || token.length() < 32) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            token = Base64.encodeToString(random, Base64.NO_WRAP | Base64.URL_SAFE);
            prefs.edit().putString(POPUP_TOKEN_KEY, token).commit();
        }
        return token;
    }

    /** Constant-time comparison so the token cannot be probed byte by byte. */
    static boolean isTrustedPopup(Context context, Intent intent) {
        String supplied = intent == null ? null : intent.getStringExtra(EXTRA_POPUP_TOKEN);
        if (supplied == null || supplied.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.UTF_8),
                popupToken(context).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        context = AppLanguage.wrap(context);
        // Continue the chain before attempting to post. A denied notification permission must not
        // make every later reminder for the course unreachable.
        if (!ReminderScheduler.scheduleNextFromBroadcast(context, intent)) {
            return;
        }
        postNotification(context, intent);
    }

    // Explicit debug-only entry: unversioned intents must never bypass production validation.
    static boolean sendDebugNotification(Context context, Intent intent) {
        if (!BuildConfig.ENABLE_TEST_COURSE || intent == null
                || !ReminderScheduler.notificationsEnabled(context)) {
            return false;
        }
        return postNotification(AppLanguage.wrap(context), intent);
    }

    private static boolean postNotification(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return false;
        }
        ensureChannel(context, manager);

        String name = intent.getStringExtra(EXTRA_COURSE_NAME);
        String time = intent.getStringExtra(EXTRA_COURSE_TIME);
        String room = intent.getStringExtra(EXTRA_COURSE_ROOM);
        int minutes = intent.getIntExtra(EXTRA_REMINDER_MINUTES, 30);
        int reminderId = intent.getIntExtra(ReminderScheduler.EXTRA_REQUEST_CODE, 1);
        boolean event = ReminderScheduler.KIND_EVENT.equals(intent.getStringExtra(EXTRA_KIND));
        if (name == null || name.trim().isEmpty()) {
            name = context.getString(event ? R.string.view_reminder_default_event : R.string.view_reminder_default_course);
        }
        long startAt = intent.getLongExtra(EXTRA_START_AT, -1L);
        if (startAt <= 0) {
            // Pending alarms created before this version still carry their scheduled trigger.
            startAt = ReminderTiming.startAt(intent.getLongExtra(ReminderScheduler.EXTRA_TRIGGER_AT, -1L), minutes);
        }
        long endAt = intent.getLongExtra(EXTRA_END_AT, -1L);
        if (endAt <= startAt) endAt = ReminderTiming.endAt(startAt, time);
        String lead = ReminderTiming.statusText(event, startAt, endAt, System.currentTimeMillis());
        String content = lead;
        if (time != null && !time.trim().isEmpty()) {
            content += " \u00b7 " + time;
        }
        if (room != null && !room.trim().isEmpty()) {
            content += " \u00b7 " + room;
        }

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        openIntent.putExtra(MainActivity.EXTRA_SHOW_REMINDER_POPUP, true);
        openIntent.putExtra(EXTRA_POPUP_TOKEN, popupToken(context));
        openIntent.putExtra(MainActivity.EXTRA_REMINDER_COURSE_NAME, name);
        openIntent.putExtra(MainActivity.EXTRA_REMINDER_COURSE_TIME, time);
        openIntent.putExtra(MainActivity.EXTRA_REMINDER_COURSE_ROOM, room);
        openIntent.putExtra(MainActivity.EXTRA_REMINDER_LEAD_MINUTES, minutes);
        openIntent.putExtra(EXTRA_KIND, event ? ReminderScheduler.KIND_EVENT : ReminderScheduler.KIND_COURSE);
        openIntent.putExtra(EXTRA_START_AT, startAt);
        openIntent.putExtra(EXTRA_END_AT, endAt);
        openIntent.setData(Uri.parse("weekweeks://reminder/" + reminderId));
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                reminderId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        // Course name and room stay off the lock screen; only this generic version is shown there.
        Notification.Builder publicBuilder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        Notification publicVersion = publicBuilder
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(event ? R.string.view_reminder_title_event : R.string.view_reminder_title_course))
                .setContentText(lead)
                .setColor(MainActivity.accentColorFor(context))
                .build();
        Notification notification = builder
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(name)
                .setContentText(content)
                .setStyle(new Notification.BigTextStyle().bigText(content))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setColor(MainActivity.accentColorFor(context))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                .setPriority(Notification.PRIORITY_HIGH)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .build();

        manager.notify(reminderId, notification);
        return true;
    }

    static void ensureChannel(Context context, NotificationManager manager) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
        if (channel == null) {
            channel = new NotificationChannel(CHANNEL_ID, context.getString(R.string.view_channel_name), NotificationManager.IMPORTANCE_HIGH);
            channel.enableVibration(true);
        }
        // Update existing installations without replacing the channel or user sound/importance settings.
        channel.setName(context.getString(R.string.view_channel_name));
        channel.setDescription(context.getString(R.string.view_channel_description));
        manager.createNotificationChannel(channel);
    }
}
