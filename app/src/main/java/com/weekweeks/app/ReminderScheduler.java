package com.weekweeks.app;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


final class ReminderScheduler {
    static final String EXTRA_REQUEST_CODE = "reminder_request_code";
    static final String EXTRA_TRIGGER_AT = "reminder_trigger_at";
    static final String EXTRA_GENERATION = "reminder_schedule_generation";
    static final String KIND_COURSE = "course";
    static final String KIND_EVENT = "event";

    private static final String PREFS_NAME = "week_weeks";
    private static final String STORE_KEY = "class_reminder_schedule_encrypted_v1";
    private static final String LAST_SUBMISSION_OK_KEY = "class_reminder_last_submission_ok";
    private static final String LAST_SUBMISSION_AT_KEY = "class_reminder_last_submission_at";
    private static final String SUBMITTED_GENERATION_KEY = "class_reminder_submitted_generation";
    private static final String SUBMITTED_EXACT_KEY = "class_reminder_submitted_exact";
    private static final String SUBMITTED_AT_KEY = "class_reminder_submitted_at";
    private static final String KEY_ALIAS = "week_weeks_reminder_schedule_key";
    private static final SecureStorage STORAGE = new SecureStorage(KEY_ALIAS);

    private ReminderScheduler() {
    }

    static void replaceSchedule(Context context, List<Entry> entries) {
        replaceSchedule(context, entries, true, true);
    }

    /** A locked data store is unknown, not empty: retain its last readable reminder plan. */
    static void replaceSchedule(Context context, List<Entry> entries,
            boolean coursesReadable, boolean eventsReadable) {
        replaceSchedule(context, entries, coursesReadable, eventsReadable,
                ReminderSourceRebuilder.sourceFingerprint(context), loadSchedule(context));
    }

    /** A foreground refresh must not revoke an unchanged alarm awaiting delayed delivery. */
    static void refreshSchedule(Context context, List<Entry> entries,
            boolean coursesReadable, boolean eventsReadable) {
        if (!notificationsEnabled(context)) return;
        String fingerprint = ReminderSourceRebuilder.sourceFingerprint(context);
        ScheduleData previous = loadSchedule(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (coursesReadable && eventsReadable && fingerprint != null
                && fingerprint.equals(previous.sourceFingerprint) && previous.generation > 0
                && prefs.getLong(SUBMITTED_GENERATION_KEY, Long.MIN_VALUE) == previous.generation
                && prefs.getBoolean(LAST_SUBMISSION_OK_KEY, false)
                && prefs.getBoolean(SUBMITTED_EXACT_KEY, false) == canScheduleExactAlarms(context)
                && systemScheduleRetained(context, previous, prefs.getLong(SUBMITTED_AT_KEY, 0L))) {
            return;
        }
        replaceSchedule(context, entries, coursesReadable, eventsReadable, fingerprint, previous);
    }

    /** Stored receipts survive force-stop even though the system removes the app's alarms. */
    private static boolean systemScheduleRetained(Context context, ScheduleData schedule, long submittedAt) {
        if (submittedAt <= 0L) return false;
        try {
            long now = System.currentTimeMillis();
            for (Entry entry : schedule.entries) {
                if (entry.nextAfter(now) <= 0L) continue;
                PendingIntent pending = PendingIntent.getBroadcast(context, entry.requestCode,
                        new Intent(context, ClassReminderReceiver.class),
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (pending == null) return false;
            }
            if (Build.VERSION.SDK_INT >= 30) {
                // Android R+ can retain PendingIntent records after removing their alarms.
                // Like AndroidX's force-stop check, inspect the process-exit history as well.
                ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (manager == null) return false;
                List<ApplicationExitInfo> exits = manager.getHistoricalProcessExitReasons(null, 0, 0);
                if (exits == null) return false;
                for (ApplicationExitInfo exit : exits) {
                    if (exit.getReason() == ApplicationExitInfo.REASON_USER_REQUESTED
                            && exit.getTimestamp() >= submittedAt) return false;
                }
            }
            return true;
        } catch (RuntimeException unavailable) {
            // A device that cannot report retained state needs a conservative full submission.
            return false;
        }
    }

    private static void replaceSchedule(Context context, List<Entry> entries,
            boolean coursesReadable, boolean eventsReadable, String fingerprint, ScheduleData previous) {
        if (!coursesReadable && !eventsReadable) return;
        if ((!coursesReadable || !eventsReadable) && previous.generation == Long.MIN_VALUE) {
            // A partial rebuild cannot safely replace an unreadable mixed schedule.
            recordSubmissionResult(context, ReminderDiagnosticRules.Code.STORE_FAILED);
            return;
        }
        List<Entry> combined = new ArrayList<>();
        Set<Integer> usedCodes = new HashSet<>();
        for (Entry entry : previous.entries) {
            if (!readable(entry, coursesReadable, eventsReadable)) {
                combined.add(entry);
                usedCodes.add(entry.requestCode);
            }
        }
        for (Entry entry : entries) {
            if (!readable(entry, coursesReadable, eventsReadable)) continue;
            int requestCode = entry.requestCode;
            while (usedCodes.contains(requestCode)) {
                requestCode = requestCode == Integer.MAX_VALUE ? 1 : requestCode + 1;
            }
            usedCodes.add(requestCode);
            combined.add(requestCode == entry.requestCode ? entry : new Entry(requestCode,
                    entry.name, entry.time, entry.room, entry.leadMinutes, entry.triggerTimes, entry.kind, entry.repeatAlarm));
        }
        long generation = nextGeneration(previous.generation);
        ScheduleData replacement = new ScheduleData(generation, combined,
                coursesReadable && eventsReadable ? fingerprint : null);
        if (!saveSchedule(context, replacement)) {
            recordSubmissionResult(context, ReminderDiagnosticRules.Code.STORE_FAILED);
            return;
        }
        cancelEntries(context, previous.entries);
        ReminderDiagnosticRules.Code result = ReminderDiagnosticRules.Code.NO_FUTURE;
        for (Entry entry : combined) {
            result = ReminderDiagnosticRules.mergeSchedule(result,
                    scheduleFirstFuture(context, entry, System.currentTimeMillis(), generation));
        }
        recordFullSubmission(context, replacement, result);
    }

    private static boolean readable(Entry entry, boolean coursesReadable, boolean eventsReadable) {
        return KIND_EVENT.equals(entry.kind) ? eventsReadable : coursesReadable;
    }

    static boolean notificationsEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean("notifications_enabled", true);
    }

    static void rescheduleStored(Context context) {
        if (!notificationsEnabled(context)) {
            ReminderDiagnostics.recordSchedule(context, ReminderDiagnosticRules.Code.APP_REMINDERS_OFF);
            return;
        }
        ScheduleData schedule = loadSchedule(context);
        if (schedule.generation == Long.MIN_VALUE) {
            recordSubmissionResult(context, ReminderDiagnosticRules.Code.STORE_FAILED);
            return;
        }
        long now = System.currentTimeMillis();
        ReminderDiagnosticRules.Code result = ReminderDiagnosticRules.Code.NO_FUTURE;
        for (Entry entry : schedule.entries) {
            result = ReminderDiagnosticRules.mergeSchedule(result,
                    scheduleFirstFuture(context, entry, now, schedule.generation));
        }
        recordFullSubmission(context, schedule, result);
    }

    // Returns whether this delivery is current and may notify, not whether a later alarm exists.
    // A course's final occurrence must still notify, even if there is nothing left to schedule.
    static boolean scheduleNextFromBroadcast(Context context, Intent deliveredIntent) {
        if (!notificationsEnabled(context)) {
            ReminderDiagnostics.recordReceive(context, ReminderDiagnosticRules.Code.APP_REMINDERS_OFF);
            return false;
        }
        if (deliveredIntent == null || !deliveredIntent.hasExtra(EXTRA_REQUEST_CODE)
                || !deliveredIntent.hasExtra(EXTRA_GENERATION)) {
            ReminderDiagnostics.recordReceive(context, ReminderDiagnosticRules.Code.RECEIVE_INVALID);
            return false;
        }
        int requestCode = deliveredIntent.getIntExtra(EXTRA_REQUEST_CODE, 0);
        long generation = deliveredIntent.getLongExtra(EXTRA_GENERATION, Long.MIN_VALUE);
        ScheduleData current = loadSchedule(context);
        if (current.generation == Long.MIN_VALUE) {
            ReminderDiagnostics.recordReceive(context, ReminderDiagnosticRules.Code.STORE_FAILED);
            return false;
        }
        if (generation == Long.MIN_VALUE || generation != current.generation) {
            ReminderDiagnostics.recordReceive(context, ReminderDiagnosticRules.Code.RECEIVE_STALE);
            return false;
        }
        Entry entry = current.entryForRequestCode(requestCode);
        if (entry == null) {
            ReminderDiagnostics.recordReceive(context, ReminderDiagnosticRules.Code.RECEIVE_UNKNOWN_ENTRY);
            return false;
        }
        ReminderDiagnostics.recordReceive(context, ReminderDiagnosticRules.Code.RECEIVE_ACCEPTED);
        long deliveredAt = deliveredIntent.getLongExtra(EXTRA_TRIGGER_AT, 0L);
        ReminderDiagnosticRules.Code submitted = scheduleFirstFuture(
                context,
                entry,
                Math.max(System.currentTimeMillis(), deliveredAt),
                current.generation
        );
        // One successful continuation cannot certify the rest of a previously failed batch.
        if (ReminderDiagnosticRules.scheduleSucceeded(submitted)) {
            ReminderDiagnostics.recordSchedule(context, submitted);
        } else {
            recordSubmissionResult(context, submitted);
        }
        return true;
    }

    @SuppressLint("ApplySharedPref") // 保存 tombstone 失败时兜底清空，必须同步落盘，避免下次启动恢复旧提醒
    static void clear(Context context) {
        ScheduleData previous = loadSchedule(context);
        ScheduleData tombstone = new ScheduleData(
                nextGeneration(previous.generation),
                new ArrayList<>()
        );
        boolean saved = saveSchedule(context, tombstone);
        if (!saved) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(STORE_KEY, "")
                    .commit();
        }
        cancelEntries(context, previous.entries);
        recordSubmissionResult(context, saved ? ReminderDiagnosticRules.Code.NO_FUTURE : ReminderDiagnosticRules.Code.STORE_FAILED);
    }

    static Status status(Context context) {
        ScheduleData stored = loadSchedule(context);
        List<Entry> entries = stored.entries;
        long now = System.currentTimeMillis();
        long nextTriggerAt = -1L;
        int pendingCourses = 0;
        for (Entry entry : entries) {
            long candidate = entry.nextAfter(now);
            if (candidate <= 0L) {
                continue;
            }
            pendingCourses++;
            if (nextTriggerAt <= 0L || candidate < nextTriggerAt) {
                nextTriggerAt = candidate;
            }
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean submissionOk = prefs.getBoolean(LAST_SUBMISSION_OK_KEY, entries.isEmpty());
        long submittedAt = prefs.getLong(LAST_SUBMISSION_AT_KEY, 0L);
        return new Status(
                pendingCourses,
                nextTriggerAt,
                submissionOk,
                submittedAt,
                canScheduleExactAlarms(context),
                stored.generation != Long.MIN_VALUE
        );
    }

    private static long nextGeneration(long previousGeneration) {
        long candidate = System.currentTimeMillis() * 1024L
                + (android.os.SystemClock.elapsedRealtimeNanos() & 1023L);
        return Math.max(candidate, previousGeneration + 1L);
    }

    private static void cancelEntries(Context context, List<Entry> entries) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            for (Entry entry : entries) {
                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        context,
                        entry.requestCode,
                        new Intent(context, ClassReminderReceiver.class),
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
                );
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent);
                    pendingIntent.cancel();
                }
            }
        }
    }

    private static ReminderDiagnosticRules.Code scheduleFirstFuture(
            Context context,
            Entry entry,
            long afterMillis,
            long generation
    ) {
        long triggerAt = entry.nextAfter(afterMillis);
        if (triggerAt <= 0L) return ReminderDiagnosticRules.Code.NO_FUTURE;
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return ReminderDiagnosticRules.Code.ALARM_UNAVAILABLE;
        try {
            Intent intent = entry.toIntent(context, generation);
            intent.putExtra(EXTRA_TRIGGER_AT, triggerAt);
            long startAt = ReminderTiming.startAt(triggerAt, entry.leadMinutes);
            intent.putExtra(ClassReminderReceiver.EXTRA_START_AT, startAt);
            intent.putExtra(ClassReminderReceiver.EXTRA_END_AT, ReminderTiming.endAt(startAt, entry.time));
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, entry.requestCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (canScheduleExactAlarms(context)) {
                try {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                    return ReminderDiagnosticRules.Code.SCHEDULE_EXACT;
                } catch (SecurityException permissionChanged) {
                    // Permission may change between inspection and submission; retain a real fallback.
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                    return ReminderDiagnosticRules.Code.SCHEDULE_INEXACT;
                }
            }
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            return ReminderDiagnosticRules.Code.SCHEDULE_INEXACT;
        } catch (SecurityException denied) {
            return ReminderDiagnosticRules.Code.SCHEDULE_SECURITY_ERROR;
        } catch (RuntimeException failed) {
            return ReminderDiagnosticRules.Code.SCHEDULE_ERROR;
        }
    }

    static boolean canScheduleExactAlarms(Context context) {
        if (Build.VERSION.SDK_INT < 31) {
            return true;
        }
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        try { return alarmManager != null && alarmManager.canScheduleExactAlarms(); }
        catch (RuntimeException unavailable) { return false; }
    }

    @SuppressLint("ApplySharedPref") // BroadcastReceiver 路径下进程可能被回收，必须同步落盘
    private static void recordSubmissionResult(Context context, ReminderDiagnosticRules.Code result) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(LAST_SUBMISSION_OK_KEY, ReminderDiagnosticRules.scheduleSucceeded(result))
                .putLong(LAST_SUBMISSION_AT_KEY, System.currentTimeMillis())
                .commit();
        ReminderDiagnostics.recordSchedule(context, result);
    }

    /** Persist the receipt only after every alarm in this generation was submitted successfully. */
    private static void recordFullSubmission(Context context, ScheduleData schedule,
            ReminderDiagnosticRules.Code result) {
        recordSubmissionResult(context, result);
        if (schedule.sourceFingerprint == null || !ReminderDiagnosticRules.scheduleSucceeded(result)) return;
        // A security race may have fallen back to inexact even while the capability query says true.
        // Keep that mismatch visible to the next foreground retry instead of caching it as exact.
        boolean exact = result != ReminderDiagnosticRules.Code.SCHEDULE_INEXACT
                && canScheduleExactAlarms(context);
        if (!SafePreferences.commit(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                new PreferenceTransaction().put(SUBMITTED_GENERATION_KEY, schedule.generation)
                        .put(SUBMITTED_EXACT_KEY, exact).put(SUBMITTED_AT_KEY, System.currentTimeMillis()))) {
            recordSubmissionResult(context, ReminderDiagnosticRules.Code.STORE_FAILED);
        }
    }

    private static boolean saveSchedule(Context context, ScheduleData schedule) {
        try {
            JSONArray array = new JSONArray();
            for (Entry entry : schedule.entries) {
                array.put(entry.toJson());
            }
            JSONObject payload = new JSONObject();
            payload.put("generation", schedule.generation);
            if (schedule.sourceFingerprint != null) payload.put("sourceFingerprint", schedule.sourceFingerprint);
            payload.put("entries", array);
            String encrypted = STORAGE.encrypt(payload.toString());
            // A failed SharedPreferences commit still changes its in-memory cache.
            // Restore the previous generation too, otherwise unchanged pending alarms
            // would be rejected as stale after this method reports a save failure.
            return SafePreferences.commit(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                    new PreferenceTransaction().put(STORE_KEY, encrypted));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static ScheduleData loadSchedule(Context context) {
        List<Entry> entries = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String encrypted = prefs.getString(STORE_KEY, null);
        if (encrypted == null || encrypted.isEmpty()) {
            return new ScheduleData(0L, entries);
        }
        try {
            String decoded = STORAGE.decrypt(encrypted);
            long generation = 0L;
            String fingerprint = null;
            JSONArray array;
            if (decoded.trim().startsWith("[")) {
                array = new JSONArray(decoded);
            } else {
                JSONObject payload = new JSONObject(decoded);
                generation = payload.optLong("generation", 0L);
                fingerprint = payload.optString("sourceFingerprint", null);
                array = payload.optJSONArray("entries");
                if (array == null) {
                    return new ScheduleData(generation, entries);
                }
            }
            for (int i = 0; i < array.length(); i++) {
                Entry entry = Entry.fromJson(array.optJSONObject(i));
                if (entry != null) {
                    entries.add(entry);
                }
            }
            return new ScheduleData(generation, entries, fingerprint);
        } catch (Exception ignored) {
            // Keep the encrypted payload intact. Opening the app can rebuild it from course data.
        }
        return new ScheduleData(Long.MIN_VALUE, entries);
    }


    static final class Entry {
        final int requestCode;
        final String name;
        final String time;
        final String room;
        final int leadMinutes;
        final long[] triggerTimes;
        /** {@link #KIND_COURSE} or {@link #KIND_EVENT}; selects the notification wording. */
        final String kind;

        Entry(int requestCode, String name, String time, String room, int leadMinutes, long[] triggerTimes) {
            this(requestCode, name, time, room, leadMinutes, triggerTimes, KIND_COURSE);
        }

        Entry(int requestCode, String name, String time, String room, int leadMinutes, long[] triggerTimes, String kind) {
            this(requestCode,name,time,room,leadMinutes,triggerTimes,kind,null);
        }
        final RepeatAlarm repeatAlarm;
        long nextAfter(long after) { return repeatAlarm==null ? TimetableRules.nextTriggerAfter(triggerTimes,after) : repeatAlarm.next(after,leadMinutes); }
        Entry(int requestCode,String name,String time,String room,int leadMinutes,long[] triggerTimes,String kind,RepeatAlarm repeatAlarm) {
            this.repeatAlarm=repeatAlarm;
            this.kind = KIND_EVENT.equals(kind) ? KIND_EVENT : KIND_COURSE;
            this.requestCode = requestCode;
            this.name = name == null ? "" : name;
            this.time = time == null ? "" : time;
            this.room = room == null ? "" : room;
            this.leadMinutes = leadMinutes;
            this.triggerTimes = triggerTimes == null ? new long[0] : triggerTimes.clone();
            Arrays.sort(this.triggerTimes);
        }

        Intent toIntent(Context context, long generation) {
            Intent intent = new Intent(context, ClassReminderReceiver.class);
            intent.putExtra(ClassReminderReceiver.EXTRA_COURSE_NAME, name);
            intent.putExtra(ClassReminderReceiver.EXTRA_COURSE_TIME, time);
            intent.putExtra(ClassReminderReceiver.EXTRA_COURSE_ROOM, room);
            intent.putExtra(ClassReminderReceiver.EXTRA_REMINDER_MINUTES, leadMinutes);
            intent.putExtra(ClassReminderReceiver.EXTRA_KIND, kind);
            intent.putExtra(EXTRA_REQUEST_CODE, requestCode);
            intent.putExtra(EXTRA_GENERATION, generation);
            return intent;
        }

        JSONObject toJson() throws Exception {
            JSONObject object = new JSONObject();
            object.put("requestCode", requestCode);
            object.put("name", name);
            object.put("time", time);
            object.put("room", room);
            object.put("leadMinutes", leadMinutes);
            object.put("kind", kind);
            JSONArray times = new JSONArray();
            for (long triggerTime : triggerTimes) {
                times.put(triggerTime);
            }
            if(repeatAlarm!=null) object.put("repeatAlarm",repeatAlarm.encode());
            object.put("triggerTimes", times);
            return object;
        }

        static Entry fromJson(JSONObject object) {
            if (object == null) {
                return null;
            }
            JSONArray times = object.optJSONArray("triggerTimes");
            if (times == null) {
                return null;
            }
            long[] triggerTimes = new long[times.length()];
            for (int i = 0; i < times.length(); i++) {
                triggerTimes[i] = times.optLong(i, -1L);
            }
            return new Entry(
                    object.optInt("requestCode", 0),
                    object.optString("name", ""),
                    object.optString("time", ""),
                    object.optString("room", ""),
                    object.optInt("leadMinutes", 30),
                    triggerTimes,
                    object.optString("kind", KIND_COURSE), RepeatAlarm.decode(object.optJSONObject("repeatAlarm"))
            );
        }

    }

    static final class Status {
        final int pendingCourses;
        final long nextTriggerAt;
        final boolean submissionOk;
        final long submittedAt;
        final boolean exactAlarmAllowed;
        final boolean scheduleReadable;

        Status(
                int pendingCourses,
                long nextTriggerAt,
                boolean submissionOk,
                long submittedAt,
                boolean exactAlarmAllowed,
                boolean scheduleReadable
        ) {
            this.pendingCourses = pendingCourses;
            this.nextTriggerAt = nextTriggerAt;
            this.submissionOk = submissionOk;
            this.submittedAt = submittedAt;
            this.exactAlarmAllowed = exactAlarmAllowed;
            this.scheduleReadable = scheduleReadable;
        }
    }

    private static final class ScheduleData {
        final long generation;
        final List<Entry> entries;
        final String sourceFingerprint;

        ScheduleData(long generation, List<Entry> entries) {
            this(generation, entries, null);
        }

        ScheduleData(long generation, List<Entry> entries, String sourceFingerprint) {
            this.generation = generation;
            this.entries = new ArrayList<>(entries);
            this.sourceFingerprint = sourceFingerprint;
        }

        Entry entryForRequestCode(int requestCode) {
            for (Entry entry : entries) {
                if (entry.requestCode == requestCode) {
                    return entry;
                }
            }
            return null;
        }
    }
}
