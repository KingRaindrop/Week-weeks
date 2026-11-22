package com.weekweeks.app;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Calendar form, child picker lifecycle and saveable draft; persistence stays with the host. */
final class CalendarSetupDialog {
    interface Saver { boolean save(long start, long end); }
    static final class State {
        final boolean firstRun;
        String start, end;
        int pickerField; // 0 none, 1 start, 2 end
        int year, month, day;
        boolean finished;
        State(boolean firstRun, String start, String end) {
            this.firstRun = firstRun; this.start = start; this.end = end;
        }
        Bundle save() {
            if (finished) return null;
            Bundle b = new Bundle();
            b.putBoolean("first", firstRun); b.putString("start", start); b.putString("end", end);
            b.putInt("picker", pickerField); b.putInt("year", year); b.putInt("month", month); b.putInt("day", day);
            return b;
        }
        static State restore(Bundle b) {
            if (b == null) return null;
            State s = new State(b.getBoolean("first"), b.getString("start", ""), b.getString("end", ""));
            s.pickerField = b.getInt("picker"); s.year = b.getInt("year");
            s.month = b.getInt("month"); s.day = b.getInt("day");
            return s;
        }
    }

    final AlertDialog dialog;
    final State state;
    private final Activity activity;
    private final SettingsDialogStyle style;
    private final EditText startInput, endInput;
    private DatePickerDialog picker;
    private boolean closed;

    CalendarSetupDialog(Activity activity, State state, EditText startInput, EditText endInput,
                        View startLabel, View endLabel, Saver saver) {
        this.activity = activity; this.state = state;
        this.style = SettingsDialogStyle.of(activity);
        this.startInput = startInput; this.endInput = endInput;
        LinearLayout body = style.body();
        TextView explanation = style.caption(activity.getString(state.firstRun
                ? R.string.main_welcome_calendar_message : R.string.main_calendar_message));
        body.addView(explanation, params(12));
        LinearLayout form = style.group();
        style.field(startInput); style.field(endInput);
        configureField(startInput, 1); configureField(endInput, 2);
        form.addView(dateLabel(startLabel, startInput), params(6));
        form.addView(startInput, params(16));
        form.addView(dateLabel(endLabel, endInput), params(6));
        form.addView(endInput, params(0));
        body.addView(form, params(0));
        MaterialAlertDialogBuilder builder = style.builder()
                .setTitle(state.firstRun ? R.string.main_welcome_calendar_title : R.string.main_calendar_settings)
                .setView(style.scrollBody(body)).setPositiveButton(R.string.action_save, null);
        if (!state.firstRun) builder.setNegativeButton(R.string.action_cancel, (d, which) -> state.finished = true);
        dialog = builder.create();
        dialog.setCancelable(!state.firstRun);
        dialog.setOnCancelListener(d -> state.finished = true);
        dialog.setOnShowListener(d -> {
            style.apply(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                capture();
                Long start = parseDateMillis(state.start), end = parseDateMillis(state.end);
                if (start == null || end == null) { toast(R.string.main_date_format); return; }
                if (end < start) { toast(R.string.main_end_before_start); return; }
                // Do not discard dates or close the required first-run form on storage failure.
                if (!saver.save(start, end)) return;
                state.finished = true;
                dialog.dismiss();
            });
            if (state.pickerField == 1 || state.pickerField == 2) openPicker(state.pickerField, true);
        });
    }

    State capture() {
        state.start = startInput.getText().toString(); state.end = endInput.getText().toString();
        if (picker != null && picker.isShowing()) {
            state.year = picker.getDatePicker().getYear();
            state.month = picker.getDatePicker().getMonth();
            state.day = picker.getDatePicker().getDayOfMonth();
        }
        return state;
    }

    void closeChild() {
        closed = true;
        if (picker != null) picker.dismiss();
        picker = null;
    }

    private void configureField(EditText field, int which) {
        // These controls open a picker rather than accept text. Keep hardware-keyboard
        // traversal, without consuming the first touch solely to focus an EditText.
        field.setKeyListener(null);
        field.setMovementMethod(null);
        field.setCursorVisible(false);
        field.setShowSoftInputOnFocus(false);
        field.setLongClickable(false);
        field.setFocusable(true);
        field.setFocusableInTouchMode(false);
        if (field.getId() == View.NO_ID) field.setId(View.generateViewId());
        field.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host,
                    android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.Button.class.getName());
                info.setEditable(false);
            }
        });
        field.setOnClickListener(v -> openPicker(which, false));
    }

    private void openPicker(int which, boolean restored) {
        if (closed || (picker != null && picker.isShowing())) return;
        EditText field = which == 1 ? startInput : endInput;
        // Each new visit starts at today's local date, even when the field holds an old term date.
        // Recreating an open picker must retain the date the user is currently choosing instead.
        Calendar initial = Calendar.getInstance();
        if (restored && state.year >= 2000 && state.year <= 2100)
            initial.set(state.year, state.month, state.day);
        Calendar min = Calendar.getInstance(); min.clear(); min.set(2000, Calendar.JANUARY, 1);
        Calendar max = Calendar.getInstance(); max.clear(); max.set(2100, Calendar.DECEMBER, 31);
        initial.setTimeInMillis(Math.max(min.getTimeInMillis(), Math.min(max.getTimeInMillis(), initial.getTimeInMillis())));
        state.pickerField = which;
        DatePickerDialog child = new DatePickerDialog(activity, (view, year, month, day) -> {
            Calendar chosen = Calendar.getInstance(); chosen.clear(); chosen.set(year, month, day);
            field.setText(formatDate(chosen.getTimeInMillis()));
            capture();
        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH));
        child.getDatePicker().setMinDate(min.getTimeInMillis());
        child.getDatePicker().setMaxDate(max.getTimeInMillis());
        child.setOnDismissListener(d -> {
            if (picker == child) { picker = null; if (!closed) state.pickerField = 0; }
        });
        picker = child;
        child.show();
        android.widget.Button ok = child.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        if (ok != null) ok.setTextColor(style.colors.accent);
        android.widget.Button cancel = child.getButton(android.content.DialogInterface.BUTTON_NEGATIVE);
        if (cancel != null) cancel.setTextColor(style.colors.secondary);
        AppFonts.install(child);
    }
    private View dateLabel(View supplied, EditText target) {
        View label = supplied instanceof TextView ? style.heading(((TextView) supplied).getText()) : supplied;
        label.setLabelFor(target.getId());
        return label;
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams params(int bottomMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(bottomMargin);
        return p;
    }
    private void toast(int resource) { Toast.makeText(activity, resource, Toast.LENGTH_SHORT).show(); }
    static String formatDate(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date(millis));
    }
    static Long parseDateMillis(String value) {
        if (value == null) return null;
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US); format.setLenient(false);
            java.util.Date date = format.parse(value.trim());
            return date == null ? null : date.getTime();
        } catch (Exception invalid) { return null; }
    }
}
