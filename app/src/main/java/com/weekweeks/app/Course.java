package com.weekweeks.app;

import java.util.ArrayList;
import java.util.List;

/** Package-local course model; persistence and UI orchestration remain with the host. */
final class Course implements CourseColorRules.Item, ImportPlanner.Item {
    static final int DEFAULT_COLOR = 0xFF347B80;

    long id;
    int day;
    int period;
    int endPeriod = 1;
    String name = "";
    String weeks = "";
    String room = "";
    String teacher = "";
    int color = DEFAULT_COLOR;
    boolean temporary = false;
    boolean invalidWeeks = false;
    int customStartMinutes = -1;
    int customEndMinutes = -1;

    @Override public String colorName() { return name; }
    @Override public int colorValue() { return color; }
    @Override public void setColorValue(int value) { color = value; }
    @Override public boolean temporaryColor() { return temporary; }
    @Override public int importDay() { return day; }
    @Override public int importStart() { return period; }
    @Override public int importEnd() { return endPeriod; }
    @Override public String importName() { return name; }
    @Override public String importWeeks() { return weeks; }
    @Override public String importRoom() { return room; }
    @Override public String importTeacher() { return teacher; }
    @Override public void applyImportedLocation(String newRoom, String newTeacher) {
        room = newRoom;
        teacher = newTeacher;
    }

    String periodLabel() {
        if (endPeriod > period) {
            return UiLang.t("第" + period + "-" + endPeriod + "节", "第" + period + "-" + endPeriod + "節",
                    "Periods " + period + "–" + endPeriod);
        }
        return UiLang.t("第" + period + "节", "第" + period + "節", "Period " + period);
    }

    String periodBadgeText() {
        if (endPeriod > period) {
            return UiLang.t("第\n" + period + "-" + endPeriod + "\n节", "第\n" + period + "-" + endPeriod + "\n節",
                    "Periods\n" + period + "–" + endPeriod);
        }
        return UiLang.t("第\n" + period + "\n节", "第\n" + period + "\n節", "Period\n" + period);
    }

    String secondaryText() {
        List<String> parts = new ArrayList<>();
        if (invalidWeeks) {
            parts.add(UiLang.t("⚠ 上课周格式需修正", "⚠ 上課週格式需修正", "⚠ Weeks need fixing"));
        }
        if (weeks != null && !weeks.isEmpty()) {
            parts.add(weeks);
        }
        if (room != null && !room.isEmpty()) {
            parts.add(room);
        }
        if (teacher != null && !teacher.isEmpty()) {
            parts.add(teacher);
        }
        return parts.isEmpty() ? UiLang.t("暂无上课周、地点和老师信息", "暫無上課週、地點和老師資訊", "No weeks, location or teacher") : join(parts, " · ");
    }

    Course copy() {
        Course copy = new Course();
        copy.id = id;
        copy.day = day;
        copy.period = period;
        copy.endPeriod = endPeriod;
        copy.name = name;
        copy.weeks = weeks;
        copy.room = room;
        copy.teacher = teacher;
        copy.color = color;
        copy.temporary = temporary;
        copy.invalidWeeks = invalidWeeks;
        copy.customStartMinutes = customStartMinutes;
        copy.customEndMinutes = customEndMinutes;
        return copy;
    }

    private static String join(List<String> parts, String separator) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                builder.append(separator);
            }
            builder.append(parts.get(i));
        }
        return builder.toString();
    }
}
