package com.weekweeks.app;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Standalone numeric week picker; creates an UN-SHOWN Dialog owned by the host coordinator.
 * No persistence, Activity casts, course changes or private date-picker implementation.
 * Navigation actions run synchronously AFTER the host's onDismiss listener, so it can
 * release its modal token before opening a secondary date picker without an input gap. Do not replace the returned dialog's
 * onDismiss listener: pass the coordinator listener to create instead.
 */
final class WeekPickerDialog {
    interface Actions {
        /** Apply both Selection navigation fields and render, or call browsePastelDate(targetDateMillis). */
        void onSelect(WeekBrowseRules.Selection selection);
        /** Host opens its existing date picker; arbitrary dates/holiday agenda remain accessible. */
        void onFindDate();
    }

    private WeekPickerDialog() { }

    static Dialog create(Context context, boolean dark, PastelAccent theme,
                         long semesterStartMillis, int totalWeeks, long pastelBrowseDate,
                         Actions actions, DialogInterface.OnDismissListener onDismiss) {
        return create(context, dark, theme, semesterStartMillis, totalWeeks, pastelBrowseDate,
                actions, onDismiss, -1);
    }

    static Dialog create(Context context, boolean dark, PastelAccent theme,
                         long semesterStartMillis, int totalWeeks, long pastelBrowseDate,
                         Actions actions, DialogInterface.OnDismissListener onDismiss, int restoredScroll) {
        if (actions == null) throw new IllegalArgumentException("actions must not be null");
        PastelAccent palette = theme == null ? PastelAccent.classic(dark) : theme;
        WeekBrowseRules.Snapshot snapshot = WeekBrowseRules.snapshot(semesterStartMillis,
                totalWeeks, pastelBrowseDate, System.currentTimeMillis());
        int surface = dark ? DarkUiPalette.SURFACE : Color.WHITE;
        int ink = dark ? DarkUiPalette.INK : 0xFF25314D;
        int secondary = dark ? DarkUiPalette.SECONDARY : 0xFF566176;
        int border = dark ? DarkUiPalette.LINE : 0xFFE5E8F2;
        int selectedInk = readable(palette.accent, dark ? DarkUiPalette.BUTTON_INK : Color.WHITE);
        int chipInk = readable(palette.tint, ink);
        int chipAccent = readable(palette.tint, palette.accent);
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Runnable[] afterDismiss = {null};
        boolean[] chosen = {false};
        dialog.setOnDismissListener(d -> {
            if (onDismiss != null) onDismiss.onDismiss(d);
            Runnable action = afterDismiss[0];
            afterDismiss[0] = null;
            if (action != null) action.run();
        });

        BoundedScrollView scroll = new BoundedScrollView(context);
        scroll.setBackground(background(surface, 22, border, dark ? 1 : 0, context));
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setClipToPadding(false);
        LinearLayout content = column(context);
        content.setPadding(dp(context, 18), dp(context, 16), dp(context, 18), dp(context, 14));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        TextView title = text(context, context.getString(R.string.week_picker_title), 20, ink, true);
        content.addView(title, new LinearLayout.LayoutParams(-1, -2));
        String status = snapshot.outsideSemester()
                ? context.getString(R.string.week_picker_outside)
                : context.getString(R.string.week_picker_viewing_week, snapshot.viewedWeek);
        TextView description = text(context,
                context.getString(R.string.week_picker_summary, totalWeeks) + "\n" + status,
                12, secondary, false);
        description.setPadding(0, dp(context, 8), 0, dp(context, 12));
        content.addView(description, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout grid = column(context);
        content.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        float scale = Math.max(1f, context.getResources().getConfiguration().fontScale);
        int availableDp = Math.max(1, Math.min(600,
                context.getResources().getConfiguration().screenWidthDp - 32) - 36);
        int columns = Math.max(1, Math.min(5, (int) (availableDp / (72f * scale))));
        View[] initialCell = {null};
        LinearLayout row = null;
        for (int week = 1; week <= totalWeeks; week++) {
            int index = week - 1;
            if (index % columns == 0) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            final int target = week;
            boolean viewed = snapshot.isViewedWeek(week);
            boolean current = snapshot.isRealWeek(week);
            int fill = viewed ? palette.accent : palette.tint;
            int fg = viewed ? selectedInk : chipInk;
            LinearLayout cell = column(context);
            cell.setGravity(Gravity.CENTER);
            cell.setMinimumHeight(dp(context, 66 * scale));
            cell.setPadding(dp(context, 3), dp(context, 9), dp(context, 3), dp(context, 9));
            cell.setSelected(viewed);
            cell.setFocusable(true);
            GradientDrawable shape = background(fill, 13, current ? chipAccent : border,
                    current ? 2 : 0, context);
            cell.setBackground(new RippleDrawable(ColorStateList.valueOf(dark ? 0x22FFFFFF : 0x22000000),
                    shape, background(Color.WHITE, 13, Color.WHITE, 0, context)));
            TextView number = text(context, String.valueOf(week), 18, fg, true);
            number.setGravity(Gravity.CENTER);
            number.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            cell.addView(number, new LinearLayout.LayoutParams(-1, -2));
            String marker = viewed ? context.getString(R.string.week_picker_viewing) : "";
            if (current) marker += (marker.isEmpty() ? "" : "\n") + context.getString(R.string.week_picker_this_week);
            TextView labels = text(context, marker, 10, viewed ? fg : chipAccent, true);
            labels.setGravity(Gravity.CENTER);
            labels.setMinLines(2);
            labels.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
            labelParams.topMargin = dp(context, 3);
            cell.addView(labels, labelParams);
            cell.setContentDescription(context.getString(R.string.week_picker_week, week)
                    + (viewed ? ", " + context.getString(R.string.week_picker_viewing) : "")
                    + (current ? ", " + context.getString(R.string.week_picker_this_week) : ""));
            cell.setOnClickListener(v -> finish(dialog, chosen, afterDismiss,
                    () -> actions.onSelect(WeekBrowseRules.selectWeek(semesterStartMillis,
                            totalWeeks, target, System.currentTimeMillis()))));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
            cp.setMargins(dp(context, 3), dp(context, 3), dp(context, 3), dp(context, 3));
            row.addView(cell, cp);
            if (week == snapshot.initialWeek()) initialCell[0] = cell;
        }
        if (row != null) {
            int remainder = totalWeeks % columns;
            for (int i = remainder; i > 0 && i < columns; i++)
                row.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1f));
        }
        TextView now = action(context, context.getString(R.string.week_picker_current),
                palette.tint, chipAccent, border);
        now.setOnClickListener(v -> finish(dialog, chosen, afterDismiss,
                () -> actions.onSelect(WeekBrowseRules.currentWeek(semesterStartMillis,
                        totalWeeks, System.currentTimeMillis()))));
        addAction(content, now, context, 12);
        TextView date = action(context, context.getString(R.string.week_picker_find_date),
                surface, readable(surface, palette.accent), border);
        date.setOnClickListener(v -> finish(dialog, chosen, afterDismiss, actions::onFindDate));
        addAction(content, date, context, 6);
        TextView close = action(context, context.getString(R.string.week_picker_close),
                surface, secondary, border);
        close.setOnClickListener(v -> dialog.cancel());
        addAction(content, close, context, 4);
        dialog.setContentView(scroll);
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                int width = Math.min(dp(context, 600),
                        Math.max(1, context.getResources().getDisplayMetrics().widthPixels - dp(context, 32)));
                window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
                window.setGravity(Gravity.CENTER);
            }
            AppFonts.install(dialog);
            scroll.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override public void onGlobalLayout() {
                    View target = initialCell[0];
                    if (scroll.getHeight() <= 0 || target == null || target.getHeight() <= 0) return;
                    scroll.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    if (restoredScroll >= 0) {
                        scroll.scrollTo(0, restoredScroll);
                        return;
                    }
                    Rect rect = new Rect();
                    target.getDrawingRect(rect);
                    scroll.offsetDescendantRectToMyCoords(target, rect);
                    scroll.scrollTo(0, Math.max(0, rect.top - scroll.getHeight() / 3));
                }
            });
        });
        return dialog;
    }

    static int scrollPosition(Dialog dialog) {
        android.view.ViewGroup content = dialog.findViewById(android.R.id.content);
        if (content != null && content.getChildCount() > 0 && content.getChildAt(0) instanceof ScrollView)
            return content.getChildAt(0).getScrollY();
        return 0;
    }

    private static void finish(Dialog dialog, boolean[] chosen, Runnable[] pending, Runnable action) {
        if (chosen[0] || !dialog.isShowing()) return;
        chosen[0] = true;
        pending[0] = action;
        dialog.dismiss();
    }

    private static int readable(int fill, int preferred) {
        return CoursePalette.contrast(fill, preferred) >= 4.5 ? preferred : CoursePalette.readableInk(fill);
    }

    private static LinearLayout column(Context context) {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private static TextView text(Context context, String value, float size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(AppFonts.typeface(context, bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private static TextView action(Context context, String label, int fill, int ink, int line) {
        TextView view = text(context, label, 14, readable(fill, ink), true);
        view.setGravity(Gravity.CENTER);
        view.setMinimumHeight(dp(context, 48));
        view.setPadding(dp(context, 8), dp(context, 10), dp(context, 8), dp(context, 10));
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22808080),
                background(fill, 12, line, 1, context), background(Color.WHITE, 12, line, 0, context)));
        return view;
    }

    private static void addAction(LinearLayout root, View view, Context context, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(context, margin);
        root.addView(view, params);
    }

    private static GradientDrawable background(int fill, int radius, int stroke, int width, Context context) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(context, radius));
        if (width > 0) shape.setStroke(dp(context, width), stroke);
        return shape;
    }

    private static int dp(Context context, float value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }

    /** All controls remain reachable on short screens: the entire content scrolls within the cap. */
    private static final class BoundedScrollView extends ScrollView {
        BoundedScrollView(Context context) { super(context); }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int cap = Math.max(1, Math.min(dp(getContext(), 640),
                    getResources().getDisplayMetrics().heightPixels - dp(getContext(), 64)));
            int mode = View.MeasureSpec.getMode(heightSpec);
            int limit = mode == View.MeasureSpec.UNSPECIFIED ? cap : Math.min(cap, View.MeasureSpec.getSize(heightSpec));
            super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(limit, View.MeasureSpec.AT_MOST));
        }
    }
}
