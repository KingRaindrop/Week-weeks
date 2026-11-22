package com.weekweeks.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One course per record: legacy pipe/tab rows, CSV quoted records, or header-mapped columns.
 * Explicit weekday-by-period grids are dispatched before the list-column mapping.
 * Unknown columns in a recognized header are metadata; required/duplicate/conflicting columns
 * and every malformed data row are reported instead of silently discarded.
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
    private static final String PERIOD_NUMBER = "(0?[1-9]|[1-9][0-9])";
    private static final Pattern PERIODS = Pattern.compile("^第?\\s*" + PERIOD_NUMBER
            + "\\s*(?:[节節]?\\s*(?:-|~|～|—|–|至|到)\\s*第?\\s*" + PERIOD_NUMBER + ")?\\s*[节節]?$" );
    private static final String[] DAY_NAMES = {"一", "二", "三", "四", "五", "六", "日"};
    private static final String[][] DAY_EN = {
            {"mon", "monday"}, {"tue", "tues", "tuesday"}, {"wed", "weds", "wednesday"},
            {"thu", "thur", "thurs", "thursday"}, {"fri", "friday"},
            {"sat", "saturday"}, {"sun", "sunday"}
    };
    private static final int NAME = 0, DAY = 1, PERIOD = 2, WEEKS = 3, ROOM = 4, TEACHER = 5;
    private static final int START = 6, END = 7;

    static final class Result {
        final List<PdfCourseParser.ParsedCourse> courses;
        final List<String> errors;

        Result(List<PdfCourseParser.ParsedCourse> courses, List<String> errors) {
            this.courses = courses;
            this.errors = errors;
        }
    }

    static final class Row {
        final List<String> fields;
        final int line;
        final boolean malformed;
        final boolean comment;
        Row(List<String> fields, int line, boolean malformed) {
            this(fields, line, malformed, false);
        }
        Row(List<String> fields, int line, boolean malformed, boolean comment) {
            this.fields = fields;
            this.line = line;
            this.malformed = malformed;
            this.comment = comment;
        }
    }

    private TextTimetableParser() { }

    /** Localized column hint; {@link #FORMAT_HINT} stays Simplified for existing callers. */
    static String formatHint() {
        return UiLang.t(FORMAT_HINT, "課程名 | 星期 | 節次 | 上課週 | 地點 | 老師",
                "Course | Day | Periods | Weeks | Location | Teacher");
    }

    static String example() {
        return UiLang.t(EXAMPLE,
                "# 每行一門課，用 | 分隔；上課週、地點、老師可留空\n" + EXAMPLE_ROWS,
                "# One course per line, separated by |. Weeks, location and teacher are optional."
                        + " Write days as 周一~周日 (or Mon~Sun) and weeks as 1-16周\n" + EXAMPLE_ROWS);
    }

    /** Detects comma/semicolon CSV or legacy pipe/tab rows, preserving source line numbers. */
    static Result parse(String text, int maxPeriods, int maxCourses) {
        if (text == null || text.trim().isEmpty()) return emptyInput();
        if (text.length() > MAX_TEXT_CHARS) return oversizedInput();
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').replace('\u00a0', ' ');
        if (normalized.startsWith("\uFEFF")) normalized = normalized.substring(1);
        char declared = '\0';
        // Excel's sep= directive is only metadata before the first data/header record.
        // Remove its text, not its newline, so diagnostics retain physical line numbers.
        for (int start = 0; start < normalized.length();) {
            int end = normalized.indexOf('\n', start);
            if (end < 0) end = normalized.length();
            String line = normalized.substring(start, end);
            if (!line.trim().isEmpty() && !line.trim().startsWith("#")) {
                String directive = line.replaceAll("^ +| +$", "");
                if (directive.regionMatches(true, 0, "sep=", 0, 4)) {
                    String separator = directive.substring(4);
                    if (separator.length() != 1 || ",;\t|｜".indexOf(separator.charAt(0)) < 0) {
                        return errorResult(UiLang.t("sep= 声明仅支持逗号、分号、Tab 或竖线分隔符",
                                "sep= 宣告僅支援逗號、分號、Tab 或直線分隔符號",
                                "sep= supports only comma, semicolon, tab or pipe delimiters"));
                    }
                    declared = separator.charAt(0);
                    normalized = normalized.substring(0, start) + normalized.substring(end);
                }
                break;
            }
            start = end + 1;
        }
        char delimiter = declared == '\0' ? detectDelimiter(normalized) : declared;
        return parseTable(tokenize(normalized, delimiter), maxPeriods, maxCourses,
                declared == '\0' && delimiter == '\0', java.util.Collections.emptyList());
    }

    /**
     * Shared entry for already decoded row tables (e.g. XLSX). Null cells mean blank cells;
     * a null row is malformed. Source numbers are 1-based row indices. The first nonblank,
     * noncomment row may be a recognized header; otherwise 3-6 positional columns are used.
     * Headers may reorder columns and include ignored metadata, but must identify course,
     * weekday and periods (or start period, with optional end period) unambiguously.
     */
    static Result parseRows(List<List<String>> rows, int maxPeriods, int maxCourses) {
        return parseRows(rows, maxPeriods, maxCourses, java.util.Collections.emptyList());
    }

    static Result parseRows(List<List<String>> rows, int maxPeriods, int maxCourses, List<WeeklyGridParser.Merge> merges) {
        if (rows == null || rows.isEmpty()) return emptyInput();
        long size = rows.size();
        if (size > MAX_TEXT_CHARS) return oversizedInput();
        List<Row> records = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row == null) {
                records.add(new Row(new ArrayList<>(), i + 1, true));
                continue;
            }
            size += row.size();
            List<String> cells = new ArrayList<>();
            for (String cell : row) {
                size += cell == null ? 0 : cell.length();
                if (size > MAX_TEXT_CHARS) return oversizedInput();
                cells.add(cell == null ? "" : cell);
            }
            if (size > MAX_TEXT_CHARS) return oversizedInput();
            if (i == 0 && !cells.isEmpty() && cells.get(0).startsWith("\uFEFF")) {
                cells.set(0, cells.get(0).substring(1));
            }
            // Already separated cells have no quote syntax. Treat only a standalone
            // note as a comment, so a course/teacher beginning with # remains data.
            boolean comment = !cells.isEmpty() && cell(cells, 0).startsWith("#")
                    && blank(cells.subList(1, cells.size()));
            records.add(new Row(cells, i + 1, false, comment));
        }
        if (size > MAX_TEXT_CHARS) return oversizedInput();
        return parseTable(records, maxPeriods, maxCourses, false, merges);
    }

    private static Result parseTable(List<Row> rows, int maxPeriods, int maxCourses, boolean legacyTail,
                                     List<WeeklyGridParser.Merge> merges) {
        WeeklyGridParser.Header grid = WeeklyGridParser.detect(rows);
        if (grid != null) return WeeklyGridParser.parse(rows, grid, merges, maxPeriods, maxCourses);
        if (!merges.isEmpty()) return errorResult(merges.get(0).ref + ": " + UiLang.t(
                "仅能识别周课表中同一星期的纵向合并；列表请取消合并", "僅能辨識週課表中同一星期的縱向合併；清單請取消合併",
                "Only vertical lesson merges in a weekly grid are supported. Unmerge cells in a course list"));
        return parseRecords(rows, maxPeriods, maxCourses, legacyTail);
    }

    private static Result emptyInput() {
        return errorResult(UiLang.t("内容为空，请按示例格式粘贴课表", "內容為空，請按範例格式貼上課表",
                "Nothing to import. Paste your timetable in the example format"));
    }

    private static Result oversizedInput() {
        return errorResult(UiLang.t("内容超过 " + MAX_TEXT_CHARS + " 字，请分批导入",
                "內容超過 " + MAX_TEXT_CHARS + " 字，請分批匯入",
                "Text exceeds " + MAX_TEXT_CHARS + " characters. Import it in parts"));
    }

    private static Result errorResult(String error) {
        List<String> errors = new ArrayList<>();
        errors.add(error);
        return new Result(new ArrayList<>(), errors);
    }

    private static String prefix(int line) {
        return UiLang.t("第 " + line + " 行：", "第 " + line + " 行：", "Line " + line + ": ");
    }

    private static Result parseRecords(List<Row> rows, int maxPeriods, int maxCourses, boolean legacyOptionalTail) {
        List<PdfCourseParser.ParsedCourse> courses = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int[] columns = null;
        int headerWidth = 0;
        boolean first = true;
        for (Row row : rows) {
            String prefix = prefix(row.line);
            if (row.malformed) {
                errors.add(prefix + UiLang.t("分隔文本引号格式错误", "分隔文字引號格式錯誤",
                        "Malformed quoted delimited record"));
                continue;
            }
            if (blank(row.fields) || row.comment) continue;
            if (first) {
                first = false;
                int[] mapping = header(row.fields);
                if (mapping != null) {
                    if (mapping[NAME] < 0 || mapping[DAY] < 0 || (mapping[PERIOD] < 0 && mapping[START] < 0)
                            || (mapping[PERIOD] >= 0 && (mapping[START] >= 0 || mapping[END] >= 0))) {
                        errors.add(prefix + UiLang.t("表头需包含且不能重复：课程名、星期、节次（或开始/结束节次）",
                                "表頭需包含且不能重複：課程名、星期、節次（或開始/結束節次）",
                                "Header needs unique course, day and periods (or start/end period) columns"));
                        return new Result(courses, errors);
                    }
                    columns = mapping;
                    headerWidth = row.fields.size();
                    // Existing pasted examples allow a 3-column header above 3-6 field rows.
                    if (legacyOptionalTail && headerWidth == 3
                            && columns[NAME] == 0 && columns[DAY] == 1 && columns[PERIOD] == 2) {
                        columns[WEEKS] = 3; columns[ROOM] = 4; columns[TEACHER] = 5;
                        headerWidth = 6;
                    }
                    continue;
                }
            }
            List<String> fields = row.fields;
            if (columns == null && fields.size() < 3) {
                errors.add(prefix + UiLang.t("至少需要「课程名 | 星期 | 节次」三项，用 | 分隔",
                        "至少需要「課程名 | 星期 | 節次」三項，用 | 分隔",
                        "Needs at least 3 fields (course | day | periods), separated by |"));
                continue;
            }
            if (fields.size() > (columns == null ? 6 : headerWidth)) {
                errors.add(prefix + (columns == null
                        ? UiLang.t("最多 6 项（" + FORMAT_HINT + "）", "最多 6 項（" + formatHint() + "）",
                                "At most 6 fields (" + formatHint() + ")")
                        : UiLang.t("列数超过表头，检查分隔符或引号", "欄數超過表頭，請檢查分隔符號或引號",
                                "More columns than the header; check delimiters or quotes")));
                continue;
            }
            if (columns != null) {
                String period = mapped(fields, columns, PERIOD);
                if (columns[PERIOD] < 0) {
                    String start = mapped(fields, columns, START);
                    String end = mapped(fields, columns, END);
                    // Separate start/end columns each contain one period, never an embedded range.
                    if (!singlePeriod(start) || (!end.isEmpty() && !singlePeriod(end))) {
                        errors.add(prefix + UiLang.t("开始/结束节次列必须各填一个正整数节次",
                                "開始/結束節次欄必須各填一個正整數節次",
                                "Start/end period columns must each contain one positive period number"));
                        continue;
                    }
                    period = stripPeriod(start) + (end.isEmpty() ? "" : "-" + stripPeriod(end));
                }
                fields = Arrays.asList(mapped(fields, columns, NAME), mapped(fields, columns, DAY), period,
                        mapped(fields, columns, WEEKS), mapped(fields, columns, ROOM), mapped(fields, columns, TEACHER));
            }
            String name = cell(fields, NAME);
            if (name.isEmpty()) {
                errors.add(prefix + UiLang.t("课程名不能为空", "課程名不能為空", "Course name is required"));
                continue;
            }
            int day = parseDay(cell(fields, DAY));
            if (day < 0) {
                String dayText = cell(fields, DAY);
                errors.add(prefix + UiLang.t("无法识别星期「" + dayText + "」，请写成 周一 ~ 周日",
                        "無法識別星期「" + dayText + "」，請寫成 周一 ~ 周日",
                        "Unrecognized day \"" + dayText + "\". Use 周一 ~ 周日 or Mon ~ Sun"));
                continue;
            }
            Matcher period = period(cell(fields, PERIOD));
            if (!period.matches()) {
                String periodText = cell(fields, PERIOD);
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
            String weeks = normalizeWeeks(cell(fields, WEEKS));
            if (!TimetableRules.parseWeeks(weeks).valid) {
                errors.add(prefix + UiLang.t("无法识别上课周「" + weeks + "」，请写成 1-16周、1-16周单周 或留空",
                        "無法識別上課週「" + weeks + "」，請寫成 1-16周、1-16周单周 或留空",
                        "Unrecognized weeks \"" + weeks + "\". Use e.g. 1-16周 or 1-16周单周, or leave blank"));
                continue;
            }
            String room = cell(fields, ROOM);
            String teacher = cell(fields, TEACHER);
            if (name.length() > MAX_FIELD_LENGTH || cell(fields, WEEKS).length() > MAX_FIELD_LENGTH
                    || weeks.length() > MAX_FIELD_LENGTH || room.length() > MAX_FIELD_LENGTH || teacher.length() > MAX_FIELD_LENGTH) {
                errors.add(prefix + UiLang.t("单项内容不能超过 " + MAX_FIELD_LENGTH + " 字",
                        "單項內容不能超過 " + MAX_FIELD_LENGTH + " 字",
                        "Each field can be at most " + MAX_FIELD_LENGTH + " characters"));
                continue;
            }
            if (courses.size() >= maxCourses) {
                errors.add(UiLang.t("课程超过 " + maxCourses + " 门上限，第 " + row.line + " 行起未读取",
                        "課程超過 " + maxCourses + " 門上限，第 " + row.line + " 行起未讀取",
                        "Course limit of " + maxCourses + " reached. Lines from " + row.line + " were not read"));
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

    static boolean blank(List<String> cells) {
        for (int i = 0; i < cells.size(); i++) if (!cell(cells, i).isEmpty()) return false;
        return true;
    }

    static String cell(List<String> fields, int index) {
        return index >= 0 && index < fields.size() && fields.get(index) != null
                ? fields.get(index).replace('\u00a0', ' ').trim() : "";
    }

    private static String mapped(List<String> fields, int[] mapping, int kind) {
        return cell(fields, mapping[kind]);
    }

    // Fold width only in structural fields; names, room codes and teachers keep their spelling.
    static Matcher period(String value) { return PERIODS.matcher(TimetableRules.scheduleNotation(value)); }
    private static boolean singlePeriod(String value) {
        Matcher matcher = period(value);
        return matcher.matches() && matcher.group(2) == null;
    }

    private static String stripPeriod(String value) {
        Matcher matcher = period(value);
        return matcher.matches() ? matcher.group(1) : value;
    }

    static String normalizeWeeks(String value) {
        String weeks = TimetableRules.scheduleNotation(value).replace('週', '周').replace('單', '单').replace('雙', '双');
        if (weeks.equalsIgnoreCase("every week") || weeks.equalsIgnoreCase("all weeks")) return "每周";
        return weeks.replaceAll("(?i)\\bweeks?\\b", "周")
                .replaceAll("(?i)\\bodd\\b", "单").replaceAll("(?i)\\beven\\b", "双");
    }

    /** A header must have at least two recognized cells, avoiding prefix-based data loss. */
    private static int[] header(List<String> fields) {
        int[] mapping = new int[8];
        Arrays.fill(mapping, -1);
        int recognized = 0;
        boolean duplicate = false;
        for (int i = 0; i < fields.size(); i++) {
            int kind = columnKind(cell(fields, i));
            if (kind < 0) continue;
            recognized++;
            if (mapping[kind] >= 0) duplicate = true;
            mapping[kind] = i;
        }
        if (recognized < 2) return null;
        if (duplicate) Arrays.fill(mapping, -1);
        return mapping;
    }

    private static int columnKind(String value) {
        String key = value.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]+", "");
        switch (key) {
            case "课程": case "课程名": case "课程名称": case "課程": case "課程名": case "課程名稱":
            case "course": case "coursename": case "coursetitle": case "name": case "subject": case "subjectname": case "classname": return NAME;
            case "星期": case "星期几": case "星期幾": case "周几": case "週幾": case "上课星期": case "上課星期":
            case "上课日": case "上課日": case "day": case "weekday": case "dayofweek": return DAY;
            case "节次": case "節次": case "上课节次": case "上課節次": case "period": case "periods":
            case "periodrange": case "classperiod": return PERIOD;
            case "开始节次": case "開始節次": case "起始节次": case "起始節次": case "开始节": case "開始節":
            case "startperiod": case "periodstart": return START;
            case "结束节次": case "結束節次": case "结束节": case "結束節": case "endperiod": case "periodend": return END;
            case "上课周": case "上課週": case "上課周": case "周": case "週": case "周次": case "週次": case "教学周": case "教學週":
            case "上课周次": case "上課週次": case "week": case "weeks": case "weekrange": return WEEKS;
            case "地点": case "地點": case "教室": case "上课地点": case "上課地點": case "room":
            case "classroom": case "location": case "venue": return ROOM;
            case "老师": case "老師": case "教师": case "教師": case "教师姓名": case "教師姓名":
            case "任课老师": case "任課老師": case "任课教师": case "任課教師":
            case "teacher": case "instructor": case "lecturer": return TEACHER;
            default: return -1;
        }
    }

    private static char detectDelimiter(String text) {
        // Bounded input is probed with the same lexer used by the final parse. Header/required
        // field evidence beats punctuation counts, so literal pipes in a CSV name are not separators.
        char[] delimiters = {'\0', ',', ';'}; // NUL means the existing mixed pipe/TSV mode.
        Row[] firstRows = new Row[delimiters.length];
        int bestHeader = 0;
        char selected = '\0';
        for (int i = 0; i < delimiters.length; i++) {
            List<Row> sample = tokenize(text, delimiters[i], 2);
            if (WeeklyGridParser.detect(sample) != null) return delimiters[i];
            for (Row row : sample) {
                if (blank(row.fields) || row.comment) continue;
                firstRows[i] = row;
                break;
            }
            int score = headerScore(firstRows[i]);
            if (score > bestHeader) { bestHeader = score; selected = delimiters[i]; }
        }
        if (bestHeader >= 2) return selected;
        // Existing legacy/CSV interpretations win ties; new semicolon support must not reinterpret
        // a formerly valid pipe/TSV record or comma CSV merely because its text contains semicolons.
        for (int i = 0; i < delimiters.length; i++) {
            if (hasRequiredFields(firstRows[i])) return delimiters[i];
        }
        for (int minimum : new int[]{3, 2}) {
            for (int i = 0; i < delimiters.length; i++) {
                if (firstRows[i] != null && firstRows[i].fields.size() >= minimum) return delimiters[i];
            }
        }
        return '\0';
    }

    private static int headerScore(Row row) {
        if (row == null || row.malformed) return 0;
        int recognized = 0;
        for (String field : row.fields) if (columnKind(field.trim()) >= 0) recognized++;
        return recognized;
    }

    static boolean hasRequiredFields(Row row) {
        return row != null && !row.malformed && row.fields.size() >= 3 && row.fields.size() <= 6
                && !cell(row.fields, NAME).isEmpty() && parseDay(cell(row.fields, DAY)) >= 0
                && period(cell(row.fields, PERIOD)).matches();
    }

    private static boolean recordHasTab(String text, int start) {
        // Raw comments end at the physical newline even if their prose contains an unmatched quote.
        int first = start;
        while (first < text.length() && text.charAt(first) == ' ') first++;
        if (first < text.length() && text.charAt(first) == '#') return false;
        boolean quoted = false;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') i++;
                else quoted = !quoted;
            } else if (!quoted) {
                if (ch == '\t') return true;
                if (ch == '\n') return false;
            }
        }
        return false;
    }

    private static boolean separator(char ch, char delimiter, boolean tab) {
        return delimiter != '\0' ? ch == delimiter : tab ? ch == '\t' : ch == '|' || ch == '｜';
    }

    /** CSV escaping also works in TSV. Newlines inside quoted fields belong to one record. */
    private static List<Row> tokenize(String text, char delimiter) {
        return tokenize(text, delimiter, Integer.MAX_VALUE);
    }

    private static List<Row> tokenize(String text, char delimiter, int recordLimit) {
        int significant = 0;
        boolean onlyPadding = true;
        List<Row> rows = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false, closed = false, malformed = false;
        boolean tab = delimiter == '\0' && recordHasTab(text, 0);
        int line = 1, startLine = 1;
        for (int i = 0; i <= text.length(); i++) {
            boolean eof = i == text.length();
            char ch = eof ? '\n' : text.charAt(i);
            if (ch == '#' && !quoted && !closed && fields.isEmpty() && onlyPadding) {
                int next = text.indexOf('\n', i);
                if (next < 0) break;
                rows.add(new Row(Arrays.asList("#"), startLine, false, true));
                value.setLength(0);
                onlyPadding = true;
                i = next;
                startLine = ++line;
                tab = delimiter == '\0' && recordHasTab(text, i + 1);
                continue;
            }
            if (quoted) {
                if (eof) {
                    fields.add(value.toString());
                    rows.add(new Row(fields, startLine, true));
                    break;
                }
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { value.append('"'); i++; }
                    else { quoted = false; closed = true; }
                } else {
                    value.append(ch);
                    if (ch == '\n') line++;
                }
                continue;
            }
            if (separator(ch, delimiter, tab) || ch == '\n') {
                fields.add(value.toString());
                value.setLength(0);
                closed = false;
                onlyPadding = true;
                if (ch == '\n') {
                    rows.add(new Row(fields, startLine, malformed));
                    if ((malformed || !blank(fields)) && ++significant >= recordLimit) break;
                    fields = new ArrayList<>();
                    malformed = false;
                    startLine = ++line;
                    tab = delimiter == '\0' && recordHasTab(text, i + 1);
                }
            } else if (ch == '"' && (delimiter != '\0' || tab)) {
                if (!closed && onlyPadding) {
                    value.setLength(0);
                    quoted = true;
                } else {
                    malformed = true;
                    onlyPadding = false;
                    value.append(ch);
                }
            } else if (closed) {
                if (!Character.isWhitespace(ch)) { malformed = true; value.append(ch); }
            } else {
                if (ch > ' ') onlyPadding = false;
                value.append(ch);
            }
        }
        return rows;
    }

    static int parseDay(String value) {
        if (value == null) return -1;
        String v = TimetableRules.scheduleNotation(value).toLowerCase(Locale.ROOT);
        if (v.isEmpty()) return -1;
        if (v.endsWith(".")) v = v.substring(0, v.length() - 1);
        if (v.length() == 1 && v.charAt(0) >= '1' && v.charAt(0) <= '7') return v.charAt(0) - '1';
        for (String prefix : new String[]{"星期", "礼拜", "禮拜", "周", "週"}) {
            if (v.startsWith(prefix)) { v = v.substring(prefix.length()); break; }
        }
        if (v.equals("天") || v.equals("7")) return 6;
        for (int i = 0; i < DAY_NAMES.length; i++) {
            if (v.equals(DAY_NAMES[i]) || v.equals(String.valueOf(i + 1))) return i;
            for (String english : DAY_EN[i]) if (v.equals(english)) return i;
        }
        return -1;
    }
}
