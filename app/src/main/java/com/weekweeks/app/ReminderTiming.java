package com.weekweeks.app;

import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure Java timing contract shared by notification delivery and the notification-open popup. */
final class ReminderTiming {
    private static final Pattern RANGE = Pattern.compile("^\\s*(\\d{1,2}):(\\d{2})\\s*[-–—]\\s*(\\d{1,2}):(\\d{2})\\s*$");

    private ReminderTiming() { }

    /** Scheduled trigger, never delivery time: delayed alarms must not shift the actual start. */
    static long startAt(long triggerAt, int leadMinutes) {
        if (triggerAt <= 0 || leadMinutes < 0) return -1L;
        long advance = leadMinutes * 60_000L;
        return triggerAt > Long.MAX_VALUE - advance ? -1L : triggerAt + advance;
    }

    /** Same-day time ranges only; a missing/ambiguous end stays unknown rather than invented. */
    static long endAt(long startAt, String timeLabel) {
        if (startAt <= 0 || timeLabel == null) return -1L;
        Matcher match = RANGE.matcher(timeLabel);
        if (!match.matches()) return -1L;
        int sh = Integer.parseInt(match.group(1)), sm = Integer.parseInt(match.group(2));
        int eh = Integer.parseInt(match.group(3)), em = Integer.parseInt(match.group(4));
        if (sh > 23 || eh > 23 || sm > 59 || em > 59 || eh * 60 + em <= sh * 60 + sm) return -1L;
        Calendar end = Calendar.getInstance();
        end.setTimeInMillis(startAt);
        if (end.get(Calendar.HOUR_OF_DAY) != sh || end.get(Calendar.MINUTE) != sm) return -1L;
        end.set(Calendar.HOUR_OF_DAY, eh);
        end.set(Calendar.MINUTE, em);
        end.set(Calendar.SECOND, 0);
        end.set(Calendar.MILLISECOND, 0);
        return end.getTimeInMillis() > startAt ? end.getTimeInMillis() : -1L;
    }

    /**
     * Evaluate at display time. Unknown start (old notification) uses neutral wording;
     * the legacy lead value must not be treated as time remaining when opening it later.
     */
    static String statusText(boolean event, long startAt, long endAt, long now) {
        if (startAt <= 0) {
            return event ? UiLang.t("日程提醒", "日程提醒", "Event reminder")
                    : UiLang.t("课程提醒", "課程提醒", "Course reminder");
        }
        if (endAt > startAt && now >= endAt) {
            return event ? UiLang.t("已结束", "已結束", "Ended") : UiLang.t("已下课", "已下課", "Finished");
        }
        if (now > startAt) {
            return event ? UiLang.t("已开始", "已開始", "Started") : UiLang.t("已上课", "已上課", "In class");
        }
        if (now == startAt) {
            return event ? UiLang.t("现在开始", "現在開始", "Starting now")
                    : UiLang.t("现在上课", "現在上課", "Class starts now");
        }
        long remaining = startAt - now;
        long minutes = remaining / 60_000L + (remaining % 60_000L == 0 ? 0 : 1);
        if (event) {
            long days = minutes / 1440, hours = minutes / 60, rest = minutes % 60;
            String duration = minutes % 1440 == 0
                    ? UiLang.t(days + " 天", days + " 天", days + (days == 1 ? " day" : " days"))
                    : rest == 0 ? UiLang.t(hours + " 小时", hours + " 小時", hours + " hr")
                    : hours > 0 ? UiLang.t(hours + " 小时 " + rest + " 分钟", hours + " 小時 " + rest + " 分鐘",
                            hours + " hr " + rest + " min")
                    : UiLang.t(minutes + " 分钟", minutes + " 分鐘", minutes + " min");
            return UiLang.t(duration + "后开始", duration + "後開始", "Starts in " + duration);
        }
        return UiLang.t("距离上课还有 " + minutes + " 分钟", "距離上課還有 " + minutes + " 分鐘",
                "Class starts in " + minutes + " min");
    }
}
