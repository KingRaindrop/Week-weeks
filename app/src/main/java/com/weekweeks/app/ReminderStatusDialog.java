package com.weekweeks.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Read-only inspection plus explicit user-initiated tests; no production plan is rewritten here. */
final class ReminderStatusDialog {
    private ReminderStatusDialog() { }

    /** Caller owns showing and main-modal coordination. Do not replace the dismiss listener. */
    static Dialog create(Activity activity, Runnable onClosed) {
        boolean dark = MainActivity.isDarkModeFor(activity);
        int accent = MainActivity.accentColorFor(activity);
        int ink = dark ? DarkUiPalette.INK : 0xFF25314D;
        int secondary = dark ? DarkUiPalette.SECONDARY : 0xFF566176;
        int cardBg = dark ? DarkUiPalette.RAISED : 0xFFF7F8FE;
        int rowBg = dark ? DarkUiPalette.RAISED : 0xFFFFFFFF;
        int border = dark ? DarkUiPalette.LINE : 0xFFE5E8F2;

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(activity, 20);
        body.setPadding(padding, padding / 2, padding, padding / 2);
        TextView report = new TextView(activity);
        report.setTextIsSelectable(true);
        report.setTextColor(ink);
        report.setTextSize(12.5f);
        report.setLineSpacing(dp(activity, 3), 1f);
        int boxPad = dp(activity, 12);
        report.setPadding(boxPad, boxPad, boxPad, boxPad);
        report.setBackground(rounded(cardBg, dp(activity, 14), border, dp(activity, 1)));
        LinearLayout.LayoutParams reportParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        reportParams.bottomMargin = dp(activity, 10);
        body.addView(report, reportParams);
        TextView feedback = new TextView(activity);
        feedback.setTextColor(accent);
        feedback.setTextSize(12.5f);
        feedback.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        feedback.setPadding(dp(activity, 2), 0, dp(activity, 2), dp(activity, 8));
        feedback.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        body.addView(feedback);
        Runnable refresh = () -> {
            String latest = ReminderDiagnostics.report(activity);
            if (!latest.contentEquals(report.getText())) report.setText(latest);
        };
        action(activity, body, R.string.reminder_diag_test_now, rowBg, border, accent, true, () -> {
            feedback.setText(ReminderDiagnostics.sendImmediateTest(activity).message(activity)); refresh.run();
        });
        action(activity, body, R.string.reminder_diag_test_delayed, rowBg, border, accent, true, () -> {
            feedback.setText(ReminderDiagnostics.scheduleTest(activity, ReminderDiagnostics.DEFAULT_TEST_DELAY_MILLIS).message(activity)); refresh.run();
        });
        action(activity, body, R.string.reminder_diag_cancel_test, rowBg, border, secondary, false, () -> {
            feedback.setText(ReminderDiagnostics.cancelTest(activity).message(activity)); refresh.run();
        });
        action(activity, body, R.string.reminder_diag_open_notifications, rowBg, border, ink, false, () -> open(activity, ReminderDiagnostics.appNotificationSettings(activity), feedback));
        action(activity, body, R.string.reminder_diag_open_channel, rowBg, border, ink, false, () -> open(activity, ReminderDiagnostics.channelSettings(activity), feedback));
        action(activity, body, R.string.reminder_diag_open_exact, rowBg, border, ink, false, () -> open(activity, ReminderDiagnostics.exactAlarmSettings(activity), feedback));
        action(activity, body, R.string.reminder_diag_open_app, rowBg, border, ink, false, () -> open(activity, ReminderDiagnostics.appDetailsSettings(activity), feedback));
        action(activity, body, R.string.reminder_diag_copy, rowBg, border, accent, false, () -> {
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.reminder_diag_title), ReminderDiagnostics.report(activity)));
                feedback.setText(R.string.reminder_diag_copied);
            }
        });
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.addView(body);
        Dialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.reminder_diag_title).setView(scroll)
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
            if (dark && dialog.getWindow() != null) {
                DialogSurfaceStyle.apply(dialog,
                        rounded(DarkUiPalette.DIALOG_SURFACE, dp(activity, 24), border, dp(activity, 1)));
            }
            AppFonts.install(dialog);
            androidx.appcompat.app.AlertDialog alert = (androidx.appcompat.app.AlertDialog) dialog;
            Button closeBtn = alert.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
            if (closeBtn != null) closeBtn.setTextColor(accent);
            Button refreshBtn = alert.getButton(android.content.DialogInterface.BUTTON_NEUTRAL);
            if (refreshBtn != null) {
                refreshBtn.setTextColor(secondary);
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

    private static void action(Activity activity, LinearLayout body, int text,
                               int fill, int border, int textColor, boolean primary, Runnable run) {
        Button button = new Button(activity);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(textColor);
        button.setTextSize(14f);
        button.setTypeface(Typeface.DEFAULT, primary ? Typeface.BOLD : Typeface.NORMAL);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setMinHeight(dp(activity, 46));
        button.setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10));
        button.setStateListAnimator(null);
        int radius = dp(activity, 12);
        int rippleColor = Color.argb(46, Color.red(textColor), Color.green(textColor), Color.blue(textColor));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(rippleColor),
                rounded(fill, radius, border, dp(activity, 1)),
                rounded(Color.WHITE, radius, Color.TRANSPARENT, 0)));
        button.setOnClickListener(v -> run.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(activity, 6);
        body.addView(button, lp);
    }

    private static GradientDrawable rounded(int fill, int radius, int stroke, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, stroke);
        return d;
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void open(Activity activity, Intent intent, TextView feedback) {
        try { activity.startActivity(intent); }
        catch (RuntimeException unavailable) {
            try { activity.startActivity(ReminderDiagnostics.appDetailsSettings(activity)); }
            catch (RuntimeException noSettings) { feedback.setText(R.string.reminder_diag_settings_unavailable); }
        }
    }
}
