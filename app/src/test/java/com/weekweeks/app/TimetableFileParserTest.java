package com.weekweeks.app;

import org.junit.After;
import org.junit.Test;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class TimetableFileParserTest {
    private static final String ROW = "高等数学 | 周一 | 1-2 | 1-16周 | A101 | 张老师";
    private TimetableFileParser.Result parse(byte[] bytes, String name) throws Exception {
        return TimetableFileParser.parse(bytes, name, 16, 1024 * 1024, 500);
    }
    @After public void resetLanguage() { UiLang.set(UiLang.HANS); }
    @Test public void utf8CsvFileUsesHeaderAndQuotedWeeks() throws Exception {
        String csv = "Course,Day,Periods,Weeks,Room,Teacher\r\nMath,Monday,1-2,\"1-4周,8-16周\",A101,Teacher";
        TimetableFileParser.Result result = parse(csv.getBytes(StandardCharsets.UTF_8), "courses.CSV");
        assertEquals("CSV", result.source);
        assertEquals(1, result.courses.size());
        assertEquals("1-4周,8-16周", result.courses.get(0).weeks);
    }
    @Test public void utf8BomAndUnknownFilenameWork() throws Exception {
        assertEquals("高等数学", parse(("\ufeff" + ROW).getBytes(StandardCharsets.UTF_8), null).courses.get(0).name);
    }
    @Test public void utf16LeExcelExportAndUtf16BeWork() throws Exception {
        byte[] le = ROW.getBytes(StandardCharsets.UTF_16LE);
        byte[] marked = new byte[le.length + 2];
        marked[0] = (byte) 0xff; marked[1] = (byte) 0xfe;
        System.arraycopy(le, 0, marked, 2, le.length);
        assertEquals(1, parse(marked, "timetable.txt").courses.size());
        assertEquals(1, parse(ROW.getBytes(StandardCharsets.UTF_16), "timetable.txt").courses.size());
    }
    @Test public void gb18030ExportWorksWithoutReplacementCharacters() throws Exception {
        assertEquals("张老师", parse(ROW.getBytes(Charset.forName("GB18030")), "课程.txt").courses.get(0).teacher);
    }
    @Test public void partialFileNeverSucceedsSilently() throws Exception {
        rejects((ROW + "\nBroken course").getBytes(StandardCharsets.UTF_8), "timetable.txt");
    }
    @Test public void legacyXlsAndRenamedBrokenFormatsAreRejected() throws Exception {
        rejects(new byte[]{(byte)0xd0,(byte)0xcf,0x11,(byte)0xe0,1,2}, "old.xls");
        rejects(ROW.getBytes(StandardCharsets.UTF_8), "bad.xlsx");
        rejects(ROW.getBytes(StandardCharsets.UTF_8), "bad.pdf");
    }
    @Test public void emptyBinaryAndMalformedBomTextAreRejected() throws Exception {
        rejects(new byte[0], "empty.txt");
        rejects(new byte[]{0, 1, 2, 3}, "bad.txt");
        rejects(new byte[]{(byte)0xef,(byte)0xbb,(byte)0xbf,(byte)0xff}, "bad.txt");
        rejects(new byte[]{(byte)0xff,(byte)0xfe,0x31}, "bad.tsv");
    }
    @Test public void excelSemicolonCsvSupportsBomAndUtf16SeparatorMetadata() throws Exception {
        String text = "sep=;\r\nCourse;Day;Periods;Weeks\r\nMath;Monday;1-2;1-4周,8-16周";
        TimetableFileParser.Result utf8 = parse(("\uFEFF" + text).getBytes(StandardCharsets.UTF_8), "excel.CSV");
        assertEquals("CSV", utf8.source);
        assertEquals("1-4周,8-16周", utf8.courses.get(0).weeks);
        TimetableFileParser.Result utf16 = parse(text.getBytes(StandardCharsets.UTF_16), "excel.csv");
        assertEquals(1, utf16.courses.size());
        assertEquals(2, utf16.courses.get(0).endPeriod);
    }
    @Test public void semicolonPartialAndMismatchedDeclarationFilesStayAllOrNothing() throws Exception {
        rejects("sep=;\nCourse;Day;Periods\nMath;Mon;1\nBroken;Mondayish;2".getBytes(StandardCharsets.UTF_8), "bad.csv");
        rejects("sep=;\nCourse,Day,Periods\nMath,Mon,1".getBytes(StandardCharsets.UTF_8), "wrong.csv");
        rejects("sep=;;\nMath,Mon,1".getBytes(StandardCharsets.UTF_8), "bad-separator.csv");
        try {
            TimetableFileParser.parse("sep=;\nMath;Mon;1\nLogic;Tue;2".getBytes(StandardCharsets.UTF_8),
                    "too-many.csv", 16, 1024, 1);
            fail("Course capacity must not return partial success");
        } catch (ImportException expected) { assertTrue(expected.getMessage().contains("上限")); }
    }
    @Test public void unknownFilenameCsvDoesNotSplitLiteralPipesInCourseName() throws Exception {
        assertEquals("Math|Logic|Lab", parse("Math|Logic|Lab,Mon,1".getBytes(StandardCharsets.UTF_8), null).courses.get(0).name);
    }
    @Test public void quotedHashCourseIsPreservedAndCannotHideInvalidFileRows() throws Exception {
        String header = "Course,Day,Periods\n";
        TimetableFileParser.Result result = parse((header + "\"# Seminar\",Mon,1\nLogic,Tue,2")
                .getBytes(StandardCharsets.UTF_8), "courses.csv");
        assertEquals(2, result.courses.size());
        assertEquals("# Seminar", result.courses.get(0).name);
        rejects((header + "\"# Seminar\",Mondayish,1\nLogic,Tue,2")
                .getBytes(StandardCharsets.UTF_8), "broken.csv");
    }

    private void rejects(byte[] bytes, String name) throws Exception {
        try { parse(bytes, name); fail("Expected import error"); }
        catch (ImportException expected) { assertFalse(expected.getMessage().isEmpty()); }
    }
}
