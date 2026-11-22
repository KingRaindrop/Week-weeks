package com.weekweeks.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

/** Read-only inspection plus explicit user-initiated tests; no production plan is rewritten here. */
final class ReminderStatusDialog {
    private ReminderStatusDialog() { }

    /** Caller owns showing and main-modal coordination. Do not replace the dismiss listener. */
    static Dialog create(Activity activity, Runnable onClosed) {
        SettingsDialogStyle style = SettingsDialogStyle.of(activity);
        LinearLayout body = style.body();
        LinearLayout reportGroup = style.group();
        TextView report = style.caption("");
        report.setTextIsSelectable(true);
        report.setTextColor(style.colors.text);
        report.setLineSpacing(dp(activity, 3), 1f);
        reportGroup.addView(report, params(activity, 8));
        TextView feedback = style.caption("");
        feedback.setTextColor(style.colors.accent);
        feedback.setTypeface(AppFonts.typeface(activity, Typeface.BOLD));
        feedback.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        feedback.setVisibility(View.GONE);
        Runnable refresh = () -> {
            String latest = ReminderDiagnostics.report(activity);
            if (!latest.contentEquals(report.getText())) report.setText(latest);
        };
        action(activity, style, reportGroup, R.string.reminder_diag_copy, () -> {
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.reminder_diag_title), ReminderDiagnostics.report(activity)));
                feedback(feedback, activity.getString(R.string.reminder_diag_copied));
            }
        });
        body.addView(reportGroup, params(activity, 12));
        body.addView(feedback, params(activity, 12));

        LinearLayout tests = style.group();
        tests.addView(style.heading(activity.getString(R.string.reminder_diag_test_notification_title)), params(activity, 8));
        action(activity, style, tests, R.string.reminder_diag_test_now, () -> {
            feedback(feedback, ReminderDiagnostics.sendImmediateTest(activity).message(activity)); refresh.run();
        });
        action(activity, style, tests, R.string.reminder_diag_test_delayed, () -> {
            feedback(feedback, ReminderDiagnostics.scheduleTest(activity, ReminderDiagnostics.DEFAULT_TEST_DELAY_MILLIS).message(activity)); refresh.run();
        });
        action(activity, style, tests, R.string.reminder_diag_cancel_test, () -> {
            feedback(feedback, ReminderDiagnostics.cancelTest(activity).message(activity)); refresh.run();
        });
        body.addView(tests, params(activity, 12));

        LinearLayout settings = style.group();
        settings.addView(style.heading(activity.getString(R.string.main_settings)), params(activity, 8));
        action(activity, style, settings, R.string.reminder_diag_open_notifications, () -> open(activity, ReminderDiagnostics.appNotificationSettings(activity), feedback));
        action(activity, style, settings, R.string.reminder_diag_open_channel, () -> open(activity, ReminderDiagnostics.channelSettings(activity), feedback));
        action(activity, style, settings, R.string.reminder_diag_open_exact, () -> open(activity, ReminderDiagnostics.exactAlarmSettings(activity), feedback));
        action(activity, style, settings, R.string.reminder_diag_open_app, () -> open(activity, ReminderDiagnostics.appDetailsSettings(activity), feedback));
        body.addView(settings, params(activity, 0));

        Dialog dialog = style.builder()
                .setTitle(R.string.reminder_diag_title).setView(style.scrollBody(body))
                .setPositiveButton(R.string.reminder_diag_close, (d, which) -> d.dismiss())
                .setNeutralButton(R.string.reminder_diag_refresh, null).create();
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable update = new Runnable() {
            @Override public void run() {
                if (!dialog.isShowing()) return;
                // Do not repeatedly decrypt the production plan while Home, Settings or the
                // lock screen owns focus; the next focused tick refreshes after returning.
                if (dialog.getWindow() != null && dialog.getWindow().getDecorView().hasWindowFocus())
                    refresh.run();
                handler.postDelayed(this, 2000L);
            }
        };
        dialog.setOnShowListener(d -> {
            AlertDialog alert = (AlertDialog) dialog;
            style.apply(alert);
            Button refreshBtn = alert.getButton(android.content.DialogInterface.BUTTON_NEUTRAL);
            if (refreshBtn != null) {
                refreshBtn.setOnClickListener(v -> refresh.run());
            }
            handler.post(update);
        });
        dialog.setOnDismissListener(d -> {
            handler.removeCallbacks(update);
            if (onClosed != null) onClosed.run();
        });
        refresh.run();
        return dialog;
    }

    static Dialog show(Activity activity) {
        Dialog dialog = create(activity, null);
        dialog.show();
        return dialog;
    }

    private static void action(Activity activity, SettingsDialogStyle style, LinearLayout body, int text, Runnable run) {
        Button button = new Button(activity);
        button.setText(text);
        style.action(button, false);
        button.setOnClickListener(v -> run.run());
        body.addView(button, params(activity, 6));
    }

    private static LinearLayout.LayoutParams params(Activity activity, int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(activity, bottomMargin);
        return params;
    }

    private static void feedback(TextView feedback, CharSequence message) {
        feedback.setVisibility(View.VISIBLE);
        feedback.setText(message);
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void open(Activity activity, Intent intent, TextView feedback) {
        try { activity.startActivity(intent); }
        catch (RuntimeException unavailable) {
            try { activity.startActivity(ReminderDiagnostics.appDetailsSettings(activity)); }
            catch (RuntimeException noSettings) { feedback(feedback, activity.getString(R.string.reminder_diag_settings_unavailable)); }
        }
    }
}
