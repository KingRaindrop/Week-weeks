package com.weekweeks.app;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
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
    private final EditText startInput, endInput;
    private final long fallbackStart, fallbackEnd;
    private DatePickerDialog picker;
    private boolean closed;

    CalendarSetupDialog(Activity activity, State state, EditText startInput, EditText endInput,
                        View startLabel, View endLabel, long fallbackStart, long fallbackEnd, Saver saver) {
        this.activity = activity; this.state = state;
        this.startInput = startInput; this.endInput = endInput;
        this.fallbackStart = fallbackStart; this.fallbackEnd = fallbackEnd;
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4), dp(8), dp(4), 0);
        configureField(startInput, 1); configureField(endInput, 2);
        form.addView(startLabel); form.addView(startInput, params());
        form.addView(endLabel); form.addView(endInput, params());
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(state.firstRun ? R.string.main_welcome_calendar_title : R.string.main_calendar_settings)
                .setMessage(state.firstRun ? R.string.main_welcome_calendar_message : R.string.main_calendar_message)
                .setView(form).setPositiveButton(R.string.action_save, null);
        if (!state.firstRun) builder.setNegativeButton(R.string.action_cancel, (d, which) -> state.finished = true);
        dialog = builder.create();
        dialog.setCancelable(!state.firstRun);
        dialog.setOnCancelListener(d -> state.finished = true);
        dialog.setOnShowListener(d -> {
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
        field.setKeyListener(null); field.setFocusable(false); field.setCursorVisible(false);
        field.setOnClickListener(v -> openPicker(which, false));
    }

    private void openPicker(int which, boolean restored) {
        if (closed || (picker != null && picker.isShowing())) return;
        EditText field = which == 1 ? startInput : endInput;
        Calendar initial = Calendar.getInstance();
        Long selected = parseDateMillis(field.getText().toString());
        initial.setTimeInMillis(selected != null ? selected : which == 1 ? fallbackStart : fallbackEnd);
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
        int accent = MainActivity.accentColorFor(activity);
        boolean dark = MainActivity.isDarkModeFor(activity);
        android.widget.Button ok = child.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        if (ok != null) ok.setTextColor(accent);
        android.widget.Button cancel = child.getButton(android.content.DialogInterface.BUTTON_NEGATIVE);
        if (cancel != null) cancel.setTextColor(dark ? DarkUiPalette.SECONDARY : 0xFF566176);
        AppFonts.install(child);
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams params() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(4); return p;
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
