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
        if (Intent.ACTION_TIMEZONE_CHANGED.equals(action) || Intent.ACTION_TIME_CHANGED.equals(action)) {
            try {
                ReminderSourceRebuilder.rebuild(context);
            } catch (RuntimeException failed) {
                ReminderDiagnostics.recordSchedule(context, ReminderDiagnosticRules.Code.RESTORE_FAILED);
            }
            return;
        }
        boolean permissionGranted = Build.VERSION.SDK_INT >= 31
                && AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)
                && ReminderScheduler.canScheduleExactAlarms(context);
        boolean systemRestore = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
        if (systemRestore || permissionGranted) {
            try {
                // Granting permission leaves the user's pending diagnostic alarm valid.
                // Only a reboot/update invalidates that short-lived test.
                if (systemRestore) ReminderDiagnostics.onSystemRestore(context);
                ReminderScheduler.rescheduleStored(context);
            } catch (RuntimeException failed) {
                ReminderDiagnostics.recordSchedule(context, ReminderDiagnosticRules.Code.RESTORE_FAILED);
            }
        }
    }
}
