package com.weekweeks.app;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class PdfCourseParserTest {
    private static final int MAX_PERIODS = 11;
    private static final int MAX_STREAM = 2 * 1024 * 1024;
    private static final int MAX_COURSES = 200;

    // U+7B2C=? U+8282=? U+5468=? U+5355=? U+4E0B=? U+5B66=? U+671F=?
    // U+53CA=? U+573A=? U+5730=? U+6559=? U+5E08=? U+5F20=? U+4E09=?
    private static final String METADATA_SINGLE = "(\u7b2c1\u8282 1-16\u5468) Tj";            // ?1? 1-16?
    private static final String METADATA_ROW = "(\u7b2c5\u8282 9-17\u5468) Tj";              // ?5? 9-17?

    @Test
    public void longValidWeeksRetainAllOccurrencesInsteadOfTruncating() throws Exception {
        String weeks = "1周,".repeat(20) + "30周";
        String content = "BT 1 0 0 1 99.08 99.08 Tm (Seminar) Tj "
                + "1 0 0 1 99.08 99.08 Tm (第1节 " + weeks + ") Tj ET";
        PdfCourseParser.ParseResult result = parseContent(content);
        check(result.courses.size() == 1, "Long valid week expression should import");
        String imported = result.courses.get(0).weeks;
        check(imported.length() <= 60, "Stored weeks must respect field limit");
        check(TimetableRules.sameWeekExpression(weeks, imported), "Week 30 was lost: " + imported);
    }

    @Test
    public void compactLongWeeksPreserveParityAndDisjointRanges() throws Exception {
        String weeks = "1-20周单周,".repeat(10) + "24-30周双周";
        String content = "BT 1 0 0 1 99.08 99.08 Tm (Seminar) Tj "
                + "1 0 0 1 99.08 99.08 Tm (第1节 " + weeks + ") Tj ET";
        PdfCourseParser.ParseResult result = parseContent(content);
        check(result.courses.size() == 1, "Long parity expression should import");
        String imported = result.courses.get(0).weeks;
        check(imported.length() <= 60, "Compact expression exceeds field limit");
        check(TimetableRules.sameWeekExpression(weeks, imported), "Parity or disjoint ranges changed: " + imported);
    }

    @Test
    public void longInvalidWeeksAreNeverAcceptedByTruncatingInvalidSuffix() throws Exception {
        String content = "BT 1 0 0 1 99.08 99.08 Tm (Seminar) Tj "
                + "1 0 0 1 99.08 99.08 Tm (第1节 " + "1周,".repeat(20) + "31周) Tj ET";
        check(parseContent(content).courses.isEmpty(), "Truncation must not bypass week validation");
    }

    @Test
    public void testInvalidHeaderRejected() {
        byte[] notPdf = "hello world".getBytes(StandardCharsets.ISO_8859_1);
        expectThrows(() -> PdfCourseParser.parseCourses(notPdf, MAX_PERIODS, MAX_STREAM, MAX_COURSES));
    }

    @Test
    public void testEmptyPdfRejected() {
        byte[] emptyPdf = "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.ISO_8859_1);
        expectThrows(() -> PdfCourseParser.parseCourses(emptyPdf, MAX_PERIODS, MAX_STREAM, MAX_COURSES));
    }

    @Test
    public void testSingleCourseParsed() throws Exception {
        String content = "BT 1 0 0 1 99.08 99.08 Tm (Data Structures) Tj "
                + "1 0 0 1 99.08 99.08 Tm " + METADATA_SINGLE + " "
                + "1 0 0 1 99.08 99.08 Tm (/\u573a\u5730:A101/\u6559\u5e08:\u5f20\u4e09 ) Tj ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                pdfWithStreams(stream(content)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 1, "single course expected, got " + result.courses.size());
        PdfCourseParser.ParsedCourse c = result.courses.get(0);
        check(c.day == 0, "course should map to Monday, got " + c.day);
        check(c.period == 1 && c.endPeriod == 1, "period 1-1 expected, got " + c.period + "-" + c.endPeriod);
        check(c.name.equals("Data Structures"), "name mismatch: " + c.name);
        check(c.weeks.equals("1-16\u5468"), "weeks mismatch: " + c.weeks);
        check(c.room.equals("A101"), "room mismatch: " + c.room);
        check(c.teacher.equals("\u5f20\u4e09"), "teacher mismatch: " + c.teacher);
    }

    @Test
    public void testRowAxisCourseParsed() throws Exception {
        String content = "BT 1 0 0 1 50 99.08 Tm (History) Tj "
                + "1 0 0 1 203.78 99.08 Tm " + METADATA_ROW + " ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                pdfWithStreams(stream(content)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 1, "row-axis course expected, got " + result.courses.size());
        PdfCourseParser.ParsedCourse c = result.courses.get(0);
        check(c.day == 0, "row-axis day mismatch: " + c.day);
        check(c.period == 5 && c.endPeriod == 5, "row-axis period mismatch: " + c.period + "-" + c.endPeriod);
        check(c.name.equals("History"), "row-axis name mismatch: " + c.name);
        check(c.weeks.equals("9-17\u5468"), "row-axis weeks mismatch: " + c.weeks);
    }

    @Test
    public void testWeekTimeSuffixCleaned() throws Exception {
        String content = "BT 1 0 0 1 99.08 99.08 Tm (Physics) Tj "
                + "1 0 0 1 99.08 99.08 Tm (\u7b2c2\u8282 1-16\u5468\u53ca18:30-20:10) Tj ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                pdfWithStreams(stream(content)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 1, "week-suffix course expected, got " + result.courses.size());
        check(result.courses.get(0).weeks.equals("1-16\u5468"),
                "time suffix should be stripped from weeks, got: " + result.courses.get(0).weeks);
    }

    @Test
    public void testInvalidWeeksSkipped() throws Exception {
        String content = "BT 1 0 0 1 99.08 99.08 Tm (History) Tj "
                + "1 0 0 1 99.08 99.08 Tm (\u7b2c3\u8282 \u4e0b\u5b66\u671f) Tj ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                pdfWithStreams(stream(content)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.isEmpty(), "unrecognizable weeks should be skipped");
    }

    @Test
    public void testPeriodOutOfRangeSkipped() throws Exception {
        String content = "BT 1 0 0 1 99.08 99.08 Tm (Physics) Tj "
                + "1 0 0 1 99.08 99.08 Tm (\u7b2c99\u8282 1-16\u5468\u5355\u5468 ) Tj ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                pdfWithStreams(stream(content)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.isEmpty(), "out-of-range period should be skipped");
    }

    @Test
    public void testMultiStreamParsed() throws Exception {
        String s1 = "BT 1 0 0 1 99.08 99.08 Tm (Data Structures) Tj "
                + "1 0 0 1 99.08 99.08 Tm " + METADATA_SINGLE + " ET";
        String s2 = "BT 1 0 0 1 99.08 99.08 Tm (History) Tj "
                + "1 0 0 1 99.08 99.08 Tm " + METADATA_ROW + " ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                pdfWithStreams(stream(s1), stream(s2)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 2, "two streams should yield two courses, got " + result.courses.size());
    }

    // 课程跨 PDF 分页：课程名留在第 1 页底部（如 x=415.62, y=22.5），
    // 节次信息出现在第 2 页顶部（y=567），中间隔着其他列的文本（如节次标签"8"）。
    // 回溯查找课程名时必须跳过其他列的文本，而不是在它们处中断。
    @Test
    public void testCrossPageNameLookup() throws Exception {
        String page1Tail = "BT 1 0 0 1 415.62 22.5 Tm (Comp Organization) Tj ET";
        String page2Head = "BT 1 0 0 1 78.13 524.5 Tm (8) Tj "
                + "1 0 0 1 415.62 567 Tm " + METADATA_ROW + " "
                + "1 0 0 1 415.62 555 Tm (/\u573a\u5730:A101/\u6559\u5e08:\u5f20\u4e09 ) Tj ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                pdfWithStreams(stream(page1Tail), stream(page2Head)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 1,
                "cross-page course should be parsed, got " + result.courses.size());
        PdfCourseParser.ParsedCourse c = result.courses.get(0);
        check(c.name.equals("Comp Organization"), "name mismatch: " + c.name);
        check(c.day == 3, "course should map to Thursday, got " + c.day);
        check(c.room.equals("A101"), "room mismatch: " + c.room);
        check(c.teacher.equals("\u5f20\u4e09"), "teacher mismatch: " + c.teacher);
    }

    @Test
    public void testStreamSizeLimitRejected() {
        byte[] big = new byte[100];
        Arrays.fill(big, (byte) 'x');
        expectThrows(() -> PdfCourseParser.parseCourses(pdfWithStreams(big), MAX_PERIODS, 50, MAX_COURSES));
    }

    @Test
    public void testCourseCountLimit() throws Exception {
        String content = "BT 1 0 0 1 99.08 99.08 Tm (Data Structures) Tj "
                + "1 0 0 1 99.08 99.08 Tm " + METADATA_SINGLE + " "
                + "1 0 0 1 99.08 99.08 Tm (History) Tj "
                + "1 0 0 1 99.08 99.08 Tm " + METADATA_ROW + " ET";
        expectThrows(() -> PdfCourseParser.parseCourses(
                pdfWithStreams(stream(content)), MAX_PERIODS, MAX_STREAM, 1));
    }

    @Test
    public void testLiteralEscapeInName() throws Exception {
        String content = "BT 1 0 0 1 99.08 99.08 Tm (Data\\)Structures) Tj "
                + "1 0 0 1 99.08 99.08 Tm " + METADATA_SINGLE + " ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                pdfWithStreams(stream(content)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 1, "escaped-literal course expected, got " + result.courses.size());
        check(result.courses.get(0).name.equals("Data)Structures"),
                "escaped paren should be decoded, got: " + result.courses.get(0).name);
    }

    @Test
    public void testHexTextInName() throws Exception {
        String content = "BT 1 0 0 1 99.08 99.08 Tm [<48656C6C6F>] TJ "
                + "1 0 0 1 99.08 99.08 Tm " + METADATA_SINGLE + " ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                pdfWithStreams(stream(content)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 1, "hex-text course expected, got " + result.courses.size());
        check(result.courses.get(0).name.equals("Hello"), "hex string should decode to Hello, got: " + result.courses.get(0).name);
    }

    @Test
    public void testDayMappingRejected() throws Exception {
        String content = "BT 1 0 0 1 50 99.08 Tm (Physics) Tj "
                + "1 0 0 1 50 99.08 Tm " + METADATA_SINGLE + " ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                pdfWithStreams(stream(content)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.isEmpty(), "off-grid x should be rejected");
    }

    @Test
    public void testDebugLogProduced() throws Exception {
        String content = "BT 1 0 0 1 99.08 99.08 Tm (Data Structures) Tj "
                + "1 0 0 1 99.08 99.08 Tm " + METADATA_SINGLE + " ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                pdfWithStreams(stream(content)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.debugLog != null && result.debugLog.length() > 0, "debug log should not be empty");
    }

    @Test
    public void testEvenLengthAsciiNames() throws Exception {
        for (String name : new String[]{"Math", "Data", "Math2026"}) {
            assertEncodedCourse(name, StandardCharsets.US_ASCII, false, false);
            assertEncodedCourse(name, StandardCharsets.US_ASCII, false, true);
        }
    }

    @Test
    public void testUtf8ChineseAndAccents() throws Exception {
        assertEncodedCourse("数学", StandardCharsets.UTF_8, false, false);
        assertEncodedCourse("数据结构", StandardCharsets.UTF_8, false, true);
        assertEncodedCourse("Café", StandardCharsets.UTF_8, false, false);
        assertEncodedCourse("数学", StandardCharsets.UTF_8, true, true);
    }

    @Test
    public void testUtf16WithoutBom() throws Exception {
        assertEncodedCourse("高等数学", StandardCharsets.UTF_16BE, false, false);
        // 学校 的 UTF-16BE 字节 5B 66 68 21 全都可打印，必须参考同流编码证据。
        assertEncodedCourse("学校", StandardCharsets.UTF_16BE, false, true);
        assertEncodedCourse("Math", StandardCharsets.UTF_16BE, false, false);
    }

    @Test
    public void testUtf16WithBom() throws Exception {
        assertEncodedCourse("高等数学", StandardCharsets.UTF_16BE, true, false);
        assertEncodedCourse("学校", StandardCharsets.UTF_16LE, true, true);
    }

    private static void assertEncodedCourse(String name, java.nio.charset.Charset charset,
            boolean bom, boolean hex) throws Exception {
        // Every string uses real encoded bytes, not Chinese UTF-8 disguised as UTF-16.
        java.nio.charset.Charset metadataCharset = charset.equals(StandardCharsets.US_ASCII)
                ? StandardCharsets.UTF_8 : charset;
        String content = "BT 1 0 0 1 99.08 500 Tm "
                + encodedText(name, charset, bom, hex)
                + " 1 0 0 1 99.08 488 Tm "
                + encodedText("第1节 1-16周", metadataCharset, bom, hex)
                + " 1 0 0 1 99.08 476 Tm "
                + encodedText("/场地:A101/教师:张三", metadataCharset, bom, hex) + " ET";
        PdfCourseParser.ParseResult result = parseContent(content);
        check(result.courses.size() == 1, "encoded course missing: " + charset + " / " + name);
        PdfCourseParser.ParsedCourse course = result.courses.get(0);
        check(course.name.equals(name), "encoding " + charset + " changed " + name + " to " + course.name);
        check(course.day == 0 && course.period == 1 && course.weeks.equals("1-16周"),
                "encoded metadata mismatch: " + charset);
        check(course.room.equals("A101") && course.teacher.equals("张三"),
                "encoded fields mismatch: " + charset + " / " + course.room + " / " + course.teacher);
    }

    private static String encodedText(String value, java.nio.charset.Charset charset,
            boolean bom, boolean hex) {
        byte[] bytes = ((bom ? "\uFEFF" : "") + value).getBytes(charset);
        StringBuilder result = new StringBuilder(hex ? "[<" : "(");
        for (byte current : bytes) {
            int unsigned = current & 0xff;
            if (hex) {
                result.append(String.format(java.util.Locale.ROOT, "%02X", unsigned));
            } else {
                // Octal escaping preserves arbitrary UTF-16 bytes, including parentheses/backslashes.
                result.append(String.format(java.util.Locale.ROOT, "\\%03o", unsigned));
            }
        }
        return result.append(hex ? ">] TJ" : ") Tj").toString();
    }

    @Test
    public void testAdjacentColumnTextDoesNotFlipAxis() throws Exception {
        String content = "BT 1 0 0 1 99.08 500 Tm (History) Tj "
                + "1 0 0 1 99.08 488 Tm " + METADATA_SINGLE + " "
                + "1 0 0 1 202.93 488 Tm (Physics) Tj ET";
        PdfCourseParser.ParseResult result = parseContent(content);
        check(result.courses.size() == 1, "adjacent-column title must not remove the course");
        check(result.courses.get(0).day == 0 && result.courses.get(0).name.equals("History"),
                "adjacent-column title changed the course axis");
    }

    @Test
    public void testInterleavedColumnTitlesKeepColumnAxis() throws Exception {
        String content = "BT 1 0 0 1 99.08 500 Tm (History) Tj "
                + "1 0 0 1 202.93 488 Tm (Physics) Tj "
                + "1 0 0 1 99.08 488 Tm " + METADATA_SINGLE + " "
                + "1 0 0 1 202.93 476 Tm " + METADATA_ROW + " ET";
        PdfCourseParser.ParseResult result = parseContent(content);
        check(result.courses.size() == 2, "interleaved columns should retain both courses");
        check(result.courses.get(0).day == 0 && result.courses.get(0).name.equals("History"),
                "preceding other-column title must not flip the Monday course");
        check(result.courses.get(1).day == 1 && result.courses.get(1).name.equals("Physics"),
                "interleaved Tuesday course mismatch");
    }

    @Test
    public void testRowAxisMultipleCourses() throws Exception {
        String content = "BT 1 0 0 1 50 99.08 Tm (History) Tj "
                + "1 0 0 1 50 202.93 Tm (Physics) Tj "
                + "1 0 0 1 203.78 99.08 Tm " + METADATA_SINGLE + " "
                + "1 0 0 1 203.78 202.93 Tm " + METADATA_ROW + " ET";
        PdfCourseParser.ParseResult result = parseContent(content);
        check(result.courses.size() == 2, "row-axis interleaving should retain both courses");
        check(result.courses.get(0).day == 0 && result.courses.get(0).name.equals("History"),
                "row-axis Monday mismatch");
        check(result.courses.get(1).day == 1 && result.courses.get(1).name.equals("Physics"),
                "row-axis Tuesday mismatch");
    }

    @Test
    public void testCrossPageNameWithAdjacentColumn() throws Exception {
        String page1 = "BT 1 0 0 1 415.62 22.5 Tm (Comp Organization) Tj ET";
        String page2 = "BT 1 0 0 1 78.13 524.5 Tm (8) Tj "
                + "1 0 0 1 415.62 567 Tm " + METADATA_ROW + " "
                + "1 0 0 1 519.47 567 Tm (Physics) Tj ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                pdfWithStreams(stream(page1), stream(page2)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 1, "adjacent column must not break cross-page lookup");
        check(result.courses.get(0).day == 3
                && result.courses.get(0).name.equals("Comp Organization"), "cross-page axis mismatch");
    }

    @Test
    public void testWrappedWeeksRetainFinalWeekAndParity() throws Exception {
        for (boolean rotated : new boolean[]{false, true}) {
            String matrix = rotated ? "0 1 -1 0 " : "1 0 0 1 ";
            String titlePoint = rotated ? "100 311.77" : "311.77 500";
            String metadataPoint = rotated ? "112 311.77" : "311.77 488";
            String continuationPoint = rotated ? "124 311.77" : "311.77 476";
            String content = "BT " + matrix + titlePoint + " Tm (Robotics) Tj "
                    + matrix + metadataPoint + " Tm "
                    + encodedText("(9-10节)2-3周,6-14周(双", StandardCharsets.UTF_8, false, false) + " "
                    + matrix + continuationPoint + " Tm "
                    + encodedText("),18周/场地:Lab/教师:Tutor", StandardCharsets.UTF_8, false, false) + " ET";
            PdfCourseParser.ParseResult result = parseContent(content);
            check(result.courses.size() == 1, "wrapped weeks course missing");
            PdfCourseParser.ParsedCourse course = result.courses.get(0);
            check(course.weeks.equals("2-3周,6-14周(双),18周"),
                    "wrapped weeks truncated: " + course.weeks);
            TimetableRules.WeekParseResult weeks = TimetableRules.parseWeeks(course.weeks);
            check(weeks.valid && weeks.weeks[18] && weeks.weeks[14] && !weeks.weeks[13],
                    "wrapped week selection/parity changed");
            check(course.day == 2 && course.period == 9 && course.endPeriod == 10,
                    "wrapped course placement changed");
            check(course.room.equals("Lab") && course.teacher.equals("Tutor"),
                    "wrapped fields changed");
        }
    }

    @Test
    public void testInterleavedWeekContinuationStopsAtNextMetadata() throws Exception {
        String content = "BT 1 0 0 1 99.08 500 Tm (Robotics) Tj "
                + "1 0 0 1 99.08 488 Tm ((1-2节)2-4周,) Tj "
                + "1 0 0 1 202.93 488 Tm (Other title) Tj "
                + "1 0 0 1 99.08 476 Tm (8周/场地:Lab/教师:Tutor) Tj "
                + "1 0 0 1 99.08 464 Tm ((3-4节)20周/场地:Wrong) Tj ET";
        PdfCourseParser.ParseResult result = parseContent(content);
        check(result.courses.size() == 1, "a titleless following block must not be borrowed");
        check(result.courses.get(0).weeks.equals("2-4周,8周"), "interleaved continuation lost");
        check(result.courses.get(0).room.equals("Lab")
                && result.courses.get(0).teacher.equals("Tutor"), "next metadata leaked into fields");
    }

    @Test
    public void testReversedStreamsWithRotatedDanglingTitle() throws Exception {
        PdfCourseParser.ParseResult result = reversedRotatedPages("Robotics");
        check(result.courses.size() == 2, "reversed page continuation must recover orphan course");
        PdfCourseParser.ParsedCourse course = result.courses.get(0);
        check(course.name.equals("Robotics") && course.day == 3,
                "rotated dangling title mismatch: " + course.name);
        check(course.period == 7 && course.endPeriod == 8 && course.weeks.equals("2-14周"),
                "recovered course metadata changed");
        check(course.room.equals("Lab") && course.teacher.equals("Tutor"),
                "recovered course fields changed");
    }

    @Test
    public void testUnrelatedDanglingTitleIsNotBorrowed() throws Exception {
        PdfCourseParser.ParseResult result = reversedRotatedPages("Unrelated");
        check(result.courses.size() == 1 && result.courses.get(0).name.equals("Geometry"),
                "unrelated page-tail title must not be used for orphan metadata");
    }

    private static PdfCourseParser.ParseResult reversedRotatedPages(String tailTitle) throws Exception {
        String continuationPage = "BT 1 0 0 1 78.13 524 Tm (8) Tj "
                + "1 0 0 1 415.62 567 Tm ((7-8节)2-14周/场地:Lab/教师:Tutor) Tj "
                + "1 0 0 1 415.62 555 Tm (/教学班:Robotics-0042/考核方式:Test) Tj ET";
        String rotatedPage = "BT 0 1 -1 0 100 99.08 Tm (Geometry★) Tj "
                + "0 1 -1 0 112 99.08 Tm ((1-2节)2-14周/场地:Hall) Tj "
                + "0 1 -1 0 572.5 415.62 Tm (" + tailTitle + "◇) Tj ET";
        return PdfCourseParser.parseCourses(pdfWithStreams(stream(continuationPage), stream(rotatedPage)),
                MAX_PERIODS, MAX_STREAM, MAX_COURSES);
    }

    @Test public void testStandaloneHexStringAndLiteralOperatorText() throws Exception {
        String content = "BT 1 0 0 1 99.08 500 Tm <546D20546420542A> Tj "
                + "0 -12 Td " + METADATA_SINGLE + " ET";
        PdfCourseParser.ParseResult result = parseContent(content);
        check(result.courses.size() == 1 && result.courses.get(0).name.equals("Tm Td T*"),
                "hex Tj or literal operator boundaries failed");
    }

    @Test public void testTdTdLeadingAndNextLinePreserveCoordinates() throws Exception {
        String content = "BT 1 0 0 1 99.08 500 Tm (Math) Tj 0 -12 TD "
                + METADATA_SINGLE + " T* (/场地:Lab/教师:Tutor) Tj ET";
        PdfCourseParser.ParseResult result = parseContent(content);
        check(result.courses.size() == 1 && result.courses.get(0).room.equals("Lab"),
                "TD leading and T* continuation must preserve the course column");
        // Td is relative to the text line matrix, including the rotation, not device X/Y.
        String rotated = "BT 0 1 -1 0 100 99.08 Tm 12 TL (History) Tj T* "
                + METADATA_ROW + " T* (/场地:Hall) Tj ET";
        result = parseContent(rotated);
        check(result.courses.size() == 1 && result.courses.get(0).day == 0
                && result.courses.get(0).name.equals("History") && result.courses.get(0).room.equals("Hall"),
                "rotated line-leading translation changed the row axis");
    }

    @Test public void testRelativeDayTranslationDoesNotLeaveStaleTmPosition() throws Exception {
        String content = "BT 1 0 0 1 99.08 500 Tm 103.85 0 Td (Tuesday) Tj "
                + "0 -12 Td " + METADATA_SINGLE + " ET";
        PdfCourseParser.ParseResult result = parseContent(content);
        check(result.courses.size() == 1 && result.courses.get(0).day == 1,
                "Td must update weekday coordinates");
    }

    @Test public void testPerPageDynamicWeekdayColumns() throws Exception {
        String headers = "BT 1 0 0 1 1000 700 Tm (星期一) Tj 80 0 Td (星期二) Tj ET ";
        String course = "BT 1 0 0 1 1080 500 Tm (Dynamic) Tj 0 -12 Td " + METADATA_SINGLE + " ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                realPdf(objectsWithContent(headers + course, null)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 1 && result.courses.get(0).day == 1,
                "weekday headers must calibrate shifted/scaled columns");
    }

    @Test public void testDynamicReversedRotatedWeekdayRows() throws Exception {
        String content = "BT 0 1 -1 0 700 1000 Tm (周一) Tj 0 80 Td (周二) Tj ET "
                + "BT 0 1 -1 0 100 1000 Tm (Rotated) Tj 0 -12 TD " + METADATA_SINGLE + " ET";
        // The second header uses local x translation so its device y differs by 80.
        content = content.replace("0 80 Td", "80 0 Td");
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                realPdf(objectsWithContent(content, null)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 1 && result.courses.get(0).day == 0,
                "rotated headers must select the dynamic row axis");
    }

    @Test public void testToUnicodeBfcharIsBoundToSelectedFont() throws Exception {
        String cmap = cmap("3 beginbfchar <0001> <" + utf16Hex("数学") + "> "
                + "<0002> <" + utf16Hex("第1节 1-16周") + "> "
                + "<0003> <" + utf16Hex("/场地:Lab/教师:张三") + "> endbfchar");
        String content = "BT /F1 10 Tf 1 0 0 1 99.08 500 Tm <0001> Tj "
                + "0 -12 Td [<0002>] TJ 0 -12 Td <0003> Tj ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                realPdf(objectsWithContent(content, cmap)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 1 && result.courses.get(0).name.equals("数学")
                && result.courses.get(0).teacher.equals("张三"), "font glyph codes must use ToUnicode");
    }

    @Test public void testToUnicodeRangeScalarAndArray() throws Exception {
        for (String range : new String[]{
                "1 beginbfrange <0001> <0004> <0041> endbfrange",
                "1 beginbfrange <0001> <0004> [<0041> <0042> <0043> <0044>] endbfrange"}) {
            String cmap = cmap(range + " 1 beginbfchar <0005> <" + utf16Hex("第1节 1-16周") + "> endbfchar");
            String content = "BT /F1 10 Tf 1 0 0 1 99.08 500 Tm <0001000200030004> Tj "
                    + "0 -12 Td <0005> Tj ET";
            PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                    realPdf(objectsWithContent(content, cmap)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
            check(result.courses.size() == 1 && result.courses.get(0).name.equals("ABCD"),
                    "ToUnicode range decoding failed");
        }
    }

    @Test public void testTwoFontResourcesDoNotShareGlyphMap() throws Exception {
        String firstMap = cmap("2 beginbfchar <0001> <" + utf16Hex("Math")
                + "> <0002> <" + utf16Hex("第1节 1-16周") + "> endbfchar");
        String secondMap = cmap("2 beginbfchar <0001> <" + utf16Hex("History")
                + "> <0002> <" + utf16Hex("第2节 1-16周") + "> endbfchar");
        String content = "BT /F1 10 Tf 1 0 0 1 99.08 500 Tm <0001> Tj 0 -12 Td <0002> Tj "
                + "/F2 10 Tf 1 0 0 1 202.93 500 Tm <0001> Tj 0 -12 Td <0002> Tj ET";
        java.util.Map<Integer, byte[]> objects = objectsWithContent(content, firstMap);
        objects.put(3, ascii("<< /Type /Page /Parent 2 0 R /Resources << /Font << /F1 5 0 R /F2 7 0 R >> >> /Contents 4 0 R >>"));
        objects.put(7, ascii("<< /Type /Font /Subtype /Type0 /Encoding /Identity-H /ToUnicode 8 0 R >>"));
        objects.put(8, pdfStream(stream(secondMap), false));
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(realPdf(objects), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 2 && result.courses.get(0).name.equals("Math")
                && result.courses.get(1).name.equals("History"), "Tf selected an unrelated font map");
    }

    @Test public void testFallbackEncodingEvidenceIsScopedToFontResource() throws Exception {
        String mapped = cmap("2 beginbfchar <0001> <" + utf16Hex("Mapped Course")
                + "> <0002> <" + utf16Hex("第3-4节 2-16周双周") + "> endbfchar");
        String metadata = encodedText("第1-2节 1-16周", StandardCharsets.UTF_16BE, false, true);
        String content = "BT /F1 10 Tf 1 0 0 1 99.08 500 Tm "
                + encodedText("高等数学", StandardCharsets.UTF_16BE, false, true) + " 0 -12 Td " + metadata + " ET "
                + "BT /F2 10 Tf 1 0 0 1 202.93 500 Tm (Math) Tj /F1 10 Tf 0 -12 Td " + metadata + " ET "
                + "BT /F3 10 Tf 1 0 0 1 306.78 500 Tm <0001> Tj 0 -12 Td <0002> Tj ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(realPdf(mixedFontObjects(content, mapped)),
                MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 3, "mixed font courses missing");
        assertMixedCourse(result.courses.get(0), "高等数学", 0, 1, 2, "1-16周");
        assertMixedCourse(result.courses.get(1), "Math", 1, 1, 2, "1-16周");
        assertMixedCourse(result.courses.get(2), "Mapped Course", 2, 3, 4, "2-16周双周");
    }

    @Test public void testGraphicsRestoreRestoresFallbackFontResourceName() throws Exception {
        String metadata = encodedText("第1-2节 1-16周", StandardCharsets.UTF_16BE, false, true);
        String content = "BT /F2 10 Tf ET q BT /F1 10 Tf 1 0 0 1 99.08 500 Tm "
                + encodedText("高等数学", StandardCharsets.UTF_16BE, false, true) + " 0 -12 Td " + metadata + " ET Q "
                + "BT 1 0 0 1 202.93 500 Tm (Math) Tj /F1 10 Tf 0 -12 Td " + metadata + " ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(realPdf(mixedFontObjects(content,
                cmap("1 beginbfchar <0001> <0041> endbfchar"))), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 2, "graphics-restored mixed fonts missing");
        assertMixedCourse(result.courses.get(0), "高等数学", 0, 1, 2, "1-16周");
        assertMixedCourse(result.courses.get(1), "Math", 1, 1, 2, "1-16周");
    }

    @Test public void testGraphicsRestoreRestoresToUnicodeFontAndResourceTogether() throws Exception {
        String mapped = cmap("2 beginbfchar <0001> <" + utf16Hex("Mapped Course")
                + "> <0002> <" + utf16Hex("第3-4节 2-16周双周") + "> endbfchar");
        String metadata = encodedText("第1-2节 1-16周", StandardCharsets.UTF_16BE, false, true);
        String content = "BT /F3 10 Tf ET q BT /F1 10 Tf 1 0 0 1 99.08 500 Tm "
                + encodedText("高等数学", StandardCharsets.UTF_16BE, false, true) + " 0 -12 Td " + metadata + " ET Q "
                + "BT 1 0 0 1 202.93 500 Tm <0001> Tj 0 -12 Td <0002> Tj ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(realPdf(mixedFontObjects(content, mapped)),
                MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 2, "restored ToUnicode font missing");
        assertMixedCourse(result.courses.get(1), "Mapped Course", 1, 3, 4, "2-16周双周");
    }

    @Test public void testFractionalAndOverflowedCmapCountsAreRejected() {
        for (String count : new String[]{"1.5", "4294967297", "-0.5"}) {
            for (String block : new String[]{"beginbfchar <0001> <0041> endbfchar",
                    "beginbfrange <0001> <0001> <0041> endbfrange"}) {
                String mapping = cmap(count + " " + block);
                expectThrows(() -> PdfCourseParser.parseCourses(realPdf(objectsWithContent(
                        "BT /F1 10 Tf 1 0 0 1 99.08 500 Tm <0001> Tj ET", mapping)),
                        MAX_PERIODS, MAX_STREAM, MAX_COURSES));
            }
        }
    }

    @Test public void testToUnicodeDecodedBudgetAllowsExactBoundaryAndLigatures() throws Exception {
        PdfDocumentStreams.UnicodeMap mapping = new PdfDocumentStreams.UnicodeMap();
        mapping.put(new byte[]{1}, "ffi".getBytes(StandardCharsets.UTF_16BE));
        check(mapping.decode(new byte[]{1, 1}, 6).equals("ffiffi"),
                "legitimate multi-codepoint mappings must fit an exact character budget");
        expectThrows(() -> mapping.decode(new byte[]{1, 1}, 5));
        expectThrows(() -> mapping.decode(new byte[]{1}, 0));
    }

    @Test public void testToUnicodeExpansionAcrossItemsIsBounded() {
        String mapping = cmap("1 beginbfchar <0001> <" + utf16Hex("A".repeat(50)) + "> endbfchar");
        String run = "<" + "0001".repeat(22) + "> Tj";
        String content = "BT /F1 10 Tf 1 0 0 1 99.08 500 Tm " + run + " 0 -12 Td " + run
                + " /F2 10 Tf 0 -12 Td " + METADATA_SINGLE + " ET";
        java.util.Map<Integer, byte[]> objects = objectsWithContent(content, mapping);
        objects.put(3, ascii("<< /Type /Page /Parent 2 0 R /Resources << /Font << /F1 5 0 R /F2 7 0 R >> >> /Contents 4 0 R >>"));
        objects.put(7, ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"));
        expectThrows(() -> PdfCourseParser.parseCourses(realPdf(objects), MAX_PERIODS, 2048, MAX_COURSES));
    }

    @Test public void testToUnicodeExpansionAcrossPagesIsBounded() {
        String mapping = cmap("2 beginbfchar <0001> <" + utf16Hex("A".repeat(50))
                + "> <0002> <" + utf16Hex("第1-2节 1-16周") + "> endbfchar");
        String content = "BT /F1 10 Tf 1 0 0 1 99.08 500 Tm <" + "0001".repeat(22)
                + "> Tj 0 -12 Td <0002> Tj ET";
        java.util.Map<Integer, byte[]> objects = objectsWithContent(content, mapping);
        objects.put(2, ascii("<< /Type /Pages /Kids [3 0 R 7 0 R] /Count 2 >>"));
        objects.put(7, objects.get(3));
        expectThrows(() -> PdfCourseParser.parseCourses(realPdf(objects), MAX_PERIODS, 2048, MAX_COURSES));
    }

    private static void assertMixedCourse(PdfCourseParser.ParsedCourse course, String name,
            int day, int start, int end, String weeks) {
        check(course.name.equals(name), "font encoding changed course name: " + course.name + " instead of " + name);
        check(course.day == day && course.period == start && course.endPeriod == end && course.weeks.equals(weeks),
                "mixed font course placement/weeks changed: " + name);
    }

    private static java.util.Map<Integer, byte[]> mixedFontObjects(String content, String mapping) {
        java.util.Map<Integer, byte[]> objects = objectsWithContent(content, null);
        objects.put(3, ascii("<< /Type /Page /Parent 2 0 R /Resources << /Font << /F1 5 0 R /F2 7 0 R /F3 8 0 R >> >> /Contents 4 0 R >>"));
        objects.put(7, ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"));
        objects.put(8, ascii("<< /Type /Font /Subtype /Type0 /Encoding /Identity-H /ToUnicode 9 0 R >>"));
        objects.put(9, pdfStream(stream(mapping), true));
        return objects;
    }

    @Test public void testPageContentArrayRetainsTextStateAndIgnoresDecoyStreams() throws Exception {
        java.util.Map<Integer, byte[]> objects = objectsWithContent("BT 1 0 0 1 99.08 500 Tm (Joined) Tj", null);
        objects.put(3, ascii("<< /Type /Page /Parent 2 0 R /Contents [4 0 R 7 0 R] >>"));
        objects.put(7, pdfStream(stream("0 -12 Td " + METADATA_SINGLE + " ET"), true));
        objects.put(8, pdfStream(stream("BT 1 0 0 1 99.08 500 Tm (Decoy) Tj 0 -12 Td " + METADATA_ROW + " ET"), false));
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(realPdf(objects), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 1 && result.courses.get(0).name.equals("Joined"),
                "content arrays must share state, and unreferenced metadata must not become courses");
    }

    @Test public void testPageTreeOrderAndInheritedFontResources() throws Exception {
        String cmap = cmap("2 beginbfchar <0001> <" + utf16Hex("First")
                + "> <0002> <" + utf16Hex("第1节 1-16周") + "> endbfchar");
        java.util.Map<Integer, byte[]> objects = objectsWithContent(
                "BT /F1 10 Tf 1 0 0 1 99.08 500 Tm <0001> Tj 0 -12 Td <0002> Tj ET", cmap);
        objects.put(2, ascii("<< /Type /Pages /Kids [9 0 R 3 0 R] /Count 2 /Resources << /Font << /F1 5 0 R >> >> >>"));
        objects.put(3, ascii("<< /Type /Page /Parent 2 0 R /Contents 4 0 R >>"));
        objects.put(9, ascii("<< /Type /Page /Parent 2 0 R /Contents 10 0 R >>"));
        objects.put(10, pdfStream(stream("BT 1 0 0 1 202.93 500 Tm (Before) Tj 0 -12 Td " + METADATA_ROW + " ET"), false));
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(realPdf(objects), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 2 && result.courses.get(0).name.equals("Before")
                && result.courses.get(1).name.equals("First"), "page tree order or inherited font resources failed");
    }

    @Test public void testMalformedHexAndUnterminatedLiteralAreRejected() {
        for (String text : new String[]{"<0G> Tj", "(unterminated Tj", "<AB Tj"}) {
            expectThrows(() -> parseContent("BT 1 0 0 1 99.08 500 Tm " + text + " ET"));
        }
    }

    @Test public void testUnmappedGlyphAndExcessiveCmapRangeAreRejected() {
        for (String mapping : new String[]{
                "1 beginbfchar <0001> <0041> endbfchar",
                "1 beginbfrange <00000000> <FFFFFFFF> <0041> endbfrange",
                "1 beginbfrange <0001> <0003> [<0041>] endbfrange"}) {
            expectThrows(() -> PdfCourseParser.parseCourses(realPdf(objectsWithContent(
                    "BT /F1 10 Tf 1 0 0 1 99.08 500 Tm <0002> Tj ET", cmap(mapping))),
                    MAX_PERIODS, MAX_STREAM, MAX_COURSES));
        }
    }

    @Test public void testUnsupportedFilterEncryptedAndCyclicPagesAreRejected() {
        java.util.Map<Integer, byte[]> filtered = objectsWithContent("BT (x) Tj ET", null);
        filtered.put(4, ascii("<< /Length 1 /Filter /ASCII85Decode >>\nstream\nx\nendstream"));
        expectThrows(() -> PdfCourseParser.parseCourses(realPdf(filtered), MAX_PERIODS, MAX_STREAM, MAX_COURSES));
        java.util.Map<Integer, byte[]> cyclic = objectsWithContent("BT (x) Tj ET", null);
        cyclic.put(2, ascii("<< /Type /Pages /Kids [2 0 R] /Count 1 >>"));
        expectThrows(() -> PdfCourseParser.parseCourses(realPdf(cyclic), MAX_PERIODS, MAX_STREAM, MAX_COURSES));
        byte[] encrypted = ascii(new String(realPdf(objectsWithContent("BT (x) Tj ET", null)), StandardCharsets.ISO_8859_1)
                .replace("/Root 1 0 R", "/Root 1 0 R /Encrypt 5 0 R"));
        expectThrows(() -> PdfCourseParser.parseCourses(encrypted, MAX_PERIODS, MAX_STREAM, MAX_COURSES));
    }

    @Test public void testStructuredInflatedAndAggregateLimitsAreRejected() {
        String content = "BT 1 0 0 1 99.08 500 Tm (Math) Tj 0 -12 Td " + METADATA_SINGLE + " ET";
        java.util.Map<Integer, byte[]> objects = objectsWithContent(content, null);
        objects.put(4, pdfStream(stream(content + " ".repeat(3000)), true));
        expectThrows(() -> PdfCourseParser.parseCourses(realPdf(objects), MAX_PERIODS, 1000, MAX_COURSES));
        java.util.Map<Integer, byte[]> aggregate = objectsWithContent(content, null);
        aggregate.put(3, ascii("<< /Type /Page /Parent 2 0 R /Contents [4 0 R 7 0 R] >>"));
        aggregate.put(7, pdfStream(stream(" ".repeat(800)), false));
        expectThrows(() -> PdfCourseParser.parseCourses(realPdf(aggregate), MAX_PERIODS, 850, MAX_COURSES));
        java.util.Map<Integer, byte[]> repeated = objectsWithContent(content, null);
        repeated.put(2, ascii("<< /Type /Pages /Kids [3 0 R 9 0 R] /Count 2 >>"));
        repeated.put(9, ascii("<< /Type /Page /Parent 2 0 R /Contents 4 0 R >>"));
        expectThrows(() -> PdfCourseParser.parseCourses(realPdf(repeated), MAX_PERIODS,
                stream(content).length * 3 / 2, MAX_COURSES));
    }

    @Test public void testIndirectLengthAndEmbeddedEndstreamLiteral() throws Exception {
        String content = "BT 1 0 0 1 99.08 500 Tm (endstream) Tj 0 -12 Td " + METADATA_SINGLE + " ET\n\n";
        // A direct Length makes an embedded 'endstream' unambiguous.
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                realPdf(objectsWithContent(content, null)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 1 && result.courses.get(0).name.equals("endstream"),
                "stream delimiters inside a literal must honor direct Length");
        // With indirect Length, the non-ambiguous stream still resolves a forward length object.
        content = content.replace("(endstream)", "(Indirect)");
        java.util.Map<Integer, byte[]> objects = objectsWithContent(content, null);
        objects.put(4, ascii(new String(pdfStream(stream(content), false), StandardCharsets.ISO_8859_1)
                .replace("/Length " + stream(content).length, "/Length 7 0 R")));
        objects.put(7, ascii(Integer.toString(stream(content).length)));
        result = PdfCourseParser.parseCourses(realPdf(objects), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 1 && result.courses.get(0).name.equals("Indirect"),
                "indirect stream Length must retain payload newlines");
    }

    @Test public void testObjectStreamsInvalidDeflateAndCreditFragmentsAreHandledSafely() throws Exception {
        java.util.Map<Integer, byte[]> compressedObjects = objectsWithContent("BT (x) Tj ET", null);
        compressedObjects.put(7, ascii("<< /Type /ObjStm /Length 1 >>\nstream\nx\nendstream"));
        expectThrows(() -> PdfCourseParser.parseCourses(realPdf(compressedObjects), MAX_PERIODS, MAX_STREAM, MAX_COURSES));
        java.util.Map<Integer, byte[]> invalidDeflate = objectsWithContent("BT (x) Tj ET", null);
        invalidDeflate.put(4, ascii("<< /Length 1 /Filter /FlateDecode >>\nstream\nx\nendstream"));
        expectThrows(() -> PdfCourseParser.parseCourses(realPdf(invalidDeflate), MAX_PERIODS, MAX_STREAM, MAX_COURSES));
        String content = "BT 1 0 0 1 99.08 520 Tm (分:3.5) Tj 0 -12 Td (Python) Tj "
                + "0 -12 Td " + METADATA_SINGLE + " ET";
        PdfCourseParser.ParseResult result = parseContent(content);
        check(result.courses.size() == 1 && result.courses.get(0).name.equals("Python"),
                "fragmented credit metadata must not pollute course titles");
    }

    @Test public void testCtmScaleTranslateAndGraphicsRestoreUseCalibratedHeaders() throws Exception {
        String content = "BT 1 0 0 1 1000 700 Tm (周一) Tj 80 0 Td (周二) Tj ET "
                + "q 2 0 0 2 0 0 cm BT 1 0 0 1 540 250 Tm (Scaled) Tj 0 -12 Td " + METADATA_SINGLE + " ET Q "
                + "BT 1 0 0 1 1000 450 Tm (Restored) Tj 0 -12 Td " + METADATA_ROW + " ET";
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                realPdf(objectsWithContent(content, null)), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 2 && result.courses.get(0).day == 1
                && result.courses.get(1).day == 0, "cm and Q must transform and restore weekday coordinates");
        check(result.debugLog.contains("header-calibrated"), "debug must identify calibrated weekday grids");
    }

    @Test public void testSafeQuarterTurnKeepsLegacyCoordinateButUnsafeTransformRejects() throws Exception {
        String course = "BT 1 0 0 1 99.08 500 Tm (Math) Tj 0 -12 Td " + METADATA_SINGLE + " ET";
        PdfCourseParser.ParseResult result = parseContent("q 0 1 -1 0 595 0 cm " + course + " Q");
        check(result.courses.size() == 1 && result.courses.get(0).day == 0,
                "quarter turn that preserves the weekday coordinate must remain supported");
        check(result.debugLog.contains("legacy-fallback"), "debug must identify legacy grid fallback");
        expectThrows(() -> parseContent("q 1 0 0 1 103.85 0 cm " + course + " Q"));
        expectThrows(() -> parseContent("q 1 0.2 0 1 0 0 cm " + course + " Q"));
        expectThrows(() -> parseContent("Q " + course));
    }

    @Test public void testFormXObjectCannotProduceSilentPartialImportButImagesCanBeIgnored() throws Exception {
        String course = "BT 1 0 0 1 99.08 500 Tm (Math) Tj 0 -12 Td " + METADATA_SINGLE + " ET /X1 Do";
        java.util.Map<Integer, byte[]> form = objectsWithContent(course, null);
        form.put(3, ascii("<< /Type /Page /Parent 2 0 R /Resources << /XObject << /X1 7 0 R >> >> /Contents 4 0 R >>"));
        form.put(7, ascii(new String(pdfStream(stream("BT (Hidden course) Tj ET"), false), StandardCharsets.ISO_8859_1)
                .replace("<< /Length", "<< /Type /XObject /Subtype /Form /BBox [0 0 100 100] /Length")));
        expectThrows(() -> PdfCourseParser.parseCourses(realPdf(form), MAX_PERIODS, MAX_STREAM, MAX_COURSES));
        java.util.Map<Integer, byte[]> image = objectsWithContent(course, null);
        image.put(3, form.get(3));
        image.put(7, ascii("<< /Type /XObject /Subtype /Image /Width 1 /Height 1 /BitsPerComponent 8 /ColorSpace /DeviceGray /Length 1 >>\nstream\nx\nendstream"));
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(realPdf(image), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 1, "ordinary image resource must not prevent text-course extraction");
    }

    @Test public void testXrefStreamDoesNotRejectAvailableOrdinaryPageObjects() throws Exception {
        String content = "BT 1 0 0 1 99.08 500 Tm (Math) Tj 0 -12 Td " + METADATA_SINGLE + " ET";
        java.util.Map<Integer, byte[]> objects = objectsWithContent(content, null);
        objects.put(14, ascii("<< /Type /XRef /Size 15 /W [1 2 1] /Index [0 1] /Root 1 0 R /Length 4 >>\nstream\nxxxx\nendstream"));
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(realPdf(objects), MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 1, "xref streams may be ignored when ordinary objects are available");
    }

    @Test public void testSyntheticFixtureFullFields() throws Exception {
        byte[] fixture;
        try (java.io.InputStream input = PdfCourseParserTest.class.getResourceAsStream("/synthetic-timetable.pdf")) {
            check(input != null, "synthetic PDF must be available as a classpath fixture");
            fixture = input.readAllBytes();
        }
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(fixture,
                MAX_PERIODS, MAX_STREAM, MAX_COURSES);
        check(result.courses.size() == 4, "synthetic course count changed");
        List<String> rows = new java.util.ArrayList<>();
        for (PdfCourseParser.ParsedCourse course : result.courses) {
            rows.add(course.day + "\t" + course.period + "\t" + course.endPeriod + "\t" + course.weeks
                    + "\t" + course.room + "\t" + course.teacher + "\t" + course.name);
        }
        java.util.Collections.sort(rows);
        check(rows.equals(Arrays.asList(
                "0\t1\t2\t1-16周\t示例楼A101\t示例教师甲\t虚构数学",
                "1\t3\t4\t1-16周单周\t示例楼B202\t示例教师乙\t虚构英语",
                "2\t5\t6\t1-8周,10-16周\t示例楼C303\t示例教师丙\t虚构实验",
                "3\t7\t8\t2-16周双周\t示例楼D404\t示例教师丁\t虚构设计")),
                "synthetic fields or cross-page title changed: " + rows);
    }

    private static String cmap(String body) {
        return "/CIDInit /ProcSet findresource begin 12 dict begin begincmap "
                + "/CMapName /Test def /CMapType 2 def 1 begincodespacerange <0000> <FFFF> endcodespacerange "
                + body + " endcmap CMapName currentdict /CMap defineresource pop end end";
    }
    private static String utf16Hex(String text) {
        StringBuilder hex = new StringBuilder();
        for (byte b : text.getBytes(StandardCharsets.UTF_16BE)) hex.append(String.format(java.util.Locale.ROOT, "%02X", b & 255));
        return hex.toString();
    }
    private static byte[] ascii(String text) { return text.getBytes(StandardCharsets.ISO_8859_1); }
    private static java.util.Map<Integer, byte[]> objectsWithContent(String content, String cmap) {
        java.util.Map<Integer, byte[]> objects = new java.util.LinkedHashMap<>();
        objects.put(1, ascii("<< /Type /Catalog /Pages 2 0 R >>"));
        objects.put(2, ascii("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"));
        objects.put(3, ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 1200 800] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>"));
        objects.put(4, pdfStream(stream(content), false));
        objects.put(5, ascii("<< /Type /Font /Subtype /Type0 /BaseFont /Test /Encoding /Identity-H /DescendantFonts [11 0 R] "
                + (cmap == null ? "" : "/ToUnicode 6 0 R ") + ">>"));
        objects.put(11, ascii("<< /Type /Font /Subtype /CIDFontType2 /BaseFont /Test /DW 1000 /CIDToGIDMap /Identity "
                + "/CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >> /FontDescriptor 12 0 R >>"));
        objects.put(12, ascii("<< /Type /FontDescriptor /FontName /Test /Flags 4 /FontBBox [0 -200 1000 1000] "
                + "/ItalicAngle 0 /Ascent 800 /Descent -200 /CapHeight 700 /StemV 80 >>"));
        if (cmap != null) objects.put(6, pdfStream(stream(cmap), true));
        return objects;
    }
    private static byte[] pdfStream(byte[] bytes, boolean compressed) {
        if (compressed) {
            ByteArrayOutputStream compressedBytes = new ByteArrayOutputStream();
            try (java.util.zip.DeflaterOutputStream zip = new java.util.zip.DeflaterOutputStream(compressedBytes)) {
                zip.write(bytes);
            } catch (java.io.IOException e) { throw new AssertionError(e); }
            bytes = compressedBytes.toByteArray();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "<< /Length " + bytes.length + (compressed ? " /Filter /FlateDecode" : "") + " >>\nstream\n");
        out.write(bytes, 0, bytes.length); write(out, "\nendstream"); return out.toByteArray();
    }
    private static byte[] realPdf(java.util.Map<Integer, byte[]> objects) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); write(out, "%PDF-1.4\n");
        java.util.Map<Integer, Integer> offsets = new java.util.HashMap<>(); int max = 0;
        for (java.util.Map.Entry<Integer, byte[]> object : objects.entrySet()) {
            int id = object.getKey(); max = Math.max(max, id); offsets.put(id, out.size());
            write(out, id + " 0 obj\n"); out.write(object.getValue(), 0, object.getValue().length); write(out, "\nendobj\n");
        }
        int xref = out.size(); write(out, "xref\n0 " + (max + 1) + "\n0000000000 65535 f \n");
        for (int id = 1; id <= max; id++) write(out, String.format(java.util.Locale.ROOT, "%010d 00000 %s \n", offsets.getOrDefault(id, 0), offsets.containsKey(id) ? "n" : "f"));
        write(out, "trailer\n<< /Size " + (max + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return out.toByteArray();
    }

    private static PdfCourseParser.ParseResult parseContent(String content) throws Exception {
        return PdfCourseParser.parseCourses(pdfWithStreams(stream(content)),
                MAX_PERIODS, MAX_STREAM, MAX_COURSES);
    }

    private static byte[] pdfWithStreams(byte[]... streamContents) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.4\n");
        for (byte[] s : streamContents) {
            write(out, "stream\n");
            out.write(s, 0, s.length);
            write(out, "\nendstream\n");
        }
        write(out, "%%EOF\n");
        return out.toByteArray();
    }

    private static byte[] stream(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private static void write(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.ISO_8859_1);
        out.write(bytes, 0, bytes.length);
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void expectThrows(ThrowingRunnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("expected ImportException but nothing was thrown");
        } catch (ImportException expected) {
            // ok
        } catch (Exception error) {
            throw new AssertionError("unexpected exception: " + error);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
