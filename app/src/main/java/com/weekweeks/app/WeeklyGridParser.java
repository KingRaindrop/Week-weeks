package com.weekweeks.app;

import static com.weekweeks.app.TextTimetableParser.blank;
import static com.weekweeks.app.TextTimetableParser.cell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared XLSX/paste parser for grids that explicitly identify weekdays and teaching periods. */
final class WeeklyGridParser {
    private static final Pattern BANNER = Pattern.compile("(?i).*(?:课程表|課程表|课表|課表|\\btimetable\\b|\\bclass schedule\\b).*");
    private static final String WEEK_LABEL = "周次|週次|上课周|上課週|周数|週數|weeks?";
    private static final String ROOM_LABEL = "教室|地点|地點|场地|場地|room|classroom|location|venue";
    private static final String TEACHER_LABEL = "教师|教師|老师|老師|任课教师|任課教師|授课教师|授課教師|teacher|instructor|lecturer";
    private static final Pattern LABEL = Pattern.compile("(?i)^(?:(" + WEEK_LABEL + ")|(" + ROOM_LABEL + ")|(" + TEACHER_LABEL + "))\\s*[:：]\\s*(.*)$");
    // A slash starts a field only when its label is on the same line. Room A/B and names ending in / stay literal.
    private static final Pattern INLINE_LABEL = Pattern.compile("(?i)[/／][ \\t　]*(?=(?:" + WEEK_LABEL + "|" + ROOM_LABEL + "|" + TEACHER_LABEL + ")[ \\t　]*[:：])");
    private static final Pattern WEEK_TOKENS = Pattern.compile("[0-9周次第单双每全所部有\\s,，;；、/|及和()（）\\[\\]~～—–至到\\-]+");
    private static final int WEEKS = 1, ROOM = 2, TEACHER = 4;

    private WeeklyGridParser() { }

    static final class Header {
        final int index, titleLine;
        Header(int index, int titleLine) { this.index = index; this.titleLine = titleLine; }
    }

    /** Original XLSX bounds: one-based rows, zero-based columns. */
    static final class Merge {
        final String ref;
        final int top, bottom, left, right;
        Merge(String ref, int top, int bottom, int left, int right) {
            this.ref = ref; this.top = top; this.bottom = bottom; this.left = left; this.right = right;
        }
    }

    private static final class Slot {
        final TextTimetableParser.Row row;
        final int first, last;
        final int[] mergedEnd; // 0: not merged, -1: covered, positive: last period of an anchor
        Slot(TextTimetableParser.Row row, int first, int last, int width) {
            this.row = row; this.first = first; this.last = last; mergedEnd = new int[width];
        }
    }

    private static final class Problem extends Exception {
        Problem(String at, String zh, String tw, String en) { super(at + ": " + UiLang.t(zh, tw, en)); }
    }

    private static int width(List<String> fields) {
        int n = fields.size();
        while (n > 0 && cell(fields, n - 1).isEmpty()) n--;
        return n;
    }

    private static boolean axisLabel(String text) {
        switch (text.toLowerCase(Locale.ROOT).replace(" ", "")) {
            case "": case "节次": case "節次": case "节数": case "節數": case "时间": case "時間":
            case "时段": case "時段": case "时间/星期": case "時間/星期": case "节次/星期": case "節次/星期":
            case "period": case "periods": case "time": case "timeslot": return true;
            default: return false;
        }
    }

    private static int headerDay(String text) {
        // Numbers alone could instead be dates or periods; a grid needs named weekdays.
        String label = TimetableRules.scheduleNotation(text);
        if (label.isEmpty() || Character.isDigit(label.charAt(0))) return -1;
        return TextTimetableParser.parseDay(label);
    }

    static Header detect(List<TextTimetableParser.Row> rows) {
        int titleLine = 0;
        for (int i = 0; i < rows.size(); i++) {
            TextTimetableParser.Row row = rows.get(i);
            if (row.malformed) return null;
            if (row.comment || blank(row.fields)) continue;
            String first = cell(row.fields, 0);
            int n = width(row.fields);
            if (titleLine == 0 && n == 1 && BANNER.matcher(first).matches()) {
                titleLine = row.line;
                continue;
            }
            if (!axisLabel(first) || TextTimetableParser.hasRequiredFields(row)) return null;
            int weekdays = 0;
            for (int col = 1; col < n; col++) if (headerDay(cell(row.fields, col)) >= 0) weekdays++;
            return weekdays >= 2 ? new Header(i, titleLine) : null;
        }
        return null;
    }

    static TextTimetableParser.Result parse(List<TextTimetableParser.Row> rows, Header header, List<Merge> merges,
                                           int maxPeriods, int maxCourses) {
        try {
            TextTimetableParser.Row head = rows.get(header.index);
            int[] days = weekdayColumns(head);
            Map<Integer, Slot> slots = readPeriods(rows, header.index + 1, days.length, maxPeriods);
            applyMerges(merges, header.titleLine, slots, days.length, maxPeriods);
            List<PdfCourseParser.ParsedCourse> courses = new ArrayList<>();
            for (Slot slot : slots.values()) {
                for (int col = 1; col < days.length; col++) {
                    String value = cell(slot.row.fields, col);
                    if (value.isEmpty()) continue;
                    int end = slot.mergedEnd[col] > 0 ? slot.mergedEnd[col] : slot.last;
                    appendCell(value, address(col, slot.row.line), days[col], slot.first, end, courses, maxCourses);
                }
            }
            if (courses.isEmpty()) throw new Problem(address(0, head.line), "周课表中没有课程", "週課表中沒有課程", "No courses in the weekly grid");
            return new TextTimetableParser.Result(courses, Collections.emptyList());
        } catch (Problem error) {
            // A partial grid can make an invalid or missing lesson look like a free period.
            return new TextTimetableParser.Result(Collections.emptyList(), Collections.singletonList(error.getMessage()));
        }
    }

    private static int[] weekdayColumns(TextTimetableParser.Row head) throws Problem {
        int[] days = new int[width(head.fields)];
        int seen = 0;
        for (int col = 1; col < days.length; col++) {
            int day = headerDay(cell(head.fields, col));
            if (day < 0 || (seen & (1 << day)) != 0) throw new Problem(address(col, head.line),
                    "星期表头缺失、重复或无法识别", "星期表頭缺失、重複或無法辨識", "Missing, duplicate or unrecognized weekday header");
            days[col] = day;
            seen |= 1 << day;
        }
        return days;
    }

    private static Map<Integer, Slot> readPeriods(List<TextTimetableParser.Row> rows, int start, int columns,
                                                 int maxPeriods) throws Problem {
        Map<Integer, Slot> slots = new LinkedHashMap<>();
        int previous = 0;
        for (int i = start; i < rows.size(); i++) {
            TextTimetableParser.Row row = rows.get(i);
            if (row.malformed) throw new Problem(address(0, row.line), "分隔文本引号格式错误", "分隔文字引號格式錯誤", "Malformed quoted record");
            if (row.comment || blank(row.fields)) continue;
            for (int col = columns; col < row.fields.size(); col++) {
                if (!cell(row.fields, col).isEmpty()) throw new Problem(address(col, row.line),
                        "此列没有对应星期", "此欄沒有對應星期", "No weekday header for this column");
            }
            Matcher period = TextTimetableParser.period(cell(row.fields, 0));
            if (!period.matches()) throw new Problem(address(0, row.line), "节次请写成 1 或 1-2", "節次請寫成 1 或 1-2", "Use a period such as 1 or 1-2");
            int first = Integer.parseInt(period.group(1));
            int last = period.group(2) == null ? first : Integer.parseInt(period.group(2));
            if (first <= previous || last < first || last > maxPeriods) throw new Problem(address(0, row.line),
                    "节次重复、倒序或超出上限", "節次重複、倒序或超出上限", "Duplicate, reversed or out-of-range periods");
            slots.put(row.line, new Slot(row, first, last, columns));
            previous = last;
        }
        return slots;
    }

    private static void applyMerges(List<Merge> merges, int titleLine, Map<Integer, Slot> slots, int columns,
                                    int maxPeriods) throws Problem {
        boolean titleMerged = false;
        for (Merge m : merges) {
            if (!titleMerged && m.top == titleLine && m.bottom == m.top && m.left == 0 && m.right > 0 && m.right < columns) {
                titleMerged = true;
                continue;
            }
            if (m.left < 1 || m.left >= columns || m.left != m.right || m.bottom <= m.top || m.bottom - m.top >= maxPeriods) throw mergeProblem(m);
            Slot anchor = slots.get(m.top);
            if (anchor == null || cell(anchor.row.fields, m.left).isEmpty()) throw mergeProblem(m);
            int end = anchor.first - 1;
            for (int line = m.top; line <= m.bottom; line++) {
                Slot slot = slots.get(line);
                if (slot == null || slot.first != end + 1 || slot.mergedEnd[m.left] != 0
                        || (line != m.top && !cell(slot.row.fields, m.left).isEmpty())) throw mergeProblem(m);
                end = slot.last;
                slot.mergedEnd[m.left] = -1;
            }
            anchor.mergedEnd[m.left] = end;
        }
    }

    private static Problem mergeProblem(Merge m) {
        return new Problem(m.ref, "课程只能在同一星期内纵向合并连续节次，其他被合并的格子须为空",
                "課程只能在同一星期內縱向合併連續節次，其他被合併的格子須為空",
                "A course merge must cover consecutive periods in one weekday column; covered cells must be empty");
    }

    private static String address(int column, int row) {
        StringBuilder name = new StringBuilder();
        for (int n = column + 1; n > 0; n = (n - 1) / 26) name.append((char) ('A' + (n - 1) % 26));
        return name.reverse().toString() + row;
    }

    private static boolean weekLine(String text) {
        if (text.length() > TextTimetableParser.MAX_FIELD_LENGTH) return false;
        String w = TextTimetableParser.normalizeWeeks(text);
        // Invalid ranges must still be diagnosed, but 周易 and Two Weeks are ordinary titles.
        return w.contains("周") && WEEK_TOKENS.matcher(w).matches();
    }

    private static void appendCell(String value, String at, int day, int first, int last,
                                   List<PdfCourseParser.ParsedCourse> out, int max) throws Problem {
        String text = INLINE_LABEL.matcher(value.replace("\r\n", "\n").replace('\r', '\n')).replaceAll("\n");
        int initialCount = out.size(), seen = 0;
        PdfCourseParser.ParsedCourse current = null;
        for (String raw : text.split("\n", -1)) {
            String line = raw.trim();
            if (line.isEmpty() || line.equals("---")) { current = null; continue; }
            Matcher label = LABEL.matcher(line);
            boolean labelled = label.matches();
            if (current == null) {
                checkLength(line, at);
                if (labelled || weekLine(line)) throw new Problem(at, "每门课的首行须为课程名", "每門課的首行須為課程名", "Start each course with its name");
                if (out.size() >= max) throw new Problem(at, "课程数量超过上限", "課程數量超過上限", "Course limit exceeded");
                current = new PdfCourseParser.ParsedCourse();
                current.name = line; current.day = day; current.period = first; current.endPeriod = last;
                out.add(current);
                seen = 0;
                continue;
            }
            int field = 0;
            if (labelled) field = label.group(1) != null ? WEEKS : label.group(2) != null ? ROOM : TEACHER;
            else if (weekLine(line)) field = WEEKS;
            if (field == 0 || (seen & field) != 0) throw new Problem(at,
                    "无法区分课程信息，请标明周次、教室、教师；两门课之间留一空行",
                    "無法區分課程資訊，請標明週次、教室、教師；兩門課之間留一空行",
                    "Use Weeks, Room or Teacher labels once per course; separate courses by a blank line");
            seen |= field;
            String data = labelled ? label.group(4).trim() : line;
            checkLength(data, at);
            if (field == WEEKS) {
                current.weeks = TextTimetableParser.normalizeWeeks(data);
                if (!TimetableRules.parseWeeks(current.weeks).valid) throw new Problem(at, "无法识别上课周", "無法辨識上課週", "Invalid teaching weeks");
            } else if (field == ROOM) current.room = data;
            else current.teacher = data;
        }
        if (out.size() == initialCount) throw new Problem(at, "此格缺少课程名", "此格缺少課程名", "Missing course name in this cell");
    }

    private static void checkLength(String field, String at) throws Problem {
        if (field.length() > TextTimetableParser.MAX_FIELD_LENGTH) throw new Problem(at, "单项内容超过 60 字", "單項內容超過 60 字", "Field exceeds 60 characters");
    }
}
