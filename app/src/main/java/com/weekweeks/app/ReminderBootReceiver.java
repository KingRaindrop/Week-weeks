package com.weekweeks.app;

import android.app.AlarmManager;
import android.os.Build;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReminderBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        context = AppLanguage.wrap(context);
        String action = intent == null ? null : intent.getAction();
        boolean permissionGranted = Build.VERSION.SDK_INT >= 31
                && AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)
                && ReminderScheduler.canScheduleExactAlarms(context);
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || permissionGranted) {
            ReminderScheduler.rescheduleStored(context);
        }
    }
}
