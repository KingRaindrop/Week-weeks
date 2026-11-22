package com.weekweeks.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;
import java.util.Locale;

/**
 * Pastel-style bottom sheets for course details, editing and confirmations.
 * Presentation only: validation and persistence stay with the host through {@link Saver}.
 */
final class PastelCourseSheet {
    static final class Draft {
        int day, start = 1, end = 1, color;
        boolean colorManuallySelected;
        String name = "", weeks = "", room = "", teacher = "";
    }

    static final class Detail {
        String name, dayLabel, periodLabel, time, weeks, room, teacher;
        int color;
    }

    /** Returns null when saved, otherwise a message shown inline while the sheet stays open. */
    interface Saver { String save(Draft draft); }
    interface ConflictCheck<T> { List<ScheduleConflicts.Hit> review(T value); }
    ConflictCheck<Draft> courseConflicts;
    ConflictCheck<PersonalEvent> eventConflicts;
    private <T> void reviewSave(T value,ConflictCheck<T> check,Runnable save) {
        List<ScheduleConflicts.Hit> hits = check == null ? null : check.review(value);
        if (hits == null || hits.isEmpty()) save.run();
        else confirmChoice("time_conflict", ConflictListView.title(context, hits), null,
                s(R.string.conflict_keep), s(R.string.conflict_back), false, save, hits);
    }

    /** Returns true when the course was removed. */
    interface Deleter { boolean delete(); }

    private static final int MAX_FIELD_LENGTH = 60;

    private final Context context;
    private final boolean dark;
    private final UiSemanticPalette palette;
    private final int sheetBg, surface, field, ink, muted, line, coral, lavender, danger;

    private final ModalCoordinator coordinator;
    private ManagedSheet mainDialog;
    private BottomSheetDialog confirmation;
    private String confirmationKind;
    private ConflictListView conflictList;
    private Bundle restoredConflictAnchor;
    Runnable onDismissed;
    boolean isShowing() { return mainDialog != null && mainDialog.isShowing(); }

    PastelCourseSheet(Context context, boolean dark, PastelAccent theme) {
        this(context, dark, theme, null);
    }

    PastelCourseSheet(Context context, boolean dark, PastelAccent theme, ModalCoordinator coordinator) {
        this.coordinator = coordinator;
        this.context = context;
        this.dark = dark;
        if (theme == null) theme = PastelAccent.classic(dark);
        palette = UiSemanticPalette.of(dark, theme.accent);
        sheetBg = palette.surface;
        surface = palette.inset;
        field = palette.inset;
        ink = palette.text;
        muted = palette.secondary;
        line = palette.divider;
        coral = palette.accent;
        lavender = theme.tint;
        danger = palette.error;
    }

    // ---------------------------------------------------------------- details

    void showDetail(Detail detail, Runnable onEdit) {
        showDetail(detail, onEdit, null);
    }

    void showDetail(Detail detail, Runnable onEdit, Runnable onClosed) {
        BottomSheetDialog dialog = new ManagedSheet(context);
        dialog.setOnDismissListener(d -> { if (onClosed != null) onClosed.run(); });
        LinearLayout root = sheetRoot();

        LinearLayout hero = column();
        hero.setPadding(dp(18), dp(16), dp(18), dp(18));
        hero.setBackground(round(pastel(detail.color), 20));
        int badgeSurface = CoursePalette.detailBadgeSurface(pastel(detail.color), dark);
        TextView when = text(detail.dayLabel + " · " + detail.periodLabel, 12,
                CoursePalette.activeInk(badgeSurface, dark), true);
        when.setPadding(dp(10), dp(4), dp(10), dp(4));
        when.setBackground(round(badgeSurface, 12));
        hero.addView(when, new LinearLayout.LayoutParams(-2, -2));
        TextView name = text(nonempty(detail.name, s(R.string.sheet_untitled_course)), 21,
                CoursePalette.activeInk(pastel(detail.color), dark), true);
        name.setPadding(0, dp(10), 0, 0);
        hero.addView(name);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, -2);
        heroParams.setMargins(dp(18), dp(6), dp(18), dp(12));
        root.addView(hero, heroParams);

        LinearLayout info = column();
        info.setBackground(round(surface, 18));
        info.setPadding(dp(14), dp(4), dp(14), dp(4));
        infoRow(info, "clock", s(R.string.sheet_class_time), nonempty(detail.time, s(R.string.sheet_time_unset)), true);
        infoRow(info, "calendar", s(R.string.sheet_weeks), nonempty(detail.weeks, s(R.string.sheet_every_week)), true);
        infoRow(info, "pin", s(R.string.sheet_location), nonempty(detail.room, s(R.string.sheet_location_tbd)), true);
        infoRow(info, "person", s(R.string.sheet_teacher), nonempty(detail.teacher, s(R.string.sheet_not_filled)), false);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(-1, -2);
        infoParams.setMargins(dp(18), 0, dp(18), 0);
        root.addView(info, infoParams);

        TextView hint = text(s(R.string.sheet_detail_hint), 11, muted, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(18), dp(12), dp(18), 0);
        root.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout buttons = row();
        buttons.setPadding(dp(18), dp(14), dp(18), dp(18));
        TextView close = secondaryButton(s(R.string.action_close));
        close.setOnClickListener(v -> dialog.dismiss());
        TextView edit = primaryButton(s(R.string.sheet_edit_course), coral);
        edit.setOnClickListener(v -> switchSheet(dialog, onEdit));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(48), 1);
        cp.rightMargin = dp(10);
        buttons.addView(close, cp);
        buttons.addView(edit, new LinearLayout.LayoutParams(0, dp(48), 2));
        root.addView(buttons, new LinearLayout.LayoutParams(-1, -2));

        present(dialog, root, true);
    }

    private TextView infoRow(LinearLayout parent, String icon, String label, String value, boolean divider) {
        LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(11), 0, dp(11));
        View badge = new View(context);
        badge.setBackground(new IconBadge(icon));
        row.addView(badge, new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout texts = column();
        texts.setPadding(dp(12), 0, 0, 0);
        texts.addView(text(label, 11, muted, false));
        TextView v = text(value, 15, ink, true);
        v.setPadding(0, dp(4), 0, 0);
        texts.addView(v);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        if (divider) {
            View d = new View(context);
            d.setBackgroundColor(line);
            LinearLayout.LayoutParams dpm = new LinearLayout.LayoutParams(-1, Math.max(1, dp(1) / 2 + 1));
            dpm.leftMargin = dp(46);
            parent.addView(d, dpm);
        }
        return v;
    }

    // ---------------------------------------------------------------- editor

    private BottomSheetDialog activeCourseDialog;
    private Draft activeCourseDraft;
    private EditText[] activeCourseInputs;
    private NestedScrollView activeCourseScroll;
    private String activeCourseInitial;
    private View activeTemporaryRow;
    private int[] activeTemporaryCount;
    private int activeTemporaryWeek;

    Bundle captureCourseEditorState() {
        if (activeCourseDialog == null || !activeCourseDialog.isShowing()) return null;
        readInputs(activeCourseDraft, activeCourseInputs);
        Bundle state = new Bundle();
        state.putInt("day", activeCourseDraft.day);
        state.putInt("start", activeCourseDraft.start);
        state.putInt("end", activeCourseDraft.end);
        state.putInt("color", activeCourseDraft.color);
        state.putBoolean("colorManuallySelected", activeCourseDraft.colorManuallySelected);
        state.putString("initial", activeCourseInitial);
        state.putInt("scroll", activeCourseScroll.getScrollY());
        state.putBoolean("temporary_visible", activeTemporaryRow.getVisibility() == View.VISIBLE);
        state.putInt("temporary_count", activeTemporaryCount[0]);
        state.putInt("temporary_week", activeTemporaryWeek);
        captureConfirmation(state);
        captureEditorInputs(state, activeCourseInputs);
        return state;
    }

    private static void captureEditorInputs(Bundle state, EditText[] inputs) {
        state.putInt("input_focus", -1);
        for (int i = 0; i < inputs.length; i++) {
            EditText input = inputs[i];
            state.putString("input" + i, input.getText().toString());
            state.putInt("input_selection_start_" + i, input.getSelectionStart());
            state.putInt("input_selection_end_" + i, input.getSelectionEnd());
            if (input.hasFocus()) state.putInt("input_focus", i);
        }
    }

    /** Restore parent view focus before reopening child windows; never request the keyboard. */
    private static void restoreEditorInputs(Bundle state, EditText[] inputs) {
        if (state == null) return;
        int focused = state.getInt("input_focus", -1);
        if (focused >= 0 && focused < inputs.length) inputs[focused].requestFocus();
        for (int i = 0; i < inputs.length; i++) {
            int start = state.getInt("input_selection_start_" + i, -1);
            int end = state.getInt("input_selection_end_" + i, -1);
            if (start < 0 || end < 0) continue;
            int length = inputs[i].length();
            inputs[i].setSelection(Math.min(start, length), Math.min(end, length));
        }
    }

    void showEditor(boolean editing, Draft draft, List<String> periodTimes, int[] colors, String[] colorNames,
                    int totalWeeks, int currentWeek, Saver saver, Deleter deleter) {
        showEditor(editing, draft, periodTimes, colors, colorNames, totalWeeks, currentWeek, saver, deleter, null, null);
    }

    void showEditor(boolean editing, Draft draft, List<String> periodTimes, int[] colors, String[] colorNames,
                    int totalWeeks, int currentWeek, Saver saver, Deleter deleter,
                    Bundle restoredState, Runnable onClosed) {
        final String baseline = snapshot(draft);
        if (restoredState != null) {
            draft.day = restoredState.getInt("day", draft.day);
            draft.start = restoredState.getInt("start", draft.start);
            draft.end = restoredState.getInt("end", draft.end);
            draft.color = restoredState.getInt("color", draft.color);
            draft.colorManuallySelected = restoredState.getBoolean("colorManuallySelected", false);
            draft.name = restoredState.getString("input0", draft.name);
            draft.weeks = restoredState.getString("input1", draft.weeks);
            draft.room = restoredState.getString("input2", draft.room);
            draft.teacher = restoredState.getString("input3", draft.teacher);
        }
        final int periods = Math.max(1, periodTimes.size());
        draft.day = clamp(draft.day, 0, 6);
        draft.start = clamp(draft.start, 1, periods);
        draft.end = clamp(Math.max(draft.end, draft.start), draft.start, periods);
        final String initial = restoredState == null ? snapshot(draft) : restoredState.getString("initial", baseline);
        final boolean[] allowClose = {false};

        final EditText[] inputs = new EditText[4];
        final BottomSheetDialog[] holder = new BottomSheetDialog[1];
        BottomSheetDialog dialog = new ManagedSheet(context) {
            @Override public void cancel() {
                readInputs(draft, inputs);
                if (allowClose[0] || snapshot(draft).equals(initial)) { super.cancel(); return; }
                confirm("discard", s(R.string.sheet_discard_course_title), s(R.string.sheet_discard_message),
                        s(R.string.action_discard_changes), true,
                        () -> { allowClose[0] = true; holder[0].cancel(); });
            }
        };
        holder[0] = dialog;
        LinearLayout root = sheetRoot();

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(2), dp(12), dp(8));
        TextView title = text(s(editing ? R.string.sheet_edit_course : R.string.sheet_add_course), 19, ink, true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        View close = new View(context);
        close.setBackground(new IconBadge("close"));
        close.setContentDescription(s(R.string.action_close));
        close.setOnClickListener(v -> dialog.cancel());
        header.addView(close, new LinearLayout.LayoutParams(dp(36), dp(36)));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout form = column();
        form.setPadding(dp(20), 0, dp(20), dp(12));
        NestedScrollView scroll = boundedScroll();
        scroll.setClipToPadding(false);
        scroll.addView(form);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        // Name
        form.addView(sectionLabel(s(R.string.sheet_course_name), null));
        inputs[0] = input(s(R.string.sheet_course_name_hint), draft.name);
        form.addView(inputs[0], fieldParams());

        // Day
        form.addView(sectionLabel(s(R.string.sheet_weekday), null));
        LinearLayout dayRow = row();
        final TextView[] dayChips = new TextView[7];
        String[] dayChipLabels = context.getResources().getStringArray(R.array.sheet_weekday_chips);
        String[] dayNames = context.getResources().getStringArray(R.array.sheet_weekday_names);
        for (int i = 0; i < 7; i++) {
            final int day = i;
            dayChips[i] = chip(dayChipLabels[i], null);
            dayChips[i].setContentDescription(dayNames[i]);
            dayChips[i].setOnClickListener(v -> { draft.day = day; refreshChoices(dayChips, day, -1); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1);
            if (i < 6) lp.rightMargin = dp(6);
            dayRow.addView(dayChips[i], lp);
        }
        refreshChoices(dayChips, draft.day, -1);
        form.addView(dayRow, new LinearLayout.LayoutParams(-1, -2));

        // Periods
        TextView periodSummary = text("", 12, coral, true);
        form.addView(sectionLabel(s(R.string.sheet_periods), periodSummary));
        final TextView[] startChips = new TextView[periods];
        final TextView[] endChips = new TextView[periods];
        final Runnable[] refreshPeriods = new Runnable[1];
        refreshPeriods[0] = () -> {
            refreshChoices(startChips, draft.start - 1, -1);
            refreshChoices(endChips, draft.end - 1, draft.start - 1);
            String startClock = clock(periodTimes.get(draft.start - 1), true);
            String endClock = clock(periodTimes.get(draft.end - 1), false);
            String range = draft.end > draft.start ? draft.start + "–" + draft.end : String.valueOf(draft.start);
            periodSummary.setText(startClock.isEmpty() || endClock.isEmpty()
                    ? context.getString(R.string.period_summary, range)
                    : context.getString(R.string.period_summary_with_time, range, startClock, endClock));
        };
        form.addView(periodRow(s(R.string.sheet_period_start), true, startChips, periodTimes, index -> {
            draft.start = index + 1;
            if (draft.end < draft.start) draft.end = draft.start;
            refreshPeriods[0].run();
        }), new LinearLayout.LayoutParams(-1, -2));
        form.addView(periodRow(s(R.string.sheet_period_end), false, endChips, periodTimes, index -> {
            if (index + 1 < draft.start) return;
            draft.end = index + 1;
            refreshPeriods[0].run();
        }), new LinearLayout.LayoutParams(-1, -2));
        refreshPeriods[0].run();

        // Weeks
        form.addView(sectionLabel(s(R.string.sheet_weeks), null));
        inputs[1] = input(s(R.string.sheet_weeks_hint), draft.weeks);
        form.addView(inputs[1], fieldParams());
        HorizontalScrollView weekScroll = new HorizontalScrollView(context);
        weekScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout weekChips = row();
        weekChips.setPadding(0, dp(8), 0, 0);
        weekScroll.addView(weekChips);
        // Older configurations may exceed the parser's supported range. Never insert invalid weeks.
        final int total = Math.max(1, Math.min(totalWeeks, TimetableRules.MAX_WEEK));
        final int temporaryWeek = restoredState == null ? currentWeek
                : restoredState.getInt("temporary_week", currentWeek);
        final int[] temporaryCount = {clamp(restoredState == null ? 1
                : restoredState.getInt("temporary_count", 1), 1, Math.max(1, total - temporaryWeek + 1))};
        LinearLayout tempRow = row();
        // Inserted week text stays in parser syntax (周/单/双) regardless of UI language.
        weekChips.addView(quickChip(s(R.string.sheet_weeks_all_chip, total), v -> setText(inputs[1], "1-" + total + "周")));
        weekChips.addView(quickChip(s(R.string.sheet_odd_weeks), v -> setText(inputs[1], "1-" + total + "周单周")));
        TextView evenWeeks = quickChip(s(R.string.sheet_even_weeks), v -> {
            if (total >= 2) setText(inputs[1], "1-" + total + "周双周");
        });
        evenWeeks.setEnabled(total >= 2);
        if (total < 2) evenWeeks.setAlpha(0.5f);
        weekChips.addView(evenWeeks);
        TextView temporaryChip = quickChip(s(R.string.sheet_temporary_chip), v -> {
            if (temporaryWeek <= 0 || temporaryWeek > total) {
                android.widget.Toast.makeText(context, s(R.string.sheet_temporary_unavailable),
                        android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            tempRow.setVisibility(tempRow.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        });
        temporaryChip.setEnabled(temporaryWeek <= TimetableRules.MAX_WEEK);
        if (temporaryWeek > TimetableRules.MAX_WEEK) temporaryChip.setAlpha(0.5f);
        weekChips.addView(temporaryChip);
        form.addView(weekScroll, new LinearLayout.LayoutParams(-1, -2));
        if (temporaryWeek > TimetableRules.MAX_WEEK)
            form.addView(text(s(R.string.sheet_temporary_week_limit, TimetableRules.MAX_WEEK), 11, muted, false));
        buildTemporaryRow(tempRow, inputs[1], temporaryWeek, total, temporaryCount);
        tempRow.setVisibility(restoredState != null && restoredState.getBoolean("temporary_visible", false)
                && temporaryWeek > 0 && temporaryWeek <= total ? View.VISIBLE : View.GONE);
        form.addView(tempRow, new LinearLayout.LayoutParams(-1, -2));

        // Room + teacher
        LinearLayout pair = row();
        LinearLayout roomCol = column();
        roomCol.addView(sectionLabel(s(R.string.sheet_location), null));
        inputs[2] = input(s(R.string.sheet_room_hint), draft.room);
        roomCol.addView(inputs[2], fieldParams());
        LinearLayout teacherCol = column();
        teacherCol.addView(sectionLabel(s(R.string.sheet_teacher), null));
        inputs[3] = input(s(R.string.sheet_teacher), draft.teacher);
        teacherCol.addView(inputs[3], fieldParams());
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, -2, 1);
        half.rightMargin = dp(10);
        pair.addView(roomCol, half);
        pair.addView(teacherCol, new LinearLayout.LayoutParams(0, -2, 1));
        form.addView(pair, new LinearLayout.LayoutParams(-1, -2));

        // Colour
        form.addView(sectionLabel(s(R.string.sheet_color), null));
        form.addView(text(s(R.string.sheet_color_hint_shared), 11, muted, false));
        form.addView(text(s(R.string.sheet_color_hint_presets), 11, muted, false));
        LinearLayout colorGrid = column();
        renderSwatches(colorGrid, colors, colorNames, draft);
        form.addView(colorGrid, new LinearLayout.LayoutParams(-1, -2));

        // Delete, kept away from the save button to avoid accidental taps.
        Runnable requestDelete = editing && deleter != null ? () -> confirm("delete",
                s(R.string.sheet_delete_course_title),
                s(R.string.sheet_delete_course_message, nonempty(draft.name, s(R.string.sheet_untitled_course))),
                s(R.string.action_delete), true, () -> {
                    if (deleter.delete()) { allowClose[0] = true; dialog.dismiss(); }
                }) : null;
        if (requestDelete != null) {
            TextView delete = text(s(R.string.sheet_delete_course), 14, palette.onErrorContainer, true);
            delete.setGravity(Gravity.CENTER);
            delete.setPadding(0, dp(14), 0, dp(14));
            delete.setBackground(ripple(palette.errorContainer, 14));
            delete.setOnClickListener(v -> requestDelete.run());
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2);
            dlp.topMargin = dp(22);
            form.addView(delete, dlp);
        }

        // Footer
        LinearLayout footer = column();
        footer.setPadding(dp(20), dp(10), dp(20), dp(16));
        footer.setBackgroundColor(sheetBg);
        View divider = new View(context);
        divider.setBackgroundColor(line);
        root.addView(divider, new LinearLayout.LayoutParams(-1, Math.max(1, dp(1) / 2 + 1)));
        TextView error = text("", 12, danger, true);
        error.setPadding(dp(2), 0, dp(2), dp(8));
        error.setVisibility(View.GONE);
        footer.addView(error, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout buttons = row();
        TextView cancel = secondaryButton(s(R.string.action_cancel));
        cancel.setOnClickListener(v -> dialog.cancel());
        TextView save = primaryButton(s(editing ? R.string.sheet_save_changes : R.string.sheet_add_to_timetable), coral);
        save.setOnClickListener(v -> {
            readInputs(draft, inputs);
            reviewSave(draft,courseConflicts,() -> {
            String message = saver == null ? null : saver.save(draft);
            if (message == null) {
                allowClose[0] = true;
                dialog.dismiss();
            } else {
                error.setText(message);
                error.setVisibility(View.VISIBLE);
                error.animate().translationX(dp(6)).setDuration(50).withEndAction(() ->
                        error.animate().translationX(0).setDuration(80).start()).start();
            }
            });
        });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(48), 1);
        cp.rightMargin = dp(10);
        buttons.addView(cancel, cp);
        buttons.addView(save, new LinearLayout.LayoutParams(0, dp(48), 2));
        footer.addView(buttons, new LinearLayout.LayoutParams(-1, -2));
        root.addView(footer, new LinearLayout.LayoutParams(-1, -2));

        TextWatcher clearError = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { error.setVisibility(View.GONE); }
            @Override public void afterTextChanged(Editable s) { }
        };
        for (EditText input : inputs) input.addTextChangedListener(clearError);
        activeCourseDialog = dialog;
        activeCourseDraft = draft;
        activeCourseInputs = inputs;
        activeCourseInitial = initial;
        activeCourseScroll = scroll;
        activeTemporaryRow = tempRow;
        activeTemporaryCount = temporaryCount;
        activeTemporaryWeek = temporaryWeek;
        dialog.setOnDismissListener(d -> {
            if (activeCourseDialog == dialog) {
                activeCourseDialog = null;
                activeCourseDraft = null;
                activeCourseInputs = null;
                activeCourseScroll = null;
                activeCourseInitial = null;
                activeTemporaryRow = null;
                activeTemporaryCount = null;
            }
            if (onClosed != null) onClosed.run();
        });
        present(dialog, root, false);
        restoreEditorInputs(restoredState, inputs);
        if (restoredState != null) scroll.post(() -> scroll.scrollTo(0, restoredState.getInt("scroll", 0)));
        if(restoredState!=null && "time_conflict".equals(restoredState.getString("confirmation"))) {
            restoredConflictAnchor = restoredState.getBundle("conflict_anchor");
            save.performClick();
        }
        else restoreEditorConfirmation(restoredState, dialog, requestDelete);
    }

    private interface IndexListener { void onIndex(int index); }

    private View periodRow(String label, boolean useStart, TextView[] chips, List<String> periodTimes, IndexListener listener) {
        LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        TextView name = text(label, 12, muted, true);
        row.addView(name, new LinearLayout.LayoutParams(dp(36), -2));
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout list = row();
        scroll.addView(list);
        for (int i = 0; i < chips.length; i++) {
            final int index = i;
            String start = clock(periodTimes.get(i), useStart);
            chips[i] = chip(String.valueOf(i + 1), start.isEmpty() ? null : start);
            chips[i].setContentDescription(start.isEmpty()
                    ? s(R.string.sheet_period_chip_desc, label, i + 1)
                    : s(R.string.sheet_period_chip_desc_time, label, i + 1, start));
            chips[i].setOnClickListener(v -> listener.onIndex(index));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(50), dp(48));
            lp.rightMargin = dp(6);
            list.addView(chips[i], lp);
        }
        row.addView(scroll, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private void buildTemporaryRow(LinearLayout row, EditText weeks, int currentWeek, int total, int[] count) {
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(8), dp(8));
        row.setBackground(round(lavender, 14));
        TextView label = text("", 13, ink, false);
        TextView minus = stepButton("−");
        TextView value = text("1", 15, ink, true);
        value.setGravity(Gravity.CENTER);
        TextView plus = stepButton("+");
        TextView apply = text(s(R.string.sheet_apply), 13, coral, true);
        apply.setGravity(Gravity.CENTER);
        apply.setPadding(dp(12), dp(8), dp(12), dp(8));
        Runnable refresh = () -> {
            value.setText(String.valueOf(count[0]));
            label.setText(context.getString(R.string.temporary_from_week, currentWeek));
        };
        minus.setOnClickListener(v -> { count[0] = Math.max(1, count[0] - 1); refresh.run(); });
        plus.setOnClickListener(v -> { count[0] = Math.min(Math.max(1, total - currentWeek + 1), count[0] + 1); refresh.run(); });
        apply.setOnClickListener(v -> {
            int end = Math.min(total, currentWeek + count[0] - 1);
            setText(weeks, currentWeek == end ? currentWeek + "周" : currentWeek + "-" + end + "周");
            row.setVisibility(View.GONE);
        });
        refresh.run();
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(minus, new LinearLayout.LayoutParams(dp(34), dp(34)));
        row.addView(value, new LinearLayout.LayoutParams(dp(34), dp(34)));
        row.addView(plus, new LinearLayout.LayoutParams(dp(34), dp(34)));
        row.addView(text(s(R.string.sheet_week_unit), 13, ink, false));
        row.addView(apply);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        row.setLayoutParams(lp);
    }

    private void renderSwatches(LinearLayout grid, int[] colors, String[] names, Draft draft) {
        grid.removeAllViews();
        final int columns = 4;
        LinearLayout lineRow = null;
        for (int i = 0; i < colors.length; i++) {
            if (i % columns == 0) {
                lineRow = row();
                grid.addView(lineRow, new LinearLayout.LayoutParams(-1, -2));
            }
            final int value = colors[i];
            String name = names != null && i < names.length ? names[i] : s(R.string.sheet_color_fallback, i + 1);
            String hex = String.format(Locale.ROOT, "#%06X", value & 0xFFFFFF);
            boolean selected = value == draft.color;
            LinearLayout cell = column();
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(dp(4), dp(5), dp(4), dp(5));
            cell.setClickable(true);
            cell.setFocusable(true);
            cell.setSelected(selected);
            cell.setContentDescription(s(selected ? R.string.sheet_swatch_desc_selected : R.string.sheet_swatch_desc, name, hex));
            cell.setOnClickListener(v -> {
                draft.color = value;
                draft.colorManuallySelected = true;
                renderSwatches(grid, colors, names, draft);
            });
            int background = pastel(value);
            TextView swatch = text(selected ? "✓" : "", 18, CoursePalette.readableInk(background), true);
            swatch.setGravity(Gravity.CENTER);
            GradientDrawable bg = round(background, 10);
            bg.setStroke(dp(selected ? 2.5f : 1), selected ? coral : withAlpha(value, dark ? 90 : 110));
            swatch.setBackground(bg);
            swatch.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            if (dark) {
                FrameLayout swatchBox = new FrameLayout(context);
                swatchBox.addView(swatch, new FrameLayout.LayoutParams(-1, -1));
                View pigmentDot = new View(context);
                GradientDrawable dotBg = round(value | 0xFF000000, 4);
                dotBg.setStroke(Math.max(1, dp(0.8f)), Color.argb(90, 0, 0, 0));
                pigmentDot.setBackground(dotBg);
                pigmentDot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(dp(8), dp(8), Gravity.TOP | Gravity.END);
                dotLp.setMargins(0, dp(4), dp(4), 0);
                swatchBox.addView(pigmentDot, dotLp);
                cell.addView(swatchBox, new LinearLayout.LayoutParams(-1, dp(36)));
            } else {
                cell.addView(swatch, new LinearLayout.LayoutParams(-1, dp(36)));
            }
            TextView label = text(name, 11, selected && dark ? coral : ink, selected);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, dp(3), 0, 0);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            cell.addView(label, new LinearLayout.LayoutParams(-1, -2));
            if (!dark) {
                TextView code = text(hex, 9, muted, false);
                code.setGravity(Gravity.CENTER);
                code.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                cell.addView(code, new LinearLayout.LayoutParams(-1, -2));
            }
            lineRow.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
        }
        if (lineRow != null) {
            for (int i = colors.length % columns; i > 0 && i < columns; i++) {
                lineRow.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1));
            }
        }
    }

    // ---------------------------------------------------------------- confirm

    private void confirm(String kind, String title, String message, String action, boolean destructive, Runnable onConfirm) {
        confirmChoice(kind, title, message, action, s(destructive ? R.string.sheet_think_again : R.string.action_cancel), destructive, onConfirm);
    }

    private void captureConfirmation(Bundle state) {
        if (confirmation != null && confirmation.isShowing()) {
            state.putString("confirmation", confirmationKind);
            if (conflictList != null) state.putBundle("conflict_anchor", conflictList.anchor());
        }
    }

    private void restoreEditorConfirmation(Bundle state, BottomSheetDialog dialog, Runnable requestDelete) {
        if (state == null || !dialog.isShowing()) return;
        String kind = state.getString("confirmation", "");
        if ("discard".equals(kind)) dialog.cancel();
        else if ("delete".equals(kind) && requestDelete != null) requestDelete.run();
    }

    private BottomSheetDialog confirmChoice(String kind, String title, String message, String action, String cancelLabel,
                                            boolean destructive, Runnable onConfirm) {
        return confirmChoice(kind, title, message, action, cancelLabel, destructive, onConfirm, null);
    }

    private BottomSheetDialog confirmChoice(String kind, String title, String message, String action, String cancelLabel,
                                            boolean destructive, Runnable onConfirm, List<ScheduleConflicts.Hit> hits) {
        if (confirmation != null && confirmation.isShowing()) return confirmation;
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        confirmation = dialog;
        confirmationKind = kind;
        dialog.setOnDismissListener(d -> {
            if (confirmation == dialog) { confirmation = null; confirmationKind = null; conflictList = null; }
        });
        LinearLayout root = sheetRoot();
        LinearLayout body = column();
        body.setPadding(dp(22), dp(4), dp(22), 0);
        body.addView(text(title, 18, ink, true));
        if (hits != null) {
            root.addView(body);
            conflictList = new ConflictListView(context, hits, muted, restoredConflictAnchor);
            restoredConflictAnchor = null;
            root.addView(conflictList, new LinearLayout.LayoutParams(-1, -2));
        } else {
            TextView msg = text(message, 14, muted, false);
            msg.setPadding(0, dp(10), 0, 0);
            msg.setLineSpacing(dp(3), 1f);
            body.addView(msg);
            NestedScrollView reviewScroll = boundedScroll();
            reviewScroll.addView(body);
            root.addView(reviewScroll, new LinearLayout.LayoutParams(-1, -2));
        }
        LinearLayout buttons = row();
        buttons.setPadding(dp(22), dp(20), dp(22), dp(18));
        TextView cancel = secondaryButton(cancelLabel);
        cancel.setOnClickListener(v -> dialog.dismiss());
        TextView ok = primaryButton(action, destructive ? danger : coral);
        ok.setOnClickListener(v -> {
            if (!dialog.isShowing()) return;
            dialog.dismiss();
            onConfirm.run();
        });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(48), 1);
        cp.rightMargin = dp(10);
        buttons.addView(cancel, cp);
        buttons.addView(ok, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(buttons, new LinearLayout.LayoutParams(-1, -2));
        present(dialog, root, true);
        return dialog;
    }

    // ---------------------------------------------------------------- import

    /** File import and editable list-style text import share the same review flow. */
    void showImportChooser(Runnable onPdf, Runnable onPaste) {
        BottomSheetDialog dialog = new ManagedSheet(context);
        LinearLayout root = sheetRoot();
        LinearLayout body = column();
        body.setPadding(dp(18), dp(2), dp(18), dp(18));
        TextView title = text(s(R.string.sheet_import_title), 20, ink, true);
        title.setPadding(dp(4), 0, 0, dp(12));
        body.addView(title);
        body.addView(importOption("import", s(R.string.import_pdf_cta), s(R.string.sheet_import_pdf_sub), () -> {
            switchSheet(dialog, onPdf);
        }), fieldParams());
        View gap = new View(context);
        body.addView(gap, new LinearLayout.LayoutParams(-1, dp(10)));
        body.addView(importOption("paste", s(R.string.sheet_paste_title), s(R.string.sheet_import_paste_sub), () -> {
            switchSheet(dialog, onPaste);
        }), fieldParams());
        root.addView(body, new LinearLayout.LayoutParams(-1, -2));
        present(dialog, root, true);
    }

    private View importOption(String icon, String label, String sub, Runnable action) {
        LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        row.setBackground(ripple(surface, 18));
        TextView badge = new TextView(context);
        badge.setBackground(round(lavender, 13));
        badge.setGravity(Gravity.CENTER);
        badge.setText(s("import".equals(icon) ? R.string.sheet_file_badge : R.string.sheet_text_badge));
        badge.setTextColor(coral);
        badge.setTextSize(12);
        badge.setTypeface(AppFonts.typeface(context, Typeface.BOLD));
        row.addView(badge, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout texts = column();
        texts.setPadding(dp(12), 0, 0, 0);
        texts.addView(text(label, 15, ink, true));
        TextView desc = text(sub, 12, muted, false);
        desc.setPadding(0, dp(3), 0, 0);
        texts.addView(desc);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        row.setContentDescription(s(R.string.sheet_label_with_sub, label, sub));
        row.setOnClickListener(v -> action.run());
        return row;
    }

    /** Returns null when the pasted text was accepted (dialog closes), otherwise an inline error. */
    interface TextImporter { String submit(String text); }

    private BottomSheetDialog activePasteDialog;
    private EditText activePasteInput;
    private NestedScrollView activePasteScroll;

    Bundle capturePasteImportState() {
        if (activePasteDialog == null || !activePasteDialog.isShowing()) return null;
        Bundle state = new Bundle();
        state.putString("text", activePasteInput.getText().toString());
        state.putInt("selectionStart", activePasteInput.getSelectionStart());
        state.putInt("selectionEnd", activePasteInput.getSelectionEnd());
        state.putInt("scroll", activePasteScroll.getScrollY());
        captureConfirmation(state);
        return state;
    }

    void showPasteImport(String formatHint, String example, TextImporter importer) {
        showPasteImport(formatHint, example, importer, null, null, null);
    }

    /** {@code onAccepted} opens the review window only after this sheet has fully closed. */
    void showPasteImport(String formatHint, String example, TextImporter importer,
                         Bundle restoredState, Runnable onClosed, Runnable onAccepted) {
        SettingsDialogStyle style = SettingsDialogStyle.of(context);
        UiSemanticPalette colors = style.colors;
        final EditText[] inputHolder = new EditText[1];
        final boolean[] allowClose = {false};
        final BottomSheetDialog[] holder = new BottomSheetDialog[1];
        BottomSheetDialog dialog = new ManagedSheet(context) {
            @Override public void cancel() {
                // Back / outside tap must not silently discard a pasted timetable.
                String typed = inputHolder[0] == null ? "" : inputHolder[0].getText().toString().trim();
                if (allowClose[0] || typed.isEmpty()) { super.cancel(); return; }
                confirm("discard", s(R.string.sheet_discard_paste_title), s(R.string.sheet_discard_paste_message),
                        s(R.string.sheet_discard), true,
                        () -> { allowClose[0] = true; holder[0].cancel(); });
            }
        };
        holder[0] = dialog;
        LinearLayout root = sheetRoot();
        GradientDrawable background = new GradientDrawable();
        background.setColor(colors.background);
        float corner = dp(26);
        background.setCornerRadii(new float[]{corner, corner, corner, corner, 0, 0, 0, 0});
        root.setBackground(background);
        root.getChildAt(0).setBackground(round(colors.divider, 3));
        NestedScrollView scroll = boundedScroll();
        LinearLayout body = style.body();
        scroll.addView(body);

        TextView title = text(s(R.string.sheet_paste_title), 22, colors.text, true);
        androidx.core.view.ViewCompat.setAccessibilityHeading(title, true);
        body.addView(title);
        TextView intro = style.caption(s(R.string.sheet_paste_intro));
        intro.setPadding(0, dp(8), 0, 0);
        intro.setLineSpacing(dp(2), 1f);
        body.addView(intro);

        LinearLayout guidance = style.group();
        LinearLayout.LayoutParams guidanceParams = new LinearLayout.LayoutParams(-1, -2);
        guidanceParams.topMargin = dp(12);
        body.addView(guidance, guidanceParams);
        TextView format = style.caption(formatHint);
        format.setPadding(dp(12), dp(10), dp(12), dp(10));
        format.setBackground(round(colors.inset, 12));
        guidance.addView(format, new LinearLayout.LayoutParams(-1, -2));

        guidance.addView(style.heading(s(R.string.sheet_example)));
        TextView exampleView = text(example, 13, colors.text, false);
        exampleView.setTypeface(Typeface.MONOSPACE);
        exampleView.setTextIsSelectable(true);
        exampleView.setLineSpacing(dp(3), 1f);
        exampleView.setPadding(dp(12), dp(10), dp(12), dp(10));
        exampleView.setBackground(round(colors.inset, 12));
        guidance.addView(exampleView, new LinearLayout.LayoutParams(-1, -2));
        TextView rules = style.caption(s(R.string.sheet_paste_rules));
        rules.setLineSpacing(dp(2), 1f);
        rules.setPadding(0, dp(8), 0, 0);
        guidance.addView(rules);

        LinearLayout inputGroup = style.group();
        LinearLayout.LayoutParams inputGroupParams = new LinearLayout.LayoutParams(-1, -2);
        inputGroupParams.topMargin = dp(12);
        body.addView(inputGroup, inputGroupParams);
        inputGroup.addView(style.heading(s(R.string.sheet_your_timetable)));

        EditText input = input(s(R.string.sheet_paste_input_hint), "");
        style.field(input);
        input.setSingleLine(false);
        input.setMinLines(6);
        input.setMaxLines(14);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(TextTimetableParser.MAX_TEXT_CHARS)});
        input.setContentDescription(s(R.string.sheet_paste_input_desc));
        inputHolder[0] = input;
        if (restoredState != null) {
            input.setText(restoredState.getString("text", ""));
            int length = input.length();
            input.setSelection(clamp(restoredState.getInt("selectionStart", length), 0, length),
                    clamp(restoredState.getInt("selectionEnd", length), 0, length));
        }
        inputGroup.addView(input, new LinearLayout.LayoutParams(-1, -2));

        android.widget.Button useExample = new android.widget.Button(context);
        useExample.setText(R.string.sheet_use_example);
        useExample.setOnClickListener(v -> setText(input, example));
        android.widget.Button paste = new android.widget.Button(context);
        paste.setText(R.string.sheet_paste_clipboard);
        paste.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip() != null
                    && clipboard.getPrimaryClip().getItemCount() > 0) {
                CharSequence clip = clipboard.getPrimaryClip().getItemAt(0).coerceToText(context);
                if (clip != null) {
                    String value = clip.toString();
                    setText(input, value.length() > TextTimetableParser.MAX_TEXT_CHARS
                            ? value.substring(0, TextTimetableParser.MAX_TEXT_CHARS) : value);
                }
            }
        });
        android.widget.Button copyExample = new android.widget.Button(context);
        copyExample.setText(R.string.sheet_copy_example);
        copyExample.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText(s(R.string.sheet_clip_label), example));
                android.widget.Toast.makeText(context, s(R.string.sheet_example_copied), android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout chips = row();
        chips.setPadding(0, dp(10), 0, 0);
        for (android.widget.Button action : new android.widget.Button[]{paste, useExample, copyExample}) {
            style.action(action, false);
            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-2, -2);
            actionParams.rightMargin = dp(8);
            chips.addView(action, actionParams);
        }
        HorizontalScrollView chipScroll = new HorizontalScrollView(context);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.addView(chips);
        inputGroup.addView(chipScroll, new LinearLayout.LayoutParams(-1, -2));

        root.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        TextView error = style.caption("");
        error.setTextColor(colors.error);
        error.setPadding(dp(16), dp(6), dp(16), 0);
        error.setVisibility(View.GONE);
        error.setMaxLines(6);
        error.setEllipsize(TextUtils.TruncateAt.END);
        root.addView(error, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout buttons = row();
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        buttons.setPadding(dp(16), dp(10), dp(16), dp(16));
        android.widget.Button cancel = new android.widget.Button(context);
        cancel.setText(R.string.action_cancel);
        style.action(cancel, false);
        cancel.setOnClickListener(v -> dialog.cancel());
        android.widget.Button submit = new android.widget.Button(context);
        submit.setText(R.string.sheet_recognize);
        style.action(submit, true);
        submit.setOnClickListener(v -> {
            if (allowClose[0]) return; // accepted: do not replace the saveable review during closing animation
            String message = importer.submit(input.getText().toString());
            if (message == null) {
                allowClose[0] = true;
                if (onAccepted != null) switchSheet(dialog, onAccepted);
                else dialog.dismiss();
            } else {
                error.setText(message);
                error.setVisibility(View.VISIBLE);
            }
        });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1);
        cp.rightMargin = dp(10);
        buttons.addView(cancel, cp);
        buttons.addView(submit, new LinearLayout.LayoutParams(0, -2, 2));
        root.addView(buttons, new LinearLayout.LayoutParams(-1, -2));
        activePasteDialog = dialog;
        activePasteInput = input;
        activePasteScroll = scroll;
        dialog.setOnDismissListener(d -> {
            if (activePasteDialog == dialog) {
                activePasteDialog = null;
                activePasteInput = null;
                activePasteScroll = null;
            }
            if (onClosed != null) onClosed.run();
        });
        present(dialog, root, false);
        if (dialog.getWindow() != null) dialog.getWindow().setNavigationBarColor(colors.background);
        if (restoredState != null) scroll.post(() -> scroll.scrollTo(0, restoredState.getInt("scroll", 0)));
        restoreEditorConfirmation(restoredState, dialog, null);
    }

    // ---------------------------------------------------------------- personal events

    /** Returns null when saved, otherwise an inline message. */
    interface EventSaver { String save(PersonalEvent event); }

    interface EventOpener { void open(PersonalEvent displayed); }

    private static final int[] REMIND_PRESETS = {PersonalEvent.NO_REMINDER, 0, 10, 30, 60, 1440};

    private BottomSheetDialog activeEventDialog;
    private android.app.DatePickerDialog activeEventDatePicker;
    private boolean pickingUntil, conflictNoReminder;
    private BottomSheetDialog activeReminderConfirmation;
    private EditText[] activeEventInputs;
    private NestedScrollView activeEventScroll;
    private PersonalEvent activeEventDraft;
    private boolean[] activeCustomReminder;
    private String activeEventInitial;

    /** Raw UI state, including invalid/empty input. The host owns editing mode and original id. */
    Bundle captureEventEditorState() {
        if (activeEventDialog == null || !activeEventDialog.isShowing()) return null;
        Bundle state = new Bundle();
        state.putInt("year", activeEventDraft.year);
        state.putInt("month", activeEventDraft.month);
        state.putInt("day", activeEventDraft.day);
        state.putInt("remind", activeEventDraft.remindMinutes);
        state.putInt("repeat",activeEventDraft.repeat); state.putInt("weekdays",activeEventDraft.weekdays);
        state.putInt("until",activeEventDraft.until); state.putBoolean("picking_until",pickingUntil);
        state.putBoolean("showInTimetable", activeEventDraft.showInTimetable);
        state.putBoolean("custom", activeCustomReminder[0]);
        state.putString("initial", activeEventInitial);
        state.putInt("scroll", activeEventScroll.getScrollY());
        captureConfirmation(state);
        if (activeEventDatePicker != null && activeEventDatePicker.isShowing())
            state.putBundle("date_picker", activeEventDatePicker.onSaveInstanceState());
        state.putBoolean("conflict_no_reminder",conflictNoReminder);
        captureEditorInputs(state, activeEventInputs);
        return state;
    }

    void showEventDetail(PersonalEvent event, long now, Runnable onEdit) {
        showEventDetail(event, now, onEdit, null);
    }

    private BottomSheetDialog activeDetailDialog;
    private NestedScrollView activeDetailScroll;

    Bundle captureEventDetailState() {
        if (activeDetailDialog == null || !activeDetailDialog.isShowing()) return null;
        Bundle state = new Bundle();
        state.putInt("scroll", activeDetailScroll.getScrollY());
        return state;
    }

    /** onBack runs only on closing/cancelling, not when navigating forward to edit. */
    void showEventDetail(PersonalEvent event, long now, Runnable onEdit, Runnable onBack) {
        showEventDetail(event, now, onEdit, onBack, null);
    }

    void showEventDetail(PersonalEvent event, long now, Runnable onEdit, Runnable onBack, Bundle restoredState) {
        BottomSheetDialog dialog = new ManagedSheet(context);
        final boolean[] forward = {false};
        LinearLayout root = sheetRoot();
        NestedScrollView scroll = boundedScroll();
        LinearLayout body = column();
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout hero = column();
        hero.setPadding(dp(18), dp(16), dp(18), dp(18));
        TextView when = text("", 12, coral, true);
        hero.addView(when);
        TextView name = text(nonempty(event.title, s(R.string.sheet_untitled_event)), 21, ink, true);
        name.setPadding(0, dp(10), 0, 0);
        hero.addView(name);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, -2);
        heroParams.setMargins(dp(18), dp(6), dp(18), dp(12));
        body.addView(hero, heroParams);
        LinearLayout info = column();
        info.setBackground(round(surface, 18));
        info.setPadding(dp(14), dp(4), dp(14), dp(4));
        infoRow(info, "clock", s(R.string.sheet_time), event.timeLabel(), true);
        if(event.sourceDate!=0 || event.repeat!=0) infoRow(info,"calendar",s(R.string.repeat_title),s(R.string.repeat_scope_label),true);
        infoRow(info, "pin", s(R.string.sheet_location), nonempty(event.location, s(R.string.sheet_not_filled)), true);
        TextView reminder = infoRow(info, "calendar", s(R.string.sheet_reminder), reminderStatus(event, now), true);
        infoRow(info, "person", s(R.string.sheet_note), nonempty(event.note, s(R.string.sheet_none)), false);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(-1, -2);
        infoParams.setMargins(dp(18), 0, dp(18), 0);
        body.addView(info, infoParams);

        LinearLayout buttons = row();
        buttons.setPadding(dp(18), dp(16), dp(18), dp(18));
        TextView close = secondaryButton(s(R.string.action_close));
        close.setOnClickListener(v -> dialog.dismiss());
        TextView edit = primaryButton(s(R.string.sheet_edit_event), coral);
        edit.setOnClickListener(v -> { forward[0] = true; switchSheet(dialog, onEdit); });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(48), 1);
        cp.rightMargin = dp(10);
        buttons.addView(close, cp);
        buttons.addView(edit, new LinearLayout.LayoutParams(0, dp(48), 2));
        root.addView(buttons, new LinearLayout.LayoutParams(-1, -2));
        final boolean[] lastPast = {event.isPast(now)};
        final boolean[] lastExpired = {event.reminderExpired(now)};
        Runnable refresh = () -> {
            boolean past = event.isPast(System.currentTimeMillis());
            lastPast[0] = past;
            lastExpired[0] = event.reminderExpired(System.currentTimeMillis());
            reminder.setText(reminderStatus(event, System.currentTimeMillis()));
            hero.setBackground(round(past ? line : lavender, 20));
            when.setTextColor(past ? muted : coral);
            String whenLabel = dateLabel(event) + " · " + event.timeLabel() + (past ? " · " + pastEventLabel(context, event) : "");
            when.setText(whenLabel);
        };
        refresh.run();
        Runnable ticker = new Runnable() {
            @Override public void run() {
                if (!dialog.isShowing()) return;
                long current = System.currentTimeMillis();
                if (lastPast[0] != event.isPast(current) || lastExpired[0] != event.reminderExpired(current)) refresh.run();
                root.postDelayed(this, 1000);
            }
        };
        dialog.setOnDismissListener(d -> {
            root.removeCallbacks(ticker);
            if (activeDetailDialog == dialog) { activeDetailDialog = null; activeDetailScroll = null; }
            if (!forward[0] && onBack != null) onBack.run();
        });
        cancelOnDetach(root, ticker);
        activeDetailDialog = dialog;
        activeDetailScroll = scroll;
        present(dialog, root, true);
        root.post(ticker);
        if (restoredState != null) scroll.post(() -> scroll.scrollTo(0, restoredState.getInt("scroll", 0)));
    }

    void showEventEditor(boolean editing, PersonalEvent draft, EventSaver saver, Deleter deleter) {
        showEventEditor(editing, draft, saver, deleter, null, null);
    }

    /** restoredState is from captureEventEditorState(); onClosed runs after every actual dismissal. */
    void showEventEditor(boolean editing, PersonalEvent draft, EventSaver saver, Deleter deleter,
                         Bundle restoredState, Runnable onClosed) {
        if (restoredState != null) {
            draft.year = restoredState.getInt("year", draft.year);
            draft.month = restoredState.getInt("month", draft.month);
            draft.day = restoredState.getInt("day", draft.day);
            draft.remindMinutes = restoredState.getInt("remind", draft.remindMinutes);
            draft.repeat=restoredState.getInt("repeat",draft.repeat); draft.weekdays=restoredState.getInt("weekdays",draft.weekdays);
            draft.until=restoredState.getInt("until",draft.until);
            draft.showInTimetable = restoredState.getBoolean("showInTimetable", draft.showInTimetable);
        }
        final EditText[] inputs = new EditText[6]; // title, start, end, location, note, custom minutes
        boolean presetReminder = false;
        for (int value : REMIND_PRESETS) presetReminder |= value == draft.remindMinutes;
        final boolean[] customReminder = {restoredState != null
                ? restoredState.getBoolean("custom", !presetReminder) : !presetReminder};
        final boolean[] changingReminder = {false};
        final boolean[] allowClose = {false};
        final BottomSheetDialog[] holder = new BottomSheetDialog[1];
        final String[] initial = {null};
        BottomSheetDialog dialog = new ManagedSheet(context) {
            @Override public void cancel() {
                if (allowClose[0] || eventSnapshot(inputs, draft, customReminder[0]).equals(initial[0])) { super.cancel(); return; }
                confirm("discard", s(R.string.sheet_discard_event_title), s(R.string.sheet_discard_message),
                        s(R.string.action_discard_changes), true,
                        () -> { allowClose[0] = true; holder[0].cancel(); });
            }
        };
        holder[0] = dialog;
        dialog.setOnDismissListener(d -> {
            closeEventDatePicker();
            if (activeReminderConfirmation != null) {
                activeReminderConfirmation.dismiss();
                activeReminderConfirmation = null;
            }
            if (activeEventDialog == dialog) {
                activeEventDialog = null;
                activeEventInputs = null;
                activeEventScroll = null;
                activeEventDraft = null;
                activeCustomReminder = null;
                activeEventInitial = null;
            }
            if (onClosed != null) onClosed.run();
        });
        LinearLayout root = sheetRoot();

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(2), dp(12), dp(8));
        header.addView(text(s(editing ? R.string.sheet_edit_event : R.string.sheet_add_event), 19, ink, true), new LinearLayout.LayoutParams(0, -2, 1));
        View close = new View(context);
        close.setBackground(new IconBadge("close"));
        close.setContentDescription(s(R.string.action_close));
        close.setOnClickListener(v -> dialog.cancel());
        header.addView(close, new LinearLayout.LayoutParams(dp(36), dp(36)));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout form = column();
        form.setPadding(dp(20), 0, dp(20), dp(12));
        NestedScrollView scroll = boundedScroll();
        scroll.setClipToPadding(false);
        scroll.addView(form);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        form.addView(sectionLabel(s(R.string.sheet_event), null));
        inputs[0] = input(s(R.string.sheet_event_title_hint), draft.title);
        inputs[0].setFilters(new InputFilter[]{new InputFilter.LengthFilter(PersonalEvent.MAX_TITLE)});
        form.addView(inputs[0], fieldParams());

        // Date: quick chips plus the system date picker for any other day.
        TextView dateValue = text("", 12, coral, true);
        form.addView(sectionLabel(s(R.string.sheet_date), dateValue));
        LinearLayout dateRow = row();
        final TextView[] dateChips = new TextView[4];
        String[] dateNames = {s(R.string.sheet_today), s(R.string.sheet_tomorrow),
                s(R.string.sheet_day_after_tomorrow), s(R.string.sheet_pick_date)};
        final Runnable[] refreshDate = new Runnable[1];
        refreshDate[0] = () -> {
            dateValue.setText(dateLabel(draft));
            int offset = dayOffsetFromToday(draft);
            refreshChoices(dateChips, offset >= 0 && offset <= 2 ? offset : 3, -1);
        };
        for (int i = 0; i < dateChips.length; i++) {
            final int index = i;
            dateChips[i] = chip(dateNames[i], null);
            dateChips[i].setOnClickListener(v -> {
                if (index < 3) {
                    java.util.Calendar c = java.util.Calendar.getInstance();
                    c.add(java.util.Calendar.DAY_OF_YEAR, index);
                    draft.year = c.get(java.util.Calendar.YEAR);
                    draft.month = c.get(java.util.Calendar.MONTH) + 1;
                    draft.day = c.get(java.util.Calendar.DAY_OF_MONTH);
                    refreshDate[0].run();
                } else {
                    showEventDatePicker(draft, refreshDate[0], null);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), i == 3 ? 1.6f : 1f);
            if (i < 3) lp.rightMargin = dp(6);
            dateRow.addView(dateChips[i], lp);
        }
        form.addView(dateRow, new LinearLayout.LayoutParams(-1, -2));
        refreshDate[0].run();

        Runnable refreshRepeat=draft.sourceDate==0 ? repeatControls(form,draft) : () -> { };
        if(draft.sourceDate!=0) form.addView(text(s(R.string.scope_once,ScheduleDay.label(draft.sourceDate)),13,coral,true));

        // Time
        LinearLayout timePair = row();
        LinearLayout startCol = column();
        startCol.addView(sectionLabel(s(R.string.sheet_start_time), null));
        inputs[1] = input(s(R.string.sheet_start_time_hint), PersonalEvent.formatTime(draft.startMinutes));
        inputs[1].setTag("event:start");
        inputs[1].setInputType(android.text.InputType.TYPE_CLASS_DATETIME | android.text.InputType.TYPE_DATETIME_VARIATION_TIME);
        startCol.addView(inputs[1], fieldParams());
        LinearLayout endCol = column();
        endCol.addView(sectionLabel(s(R.string.sheet_end_time_optional), null));
        inputs[2] = input(s(R.string.sheet_end_time_hint), PersonalEvent.formatTime(draft.endMinutes));
        inputs[2].setTag("event:end");
        inputs[2].setInputType(android.text.InputType.TYPE_CLASS_DATETIME | android.text.InputType.TYPE_DATETIME_VARIATION_TIME);
        endCol.addView(inputs[2], fieldParams());
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, -2, 1);
        half.rightMargin = dp(10);
        timePair.addView(startCol, half);
        timePair.addView(endCol, new LinearLayout.LayoutParams(0, -2, 1));
        form.addView(timePair, new LinearLayout.LayoutParams(-1, -2));
        TextView timeFeedback = text("", 12, muted, false);
        timeFeedback.setTag("event:time-feedback");
        timeFeedback.setPadding(dp(12), dp(10), dp(12), dp(10));
        timeFeedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout.LayoutParams feedbackParams = new LinearLayout.LayoutParams(-1, -2);
        feedbackParams.topMargin = dp(8);
        form.addView(timeFeedback, feedbackParams);
        TextWatcher timeWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                updateEventTimeFeedback(inputs[1], inputs[2], timeFeedback);
            }
        };
        inputs[1].addTextChangedListener(timeWatcher);
        inputs[2].addTextChangedListener(timeWatcher);

        form.addView(sectionLabel(s(R.string.sheet_location_optional), null));
        inputs[3] = input(s(R.string.sheet_event_location_hint), draft.location);
        inputs[3].setFilters(new InputFilter[]{new InputFilter.LengthFilter(PersonalEvent.MAX_LOCATION)});
        form.addView(inputs[3], fieldParams());

        form.addView(sectionLabel(s(R.string.sheet_note_optional), null));
        inputs[4] = input(s(R.string.sheet_note_hint), draft.note);
        inputs[4].setSingleLine(false);
        inputs[4].setMinLines(2);
        inputs[4].setMaxLines(5);
        inputs[4].setGravity(Gravity.TOP | Gravity.START);
        inputs[4].setFilters(new InputFilter[]{new InputFilter.LengthFilter(PersonalEvent.MAX_NOTE)});
        form.addView(inputs[4], fieldParams());

        com.google.android.material.switchmaterial.SwitchMaterial showInTimetable =
                new com.google.android.material.switchmaterial.SwitchMaterial(context);
        showInTimetable.setUseMaterialThemeColors(false);
        styleSwitch(showInTimetable);
        showInTimetable.setText(s(R.string.event_show_in_timetable));
        showInTimetable.setTextColor(ink);
        showInTimetable.setTextSize(14);
        showInTimetable.setPadding(dp(2), dp(12), 0, dp(8));
        showInTimetable.setChecked(draft.showInTimetable);
        showInTimetable.setOnCheckedChangeListener((button, checked) -> draft.showInTimetable = checked);
        form.addView(showInTimetable, new LinearLayout.LayoutParams(-1, -2));
        TextView visibilityHint = text(s(R.string.event_show_in_timetable_hint), 11, muted, false);
        visibilityHint.setPadding(dp(2), 0, 0, dp(8));
        form.addView(visibilityHint);

        // Reminder: presets, or any N minutes typed by the user.
        TextView remindMode = text("", 11, coral, true);
        form.addView(sectionLabel(s(R.string.sheet_reminder), remindMode));
        HorizontalScrollView remindScroll = new HorizontalScrollView(context);
        remindScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout remindRow = row();
        remindScroll.addView(remindRow);
        final TextView[] remindChips = new TextView[REMIND_PRESETS.length];
        LinearLayout customRow = row();
        customRow.setGravity(Gravity.CENTER_VERTICAL);
        customRow.setPadding(0, dp(8), 0, 0);
        customRow.addView(text(s(R.string.sheet_custom_before), 13, ink, false));
        inputs[5] = input("N", "");
        inputs[5].setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        inputs[5].setGravity(Gravity.CENTER);
        inputs[5].setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});
        inputs[5].setContentDescription(s(R.string.sheet_custom_minutes_desc));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(dp(86), -2);
        np.setMargins(dp(8), 0, dp(8), 0);
        customRow.addView(inputs[5], np);
        customRow.addView(text(s(R.string.sheet_minutes_unit), 13, ink, false));
        final Runnable[] refreshRemind = new Runnable[1];
        refreshRemind[0] = () -> {
            int selected = -1;
            if (!customReminder[0]) {
                for (int i = 0; i < REMIND_PRESETS.length; i++) if (REMIND_PRESETS[i] == draft.remindMinutes) selected = i;
            }
            refreshChoices(remindChips, selected, -1);
            remindMode.setText(customReminder[0]
                    ? s(inputs[5].getText().toString().trim().isEmpty() ? R.string.sheet_custom_pending : R.string.sheet_custom_reminder)
                    : PersonalEvent.remindLabel(draft.remindMinutes));
            customRow.setSelected(customReminder[0]);
        };
        for (int i = 0; i < REMIND_PRESETS.length; i++) {
            final int value = REMIND_PRESETS[i];
            String label = value == PersonalEvent.NO_REMINDER ? s(R.string.sheet_remind_none)
                    : value == 0 ? s(R.string.sheet_remind_at_start)
                    : value >= 1440 ? context.getResources().getQuantityString(R.plurals.sheet_remind_days, value / 1440, value / 1440)
                    : value >= 60 ? context.getResources().getQuantityString(R.plurals.sheet_remind_hours, value / 60, value / 60)
                    : s(R.string.sheet_remind_minutes, value);
            remindChips[i] = chip(label, null);
            remindChips[i].setPadding(dp(14), 0, dp(14), 0);
            remindChips[i].setContentDescription(PersonalEvent.remindLabel(value));
            remindChips[i].setOnClickListener(v -> {
                changingReminder[0] = true;
                customReminder[0] = false;
                draft.remindMinutes = value;
                setText(inputs[5], "");
                changingReminder[0] = false;
                refreshRemind[0].run();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(40));
            lp.rightMargin = dp(6);
            remindRow.addView(remindChips[i], lp);
        }
        boolean preset = false;
        for (int value : REMIND_PRESETS) preset |= value == draft.remindMinutes;
        if (!preset) setText(inputs[5], String.valueOf(draft.remindMinutes));
        inputs[5].addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                if (changingReminder[0]) return;
                customReminder[0] = true;
                String typed = s.toString().trim();
                // Empty/invalid custom input must never silently keep the previous valid reminder.
                try { draft.remindMinutes = Integer.parseInt(typed); }
                catch (NumberFormatException e) { draft.remindMinutes = Integer.MAX_VALUE; }
                refreshRemind[0].run();
            }
        });
        refreshRemind[0].run();
        form.addView(remindScroll, new LinearLayout.LayoutParams(-1, -2));
        form.addView(customRow, new LinearLayout.LayoutParams(-1, -2));
        TextView remindHint = text(s(R.string.sheet_remind_hint), 11, muted, false);
        remindHint.setPadding(dp(2), dp(8), 0, 0);
        form.addView(remindHint);

        Runnable requestDelete = editing && deleter != null ? () -> confirm("delete",
                s(R.string.sheet_delete_event_title),
                s(draft.sourceDate==0?R.string.sheet_delete_event_message:R.string.repeat_delete_one_msg, nonempty(draft.title, s(R.string.sheet_untitled_event))),
                s(R.string.action_delete), true, () -> {
                    if (deleter.delete()) { allowClose[0] = true; dialog.dismiss(); }
                }) : null;
        if (requestDelete != null) {
            TextView delete = text(s(draft.sourceDate==0?R.string.sheet_delete_event:R.string.repeat_delete_one), 14, palette.onErrorContainer, true);
            delete.setGravity(Gravity.CENTER);
            delete.setPadding(0, dp(14), 0, dp(14));
            delete.setBackground(ripple(palette.errorContainer, 14));
            delete.setOnClickListener(v -> requestDelete.run());
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2);
            dlp.topMargin = dp(22);
            form.addView(delete, dlp);
        }

        View divider = new View(context);
        divider.setBackgroundColor(line);
        root.addView(divider, new LinearLayout.LayoutParams(-1, Math.max(1, dp(1) / 2 + 1)));
        LinearLayout footer = column();
        footer.setPadding(dp(20), dp(10), dp(20), dp(16));
        footer.setBackgroundColor(sheetBg);
        TextView error = text("", 12, danger, true);
        error.setPadding(dp(2), 0, dp(2), dp(8));
        error.setVisibility(View.GONE);
        footer.addView(error, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout buttons = row();
        TextView cancel = secondaryButton(s(R.string.action_cancel));
        cancel.setOnClickListener(v -> dialog.cancel());
        TextView save = primaryButton(s(editing ? R.string.sheet_save_changes : R.string.sheet_add_event), coral);
        EventSaveAction saveAction = restoreExpiredConfirmation -> {
            PersonalEvent candidate = draft.copy();
            String message = readEvent(context, inputs, candidate);
            if (message == null && customReminder[0]) {
                String rawMinutes = inputs[5].getText().toString().trim();
                if (rawMinutes.isEmpty()) {
                    message = s(R.string.sheet_error_custom_empty);
                } else {
                    try { candidate.remindMinutes = Integer.parseInt(rawMinutes); }
                    catch (NumberFormatException invalid) { message = s(R.string.sheet_error_custom_invalid); }
                }
            }
            if (message == null) message = candidate.validateForEditing();
            if (message != null) {
                error.setText(message);
                error.setVisibility(View.VISIBLE);
                return;
            }
            Runnable persist = () -> {
                if (!dialog.isShowing()) return;
                String saveError = saver == null ? null : saver.save(candidate);
                if (saveError == null) {
                    allowClose[0] = true;
                    dialog.dismiss();
                } else {
                    error.setText(saveError);
                    error.setVisibility(View.VISIBLE);
                }
            };
            Runnable reviewedPersist=() -> reviewSave(candidate,eventConflicts,persist);
            // A restored conflict must remember a preceding explicit “save without reminder”.
            if(restoredState!=null && "time_conflict".equals(restoredState.getString("confirmation"))
                    && restoredState.getBoolean("conflict_no_reminder")) {
                candidate.remindMinutes=PersonalEvent.NO_REMINDER; conflictNoReminder=true;
                restoredState.remove("conflict_no_reminder");
            } else {
                conflictNoReminder=false;
            }
            if (restoreExpiredConfirmation || (candidate.repeat==0 && candidate.reminderExpired(System.currentTimeMillis()))) {
                activeReminderConfirmation = confirmChoice("expired_reminder", s(R.string.sheet_reminder_passed_title),
                        s(R.string.sheet_reminder_passed_message),
                        s(R.string.sheet_save_without_reminder), s(R.string.sheet_go_back_edit), false, () -> {
                            candidate.remindMinutes = PersonalEvent.NO_REMINDER; conflictNoReminder=true;
                            reviewedPersist.run();
                        });
            } else {
                reviewedPersist.run();
            }
        };
        save.setOnClickListener(v -> saveAction.run(false));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(48), 1);
        cp.rightMargin = dp(10);
        buttons.addView(cancel, cp);
        buttons.addView(save, new LinearLayout.LayoutParams(0, dp(48), 2));
        footer.addView(buttons, new LinearLayout.LayoutParams(-1, -2));
        root.addView(footer, new LinearLayout.LayoutParams(-1, -2));

        TextWatcher clearError = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { error.setVisibility(View.GONE); }
            @Override public void afterTextChanged(Editable s) { }
        };
        for (EditText input : inputs) input.addTextChangedListener(clearError);
        // Keep the original baseline: restored dirty input must still ask before discarding.
        initial[0] = eventSnapshot(inputs, draft, customReminder[0]);
        if (restoredState != null) {
            changingReminder[0] = true;
            for (int i = 0; i < inputs.length; i++) {
                String raw = restoredState.getString("input" + i);
                if (raw != null) inputs[i].setText(raw);
            }
            changingReminder[0] = false;
            initial[0] = restoredState.getString("initial", initial[0]);
            refreshDate[0].run();
            refreshRemind[0].run();
        }
        updateEventTimeFeedback(inputs[1], inputs[2], timeFeedback);
        activeEventDialog = dialog;
        activeEventInputs = inputs;
        activeEventScroll = scroll;
        activeEventDraft = draft;
        activeCustomReminder = customReminder;
        activeEventInitial = initial[0];
        present(dialog, root, false);
        restoreEditorInputs(restoredState, inputs);
        if (restoredState != null) restoreEventScrollAfterLayout(scroll, restoredState.getInt("scroll", 0));
        if (restoredState != null && restoredState.getBundle("date_picker") != null && dialog.isShowing())
            showEventDatePicker(draft, restoredState.getBoolean("picking_until")?refreshRepeat:refreshDate[0],
                    restoredState.getBundle("date_picker"),restoredState.getBoolean("picking_until"));
        else if (restoredState != null && "expired_reminder".equals(restoredState.getString("confirmation")))
            saveAction.run(true);
        else if(restoredState!=null && "time_conflict".equals(restoredState.getString("confirmation"))) {
            restoredConflictAnchor = restoredState.getBundle("conflict_anchor");
            saveAction.run(false);
        }
        else restoreEditorConfirmation(restoredState, dialog, requestDelete);
    }

    private interface EventSaveAction { void run(boolean restoreExpiredConfirmation); }

    private void updateEventTimeFeedback(EditText startInput, EditText endInput, TextView feedback) {
        String startText = startInput.getText().toString().trim();
        String endText = endInput.getText().toString().trim();
        int start = PersonalEvent.parseTime(startText);
        int end = PersonalEvent.parseTime(endText);
        int error = startText.isEmpty() ? R.string.event_time_error_start_required
                : start < 0 ? R.string.event_time_error_start_invalid
                : !endText.isEmpty() && end < 0 ? R.string.event_time_error_end_invalid
                : !endText.isEmpty() && end <= start ? R.string.event_time_error_order : 0;
        String message;
        if (error != 0) {
            message = s(error);
        } else if (endText.isEmpty()) {
            message = s(R.string.event_time_summary_no_end, PersonalEvent.formatTime(start));
        } else {
            int duration = end - start;
            String durationText = duration < 60 ? s(R.string.event_time_duration_minutes, duration)
                    : duration % 60 == 0 ? s(R.string.event_time_duration_hours, duration / 60)
                    : s(R.string.event_time_duration_hours_minutes, duration / 60, duration % 60);
            message = s(R.string.event_time_summary, PersonalEvent.formatTime(start),
                    PersonalEvent.formatTime(end), durationText);
        }
        if (!message.contentEquals(feedback.getText())) feedback.setText(message);
        feedback.setTextColor(error == 0 ? palette.secondary : palette.onErrorContainer);
        feedback.setBackground(round(error == 0 ? palette.inset : palette.errorContainer, 12));
    }

    private static void restoreEventScrollAfterLayout(NestedScrollView scroll, int position) {
        // A posted callback can precede the first measured frame after a configuration change.
        scroll.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                if (scroll.getHeight() <= 0 || scroll.getChildCount() == 0
                        || scroll.getChildAt(0).getHeight() <= 0) return;
                scroll.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                scroll.scrollTo(0, Math.max(0, position));
            }
        });
    }

    private Runnable repeatControls(LinearLayout form,PersonalEvent draft) {
        form.addView(sectionLabel(s(R.string.repeat_title),null));
        android.widget.Spinner modes=new android.widget.Spinner(context); modes.setTag("event:repeat");
        String[] names={s(R.string.repeat_once),s(R.string.repeat_daily),s(R.string.repeat_work),s(R.string.repeat_weekly)};
        android.widget.ArrayAdapter<String> adapter=new android.widget.ArrayAdapter<>(context,android.R.layout.simple_spinner_dropdown_item,names);
        modes.setAdapter(adapter); modes.setSelection(draft.repeat); form.addView(modes,fieldParams());
        LinearLayout days=row(); form.addView(days); android.widget.CheckBox[] boxes=new android.widget.CheckBox[7];
        String[] labels=context.getResources().getStringArray(R.array.sheet_weekday_chips);
        String[] spoken=context.getResources().getStringArray(R.array.sheet_weekday_names);
        // Wrap weekdays over two rows to keep full-size tap targets even on narrow screens.
        days.setOrientation(LinearLayout.VERTICAL); LinearLayout group=null;
        for(int i=0;i<7;i++) { if(i%4==0){group=row();days.addView(group);} final int day=i;
            android.widget.CheckBox box=new android.widget.CheckBox(context); boxes[i]=box;
            box.setText(labels[i]); box.setContentDescription(spoken[i]); box.setTextColor(ink); box.setMinimumHeight(dp(48));
            box.setOnCheckedChangeListener((b,on)->{if(on) draft.weekdays|=1<<day; else draft.weekdays&=~(1<<day);});
            group.addView(box,new LinearLayout.LayoutParams(0,-2,1));
        }
        LinearLayout end=column(); form.addView(end); end.addView(sectionLabel(s(R.string.repeat_until),null));
        TextView pick=secondaryButton(""); pick.setTag("event:repeat_until"); end.addView(pick,fieldParams());
        TextView clear=quickChip(s(R.string.repeat_end_clear),null); end.addView(clear);
        Runnable update=()->{
            days.setVisibility(draft.repeat==EventSeries.WEEKLY?View.VISIBLE:View.GONE);
            end.setVisibility(draft.repeat==0?View.GONE:View.VISIBLE);
            for(int i=0;i<7;i++) boxes[i].setChecked((draft.weekdays&(1<<i))!=0);
            pick.setText(draft.until==0?s(R.string.repeat_no_end)+" · "+s(R.string.repeat_pick):ScheduleDay.label(draft.until));
            clear.setVisibility(draft.until==0?View.GONE:View.VISIBLE);
        };
        pick.setOnClickListener(v->showEventDatePicker(draft,update,null,true));
        clear.setOnClickListener(v->{draft.until=0;update.run();});
        modes.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                int old=draft.repeat; draft.repeat=pos;
                if(pos==EventSeries.WEEKLY && old!=pos && draft.weekdays==0) draft.weekdays=1<<draft.weekday();
                update.run();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
        });
        update.run(); return update;
    }

    private void showEventDatePicker(PersonalEvent draft, Runnable refresh, Bundle restored) {
        showEventDatePicker(draft,refresh,restored,false);
    }
    private void showEventDatePicker(PersonalEvent draft,Runnable refresh,Bundle restored,boolean until) {
        if (activeEventDialog == null || !activeEventDialog.isShowing() || activeEventDatePicker != null) return;
        pickingUntil=until; int date=until && draft.until!=0?draft.until:ScheduleDay.of(draft);
        android.app.DatePickerDialog picker = new android.app.DatePickerDialog(context,
                (view, year, month, day) -> {
                    if (activeEventDialog == null || !activeEventDialog.isShowing()) return;
                    if(until) draft.until=ScheduleDay.of(year,month+1,day); else {draft.year = year; draft.month = month + 1; draft.day = day;}
                    refresh.run();
                }, date/10000, date/100%100-1, date%100);
        picker.getDatePicker().setMinDate(ScheduleDay.millis(until?ScheduleDay.of(draft):20000101));
        picker.getDatePicker().setMaxDate(ScheduleDay.millis(ScheduleDay.LAST));
        activeEventDatePicker = picker;
        picker.setOnDismissListener(d -> { if (activeEventDatePicker == picker) activeEventDatePicker = null; });
        try {
            picker.show();
            if (restored != null) picker.onRestoreInstanceState(restored);
            stylePickerButtons(picker);
            AppFonts.install(picker);
        } catch (RuntimeException failure) {
            closeEventDatePicker();
            throw failure;
        }
    }

    private void closeEventDatePicker() {
        android.app.DatePickerDialog picker = activeEventDatePicker;
        activeEventDatePicker = null;
        if (picker != null) picker.dismiss();
    }

    /** Copies the text fields into {@code event}; returns an error for unparsable times. */
    private static String readEvent(Context context, EditText[] inputs, PersonalEvent event) {
        event.title = inputs[0].getText().toString().trim();
        String start = inputs[1].getText().toString().trim();
        String end = inputs[2].getText().toString().trim();
        if (start.isEmpty()) return context.getString(R.string.sheet_error_start_empty);
        event.startMinutes = PersonalEvent.parseTime(start);
        if (event.startMinutes < 0) return context.getString(R.string.sheet_error_start_format);
        if (end.isEmpty()) {
            event.endMinutes = PersonalEvent.NO_END;
        } else {
            event.endMinutes = PersonalEvent.parseTime(end);
            if (event.endMinutes < 0) return context.getString(R.string.sheet_error_end_format);
        }
        event.location = inputs[3].getText().toString().trim();
        event.note = inputs[4].getText().toString().trim();
        return null;
    }

    private static String eventSnapshot(EditText[] inputs, PersonalEvent d, boolean customReminder) {
        StringBuilder s = new StringBuilder(d.year + "-" + d.month + "-" + d.day + "|" + d.remindMinutes + "|" + customReminder
                + "|" + d.showInTimetable + "|" + d.repeat + "|" + d.weekdays + "|" + d.until);
        for (EditText input : inputs) {
            String raw = input == null ? "" : input.getText().toString();
            s.append('|').append(raw.length()).append(':').append(raw);
        }
        return s.toString();
    }

    private static String pastEventLabel(Context context, PersonalEvent event) {
        return context.getString(event.endMinutes == PersonalEvent.NO_END ? R.string.sheet_event_due : R.string.sheet_event_ended);
    }

    private static int dayOffsetFromToday(PersonalEvent e) {
        if (!PersonalEvent.validDate(e.year, e.month, e.day)) return -1;
        java.util.Calendar today = java.util.Calendar.getInstance();
        java.util.Calendar mine = java.util.Calendar.getInstance();
        mine.clear();
        mine.set(e.year, e.month - 1, e.day);
        today.set(java.util.Calendar.HOUR_OF_DAY, 0);
        today.set(java.util.Calendar.MINUTE, 0);
        today.set(java.util.Calendar.SECOND, 0);
        today.set(java.util.Calendar.MILLISECOND, 0);
        long diff = mine.getTimeInMillis() - today.getTimeInMillis();
        // Round, so a daylight-saving shift of one hour cannot change the day count.
        return (int) Math.round(diff / 86_400_000.0);
    }

    private String dateLabel(PersonalEvent e) {
        if (!PersonalEvent.validDate(e.year, e.month, e.day)) return s(R.string.sheet_date_invalid);
        String[] months = context.getResources().getStringArray(R.array.sheet_month_labels);
        String[] days = context.getResources().getStringArray(R.array.sheet_weekday_names);
        return s(R.string.sheet_date_label, e.year, months[e.month - 1], e.day, days[e.weekday()]);
    }

    private static final class MineScrollState {
        int generation;
        Integer pendingPosition;
        Runnable anchorCancellation;

        void cancelAnchorRestore() {
            Runnable pending = anchorCancellation;
            anchorCancellation = null;
            if (pending != null) pending.run();
        }
    }

    /** The full-screen 「我的」 tab. Event sheets open above it, so it never has to be re-shown. */
    static final class MinePage {
        final View view;
        private final List<PersonalEvent> events;
        private final Runnable refresh;
        private final View eventSection, hiddenHint;
        private final DisplayMode[] displayMode;
        private final MineScrollState scrollState;
        private android.view.ViewTreeObserver restoreObserver;
        private final android.view.ViewTreeObserver.OnGlobalLayoutListener restoreAfterLayout =
                this::applyPendingScrollPosition;

        private MinePage(View view, List<PersonalEvent> events, Runnable refresh,
                          View eventSection, View hiddenHint, DisplayMode[] displayMode, MineScrollState scrollState) {
            this.view = view;
            this.events = events;
            this.refresh = refresh;
            this.eventSection = eventSection;
            this.hiddenHint = hiddenHint;
            this.displayMode = displayMode;
            this.scrollState = scrollState;
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) { observeScrollRestore(); }
                @Override public void onViewDetachedFromWindow(View v) {
                    stopObservingScrollRestore();
                    scrollState.cancelAnchorRestore();
                }
            });
        }

        void setDisplayMode(DisplayMode mode) {
            DisplayMode nextMode = mode == null ? DisplayMode.COMBINED : mode;
            if (displayMode[0] == nextMode) return;
            displayMode[0] = nextMode;
            boolean showEvents = displayMode[0].showsEvents();
            eventSection.setVisibility(showEvents ? View.VISIBLE : View.GONE);
            hiddenHint.setVisibility(showEvents ? View.GONE : View.VISIBLE);
            refresh.run();
        }

        void setEvents(List<PersonalEvent> next) {
            events.clear();
            // Own the displayed values: callers may edit their existing event objects in place.
            if (next != null) for (PersonalEvent event : next) if (event != null) events.add(event.copy());
            refresh.run();
        }

        public int scrollPosition() {
            return scrollState.pendingPosition == null ? view.getScrollY() : scrollState.pendingPosition;
        }

        public void restoreScrollPosition(int y) {
            scrollState.pendingPosition = Math.max(0, y);
            scrollState.generation++;
            scrollState.cancelAnchorRestore();
            observeScrollRestore();
        }

        private void observeScrollRestore() {
            if (scrollState.pendingPosition == null) return;
            stopObservingScrollRestore();
            restoreObserver = view.getViewTreeObserver();
            restoreObserver.addOnGlobalLayoutListener(restoreAfterLayout);
            view.post(this::applyPendingScrollPosition);
        }

        private void applyPendingScrollPosition() {
            if (scrollState.pendingPosition == null || !view.isAttachedToWindow() || view.getHeight() == 0
                    || view.isLayoutRequested()) return;
            ViewGroup container = (ViewGroup) view;
            if (container.getChildCount() == 0 || container.getChildAt(0).isLayoutRequested()
                    || container.getChildAt(0).getHeight() == 0) return;
            int y = scrollState.pendingPosition;
            scrollState.pendingPosition = null;
            stopObservingScrollRestore();
            view.scrollTo(0, y);
        }

        private void stopObservingScrollRestore() {
            if (restoreObserver != null && restoreObserver.isAlive())
                restoreObserver.removeOnGlobalLayoutListener(restoreAfterLayout);
            restoreObserver = null;
        }
    }

    /** 「我的」: all personal events (upcoming first, finished ones dimmed) plus the settings entry. */
    MinePage minePage(Runnable onAdd, EventOpener onOpen, Runnable onSettings) {
        List<PersonalEvent> events = new java.util.ArrayList<>();
        DisplayMode[] displayMode = {DisplayMode.COMBINED};
        EventOpener openForward = row -> { if (displayMode[0].showsEvents() && onOpen != null) onOpen.open(row); };
        NestedScrollView scroll = new NestedScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = column();
        body.setPadding(dp(16), 0, dp(16), dp(18));
        scroll.addView(body, new ViewGroup.LayoutParams(-1, -2));

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text(s(R.string.sheet_mine_title), 23, ink, true), new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(header, new LinearLayout.LayoutParams(-1, dp(52)));

        LinearLayout eventSection = column();
        body.addView(eventSection, new LinearLayout.LayoutParams(-1, -2));
        TextView hiddenHint = text(s(R.string.display_mode_mine_courses_only), 13, muted, false);
        hiddenHint.setPadding(dp(16), dp(26), dp(16), dp(26));
        hiddenHint.setBackground(round(surface, 16));
        hiddenHint.setVisibility(View.GONE);
        body.addView(hiddenHint, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout titleRow = row();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(dp(2), dp(4), 0, dp(8));
        TextView count = text("", 14, ink, true);
        titleRow.addView(count, new LinearLayout.LayoutParams(0, -2, 1));
        TextView add = quickChip(s(R.string.sheet_add_event_chip), v -> {
            if (displayMode[0].showsEvents() && onAdd != null) onAdd.run();
        });
        add.setContentDescription(s(R.string.sheet_add_event));
        titleRow.addView(add);
        eventSection.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = text(s(R.string.sheet_mine_hint), 11, muted, false);
        hint.setPadding(dp(2), 0, 0, dp(10));
        eventSection.addView(hint);

        LinearLayout eventRows = column();
        eventSection.addView(eventRows, new LinearLayout.LayoutParams(-1, -2));
        MineScrollState scrollState = new MineScrollState();
        Runnable refreshRows = () -> {
            scrollState.cancelAnchorRestore();
            int generation = ++scrollState.generation;
            int oldY = scroll.getScrollY();
            Long anchorId = null;
            int anchorOffset = 0;
            for (int i = 0; i < eventRows.getChildCount(); i++) {
                View child = eventRows.getChildAt(i);
                if (child.getTag() instanceof Long && eventSection.getTop() + eventRows.getTop() + child.getBottom() > oldY) {
                    anchorId = (Long) child.getTag();
                    anchorOffset = eventSection.getTop() + eventRows.getTop() + child.getTop() - oldY;
                    break;
                }
            }
            eventRows.removeAllViews();
            long current = System.currentTimeMillis();
            List<PersonalEvent> upcoming = new java.util.ArrayList<>();
            List<PersonalEvent> past = new java.util.ArrayList<>();
            for (PersonalEvent e : events) (e.isPast(current) ? past : upcoming).add(e);
            java.util.Collections.sort(upcoming, (a, b) -> Long.compare(a.startMillis(), b.startMillis()));
            java.util.Collections.sort(past, (a, b) -> Long.compare(b.startMillis(), a.startMillis()));
            String countLabel = s(R.string.sheet_mine_count, upcoming.size());
            count.setText(countLabel);
            if (events.isEmpty()) {
                TextView empty = text(s(R.string.sheet_mine_empty), 13, muted, false);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(dp(16), dp(26), dp(16), dp(26));
                empty.setBackground(round(surface, 16));
                eventRows.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            }
            for (PersonalEvent e : upcoming) eventRows.addView(mineRow(e, false, openForward), mineRowParams());
            if (!past.isEmpty()) {
                TextView label = text(s(R.string.sheet_mine_past_header, past.size()), 12, muted, true);
                label.setPadding(dp(2), dp(14), 0, dp(8));
                eventRows.addView(label);
                for (PersonalEvent e : past) eventRows.addView(mineRow(e, true, openForward), mineRowParams());
            }
            final Long keepId = anchorId;
            final int keepOffset = anchorOffset;
            // New rows have no positions until layout. Explicit page restoration takes precedence.
            if (oldY > 0 && scrollState.pendingPosition == null && scroll.isAttachedToWindow()
                    && eventSection.getVisibility() != View.GONE) {
                android.view.ViewTreeObserver observer = scroll.getViewTreeObserver();
                class RestoreAnchor implements android.view.ViewTreeObserver.OnGlobalLayoutListener,
                        View.OnAttachStateChangeListener, Runnable {
                    private boolean cancelled;

                    @Override public void run() {
                        if (cancelled) return;
                        cancelled = true;
                        if (observer.isAlive()) observer.removeOnGlobalLayoutListener(this);
                        scroll.removeOnAttachStateChangeListener(this);
                        if (scrollState.anchorCancellation == this) scrollState.anchorCancellation = null;
                    }

                    @Override public void onGlobalLayout() {
                        if (cancelled) return;
                        if (scrollState.generation != generation || scrollState.pendingPosition != null
                                || !scroll.isAttachedToWindow() || eventSection.getVisibility() == View.GONE) {
                            run();
                            return;
                        }
                        if (scroll.getHeight() == 0 || scroll.isLayoutRequested() || body.isLayoutRequested()
                                || eventSection.isLayoutRequested() || eventRows.isLayoutRequested()) return;
                        View anchor = keepId == null ? null : eventRows.findViewWithTag(keepId);
                        if (anchor != null && (anchor.isLayoutRequested() || anchor.getHeight() == 0)) return;
                        int y = anchor == null ? oldY
                                : eventSection.getTop() + eventRows.getTop() + anchor.getTop() - keepOffset;
                        run();
                        scroll.scrollTo(0, Math.max(0, y));
                    }

                    @Override public void onViewAttachedToWindow(View v) { }
                    @Override public void onViewDetachedFromWindow(View v) { run(); }
                }
                RestoreAnchor restore = new RestoreAnchor();
                scrollState.anchorCancellation = restore;
                scroll.addOnAttachStateChangeListener(restore);
                observer.addOnGlobalLayoutListener(restore);
            }
        };
        refreshRows.run();

        LinearLayout settings = row();
        settings.setGravity(Gravity.CENTER_VERTICAL);
        settings.setPadding(dp(14), dp(14), dp(14), dp(14));
        settings.setBackground(ripple(surface, 16));
        TextView gear = text("⚙", 18, coral, false);
        gear.setGravity(Gravity.CENTER);
        gear.setBackground(round(lavender, 12));
        settings.addView(gear, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout texts = column();
        texts.setPadding(dp(12), 0, 0, 0);
        texts.addView(text(s(R.string.sheet_settings), 15, ink, true));
        TextView sub = text(s(R.string.sheet_settings_sub), 12, muted, false);
        sub.setPadding(0, dp(3), 0, 0);
        texts.addView(sub);
        settings.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        settings.setContentDescription(s(R.string.sheet_label_with_sub,
                s(R.string.sheet_settings), s(R.string.sheet_settings_sub)));
        settings.setOnClickListener(v -> { if (onSettings != null) onSettings.run(); });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(16);
        body.addView(settings, sp);
        final String[] lastState = {mineState(events)};
        final String[] lastContentState = {mineContentState(events, displayMode[0], lastState[0])};
        Runnable ticker = new Runnable() {
            @Override public void run() {
                if (!displayMode[0].showsEvents() || !scroll.isAttachedToWindow() || !scroll.isShown()) return;
                String next = mineState(events);
                if (!lastState[0].equals(next)) {
                    lastState[0] = next;
                    lastContentState[0] = mineContentState(events, displayMode[0], next);
                    refreshRows.run();
                }
                scroll.postDelayed(this, 1000);
            }
        };
        Runnable update = () -> {
            String temporalState = mineState(events);
            String contentState = mineContentState(events, displayMode[0], temporalState);
            if (!lastContentState[0].equals(contentState)) {
                lastState[0] = temporalState;
                lastContentState[0] = contentState;
                refreshRows.run();
            }
            // Restart the per-second check whenever the page is refreshed or becomes visible again.
            scroll.removeCallbacks(ticker);
            if (displayMode[0].showsEvents() && scroll.isAttachedToWindow() && scroll.isShown()) scroll.postDelayed(ticker, 1000);
        };
        scroll.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { v.removeCallbacks(ticker); v.post(ticker); }
            @Override public void onViewDetachedFromWindow(View v) { v.removeCallbacks(ticker); }
        });
        return new MinePage(scroll, events, update, eventSection, hiddenHint, displayMode, scrollState);
    }

    /** Immutable snapshot of row text, ordering, accessibility and the event opened by a click. */
    private String mineContentState(List<PersonalEvent> events, DisplayMode mode, String temporalState) {
        StringBuilder state = new StringBuilder(mode.name()).append('|').append(temporalState).append('|');
        for (PersonalEvent event : events) {
            state.append(event.id).append(',').append(event.sourceDate).append(',').append(event.year).append(',').append(event.month).append(',')
                    .append(event.day).append(',').append(event.startMinutes).append(',').append(event.endMinutes)
                    .append(',').append(event.remindMinutes).append(',').append(event.showInTimetable).append('|');
            appendMineField(state, event.title);
            appendMineField(state, event.location);
            appendMineField(state, event.note);
        }
        return state.toString();
    }

    private static void appendMineField(StringBuilder target, String value) {
        if (value == null) target.append("-1:");
        else target.append(value.length()).append(':').append(value);
        target.append('|');
    }

    private String mineState(List<PersonalEvent> events) {
        long now = System.currentTimeMillis();
        StringBuilder state = new StringBuilder();
        for (PersonalEvent event : events) {
            state.append(event.isPast(now) ? '1' : '0');
            state.append(event.reminderExpired(now) ? '1' : '0');
        }
        return state.toString();
    }

    private LinearLayout.LayoutParams mineRowParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(7);
        return lp;
    }

    private View mineRow(PersonalEvent e, boolean past, EventOpener onOpen) {
        LinearLayout row = column();
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        int rowFill = past ? (dark ? DarkUiPalette.RECESSED : color("#ECEEF3")) : surface;
        row.setBackground(dark
                ? rippleBordered(rowFill, 14, past ? DarkUiPalette.SUBTLE_LINE : line)
                : ripple(rowFill, 14));
        int titleColor = past ? muted : ink;
        TextView title = text(nonempty(e.title, s(R.string.sheet_untitled_event)), 15, titleColor, true);
        title.setSingleLine(true);
        row.addView(title);
        String where = e.location == null || e.location.isEmpty() ? "" : " · " + e.location;
        String remind = e.remindMinutes >= 0 && !past ? " · " + reminderStatus(e, System.currentTimeMillis()) : "";
        TextView detail = text(dateLabel(e) + " " + e.timeLabel() + where + remind
                + (past ? " · " + pastEventLabel(context, e) : ""), 12, past ? muted : coral, !past);
        detail.setPadding(0, dp(4), 0, 0);
        detail.setSingleLine(true);
        row.addView(detail);
        String rowTitle = nonempty(e.title, s(R.string.sheet_untitled_event));
        String rowWhen = dateLabel(e) + " " + e.timeLabel();
        row.setContentDescription(past
                ? s(R.string.sheet_mine_row_desc_past, rowTitle, rowWhen, pastEventLabel(context, e))
                : s(R.string.sheet_mine_row_desc, rowTitle, rowWhen));
        row.setTag(Long.valueOf(e.id));
        row.setOnClickListener(v -> onOpen.open(e));
        return row;
    }
    // ---------------------------------------------------------------- helpers

    private String reminderStatus(PersonalEvent event, long now) {
        String original = PersonalEvent.remindLabel(event.remindMinutes);
        return event.reminderExpired(now) ? s(R.string.sheet_reminder_expired_status, original) : original;
    }

    private String s(int resId, Object... args) {
        return args.length == 0 ? context.getString(resId) : context.getString(resId, args);
    }

    /** Activity teardown may detach a dialog without a user dismissal. Never retain a timer then. */
    private void cancelOnDetach(View view, Runnable callback) {
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { }
            @Override public void onViewDetachedFromWindow(View v) { v.removeCallbacks(callback); }
        });
    }

    /** Scrolls the form while the footer buttons stay visible, also above the keyboard. */
    private NestedScrollView boundedScroll() {
        return new NestedScrollView(context) {
            private final Rect frame = new Rect();
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                getWindowVisibleDisplayFrame(frame);
                int screen = getResources().getDisplayMetrics().heightPixels;
                int visible = frame.height() > 0 ? frame.height() : screen;
                int max = Math.max(0, Math.min(Math.round(screen * 0.66f), visible - dp(200)));
                if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED)
                    max = Math.min(max, MeasureSpec.getSize(heightSpec));
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
            }
        };
    }

    /** Dismissal listeners are composed rather than replacing host draft cleanup. */
    private class ManagedSheet extends GuardedBottomSheetDialog {
        ModalCoordinator.Token token;
        android.content.DialogInterface.OnDismissListener cleanup;
        Runnable fallbackNext;
        ManagedSheet(Context context) {
            super(context);
            super.setOnDismissListener(d -> {
                if (mainDialog == this) mainDialog = null;
                if (confirmation != null) confirmation.dismiss();
                if (cleanup != null) cleanup.onDismiss(d);
                if (onDismissed != null) onDismissed.run();
                if (coordinator != null) coordinator.dismissed(token);
                else if (fallbackNext != null) { Runnable next = fallbackNext; fallbackNext = null; next.run(); }
            });
        }
        @Override public void setOnDismissListener(android.content.DialogInterface.OnDismissListener listener) {
            cleanup = listener;
        }
        @Override public void dismiss() {
            if (coordinator != null) coordinator.closing(token);
            super.dismiss();
        }
    }

    private void switchSheet(BottomSheetDialog dialog, Runnable next) {
        ManagedSheet managed = (ManagedSheet) dialog;
        if (coordinator != null) {
            if (!coordinator.switchAfterDismiss(managed.token, next)) return;
        } else {
            if (managed.fallbackNext != null) return;
            managed.fallbackNext = next;
        }
        dialog.dismiss();
    }

    void dismissForDestroy() {
        closeEventDatePicker();
        if (confirmation != null) confirmation.dismiss();
        if (mainDialog != null) mainDialog.dismiss();
    }

    private void present(BottomSheetDialog dialog, View content, boolean draggable) {
        if (dialog instanceof ManagedSheet) {
            ManagedSheet managed = (ManagedSheet) dialog;
            if (coordinator != null) {
                managed.token = coordinator.tryAcquire("sheet");
                if (managed.token == null) return;
            }
            mainDialog = managed;
        }
        dialog.setContentView(content);
        if (content.getParent() instanceof View) {
            ((View) content.getParent()).setBackgroundColor(Color.TRANSPARENT);
        }
        BottomSheetBehavior<?> behavior = dialog.getBehavior();
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        behavior.setDraggable(draggable);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            window.setNavigationBarColor(sheetBg);
            WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
            controller.setAppearanceLightNavigationBars(!dark);
        }
        try {
            dialog.show();
            if (dialog instanceof ManagedSheet && coordinator != null) {
                ModalCoordinator.Token token = ((ManagedSheet) dialog).token;
                content.post(() -> coordinator.opened(token));
            }
            AppFonts.install(dialog);
        } catch (RuntimeException error) {
            if (dialog instanceof ManagedSheet && coordinator != null)
                coordinator.dismissed(((ManagedSheet) dialog).token);
            throw error;
        }
    }

    private LinearLayout sheetRoot() {
        LinearLayout root = column();
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(sheetBg);
        float r = dp(26);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        if (dark) {
            GradientDrawable rim = new GradientDrawable();
            rim.setColor(Color.TRANSPARENT);
            rim.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
            rim.setStroke(dp(1), Color.argb(26, 255, 255, 255));
            LayerDrawable layered = new LayerDrawable(new Drawable[]{bg, rim});
            layered.setLayerInset(1, 0, 0, 0, -dp(2));
            root.setBackground(layered);
        } else {
            root.setBackground(bg);
        }
        View handle = new View(context);
        handle.setBackground(round(dark ? DarkUiPalette.HANDLE : color("#D5D9E8"), 3));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(38), dp(5));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.setMargins(0, dp(10), 0, dp(12));
        root.addView(handle, hp);
        return root;
    }

    private View sectionLabel(String label, TextView trailing) {
        LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(16), dp(2), dp(8));
        row.addView(text(label, 13, muted, true), new LinearLayout.LayoutParams(0, -2, 1));
        if (trailing != null) row.addView(trailing);
        return row;
    }

    private EditText input(String hint, String value) {
        EditText edit = new EditText(context);
        edit.setHint(hint);
        edit.setText(value == null ? "" : value);
        edit.setSingleLine(true);
        edit.setTextSize(15);
        edit.setTextColor(ink);
        edit.setHintTextColor(muted);
        // Cursor and selection follow the chosen accent instead of the Material default purple.
        edit.setHighlightColor(withAlpha(coral, 90));
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            GradientDrawable cursor = new GradientDrawable();
            cursor.setColor(coral);
            cursor.setSize(dp(2), dp(20));
            edit.setTextCursorDrawable(cursor);
        }
        edit.setPadding(dp(14), dp(12), dp(14), dp(12));
        edit.setMinHeight(dp(48));
        edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_FIELD_LENGTH)});
        StateListDrawable states = new StateListDrawable();
        GradientDrawable focused = round(field, 14);
        focused.setStroke(dp(1.5f), coral);
        GradientDrawable normal = round(field, 14);
        normal.setStroke(dp(1), line);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        edit.setBackground(states);
        return edit;
    }

    private LinearLayout.LayoutParams fieldParams() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private TextView chip(String label, String sub) {
        TextView chip = new TextView(context);
        chip.setGravity(Gravity.CENTER);
        chip.setIncludeFontPadding(false);
        if (sub == null) {
            chip.setText(label);
            chip.setTextSize(14);
        } else {
            android.text.SpannableString text = new android.text.SpannableString(label + "\n" + sub);
            text.setSpan(new android.text.style.AbsoluteSizeSpan(15, true), 0, label.length(), 0);
            text.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, label.length(), 0);
            text.setSpan(new android.text.style.AbsoluteSizeSpan(9, true), label.length() + 1, text.length(), 0);
            chip.setText(text);
            chip.setLineSpacing(dp(2), 1f);
        }
        return chip;
    }

    /** Selected chip: coral fill with contrast-safe ink. Chips before {@code disabledBefore} are dimmed and inert. */
    private void refreshChoices(TextView[] chips, int selected, int disabledBefore) {
        int selectedInk = DarkUiPalette.chipInk(coral, dark);
        for (int i = 0; i < chips.length; i++) {
            boolean on = i == selected;
            boolean disabled = i < disabledBefore;
            GradientDrawable bg = round(on ? coral : surface, 13);
            if (!on) bg.setStroke(dp(1), line);
            chips[i].setBackground(bg);
            chips[i].setTextColor(on ? selectedInk : ink);
            if (!on && chips[i].getText() instanceof android.text.Spanned) chips[i].setTextColor(ink);
            chips[i].setTypeface(Typeface.DEFAULT, on ? Typeface.BOLD : Typeface.NORMAL);
            chips[i].setAlpha(disabled ? 0.32f : 1f);
            chips[i].setEnabled(!disabled);
            chips[i].setSelected(on);
        }
    }

    private TextView quickChip(String label, View.OnClickListener listener) {
        TextView chip = text(label, 12, coral, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(12), dp(7), dp(12), dp(7));
        chip.setBackground(ripple(lavender, 15));
        chip.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(32));
        lp.rightMargin = dp(8);
        chip.setLayoutParams(lp);
        return chip;
    }

    private TextView stepButton(String label) {
        TextView b = text(label, 17, coral, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(ripple(surface, 17));
        return b;
    }

    private TextView primaryButton(String label, int fill) {
        TextView b = text(label, 15, fill == coral ? palette.onAccent : CoursePalette.readableInk(fill), true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(ripple(fill, 15));
        return b;
    }

    private TextView secondaryButton(String label) {
        TextView b = text(label, 15, ink, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(ripple(palette.inset, 15));
        return b;
    }

    private Drawable ripple(int fill, float radius) {
        return rippleBordered(fill, radius, Color.TRANSPARENT);
    }

    private Drawable rippleBordered(int fill, float radius, int strokeColor) {
        GradientDrawable content = round(fill, radius);
        if (strokeColor != Color.TRANSPARENT) content.setStroke(dp(1), strokeColor);
        return new RippleDrawable(ColorStateList.valueOf(dark ? Color.argb(50, 255, 255, 255) : Color.argb(36, 37, 49, 77)),
                content, round(Color.WHITE, radius));
    }

    private void styleSwitch(androidx.appcompat.widget.SwitchCompat toggle) {
        int[][] states = {{android.R.attr.state_checked}, {}};
        toggle.setThumbTintList(new ColorStateList(states,
                new int[]{coral, dark ? 0xFFA2AAB8 : Color.WHITE}));
        toggle.setTrackTintList(new ColorStateList(states,
                new int[]{withAlpha(coral, dark ? 115 : 130), withAlpha(muted, dark ? 76 : 64)}));
    }

    private void stylePickerButtons(android.app.DatePickerDialog picker) {
        android.widget.Button ok = picker.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        if (ok != null) ok.setTextColor(coral);
        android.widget.Button cancel = picker.getButton(android.content.DialogInterface.BUTTON_NEGATIVE);
        if (cancel != null) cancel.setTextColor(muted);
    }

    private void readInputs(Draft draft, EditText[] inputs) {
        if (inputs[0] != null) draft.name = inputs[0].getText().toString().trim();
        if (inputs[1] != null) draft.weeks = inputs[1].getText().toString().trim();
        if (inputs[2] != null) draft.room = inputs[2].getText().toString().trim();
        if (inputs[3] != null) draft.teacher = inputs[3].getText().toString().trim();
    }

    private static String snapshot(Draft d) {
        StringBuilder result = new StringBuilder(d.day + "|" + d.start + "|" + d.end + "|" + d.color
                + "|" + d.colorManuallySelected);
        for (String value : new String[]{d.name, d.weeks, d.room, d.teacher}) {
            String raw = value == null ? "" : value;
            result.append('|').append(raw.length()).append(':').append(raw);
        }
        return result.toString();
    }

    private static void setText(EditText edit, String value) {
        edit.setText(value);
        edit.setSelection(edit.getText().length());
    }

    /** First or last HH:mm of a period interval such as "08:00-08:50". */
    private static String clock(String interval, boolean first) {
        if (interval == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(interval);
        String found = "";
        while (m.find()) {
            found = String.format(Locale.ROOT, "%02d:%s", Integer.parseInt(m.group(1)), m.group(2));
            if (first) break;
        }
        return found;
    }

    /** Same pastel mapping as the timetable cards, so the swatch matches what the grid shows. */
    private int pastel(int c) { return pastelSurface(c, dark); }

    /** All stored presets share the same tonal range; storage retains the original HEX. */
    static int pastelSurface(int c, boolean dark) {
        return CoursePalette.courseSurface(c, dark);
    }

    private static int withAlpha(int c, int alpha) { return Color.argb(alpha, Color.red(c), Color.green(c), Color.blue(c)); }
    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private static String nonempty(String s, String fallback) { return s == null || s.trim().isEmpty() ? fallback : s; }
    private int dp(float v) { return Math.round(v * context.getResources().getDisplayMetrics().density); }
    private static int color(String value) { return Color.parseColor(value); }

    private LinearLayout column() { LinearLayout l = new LinearLayout(context); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(context); l.setOrientation(LinearLayout.HORIZONTAL); return l; }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(context);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setEllipsize(TextUtils.TruncateAt.END);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable round(int c, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(c);
        d.setCornerRadius(dp(radius));
        return d;
    }

    /** Small line icon on a lavender rounded square, drawn on a 24-unit grid. */
    private final class IconBadge extends Drawable {
        private final String kind;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        IconBadge(String kind) { this.kind = kind; }

        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(lavender);
            float radius = "close".equals(kind) ? b.width() / 2f : dp(10);
            canvas.drawRoundRect(b.left, b.top, b.right, b.bottom, radius, radius, paint);
            canvas.save();
            float size = Math.min(b.width(), b.height()) * 0.56f;
            canvas.translate(b.exactCenterX() - size / 2f, b.exactCenterY() - size / 2f);
            canvas.scale(size / 24f, size / 24f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor("close".equals(kind) ? ink : coral);
            path.reset();
            switch (kind) {
                case "clock":
                    canvas.drawCircle(12, 12, 9, paint);
                    path.moveTo(12, 7); path.lineTo(12, 12); path.lineTo(15.5f, 14); canvas.drawPath(path, paint);
                    break;
                case "calendar":
                    canvas.drawRoundRect(3, 5, 21, 21, 3, 3, paint);
                    canvas.drawLine(3, 10, 21, 10, paint);
                    canvas.drawLine(8, 3, 8, 7, paint);
                    canvas.drawLine(16, 3, 16, 7, paint);
                    break;
                case "pin":
                    path.moveTo(12, 21);
                    path.cubicTo(6, 15, 5, 12, 5, 9.5f);
                    path.cubicTo(5, 5.5f, 8.2f, 3, 12, 3);
                    path.cubicTo(15.8f, 3, 19, 5.5f, 19, 9.5f);
                    path.cubicTo(19, 12, 18, 15, 12, 21);
                    canvas.drawPath(path, paint);
                    canvas.drawCircle(12, 9.5f, 2.5f, paint);
                    break;
                case "person":
                    canvas.drawCircle(12, 8, 4, paint);
                    canvas.drawArc(4.5f, 13.5f, 19.5f, 27, 200, 140, false, paint);
                    break;
                case "close":
                    canvas.drawLine(7, 7, 17, 17, paint);
                    canvas.drawLine(17, 7, 7, 17, paint);
                    break;
                default:
                    break;
            }
            canvas.restore();
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
