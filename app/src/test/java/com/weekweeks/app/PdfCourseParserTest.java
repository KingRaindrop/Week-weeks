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
        PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(
                pdfWithStreams(stream(content)), MAX_PERIODS, MAX_STREAM, 1);
        check(result.courses.size() == 1, "course count limit should cap results, got " + result.courses.size());
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
