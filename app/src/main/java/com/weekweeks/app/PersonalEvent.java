package com.weekweeks.app;

import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A one-off personal agenda item (e.g. "9.24 10:00 去图书馆"). Pure Java so the rules are unit-testable.
 * Dates are stored as calendar fields, times as minutes after midnight, both in the device time zone.
 */
final class PersonalEvent {
    static final int MAX_EVENTS = 300;
    static final int MAX_TITLE = 60;
    static final int MAX_LOCATION = 60;
    static final int MAX_NOTE = 200;
    static final int NO_END = -1;
    static final int NO_REMINDER = -1;
    /** Longest supported advance notice: one week. */
    static final int MAX_LEAD_MINUTES = 7 * 24 * 60;

    long id;
    int year, month, day; // month is 1-12
    int startMinutes;
    int endMinutes = NO_END;
    int remindMinutes = NO_REMINDER;
    String title = "", location = "", note = "";

    private static final Pattern TIME = Pattern.compile(
            "^(\\d{1,2})(?:\\s*(?:[:：.点时])\\s*(\\d{1,2})?\\s*分?)?$");
    private static final Pattern COMPACT_TIME = Pattern.compile("^(\\d{1,2})(\\d{2})$");

    PersonalEvent copy() {
        PersonalEvent e = new PersonalEvent();
        e.id = id; e.year = year; e.month = month; e.day = day;
        e.startMinutes = startMinutes; e.endMinutes = endMinutes; e.remindMinutes = remindMinutes;
        e.title = title; e.location = location; e.note = note;
        return e;
    }

    long dayMillis() {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month - 1, day, 0, 0, 0);
        return c.getTimeInMillis();
    }

    long startMillis() { return atMinutes(startMinutes); }

    /** Events without an end time count as over once they start. */
    long endMillis() { return atMinutes(endMinutes >= 0 ? endMinutes : startMinutes); }

    boolean isPast(long now) { return now >= endMillis(); }

    /** Notification instant, or -1 when no reminder is wanted. */
    long triggerAtMillis() {
        if (remindMinutes < 0) return -1L;
        return startMillis() - remindMinutes * 60_000L;
    }

    /** A configured reminder cannot be scheduled at or before the current instant. */
    boolean reminderExpired(long now) {
        return remindMinutes != NO_REMINDER && triggerAtMillis() <= now;
    }

    /** True when the event's date lies in [fromDayMillis, fromDayMillis + days). */
    boolean onDaysFrom(long fromDayMillis, int days) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(fromDayMillis);
        c.add(Calendar.DAY_OF_YEAR, days);
        long mine = dayMillis();
        return mine >= fromDayMillis && mine < c.getTimeInMillis();
    }

    /** Monday=0 … Sunday=6, matching the timetable. */
    int weekday() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(dayMillis());
        return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
    }

    private long atMinutes(int minutes) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month - 1, day, minutes / 60, minutes % 60, 0);
        return c.getTimeInMillis();
    }

    /** Validation shared by the editor and the loader. Returns null when valid. */
    String validate() {
        if (title == null || title.trim().isEmpty()) {
            return UiLang.t("请填写日程标题", "請填寫日程標題", "Enter an event title");
        }
        if (title.length() > MAX_TITLE) {
            return UiLang.t("标题最多 " + MAX_TITLE + " 个字", "標題最多 " + MAX_TITLE + " 個字",
                    "Title can be at most " + MAX_TITLE + " characters");
        }
        if (location != null && location.length() > MAX_LOCATION) {
            return UiLang.t("地点最多 " + MAX_LOCATION + " 个字", "地點最多 " + MAX_LOCATION + " 個字",
                    "Location can be at most " + MAX_LOCATION + " characters");
        }
        if (note != null && note.length() > MAX_NOTE) {
            return UiLang.t("备注最多 " + MAX_NOTE + " 个字", "備註最多 " + MAX_NOTE + " 個字",
                    "Note can be at most " + MAX_NOTE + " characters");
        }
        if (!validDate(year, month, day)) return UiLang.t("日期无效", "日期無效", "Invalid date");
        if (startMinutes < 0 || startMinutes >= 24 * 60) {
            return UiLang.t("开始时间无效", "開始時間無效", "Invalid start time");
        }
        if (endMinutes != NO_END && (endMinutes <= startMinutes || endMinutes >= 24 * 60)) {
            return UiLang.t("结束时间必须晚于开始时间，且在当天内", "結束時間必須晚於開始時間，且在當天內",
                    "End time must be after the start time and on the same day");
        }
        if (remindMinutes != NO_REMINDER && (remindMinutes < 0 || remindMinutes > MAX_LEAD_MINUTES)) {
            return UiLang.t("提前提醒需在 0~" + MAX_LEAD_MINUTES + " 分钟之间",
                    "提前提醒需在 0~" + MAX_LEAD_MINUTES + " 分鐘之間",
                    "Reminder lead time must be 0–" + MAX_LEAD_MINUTES + " minutes");
        }
        return null;
    }

    static boolean validDate(int year, int month, int day) {
        if (year < 2000 || year > 2100 || month < 1 || month > 12 || day < 1) return false;
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month - 1, 1);
        return day <= c.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    /**
     * Parses "10", "10:00", "10：30", "10点", "10点半", "10.30", "1030". Returns minutes after
     * midnight, or -1 when the text is empty or not a valid time of day.
     */
    static int parseTime(String text) {
        if (text == null) return -1;
        String value = text.trim().replace("半", "30");
        if (value.isEmpty()) return -1;
        Matcher m = TIME.matcher(value);
        int hour, minute;
        if (m.matches()) {
            hour = Integer.parseInt(m.group(1));
            minute = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        } else {
            Matcher compact = COMPACT_TIME.matcher(value);
            if (!compact.matches()) return -1;
            hour = Integer.parseInt(compact.group(1));
            minute = Integer.parseInt(compact.group(2));
        }
        if (hour > 23 || minute > 59) return -1;
        return hour * 60 + minute;
    }

    static String formatTime(int minutes) {
        if (minutes < 0) return "";
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }

    /** "10:00" or "10:00–11:30". */
    String timeLabel() {
        return endMinutes >= 0 ? formatTime(startMinutes) + "–" + formatTime(endMinutes) : formatTime(startMinutes);
    }

    static String remindLabel(int minutes) {
        if (minutes < 0) return UiLang.t("不提醒", "不提醒", "No reminder");
        if (minutes == 0) return UiLang.t("开始时提醒", "開始時提醒", "At start time");
        String duration = durationLabel(minutes);
        return UiLang.t("提前 " + duration, "提前 " + duration, duration + " before");
    }

    /** Notification text: "10 分钟后开始", "1 天后开始", "现在开始". */
    static String startsInLabel(int minutes) {
        if (minutes <= 0) return UiLang.t("现在开始", "現在開始", "Starting now");
        String duration = durationLabel(minutes);
        return UiLang.t(duration + "后开始", duration + "後開始", "Starts in " + duration);
    }

    private static String durationLabel(int minutes) {
        if (minutes % 1440 == 0) {
            int days = minutes / 1440;
            return UiLang.t(days + " 天", days + " 天", days + (days == 1 ? " day" : " days"));
        }
        if (minutes % 60 == 0) {
            int hours = minutes / 60;
            return UiLang.t(hours + " 小时", hours + " 小時", hours + " hr");
        }
        if (minutes > 60) {
            int hours = minutes / 60;
            int rest = minutes % 60;
            return UiLang.t(hours + " 小时 " + rest + " 分钟", hours + " 小時 " + rest + " 分鐘",
                    hours + " hr " + rest + " min");
        }
        return UiLang.t(minutes + " 分钟", minutes + " 分鐘", minutes + " min");
    }
}
