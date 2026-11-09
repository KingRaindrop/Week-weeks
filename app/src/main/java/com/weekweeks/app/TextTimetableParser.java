package com.weekweeks.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a pasted plain-text timetable, one course per line:
 * <pre>课程名 | 星期 | 节次 | 上课周 | 地点 | 老师</pre>
 * Fields are separated by "|", "｜" or a tab (commas stay usable inside weeks such as
 * "1-2周,4-12周"). Weeks, room and teacher are optional. Blank lines, lines starting with "#"
 * and the header line are ignored. Pure Java so it can be unit-tested without Android.
 */
final class TextTimetableParser {
    static final String FORMAT_HINT = "课程名 | 星期 | 节次 | 上课周 | 地点 | 老师";
    static final String EXAMPLE =
            "# 每行一门课，用 | 分隔；上课周、地点、老师可留空\n"
            + "高等数学 | 周一 | 1-2 | 1-16周 | 教学楼A101 | 张老师\n"
            + "大学英语 | 周二 | 3-4 | 1-16周单周 | 外语楼203 | 李老师\n"
            + "数据结构 | 周三 | 5-6 | 1-8周,10-16周 | 实验楼301 | 王老师\n"
            + "体育 | 周五 | 7-8 | | 体育馆 |\n"
            + "中外戏剧欣赏 | 周四 | 9-10 | 2-16周双周 | 宪梓楼407 | 侯老师";
    private static final String EXAMPLE_ROWS = EXAMPLE.substring(EXAMPLE.indexOf('\n') + 1);

    static final int MAX_TEXT_CHARS = 20_000;
    static final int MAX_FIELD_LENGTH = 60;

    private static final Pattern SEPARATOR = Pattern.compile("\\s*[|｜\\t]\\s*");
    private static final Pattern PERIODS = Pattern.compile(
            "^第?\\s*([1-9]\\d?)\\s*(?:(?:-|~|～|—|–|至|到)\\s*第?\\s*([1-9]\\d?))?\\s*节?$");
    private static final String[] DAY_NAMES = {"一", "二", "三", "四", "五", "六", "日"};
    private static final String[] DAY_EN = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"};

    static final class Result {
        final List<PdfCourseParser.ParsedCourse> courses;
        final List<String> errors;

        Result(List<PdfCourseParser.ParsedCourse> courses, List<String> errors) {
            this.courses = courses;
            this.errors = errors;
        }
    }

    private TextTimetableParser() {
    }

    /** Localized column hint; {@link #FORMAT_HINT} stays Simplified for existing callers. */
    static String formatHint() {
        return UiLang.t(FORMAT_HINT, "課程名 | 星期 | 節次 | 上課週 | 地點 | 老師",
                "Course | Day | Periods | Weeks | Location | Teacher");
    }

    /**
     * Localized example. Only the "#" instruction line changes: the parser reads Chinese
     * weekday and week keywords, so the course rows stay as they are.
     */
    static String example() {
        return UiLang.t(EXAMPLE,
                "# 每行一門課，用 | 分隔；上課週、地點、老師可留空\n" + EXAMPLE_ROWS,
                "# One course per line, separated by |. Weeks, location and teacher are optional."
                        + " Write days as 周一~周日 (or Mon~Sun) and weeks as 1-16周\n" + EXAMPLE_ROWS);
    }

    static Result parse(String text, int maxPeriods, int maxCourses) {
        List<PdfCourseParser.ParsedCourse> courses = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            errors.add(UiLang.t("内容为空，请按示例格式粘贴课表", "內容為空，請按範例格式貼上課表",
                    "Nothing to import. Paste your timetable in the example format"));
            return new Result(courses, errors);
        }
        if (text.length() > MAX_TEXT_CHARS) {
            errors.add(UiLang.t("内容超过 " + MAX_TEXT_CHARS + " 字，请分批导入",
                    "內容超過 " + MAX_TEXT_CHARS + " 字，請分批匯入",
                    "Text exceeds " + MAX_TEXT_CHARS + " characters. Import it in parts"));
            return new Result(courses, errors);
        }
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].replace('\u00a0', ' ').trim();
            if (line.isEmpty() || line.startsWith("#") || isHeader(line)) {
                continue;
            }
            String prefix = UiLang.t("第 " + (i + 1) + " 行：", "第 " + (i + 1) + " 行：", "Line " + (i + 1) + ": ");
            String[] fields = SEPARATOR.split(line, -1);
            if (fields.length < 3) {
                errors.add(prefix + UiLang.t("至少需要「课程名 | 星期 | 节次」三项，用 | 分隔",
                        "至少需要「課程名 | 星期 | 節次」三項，用 | 分隔",
                        "Needs at least 3 fields (course | day | periods), separated by |"));
                continue;
            }
            if (fields.length > 6) {
                errors.add(prefix + UiLang.t("最多 6 项（" + FORMAT_HINT + "）", "最多 6 項（" + formatHint() + "）",
                        "At most 6 fields (" + formatHint() + ")"));
                continue;
            }
            String name = field(fields, 0);
            if (name.isEmpty()) {
                errors.add(prefix + UiLang.t("课程名不能为空", "課程名不能為空", "Course name is required"));
                continue;
            }
            int day = parseDay(field(fields, 1));
            if (day < 0) {
                String dayText = field(fields, 1);
                errors.add(prefix + UiLang.t("无法识别星期「" + dayText + "」，请写成 周一 ~ 周日",
                        "無法識別星期「" + dayText + "」，請寫成 周一 ~ 周日",
                        "Unrecognized day \"" + dayText + "\". Use 周一 ~ 周日 or Mon ~ Sun"));
                continue;
            }
            Matcher period = PERIODS.matcher(field(fields, 2).replace(" ", ""));
            if (!period.matches()) {
                String periodText = field(fields, 2);
                errors.add(prefix + UiLang.t("无法识别节次「" + periodText + "」，请写成 1-2 或 3",
                        "無法識別節次「" + periodText + "」，請寫成 1-2 或 3",
                        "Unrecognized periods \"" + periodText + "\". Use e.g. 1-2 or 3"));
                continue;
            }
            int start = Integer.parseInt(period.group(1));
            int end = period.group(2) == null ? start : Integer.parseInt(period.group(2));
            if (end < start) {
                errors.add(prefix + UiLang.t("结束节次不能早于开始节次", "結束節次不能早於開始節次",
                        "End period can't be before the start period"));
                continue;
            }
            if (end > maxPeriods) {
                errors.add(prefix + UiLang.t("节次最多到第 " + maxPeriods + " 节", "節次最多到第 " + maxPeriods + " 節",
                        "Periods go up to " + maxPeriods));
                continue;
            }
            String weeks = field(fields, 3);
            if (!TimetableRules.parseWeeks(weeks).valid) {
                errors.add(prefix + UiLang.t("无法识别上课周「" + weeks + "」，请写成 1-16周、1-16周单周 或留空",
                        "無法識別上課週「" + weeks + "」，請寫成 1-16周、1-16周单周 或留空",
                        "Unrecognized weeks \"" + weeks + "\". Use e.g. 1-16周 or 1-16周单周, or leave blank"));
                continue;
            }
            String room = field(fields, 4);
            String teacher = field(fields, 5);
            if (name.length() > MAX_FIELD_LENGTH || weeks.length() > MAX_FIELD_LENGTH
                    || room.length() > MAX_FIELD_LENGTH || teacher.length() > MAX_FIELD_LENGTH) {
                errors.add(prefix + UiLang.t("单项内容不能超过 " + MAX_FIELD_LENGTH + " 字",
                        "單項內容不能超過 " + MAX_FIELD_LENGTH + " 字",
                        "Each field can be at most " + MAX_FIELD_LENGTH + " characters"));
                continue;
            }
            if (courses.size() >= maxCourses) {
                errors.add(UiLang.t("课程超过 " + maxCourses + " 门上限，第 " + (i + 1) + " 行起未读取",
                        "課程超過 " + maxCourses + " 門上限，第 " + (i + 1) + " 行起未讀取",
                        "Course limit of " + maxCourses + " reached. Lines from " + (i + 1) + " were not read"));
                break;
            }
            PdfCourseParser.ParsedCourse course = new PdfCourseParser.ParsedCourse();
            course.name = name;
            course.day = day;
            course.period = start;
            course.endPeriod = end;
            course.weeks = weeks;
            course.room = room;
            course.teacher = teacher;
            courses.add(course);
        }
        if (courses.isEmpty() && errors.isEmpty()) {
            errors.add(UiLang.t("没有找到课程行，请按示例格式粘贴", "沒有找到課程行，請按範例格式貼上",
                    "No course lines found. Paste in the example format"));
        }
        return new Result(courses, errors);
    }

    private static boolean isHeader(String line) {
        return line.startsWith("课程名") || line.startsWith("课程名称");
    }

    private static String field(String[] fields, int index) {
        return index < fields.length ? fields[index].trim() : "";
    }

    static int parseDay(String value) {
        if (value == null) {
            return -1;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.isEmpty()) {
            return -1;
        }
        if (v.length() == 1 && v.charAt(0) >= '1' && v.charAt(0) <= '7') {
            return v.charAt(0) - '1';
        }
        for (String prefix : new String[]{"星期", "礼拜", "周"}) {
            if (v.startsWith(prefix)) {
                v = v.substring(prefix.length());
                break;
            }
        }
        if (v.equals("天") || v.equals("7")) {
            return 6;
        }
        for (int i = 0; i < DAY_NAMES.length; i++) {
            if (v.equals(DAY_NAMES[i]) || v.equals(String.valueOf(i + 1))) {
                return i;
            }
            if (v.startsWith(DAY_EN[i])) {
                return i;
            }
        }
        return -1;
    }
}
