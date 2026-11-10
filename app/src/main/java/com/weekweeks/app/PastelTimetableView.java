package com.weekweeks.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Dependency-free, presentation-only timetable. Days are Monday=0; periods are 1-based. */
public class PastelTimetableView extends FrameLayout {
    public static class Entry {
        long id;
        int day, start, end, color;
        String name, room, time, period, teacherLabel;
    }

    /** A personal agenda item shown in the list and independent week event strip. */
    public static class EventEntry implements WeekEventLayout.Item {
        long id, endMillis;
        boolean hasEnd;
        int day, startMinutes;
        String title, time, location;
        @Override public int eventDay() { return day; }
        @Override public int eventStartMinutes() { return startMinutes; }
        @Override public long eventId() { return id; }
    }

    public interface Actions {
        void onImport();
        void onSettings();
        /** Switch between light and dark mode. */
        void onToggleTheme();
        void onAdd(int day, int period);
        /** Tap: read-only course details. */
        void onCourse(long id);
        /** Long press: open the editor. Kept separate from tap to avoid accidental edits. */
        void onEditCourse(long id);
        void onPreviousWeek();
        void onNextWeek();
        void onPickWeek();
        void onCurrentWeek();
        /** The hero countdown reached zero: a class just started or ended. Re-bind fresh data. */
        void onCountdownFinished();
        void onAddEvent();
        void onEvent(long id);
        /** 「我的」tab: personal agenda management and settings. */
        void onMine();
    }

    private final String[] DAYS = getResources().getStringArray(R.array.view_days);
    private static final Pattern CLOCK = Pattern.compile("(\\d{1,2}):(\\d{2})");
    private final boolean dark;
    private final Actions actions;
    private final int background, surface, ink, muted, line, coral;
    private final PastelAccent theme;
    private final LinearLayout content, hero, list, bottom;
    private final ScrollView scroll;
    private final TextView status, heroTitle, heroTime, heroRoom, heroPeriod, countdown;
    private long countdownTarget = -1;
    private boolean countdownUntilEnd, countdownFired;
    private final Runnable countdownTick = new Runnable() {
        @Override public void run() {
            updateCountdown();
            if (countdownTarget > 0 && isAttachedToWindow()) {
                long now = System.currentTimeMillis();
                long remaining = countdownTarget - now;
                // mm:ss below one hour: tick on each whole second. Otherwise only minutes are shown,
                // so wake up when the displayed minute changes (or when the one-hour mark is crossed).
                long delay = remaining <= 3_600_000L
                        ? 1000 - (now % 1000)
                        : Math.min(remaining % 60_000L + 1, remaining - 3_600_000L + 1);
                postDelayed(this, Math.max(50, delay));
            }
        }
    };
    private final TextView weekTitle, previous, next, current, weekSegment, listSegment, notice;
    private final TextView[] tabLabels = new TextView[3];
    private final IconView[] tabIcons = new IconView[3];
    private final WeekGrid grid;
    private final List<Entry> entries = new ArrayList<>();
    private final List<EventEntry> events = new ArrayList<>();
    private String[] dayDates;
    private long[] dayDateMillis;
    private boolean outsideSemester;
    /** Display order of weekdays (Mon=0) by actual date. */
    private final int[] dayRank = {0, 1, 2, 3, 4, 5, 6};
    private final List<String> times = new ArrayList<>();
    private boolean listMode, preview, storageLocked, actualWeek;
    private Entry featured;
    private final String HINT = s(R.string.view_hint);
    private String emptyMessage = s(R.string.view_empty_default);
    private int selectedTab;

    /** Layout-tool constructor: light theme, no host actions. */
    public PastelTimetableView(Context context) {
        this(context, false, PastelAccent.classic(false), null);
    }

    public PastelTimetableView(Context context, boolean dark, PastelAccent theme, Actions actions) {
        super(context);
        this.dark = dark;
        this.theme = theme == null ? PastelAccent.classic(dark) : theme;
        this.actions = actions;
        background = color(dark ? "#111622" : "#F7F8FE");
        surface = color(dark ? "#1C2433" : "#FFFFFF");
        ink = color(dark ? "#F1F3FB" : "#25314D");
        muted = color(dark ? "#A9B4CB" : "#8A93AA");
        line = color(dark ? "#303B50" : "#EDF0F8");
        coral = this.theme.accent;
        setBackgroundColor(background);
        setClipToPadding(true);
        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        FrameLayout.LayoutParams scrolling = new FrameLayout.LayoutParams(-1, -1);
        scrolling.bottomMargin = dp(62);
        addView(scroll, scrolling);
        content = column();
        content.setPadding(dp(14), 0, dp(14), dp(10));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        IconView cap = new IconView(context, "cap", ink);
        header.addView(cap, new LinearLayout.LayoutParams(dp(31), dp(32)));
        TextView title = text(s(R.string.view_title), 23, ink, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -1, 1);
        titleParams.leftMargin = dp(8);
        header.addView(title, titleParams);
        header.addView(iconButton("import", s(R.string.view_import_timetable), () -> { if (actions != null) actions.onImport(); }), new LinearLayout.LayoutParams(dp(44), dp(44)));
        header.addView(iconButton(dark ? "sun" : "moon", dark ? s(R.string.view_switch_light) : s(R.string.view_switch_dark),
                () -> { if (actions != null) actions.onToggleTheme(); }), new LinearLayout.LayoutParams(dp(44), dp(44)));
        header.addView(iconButton("settings", s(R.string.view_timetable_settings), () -> { if (actions != null) actions.onSettings(); }), new LinearLayout.LayoutParams(dp(44), dp(44)));
        content.addView(header, new LinearLayout.LayoutParams(-1, dp(52)));

        FrameLayout heroFrame = new FrameLayout(context);
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{this.theme.heroStart, this.theme.heroEnd});
        gradient.setCornerRadius(dp(24));
        heroFrame.setBackground(gradient);
        hero = column();
        hero.setPadding(dp(18), dp(12), dp(92), dp(10));
        heroFrame.addView(hero, new FrameLayout.LayoutParams(-1, -1));
        status = text(s(R.string.view_status_schedule), 10, color("#55451A"), true);
        status.setPadding(dp(9), dp(3), dp(9), dp(3));
        status.setBackground(round(color("#FFE6A0"), 12));
        LinearLayout statusRow = row();
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.addView(status, new LinearLayout.LayoutParams(-2, -2));
        countdown = text("", 10, Color.WHITE, true);
        countdown.setSingleLine(true);
        countdown.setPadding(dp(9), dp(3), dp(9), dp(3));
        countdown.setBackground(round(Color.argb(46, 255, 255, 255), 12));
        countdown.setVisibility(GONE);
        LinearLayout.LayoutParams cdp = new LinearLayout.LayoutParams(-2, -2);
        cdp.leftMargin = dp(6);
        statusRow.addView(countdown, cdp);
        hero.addView(statusRow, new LinearLayout.LayoutParams(-2, -2));
        heroTitle = text(s(R.string.view_hero_new_day), 19, Color.WHITE, true);
        heroTitle.setMaxLines(1);
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams ht = new LinearLayout.LayoutParams(-1, -2);
        ht.topMargin = dp(7);
        hero.addView(heroTitle, ht);
        heroTime = text(s(R.string.view_hero_add_courses), 11, this.theme.heroText, false);
        heroRoom = text("", 10, this.theme.heroText, false);
        heroPeriod = text("", 10, this.theme.heroText, false);
        heroTime.setSingleLine(true);
        heroTime.setEllipsize(TextUtils.TruncateAt.END);
        heroRoom.setSingleLine(true);
        heroRoom.setEllipsize(TextUtils.TruncateAt.END);
        heroPeriod.setSingleLine(true);
        heroTime.setCompoundDrawablesWithIntrinsicBounds(new SmallIcon("clock", this.theme.heroText), null, null, null);
        heroTime.setCompoundDrawablePadding(dp(4));
        heroRoom.setCompoundDrawablesWithIntrinsicBounds(new SmallIcon("location", this.theme.heroText), null, null, null);
        heroRoom.setCompoundDrawablePadding(dp(4));
        hero.addView(heroTime);
        LinearLayout metadata = row();
        metadata.addView(heroRoom, new LinearLayout.LayoutParams(0, -2, 1));
        metadata.addView(heroPeriod, new LinearLayout.LayoutParams(-2, -2));
        hero.addView(metadata);
        IconView book = new IconView(context, "book", this.theme.heroIcon);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(80), dp(80), Gravity.END | Gravity.CENTER_VERTICAL);
        bp.rightMargin = dp(9);
        heroFrame.addView(book, bp);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1,
                dp(112 * Math.max(1f, getResources().getConfiguration().fontScale)));
        heroParams.topMargin = dp(4);
        heroParams.bottomMargin = dp(4);
        content.addView(heroFrame, heroParams);
        heroFrame.setOnClickListener(v -> { if (featured != null) openCourse(featured); });
        heroFrame.setOnLongClickListener(v -> { if (featured == null) return false; editCourse(v, featured); return true; });
        heroFrame.setContentDescription(s(R.string.view_hero_desc));

        LinearLayout navigation = row();
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        previous = nav("‹", s(R.string.view_previous_week), () -> { if (actions != null) actions.onPreviousWeek(); });
        next = nav("›", s(R.string.view_next_week), () -> { if (actions != null) actions.onNextWeek(); });
        weekTitle = text(s(R.string.view_this_week), 13, ink, true);
        weekTitle.setGravity(Gravity.CENTER);
        weekTitle.setMaxLines(2);
        weekTitle.setEllipsize(TextUtils.TruncateAt.END);
        weekTitle.setOnClickListener(v -> { if (actions != null) actions.onPickWeek(); });
        weekTitle.setContentDescription(s(R.string.view_pick_week_desc));
        navigation.addView(previous, new LinearLayout.LayoutParams(dp(38), dp(40)));
        navigation.addView(weekTitle, new LinearLayout.LayoutParams(0, dp(40), 1));
        navigation.addView(next, new LinearLayout.LayoutParams(dp(38), dp(40)));
        current = text(s(R.string.view_this_week_short), 11, coral, true);
        current.setGravity(Gravity.CENTER);
        current.setOnClickListener(v -> { if (actions != null) actions.onCurrentWeek(); });
        current.setContentDescription(s(R.string.view_back_to_this_week));
        navigation.addView(current, new LinearLayout.LayoutParams(dp(43), dp(40)));
        content.addView(navigation, new LinearLayout.LayoutParams(-1, dp(40)));

        LinearLayout segments = row();
        segments.setPadding(dp(3), dp(3), dp(3), dp(3));
        segments.setBackground(round(this.theme.track, 13));
        weekSegment = text(s(R.string.view_segment_week), 12, Color.WHITE, true);
        listSegment = text(s(R.string.view_segment_list), 12, coral, true);
        weekSegment.setGravity(Gravity.CENTER);
        listSegment.setGravity(Gravity.CENTER);
        segments.addView(weekSegment, new LinearLayout.LayoutParams(0, -1, 1));
        segments.addView(listSegment, new LinearLayout.LayoutParams(0, -1, 1));
        weekSegment.setOnClickListener(v -> setListMode(false));
        listSegment.setOnClickListener(v -> setListMode(true));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(36));
        sp.bottomMargin = dp(9);
        content.addView(segments, sp);

        grid = new WeekGrid(context);
        content.addView(grid, new LinearLayout.LayoutParams(-1, dp(34 + 8 * 54)));
        list = column();
        list.setVisibility(GONE);
        content.addView(list, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout announcement = row();
        announcement.setGravity(Gravity.CENTER_VERTICAL);
        announcement.setPadding(dp(11), dp(9), dp(9), dp(9));
        announcement.setBackground(round(this.theme.banner, 12));
        announcement.addView(new IconView(context, "megaphone", color("#D9AC40")), new LinearLayout.LayoutParams(dp(21), dp(21)));
        notice = text(HINT, 11, dark ? muted : color("#62668B"), false);
        notice.setGravity(Gravity.CENTER_VERTICAL);
        notice.setPadding(dp(8), 0, dp(5), 0);
        announcement.addView(notice, new LinearLayout.LayoutParams(0, -2, 1));
        announcement.addView(new IconView(context, "chevron", muted), new LinearLayout.LayoutParams(dp(16), dp(20)));
        LinearLayout.LayoutParams announcementParams = new LinearLayout.LayoutParams(-1, -2);
        announcementParams.topMargin = dp(9);
        content.addView(announcement, announcementParams);

        bottom = row();
        bottom.setGravity(Gravity.CENTER);
        bottom.setBackgroundColor(surface);
        bottom.setElevation(dp(8));
        String[] labels = {s(R.string.view_tab_timetable), s(R.string.view_tab_agenda), s(R.string.view_tab_mine)};
        String[] icons = {"grid", "calendar", "profile"};
        for (int i = 0; i < labels.length; i++) {
            final int tab = i;
            LinearLayout item = column();
            item.setGravity(Gravity.CENTER);
            tabIcons[i] = new IconView(context, icons[i], i == 0 ? coral : muted);
            item.addView(tabIcons[i], new LinearLayout.LayoutParams(dp(24), dp(24)));
            tabLabels[i] = text(labels[i], 10, i == 0 ? coral : muted, i == 0);
            tabLabels[i].setGravity(Gravity.CENTER);
            item.addView(tabLabels[i]);
            item.setContentDescription(labels[i]);
            item.setOnClickListener(v -> selectTab(tab));
            bottom.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
        }
        addView(bottom, new FrameLayout.LayoutParams(-1, dp(62), Gravity.BOTTOM));
        setOnApplyWindowInsetsListener((v, insets) -> {
            int left, top, right, bottomInset;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = safe.left; top = safe.top; right = safe.right; bottomInset = safe.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft(); top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight(); bottomInset = insets.getSystemWindowInsetBottom();
            }
            setPadding(left, top, right, bottomInset);
            return insets;
        });
        updateSegments();
    }

    public void bind(List<Entry> newEntries, List<String> newTimes, String weekLabel,
                     boolean preview, boolean canPrevious, boolean canNext, Entry featured,
                     String featureStatus, String emptyMessage, boolean storageLocked) {
        bind(newEntries, newTimes, weekLabel, preview, canPrevious, canNext, featured,
                featureStatus, emptyMessage, storageLocked, false);
    }

    public void bind(List<Entry> newEntries, List<String> newTimes, String weekLabel,
                     boolean preview, boolean canPrevious, boolean canNext, Entry featured,
                     String featureStatus, String emptyMessage, boolean storageLocked, boolean outsideSemester) {
        this.outsideSemester = outsideSemester;
        entries.clear();
        if (newEntries != null) for (Entry entry : newEntries) if (entry != null) entries.add(entry);
        times.clear();
        if (newTimes != null) times.addAll(newTimes);
        this.preview = preview;
        this.featured = featured;
        this.storageLocked = storageLocked;
        this.emptyMessage = nonempty(emptyMessage, s(R.string.view_empty_default));
        String label = nonempty(weekLabel, s(R.string.view_teaching_week));
        // No date may be invented from a teaching-week number. The host marks the actual week.
        actualWeek = !preview && label.contains(s(R.string.view_this_week));
        weekTitle.setText(getContext().getString(R.string.week_title_picker, label));
        previous.setEnabled(canPrevious);
        previous.setAlpha(canPrevious ? 1f : .3f);
        next.setEnabled(canNext);
        next.setAlpha(canNext ? 1f : .3f);
        status.setText(nonempty(featureStatus, preview ? s(R.string.view_status_preview) : featured == null ? s(R.string.view_status_plan) : s(R.string.view_status_schedule)));
        if (featured == null) {
            heroTitle.setText(storageLocked ? s(R.string.view_hero_unavailable) : preview ? s(R.string.view_status_preview) : s(R.string.view_hero_free_time));
            heroTime.setText(this.emptyMessage);
            heroRoom.setText(storageLocked ? s(R.string.view_hero_check_storage)
                    : outsideSemester ? s(R.string.view_hero_outside_term) : s(R.string.view_hero_long_press_add));
            heroPeriod.setText("");
        } else {
            heroTitle.setText(nonempty(featured.name, s(R.string.view_untitled_course)));
            heroTime.setText(nonempty(featured.time, timeRange(featured)));
            heroRoom.setText(nonempty(featured.room, s(R.string.view_location_tbd)));
            heroPeriod.setText(nonempty(featured.period, featured.end > featured.start
                    ? s(R.string.view_period_range, featured.start, featured.end)
                    : s(R.string.view_period_single, featured.start)));
        }
        notice.setText(storageLocked ? s(R.string.view_notice_storage)
                : outsideSemester ? s(R.string.view_notice_outside_term)
                : preview ? s(R.string.view_notice_preview)
                : nonempty(emptyMessage, entries.isEmpty() ? this.emptyMessage : HINT));
        grid.updateHeight();
        renderedFinishedKey = finishedKey();
        grid.rebuild();
        rebuildList();
    }

    /** Refresh the time overlay; rebuild cards only when a class has just ended. */
    public void tick() {
        String key = finishedKey();
        if (!key.equals(renderedFinishedKey)) {
            renderedFinishedKey = key;
            grid.rebuild();
            rebuildList();
        } else {
            grid.invalidate();
        }
    }
    private String renderedFinishedKey = "";

    /**
     * Shows a live countdown in the hero card. {@code untilEnd} = the featured class is in progress
     * (counts down to its end); otherwise counts down to the next class start. Pass a non-positive
     * target to hide it.
     */
    public void setCountdown(long targetMillis, boolean untilEnd) {
        removeCallbacks(countdownTick);
        long target = targetMillis > 0 && targetMillis != Long.MAX_VALUE ? targetMillis : -1;
        // Re-binding the same target (e.g. boundary minute) must not re-trigger the finish callback.
        if (target != countdownTarget || untilEnd != countdownUntilEnd) countdownFired = false;
        countdownTarget = target;
        countdownUntilEnd = untilEnd;
        if (countdownTarget > 0 && isAttachedToWindow()) post(countdownTick); else updateCountdown();
    }

    private void updateCountdown() {
        if (countdownTarget <= 0 || preview || storageLocked) {
            countdown.setVisibility(GONE);
            return;
        }
        long remaining = countdownTarget - System.currentTimeMillis();
        if (remaining <= 0) {
            countdown.setText(countdownUntilEnd ? s(R.string.view_countdown_ended) : s(R.string.view_countdown_started));
            countdown.setVisibility(VISIBLE);
            if (!countdownFired) {
                countdownFired = true;
                removeCallbacks(countdownTick);
                if (actions != null) post(actions::onCountdownFinished);
            }
            return;
        }
        long seconds = (remaining + 999) / 1000;
        long days = seconds / 86400, hours = seconds % 86400 / 3600, minutes = seconds % 3600 / 60, secs = seconds % 60;
        String value;
        if (days > 0) value = s(R.string.view_countdown_days_hours, days, hours);
        else if (hours > 0) value = s(R.string.view_countdown_hours_minutes, hours, minutes);
        else value = String.format(Locale.getDefault(), "%02d:%02d", minutes, secs);
        String spokenValue = spoken(days, hours, minutes + (secs > 0 ? 1 : 0));
        int format = countdownUntilEnd ? R.string.countdown_until_end : R.string.countdown_until_start;
        countdown.setText(getContext().getString(format, value));
        // Minute granularity for screen readers: avoids an accessibility event every second.
        String description = getContext().getString(format, spokenValue);
        if (!description.contentEquals(countdown.getContentDescription() == null ? "" : countdown.getContentDescription())) {
            countdown.setContentDescription(description);
        }
        countdown.setVisibility(VISIBLE);
    }

    private String spoken(long days, long hours, long minutes) {
        if (days > 0) return s(R.string.view_spoken_pair, unit(R.plurals.view_unit_days, days), unit(R.plurals.view_unit_hours, hours));
        if (hours > 0) return s(R.string.view_spoken_pair, unit(R.plurals.view_unit_hours, hours), unit(R.plurals.view_unit_minutes, minutes));
        return unit(R.plurals.view_unit_minutes, Math.max(1, minutes));
    }

    private String unit(int plural, long value) {
        int quantity = (int) Math.min(Integer.MAX_VALUE, value);
        return getResources().getQuantityString(plural, quantity, value);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(countdownTick);
        super.onDetachedFromWindow();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        requestApplyInsets();
        removeCallbacks(countdownTick);
        if (countdownTarget > 0) post(countdownTick);
    }

    private void selectTab(int tab) {
        if (tab == 2) { setMineSelected(true); if (actions != null) actions.onMine(); return; }
        setMinePageVisible(false);
        setListMode(tab == 1);
    }

    private View minePage;

    /** Hosts the 「我的」 page as a full tab, sharing the bottom navigation with 课表 and 日程. */
    public void setMinePage(View page) {
        if (minePage != null) removeView(minePage);
        minePage = page;
        if (page == null) return;
        page.setBackgroundColor(background);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
        lp.bottomMargin = dp(62);
        addView(page, indexOfChild(bottom), lp);
        page.setVisibility(selectedTab == 2 ? VISIBLE : GONE);
    }

    public boolean isMine() { return selectedTab == 2; }

    /** Horizontal swipe: -1 = previous tab, +1 = next tab (课表 → 日程 → 我的). */
    public boolean canSwipeTab(int direction) {
        int target = selectedTab + direction;
        return target >= 0 && target <= 2;
    }

    public void swipeTab(int direction) {
        if (!canSwipeTab(direction)) return;
        selectTab(selectedTab + direction);
        View shown = selectedTab == 2 && minePage != null ? minePage : scroll;
        shown.animate().cancel();
        shown.setAlpha(0.6f);
        shown.setTranslationX(direction * dp(36));
        shown.animate().alpha(1f).translationX(0).setDuration(180).start();
    }

    private void setMinePageVisible(boolean visible) {
        if (minePage != null) minePage.setVisibility(visible ? VISIBLE : GONE);
        scroll.setVisibility(visible ? GONE : VISIBLE);
    }

    private void setListMode(boolean value) {
        listMode = value;
        selectedTab = value ? 1 : 0;
        grid.setVisibility(value ? GONE : VISIBLE);
        list.setVisibility(value ? VISIBLE : GONE);
        updateSegments();
    }

    private void updateSegments() {
        weekSegment.setBackground(listMode ? null : round(coral, 10));
        listSegment.setBackground(listMode ? round(coral, 10) : null);
        int selectedText = dark ? color("#282037") : Color.WHITE;
        weekSegment.setTextColor(listMode ? coral : selectedText);
        listSegment.setTextColor(listMode ? selectedText : coral);
        weekSegment.setCompoundDrawablesWithIntrinsicBounds(new SmallIcon("calendar", listMode ? coral : selectedText), null, null, null);
        listSegment.setCompoundDrawablesWithIntrinsicBounds(new SmallIcon("list", listMode ? selectedText : coral), null, null, null);
        // Equal inner padding keeps both compact labels centered as a single icon/text group.
        int inner = Math.max(dp(12), (getResources().getDisplayMetrics().widthPixels - dp(28)) / 4 - dp(35));
        weekSegment.setPadding(inner, 0, inner, 0);
        listSegment.setPadding(inner, 0, inner, 0);
        weekSegment.setCompoundDrawablePadding(dp(5));
        listSegment.setCompoundDrawablePadding(dp(5));
        weekSegment.setSelected(!listMode);
        listSegment.setSelected(listMode);
        for (int i = 0; i < tabLabels.length; i++) if (tabLabels[i] != null) {
            tabLabels[i].setTextColor(i == selectedTab ? coral : muted);
            tabIcons[i].tint = i == selectedTab ? coral : muted;
            tabIcons[i].invalidate();
            ((View) tabLabels[i].getParent()).setSelected(i == selectedTab);
        }
    }

    private void rebuildList() {
        list.removeAllViews();
        list.addView(addEventButton(), new LinearLayout.LayoutParams(-1, dp(44)));
        if (entries.isEmpty() && events.isEmpty()) {
            TextView empty = text(emptyMessage, 14, muted, false);
            empty.setPadding(dp(20), dp(32), dp(20), dp(32));
            empty.setGravity(Gravity.CENTER);
            empty.setBackground(round(surface, 18));
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, -2);
            ep.topMargin = dp(10);
            list.addView(empty, ep);
            // The primary add-event button is already above the empty state. Never offer
            // a semester-wide course here while browsing a holiday or another term.
            if (outsideSemester && !storageLocked) return;
            TextView add = text(storageLocked ? s(R.string.view_open_settings) : s(R.string.view_add_course), 13, coral, true);
            add.setGravity(Gravity.CENTER);
            add.setPadding(0, dp(16), 0, dp(16));
            add.setOnClickListener(v -> { if (storageLocked) { if (actions != null) actions.onSettings(); } else addCourse(today(), 1); });
            list.addView(add);
            return;
        }
        // Courses and personal events share one day-by-day agenda, ordered by start time.
        List<Object> items = new ArrayList<>(entries);
        items.addAll(events);
        Collections.sort(items, new Comparator<Object>() {
            @Override public int compare(Object a, Object b) {
                int dayOrder = Integer.compare(rank(dayOf(a)), rank(dayOf(b)));
                if (dayOrder != 0) return dayOrder;
                int timeOrder = Integer.compare(startOf(a), startOf(b));
                return timeOrder != 0 ? timeOrder : Boolean.compare(a instanceof EventEntry, b instanceof EventEntry);
            }
        });
        int lastDay = -1;
        for (Object item : items) {
            int itemDay = dayOf(item);
            if (itemDay < 0 || itemDay > 6) continue;
            if (itemDay != lastDay) {
                String date = dayDates != null && itemDay < dayDates.length && dayDates[itemDay] != null ? "  " + dayDates[itemDay] : "";
                TextView day = text(DAYS[itemDay] + date, 13, ink, true);
                day.setPadding(dp(3), dp(12), 0, dp(8));
                list.addView(day);
                lastDay = itemDay;
            }
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.bottomMargin = dp(7);
            if (item instanceof EventEntry) {
                list.addView(eventCard((EventEntry) item), cp);
                continue;
            }
            Entry entry = (Entry) item;
            LinearLayout card = column();
            card.setPadding(dp(15), dp(13), dp(15), dp(13));
            card.setBackground(round(courseColor(entry), 15));
            card.addView(text(nonempty(entry.name, s(R.string.view_untitled_course)), 15, courseInk(entry), true));
            TextView detail = text(nonempty(entry.time, timeRange(entry)) + "  ·  " + nonempty(entry.room, s(R.string.view_location_tbd))
                    + (isFinished(entry) ? "  ·  " + s(R.string.view_finished) : ""), 12, courseInk(entry), false);
            detail.setPadding(0, dp(5), 0, 0);
            card.addView(detail);
            if (!TextUtils.isEmpty(entry.teacherLabel)) {
                TextView teacher = text(entry.teacherLabel, 12, courseInk(entry), false);
                teacher.setPadding(0, dp(4), 0, 0);
                card.addView(teacher);
            }
            card.setOnClickListener(v -> openCourse(entry));
            card.setOnLongClickListener(v -> { editCourse(v, entry); return true; });
            card.setContentDescription(description(entry));
            list.addView(card, cp);
        }
    }

    private View addEventButton() {
        TextView add = text(s(R.string.view_add_event_button), 14, coral, true);
        add.setGravity(Gravity.CENTER);
        add.setBackground(round(theme.tint, 14));
        add.setContentDescription(s(R.string.view_add_event));
        add.setOnClickListener(v -> {
            if (storageLocked) Toast.makeText(getContext(), s(R.string.view_toast_storage), Toast.LENGTH_SHORT).show();
            else if (actions != null) actions.onAddEvent();
        });
        return add;
    }

    private View eventCard(EventEntry event) {
        boolean past = System.currentTimeMillis() >= event.endMillis;
        int textColor = past ? (dark ? color("#7F8AA3") : color("#8A93AA")) : ink;
        LinearLayout card = row();
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(12), dp(15), dp(12));
        GradientDrawable bg = round(past ? (dark ? color("#1A2030") : color("#ECEEF3")) : surface, 15);
        bg.setStroke(dp(1), past ? line : PastelTimetableView.withAlpha(coral, 120));
        card.setBackground(bg);
        TextView badge = text(s(R.string.view_event_badge), 10, past ? textColor : coral, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(7), dp(3), dp(7), dp(3));
        badge.setBackground(round(past ? line : theme.tint, 8));
        card.addView(badge, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout texts = column();
        texts.setPadding(dp(10), 0, 0, 0);
        TextView title = text(nonempty(event.title, s(R.string.view_untitled_event)), 15, textColor, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title);
        String where = event.location == null || event.location.trim().isEmpty() ? "" : "  ·  " + event.location;
        TextView detail = text(nonempty(event.time, "") + where + (past ? "  ·  " + (event.hasEnd ? s(R.string.view_event_ended) : s(R.string.view_event_due)) : ""), 12, past ? textColor : muted, false);
        detail.setPadding(0, dp(5), 0, 0);
        detail.setSingleLine(true);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(detail);
        card.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        card.setContentDescription(s(R.string.view_event_desc, nonempty(event.title, s(R.string.view_untitled_event)),
                DAYS[Math.max(0, Math.min(6, event.day))], nonempty(event.time, ""),
                (where.isEmpty() ? "" : s(R.string.view_desc_suffix, event.location))
                        + (past ? s(R.string.view_desc_suffix, event.hasEnd ? s(R.string.view_event_ended) : s(R.string.view_event_due)) : "")));
        card.setOnClickListener(v -> { if (actions != null) actions.onEvent(event.id); });
        return card;
    }

    private int rank(int day) { return day >= 0 && day < 7 ? dayRank[day] : day; }

    private int dayOf(Object item) { return item instanceof EventEntry ? ((EventEntry) item).day : ((Entry) item).day; }

    private int startOf(Object item) {
        if (item instanceof EventEntry) return ((EventEntry) item).startMinutes;
        Entry e = (Entry) item;
        if (e.start < 1 || e.start > times.size()) return 24 * 60;
        Matcher m = CLOCK.matcher(times.get(e.start - 1) == null ? "" : times.get(e.start - 1));
        if (!m.find()) return 24 * 60;
        int h = Integer.parseInt(m.group(1)), min = Integer.parseInt(m.group(2));
        return h > 23 || min > 59 ? 24 * 60 : h * 60 + min;
    }

    private static int withAlpha(int c, int alpha) { return Color.argb(alpha, Color.red(c), Color.green(c), Color.blue(c)); }

    /** Personal events of the displayed week; {@code dates} labels each weekday (Mon=0), may be null. */
    public void setEvents(List<EventEntry> weekEvents, String[] dates, long[] dateMillis) {
        if (!java.util.Arrays.equals(dayDateMillis, dateMillis)) grid.collapseEvents();
        events.clear();
        if (weekEvents != null) for (EventEntry e : weekEvents) if (e != null) events.add(e);
        dayDates = dates == null ? null : dates.clone();
        dayDateMillis = dateMillis != null && dateMillis.length == 7 ? dateMillis.clone() : null;
        for (int d = 0; d < 7; d++) {
            int r = 0;
            if (dateMillis != null && dateMillis.length == 7) for (int o = 0; o < 7; o++) if (dateMillis[o] < dateMillis[d]) r++;
            dayRank[d] = dateMillis != null && dateMillis.length == 7 ? r : d;
        }
        renderedFinishedKey = finishedKey();
        grid.rebuild();
        rebuildList();
    }

    /** Opens the 日程 list, e.g. right after adding an event so the user sees where it went. */
    public void showAgenda() { setMinePageVisible(false); setListMode(true); }
    public boolean isAgenda() { return listMode; }
    public int scrollPosition() { return scroll.getScrollY(); }
    public void restoreUiState(boolean agenda, int y) {
        setListMode(agenda);
        scroll.post(() -> scroll.scrollTo(0, Math.max(0, y)));
    }
    public void setMineSelected(boolean selected) {
        selectedTab = selected ? 2 : listMode ? 1 : 0;
        setMinePageVisible(selected);
        updateSegments();
    }
    private void addCourse(int day, int period) {
        if (storageLocked) {
            Toast.makeText(getContext(), s(R.string.view_toast_storage), Toast.LENGTH_SHORT).show();
        } else if (outsideSemester) {
            showAgenda();
            if (actions != null) actions.onAddEvent();
        } else if (actions != null) actions.onAdd(day, period);
    }

    private void openCourse(Entry entry) { if (actions != null) actions.onCourse(entry.id); }
    private void editCourse(View source, Entry entry) {
        source.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        if (storageLocked) {
            Toast.makeText(getContext(), s(R.string.view_toast_storage), Toast.LENGTH_SHORT).show();
        } else if (actions != null) actions.onEditCourse(entry.id);
    }
    private long lastHintAt;
    private void hintLongPress() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastHintAt < 2500) return;
        lastHintAt = now;
        if (outsideSemester) {
            showAgenda();
            scroll.post(() -> scroll.smoothScrollTo(0, 0));
        }
        Toast.makeText(getContext(), outsideSemester ? s(R.string.view_toast_outside_term) : s(R.string.view_toast_long_press_add),
                Toast.LENGTH_SHORT).show();
    }
    private String description(Entry e) {
        return s(R.string.view_course_desc, nonempty(e.name, s(R.string.view_untitled_course)),
                DAYS[Math.max(0, Math.min(6, e.day))], e.start, e.end,
                nonempty(e.room, s(R.string.view_location_tbd)), nonempty(e.time, timeRange(e)))
                + (TextUtils.isEmpty(e.teacherLabel) ? "" : s(R.string.view_desc_suffix, e.teacherLabel))
                + (isFinished(e) ? s(R.string.view_desc_suffix, s(R.string.view_finished)) : "");
    }
    private String timeRange(Entry e) {
        if (e.start < 1 || e.end < e.start || e.end > times.size()) return s(R.string.view_time_unset);
        String first = times.get(e.start - 1), last = times.get(e.end - 1);
        Matcher a = CLOCK.matcher(first == null ? "" : first), b = CLOCK.matcher(last == null ? "" : last);
        String start = a.find() ? a.group() : "";
        String end = "";
        while (b.find()) end = b.group();
        return start.isEmpty() || end.isEmpty() ? s(R.string.view_time_unset) : start + "–" + end;
    }

    private int courseColor(Entry entry) {
        int[] fallback = {0xFFDDEEE7, 0xFFE2E9FC, 0xFFF8E5D8, 0xFFEDE1F6, 0xFFF9E3E5};
        int paletteIndex = (int) ((entry.id % fallback.length + fallback.length) % fallback.length);
        int c = entry.color == 0 ? fallback[paletteIndex] : entry.color;
        int surfaceColor = PastelCourseSheet.pastelSurface(c, dark);
        if (!isFinished(entry)) return surfaceColor;
        return CoursePalette.finishedSurface(surfaceColor, dark);
    }
    private int courseInk(Entry entry) {
        // Completion styling has priority over the selected palette, including custom muted shades.
        if (isFinished(entry)) return CoursePalette.finishedInk(courseColor(entry), dark);
        return CoursePalette.activeInk(courseColor(entry), dark);
    }
    /** Use the displayed dates, so past/future previews follow the same rule as this week. */
    private boolean isFinished(Entry e) {
        if (storageLocked || e.day < 0 || e.day > 6) return false;
        Calendar now = Calendar.getInstance();
        if (dayDateMillis != null) {
            Calendar courseDay = Calendar.getInstance();
            courseDay.setTimeInMillis(dayDateMillis[e.day]);
            courseDay.set(Calendar.HOUR_OF_DAY, 0);
            courseDay.set(Calendar.MINUTE, 0);
            courseDay.set(Calendar.SECOND, 0);
            courseDay.set(Calendar.MILLISECOND, 0);
            Calendar today = (Calendar) now.clone();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);
            if (courseDay.before(today)) return true;
            if (courseDay.after(today)) return false;
        } else {
            // Legacy/layout callers without actual dates must not invent preview dates.
            if (!actualWeek || preview) return false;
            int today = rank(today());
            if (rank(e.day) < today) return true;
            if (rank(e.day) > today) return false;
        }
        int end = endMinute(e);
        return end >= 0 && now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE) >= end;
    }
    private int endMinute(Entry e) {
        if (e.end < 1 || e.end > times.size()) return -1;
        String interval = times.get(e.end - 1);
        Matcher m = CLOCK.matcher(interval == null ? "" : interval);
        int last = -1;
        while (m.find()) {
            int h = Integer.parseInt(m.group(1)), min = Integer.parseInt(m.group(2));
            last = h > 23 || min > 59 ? -1 : h * 60 + min;
        }
        return last;
    }
    private String finishedKey() {
        StringBuilder key = new StringBuilder();
        for (Entry e : entries) if (isFinished(e)) key.append(e.id).append(',');
        long now = System.currentTimeMillis();
        for (EventEntry e : events) if (now >= e.endMillis) key.append('e').append(e.id).append(',');
        return key.toString();
    }
    private int today() { return (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7; }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private int color(String value) { return Color.parseColor(value); }
    private String s(int id, Object... args) { return args.length == 0 ? getContext().getString(id) : getContext().getString(id, args); }
    private static String nonempty(String s, String fallback) { return s == null || s.trim().isEmpty() ? fallback : s; }
    private LinearLayout column() { LinearLayout l = new LinearLayout(getContext()); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(getContext()); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(getContext());
        t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setGravity(Gravity.CENTER_VERTICAL);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private GradientDrawable round(int c, float radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(c); d.setCornerRadius(dp(radius)); return d;
    }
    private View iconButton(String kind, String label, Runnable action) {
        IconView v = new IconView(getContext(), kind, ink);
        v.setPadding(dp(11), dp(11), dp(11), dp(11));
        GradientDrawable buttonBackground = round(theme.tint, 16);
        v.setBackground(new android.graphics.drawable.InsetDrawable(buttonBackground, dp(3), dp(3), dp(3), dp(3)));
        v.setContentDescription(label); v.setOnClickListener(w -> action.run()); return v;
    }
    private TextView nav(String label, String description, Runnable action) {
        TextView t = text(label, 27, ink, false); t.setGravity(Gravity.CENTER);
        t.setContentDescription(description); t.setOnClickListener(v -> action.run()); return t;
    }

    private final class WeekGrid extends FrameLayout {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF clip = new RectF();
        private float left, columnWidth;
        private final float textScale = Math.max(1f, getResources().getConfiguration().fontScale);
        private final int heading = dp(34 * textScale), rowHeight = dp(54 * textScale);
        private int builtWidth = -1;
        private final boolean[] expandedEvents = new boolean[7];
        private final int eventCardHeight = dp(58 * textScale);
        private final int eventControlHeight = dp(44 * textScale);
        private int eventStripHeight;
        private int courseTop() { return heading + eventStripHeight; }
        void collapseEvents() { java.util.Arrays.fill(expandedEvents, false); }
        void updateHeight() {
            int rowsHeight = 0;
            for (int day = 0; day < 7; day++) {
                int count = WeekEventLayout.forDay(events, day).size();
                int height = WeekEventLayout.visibleCount(count, expandedEvents[day]) * eventCardHeight;
                if (count > WeekEventLayout.PREVIEW_COUNT) height += eventControlHeight;
                rowsHeight = Math.max(rowsHeight, height);
            }
            eventStripHeight = rowsHeight == 0 ? 0 : rowsHeight + dp(4);
            if (getLayoutParams() != null) {
                int height = courseTop() + Math.max(1, times.size()) * rowHeight;
                if (getLayoutParams().height != height) {
                    getLayoutParams().height = height;
                    requestLayout();
                }
            }
        }
        WeekGrid(Context context) {
            super(context);
            setBackground(round(surface, 18));
            setClipToOutline(true);
            setWillNotDraw(false);
        }
        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            // Children added during a layout pass are never measured; rebuild on the next frame.
            if (w != builtWidth) post(() -> { if (getWidth() != builtWidth) rebuild(); });
        }
        void rebuild() {
            updateHeight();
            if (getWidth() == 0) return;
            builtWidth = getWidth();
            removeAllViews();
            left = dp(35);
            columnWidth = (getWidth() - left) / 7f;
            TextView periodHeading = text(s(R.string.view_period_heading), 9, muted, false);
            periodHeading.setGravity(Gravity.CENTER);
            place(periodHeading, 0, 0, Math.round(left), heading);
            for (int day = 0; day < 7; day++) {
                String dayLabel = DAYS[day];
                if (dayDateMillis != null && dayDateMillis[day] > 0) {
                    Calendar date = Calendar.getInstance();
                    date.setTimeInMillis(dayDateMillis[day]);
                    dayLabel += "\n" + (date.get(Calendar.MONTH) + 1) + "/" + date.get(Calendar.DAY_OF_MONTH);
                }
                TextView label = text(dayLabel, 10, actualWeek && day == today() ? coral : ink, actualWeek && day == today());
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(2);
                label.setEllipsize(TextUtils.TruncateAt.END);
                place(label, Math.round(left + day * columnWidth), 0,
                        Math.round(left + (day + 1) * columnWidth) - Math.round(left + day * columnWidth), heading);
            }
            buildEventStrip();
            for (int p = 0; p < times.size(); p++) {
                LinearLayout ruler = column(); ruler.setGravity(Gravity.CENTER);
                TextView number = text(String.valueOf(p + 1), 11, muted, true); number.setGravity(Gravity.CENTER);
                ruler.addView(number);
                String interval = times.get(p);
                Matcher m = CLOCK.matcher(interval == null ? "" : interval);
                while (m.find()) {
                    TextView clock = text(m.group(), 7, muted, false); clock.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams c = new LinearLayout.LayoutParams(-1, -2); c.topMargin = dp(2); ruler.addView(clock, c);
                }
                ruler.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                place(ruler, 0, courseTop() + p * rowHeight, Math.round(left), rowHeight);
                for (int day = 0; day < 7; day++) {
                    final int d = day, period = p + 1;
                    View empty = new View(getContext());
                    empty.setContentDescription(outsideSemester
                            ? s(R.string.view_cell_outside_term_desc, DAYS[day])
                            : s(R.string.view_cell_desc, DAYS[day], period));
                    empty.setOnClickListener(v -> hintLongPress());
                    empty.setOnLongClickListener(v -> {
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                        addCourse(d, period);
                        return true;
                    });
                    for (Entry occupied : entries) {
                        if (occupied.day == day && occupied.start <= period && occupied.end >= period) {
                            empty.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                            empty.setFocusable(false);
                            break;
                        }
                    }
                    int x = Math.round(left + day * columnWidth);
                    place(empty, x, courseTop() + p * rowHeight,
                            Math.round(left + (day + 1) * columnWidth) - x, rowHeight);
                }
            }
            for (Entry entry : entries) {
                if (entry.day < 0 || entry.day > 6 || entry.start < 1 || entry.start > times.size() || entry.end < entry.start) continue;
                int end = Math.min(entry.end, times.size());
                LinearLayout card = column(); card.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
                card.setPadding(dp(2), dp(5), dp(2), dp(3));
                card.setBackground(round(courseColor(entry), 10));
                int cardHeight = (end - entry.start + 1) * rowHeight - dp(4);
                boolean showTeacher = !TextUtils.isEmpty(entry.teacherLabel);
                TextView name = text(nonempty(entry.name, s(R.string.view_untitled_course)), 10.5f, courseInk(entry), true);
                name.setGravity(Gravity.CENTER);
                if (showTeacher) name.setIncludeFontPadding(false);
                name.setMaxLines(Math.max(1, Math.min(8, (cardHeight - dp((showTeacher ? 38 : 20) * textScale)) / dp(14 * textScale))));
                name.setEllipsize(TextUtils.TruncateAt.END);
                name.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
                card.addView(name, new LinearLayout.LayoutParams(-1, -2));
                TextView room = text(nonempty(entry.room, s(R.string.view_room_tbd_short)), 8.5f, courseInk(entry), false);
                room.setGravity(Gravity.CENTER); room.setMaxLines(2); room.setEllipsize(TextUtils.TruncateAt.END);
                if (showTeacher) {
                    room.setIncludeFontPadding(false);
                    if (end == entry.start) room.setMaxLines(1);
                }
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2); rp.topMargin = dp(3); card.addView(room, rp);
                if (showTeacher) {
                    TextView teacher = text(entry.teacherLabel, 8.5f, courseInk(entry), false);
                    teacher.setGravity(Gravity.CENTER);
                    teacher.setIncludeFontPadding(false);
                    teacher.setSingleLine(true);
                    teacher.setEllipsize(TextUtils.TruncateAt.END);
                    teacher.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                    LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
                    tp.topMargin = dp(2);
                    card.addView(teacher, tp);
                }
                card.setContentDescription(description(entry));
                card.setOnClickListener(v -> openCourse(entry));
                card.setOnLongClickListener(v -> { editCourse(v, entry); return true; });
                card.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
                name.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                room.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                int x = Math.round(left + entry.day * columnWidth) + dp(2);
                place(card, x, courseTop() + (entry.start - 1) * rowHeight + dp(2),
                        Math.max(1, Math.round(left + (entry.day + 1) * columnWidth) - x - dp(2)), cardHeight);
            }
            invalidate();
        }
        private void buildEventStrip() {
            if (eventStripHeight == 0) return;
            TextView label = text(s(R.string.week_events_label), 8, muted, true);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(3);
            place(label, 0, heading, Math.round(left), Math.min(eventStripHeight, eventCardHeight));
            long now = System.currentTimeMillis();
            for (int day = 0; day < 7; day++) {
                final int selectedDay = day;
                List<EventEntry> dayEvents = WeekEventLayout.forDay(events, day);
                int count = WeekEventLayout.visibleCount(dayEvents.size(), expandedEvents[day]);
                // Use exactly the same rounded column boundaries as the course grid.
                int x = Math.round(left + day * columnWidth) + dp(2);
                int width = Math.max(1, Math.round(left + (day + 1) * columnWidth) - x - dp(2));
                for (int index = 0; index < count; index++) {
                    EventEntry event = dayEvents.get(index);
                    boolean past = now >= event.endMillis;
                    LinearLayout card = column();
                    card.setPadding(dp(3), dp(4), dp(3), dp(3));
                    card.setBackground(round(past ? withAlpha(theme.tint, 120) : theme.tint, 7));
                    String clock = String.format(Locale.getDefault(), "%02d:%02d",
                            event.startMinutes / 60, event.startMinutes % 60);
                    TextView time = text(clock, 8, past ? muted : coral, true);
                    time.setSingleLine(true);
                    time.setEllipsize(TextUtils.TruncateAt.END);
                    card.addView(time, new LinearLayout.LayoutParams(-1, -2));
                    TextView title = text(nonempty(event.title, s(R.string.view_untitled_event)), 9, past ? muted : ink, true);
                    title.setMaxLines(2);
                    title.setEllipsize(TextUtils.TruncateAt.END);
                    card.addView(title, new LinearLayout.LayoutParams(-1, -2));
                    card.setContentDescription(s(R.string.week_event_description, DAYS[day],
                            nonempty(event.time, clock), nonempty(event.title, s(R.string.view_untitled_event))));
                    time.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                    title.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                    card.setOnClickListener(v -> { if (actions != null) actions.onEvent(event.id); });
                    place(card, x, heading + index * eventCardHeight + dp(2), width, eventCardHeight - dp(4));
                }
                if (dayEvents.size() > WeekEventLayout.PREVIEW_COUNT) {
                    int hidden = WeekEventLayout.hiddenCount(dayEvents.size(), expandedEvents[day]);
                    TextView more = text(expandedEvents[day] ? s(R.string.week_events_collapse)
                            : s(R.string.week_events_more, hidden), 9, coral, true);
                    more.setGravity(Gravity.CENTER);
                    more.setMaxLines(3);
                    more.setEllipsize(TextUtils.TruncateAt.END);
                    more.setContentDescription(s(R.string.week_events_toggle_description, DAYS[day], more.getText()));
                    more.setBackground(round(theme.track, 7));
                    more.setOnClickListener(v -> {
                        expandedEvents[selectedDay] = !expandedEvents[selectedDay];
                        rebuild();
                    });
                    place(more, x, heading + count * eventCardHeight + dp(2), width, eventControlHeight - dp(4));
                }
            }
        }
        private void place(View view, int x, int y, int width, int height) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height); lp.leftMargin = x; lp.topMargin = y; addView(view, lp);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setColor(line); paint.setStrokeWidth(dp(1));
            if (eventStripHeight > 0) canvas.drawLine(0, heading, getWidth(), heading, paint);
            for (int day = 0; day < 7; day++) canvas.drawLine(left + day * columnWidth, heading, left + day * columnWidth, getHeight(), paint);
            for (int p = 0; p <= times.size(); p++) canvas.drawLine(0, courseTop() + p * rowHeight, getWidth(), courseTop() + p * rowHeight, paint);
        }
        @Override protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (!actualWeek || preview || storageLocked) return;
            Calendar now = Calendar.getInstance();
            float minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE) + now.get(Calendar.SECOND) / 60f;
            float y = -1;
            int previousEnd = -1;
            for (int p = 0; p < times.size(); p++) {
                Matcher m = CLOCK.matcher(times.get(p) == null ? "" : times.get(p));
                if (!m.find()) { previousEnd = -1; continue; }
                int start = minuteOf(m);
                if (!m.find()) { previousEnd = -1; continue; }
                int end = minuteOf(m);
                if (start < 0 || end <= start) { previousEnd = -1; continue; }
                // Breaks between periods (课间、午休、晚饭) pin the line to the row boundary.
                if (previousEnd >= 0 && minute >= previousEnd && minute < start) { y = courseTop() + p * rowHeight; break; }
                if (minute >= start && minute < end) { y = courseTop() + (p + (minute - start) / (end - start)) * rowHeight; break; }
                previousEnd = end;
            }
            if (y >= 0) {
                int save = canvas.save();
                clip.set(0, courseTop(), getWidth(), getHeight());
                canvas.clipRect(clip);
                paint.setColor(coral); paint.setStrokeWidth(dp(1.5f));
                canvas.drawLine(left, y, getWidth(), y, paint);
                String clock = s(R.string.view_now_clock, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
                float clockTextSize = android.util.TypedValue.applyDimension(
                        android.util.TypedValue.COMPLEX_UNIT_SP, 8f, getResources().getDisplayMetrics());
                paint.setTextSize(clockTextSize);
                paint.setTypeface(AppFonts.typeface(getContext(), Typeface.BOLD));
                float labelScale = clockTextSize / (8f * getResources().getDisplayMetrics().density);
                float paddingX = dp(5) * labelScale;
                float paddingY = dp(3) * labelScale;
                Paint.FontMetrics metrics = paint.getFontMetrics();
                float labelHeight = metrics.bottom - metrics.top + paddingY * 2;
                float w = paint.measureText(clock) + paddingX * 2;
                float labelTop = Math.max(heading, Math.min(getHeight() - labelHeight, y - labelHeight / 2));
                float radius = labelHeight / 2;
                canvas.drawRoundRect(new RectF(0, labelTop, w, labelTop + labelHeight), radius, radius, paint);
                paint.setColor(Color.WHITE);
                canvas.drawText(clock, paddingX, labelTop + paddingY - metrics.top, paint);
                canvas.restoreToCount(save);
            }
        }
        private int minuteOf(Matcher m) {
            int h = Integer.parseInt(m.group(1)), min = Integer.parseInt(m.group(2));
            return h > 23 || min > 59 ? -1 : h * 60 + min;
        }
    }

    /** Canvas-backed compound drawable; uses the same vector paths as the action views. */
    private final class SmallIcon extends Drawable {
        private final IconView icon;
        private int alpha = 255;
        SmallIcon(String kind, int tint) {
            icon = new IconView(getContext(), kind, tint);
            icon.layout(0, 0, dp(14), dp(14));
            setBounds(0, 0, dp(14), dp(14));
        }
        @Override public int getIntrinsicWidth() { return dp(14); }
        @Override public int getIntrinsicHeight() { return dp(14); }
        @Override public void draw(Canvas canvas) {
            int save = canvas.save();
            canvas.translate(getBounds().left, getBounds().top);
            canvas.scale(getBounds().width() / (float) dp(14), getBounds().height() / (float) dp(14));
            int layer = canvas.saveLayerAlpha(0, 0, dp(14), dp(14), alpha);
            icon.draw(canvas);
            canvas.restoreToCount(layer);
            canvas.restoreToCount(save);
        }
        @Override public void setAlpha(int value) { alpha = value; invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { icon.p.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** Tiny vector icon set, drawn without fonts, bitmaps, resources, or external libraries. */
    private final class IconView extends View {
        final String kind;
        int tint;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();
        final RectF gearRoot = new RectF();
        IconView(Context context, String kind, int tint) { super(context); this.kind = kind; this.tint = tint; }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int save = canvas.save();
            float width = getWidth() - getPaddingLeft() - getPaddingRight();
            float height = getHeight() - getPaddingTop() - getPaddingBottom();
            float scale = Math.min(width, height) / 24f;
            canvas.translate(getPaddingLeft() + (width - scale * 24) / 2, getPaddingTop() + (height - scale * 24) / 2);
            canvas.scale(scale, scale);
            p.setColor(tint); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.65f);
            p.setStrokeJoin(Paint.Join.ROUND); p.setStrokeCap(Paint.Cap.ROUND);
            path.reset();
            switch (kind) {
                case "clock":
                    canvas.drawCircle(12, 12, 9, p);
                    path.moveTo(12, 6); path.lineTo(12, 12); path.lineTo(16, 14); canvas.drawPath(path, p); break;
                case "location":
                    path.moveTo(12, 22); path.cubicTo(9, 18, 4, 13, 4, 9); path.cubicTo(4, -1, 20, -1, 20, 9); path.cubicTo(20, 13, 15, 18, 12, 22); path.close(); canvas.drawPath(path, p);
                    canvas.drawCircle(12, 9, 3, p); break;
                case "list":
                    for (int y = 6; y <= 18; y += 6) { canvas.drawCircle(4, y, .7f, p); canvas.drawLine(9, y, 21, y, p); } break;
                case "megaphone":
                    path.moveTo(3, 9); path.lineTo(9, 9); path.lineTo(20, 4); path.lineTo(20, 19); path.lineTo(9, 14); path.lineTo(3, 14); path.close(); canvas.drawPath(path, p);
                    path.reset(); path.moveTo(7, 14); path.lineTo(9, 21); path.lineTo(13, 21); path.lineTo(11, 15); canvas.drawPath(path, p); break;
                case "chevron":
                    path.moveTo(9, 5); path.lineTo(16, 12); path.lineTo(9, 19); canvas.drawPath(path, p); break;
                case "cap":
                    path.moveTo(2, 8); path.lineTo(12, 3); path.lineTo(22, 8); path.lineTo(12, 13); path.close(); canvas.drawPath(path, p);
                    path.reset(); path.moveTo(6, 11); path.lineTo(6, 16); path.quadTo(12, 21, 18, 16); path.lineTo(18, 11); canvas.drawPath(path, p);
                    canvas.drawLine(22, 8, 22, 16, p); break;
                case "import":
                    // Document with a folded corner and a downward arrow: "import a file".
                    path.moveTo(13.5f, 3); path.lineTo(7.5f, 3); path.quadTo(5, 3, 5, 5.5f);
                    path.lineTo(5, 18.5f); path.quadTo(5, 21, 7.5f, 21);
                    path.lineTo(16.5f, 21); path.quadTo(19, 21, 19, 18.5f);
                    path.lineTo(19, 8.5f); path.close();
                    canvas.drawPath(path, p);
                    path.reset(); path.moveTo(13.5f, 3); path.lineTo(13.5f, 7); path.quadTo(13.5f, 8.5f, 15, 8.5f); path.lineTo(19, 8.5f);
                    canvas.drawPath(path, p);
                    canvas.drawLine(12, 10.5f, 12, 17, p);
                    path.reset(); path.moveTo(9, 14.2f); path.lineTo(12, 17.2f); path.lineTo(15, 14.2f);
                    canvas.drawPath(path, p); break;
                case "settings": {
                    // Gear: 8 tapered teeth joined to the body, with a centre hub.
                    final float outer = 9.6f, root = 7.1f;
                    for (int i = 0; i < 8; i++) {
                        double a = Math.toRadians(i * 45 - 90);
                        double[] angles = {a - Math.toRadians(15), a - Math.toRadians(8.5), a + Math.toRadians(8.5), a + Math.toRadians(15)};
                        float[] radii = {root, outer, outer, root};
                        for (int k = 0; k < 4; k++) {
                            float x = 12 + (float) Math.cos(angles[k]) * radii[k];
                            float y = 12 + (float) Math.sin(angles[k]) * radii[k];
                            if (i == 0 && k == 0) path.moveTo(x, y); else path.lineTo(x, y);
                        }
                        gearRoot.set(12 - root, 12 - root, 12 + root, 12 + root);
                        path.arcTo(gearRoot, (float) Math.toDegrees(a + Math.toRadians(15)), 15, false);
                    }
                    path.close();
                    canvas.drawPath(path, p);
                    canvas.drawCircle(12, 12, 3f, p);
                    break;
                }
                case "sun":
                    canvas.drawCircle(12, 12, 4.2f, p);
                    for (int i = 0; i < 8; i++) { canvas.save(); canvas.rotate(i * 45, 12, 12); canvas.drawLine(12, 2.5f, 12, 4.8f, p); canvas.restore(); } break;
                case "moon":
                    path.moveTo(20, 14.5f); path.cubicTo(17, 20.5f, 8, 21, 4.5f, 15.5f); path.cubicTo(1.5f, 10.5f, 4.5f, 4, 10.5f, 3.2f);
                    path.cubicTo(7.5f, 8.5f, 12.5f, 16.5f, 20, 14.5f); path.close(); canvas.drawPath(path, p); break;
                case "grid":
                    canvas.drawRoundRect(3, 3, 21, 21, 3, 3, p); canvas.drawLine(3, 9, 21, 9, p); canvas.drawLine(3, 15, 21, 15, p);
                    canvas.drawLine(9, 3, 9, 21, p); canvas.drawLine(15, 3, 15, 21, p); break;
                case "calendar":
                    canvas.drawRoundRect(3, 5, 21, 21, 3, 3, p); canvas.drawLine(7, 3, 7, 7, p); canvas.drawLine(17, 3, 17, 7, p);
                    canvas.drawLine(3, 10, 21, 10, p); path.moveTo(8, 15); path.lineTo(11, 18); path.lineTo(17, 13); canvas.drawPath(path, p); break;
                case "profile":
                    canvas.drawCircle(12, 7, 4, p); canvas.drawArc(4, 13, 20, 26, 190, 160, false, p); break;
                case "book":
                    canvas.rotate(-10, 12, 12);
                    p.setStyle(Paint.Style.FILL); p.setColor(theme.bookFill);
                    path.moveTo(2, 7); path.quadTo(7, 5, 12, 8); path.quadTo(17, 5, 22, 7); path.lineTo(22, 20); path.quadTo(17, 18, 12, 21); path.quadTo(7, 18, 2, 20); path.close(); canvas.drawPath(path, p);
                    p.setColor(tint); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(.7f);
                    path.reset(); path.moveTo(2, 5); path.quadTo(7, 3, 12, 6); path.quadTo(17, 3, 22, 5); path.lineTo(22, 18); path.quadTo(17, 16, 12, 19); path.quadTo(7, 16, 2, 18); path.close(); canvas.drawPath(path, p);
                    canvas.drawLine(12, 6, 12, 19, p);
                    for (int j = 0; j < 3; j++) { canvas.drawLine(4, 8 + j * 3, 9, 9 + j * 3, p); canvas.drawLine(15, 9 + j * 3, 20, 8 + j * 3, p); }
                    break;
                default: break;
            }
            canvas.restoreToCount(save);
        }
    }
}
