package com.weekweeks.app;

import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.*;

public class XlsxTimetableParserTest {
    private static final String NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String HEADER = row(cell("Course") + cell("Day") + cell("Periods") + cell("Weeks"));
    private static String cell(String text) { return "<c t=\"inlineStr\"><is><t>" + text + "</t></is></c>"; }
    private static String row(String cells) { return "<row>" + cells + "</row>"; }
    private static String worksheet(String rows) { return "<worksheet xmlns=\"" + NS + "\"><sheetData>" + rows + "</sheetData></worksheet>"; }
    private static Map<String, String> workbook(String rows) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("xl/workbook.xml", "<workbook xmlns=\"" + NS + "\" xmlns:r=\"" + REL + "\"><sheets><sheet name=\"Courses\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
        files.put("xl/_rels/workbook.xml.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"" + REL + "/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
        files.put("xl/worksheets/sheet1.xml", worksheet(rows));
        return files;
    }
    private static byte[] zip(Map<String, String> files) throws Exception {
        return zip(files, StandardCharsets.UTF_8);
    }
    private static byte[] zip(Map<String, String> files, java.nio.charset.Charset charset) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(charset));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
    private static TextTimetableParser.Result parse(Map<String, String> files) throws Exception {
        return XlsxTimetableParser.parse(zip(files), 16, 500);
    }
    @Test public void readsRealOpenpyxlWorkbook() throws Exception {
        try (java.io.InputStream stream = getClass().getResourceAsStream("/timetable-list-template.xlsx")) {
            assertNotNull(stream);
            TimetableFileParser.Result result = TimetableFileParser.parse(stream.readAllBytes(), "template.xlsx", 16, 1024 * 1024, 500);
            assertEquals(3, result.courses.size());
            assertEquals("高等数学", result.courses.get(0).name);
            assertEquals(2, result.courses.get(0).endPeriod);
            assertEquals("1-8周,10-16周", result.courses.get(2).weeks);
        }
    }
    @Test public void readsInlineStringsNumericCellsAndSparseColumns() throws Exception {
        String data = row("<c r=\"A2\" t=\"inlineStr\"><is><t>Math</t></is></c><c r=\"B2\"><v>1</v></c><c r=\"C2\"><v>2</v></c>");
        TextTimetableParser.Result result = parse(workbook(HEADER + data));
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(1, result.courses.size());
        assertEquals("Math", result.courses.get(0).name);
        assertEquals(0, result.courses.get(0).day);
        assertEquals(2, result.courses.get(0).period);
        assertEquals("", result.courses.get(0).weeks);
    }
    @Test public void readsSharedAndRichStringsWithoutFormattingArtifacts() throws Exception {
        Map<String, String> files = workbook(HEADER + row("<c t=\"s\"><v>0</v></c>" + cell("Monday") + cell("1-2") + cell("1-16周")));
        files.put("xl/sharedStrings.xml", "<sst xmlns=\"" + NS + "\"><si><r><t>高等</t></r><r><t>数学</t></r><rPh sb=\"0\" eb=\"2\"><t>gaodeng</t></rPh></si></sst>");
        assertEquals("高等数学", parse(files).courses.get(0).name);
    }
    @Test public void ignoresHiddenSheetsAndPreservesVisibleSheetOrder() throws Exception {
        Map<String, String> files = workbook(HEADER + row(cell("First") + cell("Mon") + cell("1") + cell("")));
        files.put("xl/workbook.xml", files.get("xl/workbook.xml").replace("</sheets>", "<sheet name=\"Hidden\" state=\"hidden\" sheetId=\"2\" r:id=\"rId2\"/><sheet name=\"Next\" sheetId=\"3\" r:id=\"rId3\"/></sheets>"));
        files.put("xl/_rels/workbook.xml.rels", files.get("xl/_rels/workbook.xml.rels").replace("</Relationships>", "<Relationship Id=\"rId3\" Type=\"" + REL + "/worksheet\" Target=\"/xl/worksheets/sheet3.xml\"/></Relationships>"));
        files.put("xl/worksheets/sheet3.xml", worksheet(HEADER + row(cell("Next") + cell("Tue") + cell("3-4") + cell(""))));
        TextTimetableParser.Result result = parse(files);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(2, result.courses.size());
        assertEquals("First", result.courses.get(0).name);
        assertEquals("Next", result.courses.get(1).name);
        TextTimetableParser.Result capped = XlsxTimetableParser.parse(zip(files), 16, 1);
        assertFalse(capped.errors.isEmpty());
    }
    @Test public void rejectsFormulasEvenWithCachedValuesAndRejectsMerges() throws Exception {
        Map<String, String> formula = workbook(HEADER + row(cell("Math") + cell("Mon") + "<c><f>1+1</f><v>2</v></c>"));
        assertFalse(parse(formula).errors.isEmpty());
        Map<String, String> merged = workbook(HEADER + row(cell("Math") + cell("Mon") + cell("1-2")));
        merged.put("xl/worksheets/sheet1.xml", merged.get("xl/worksheets/sheet1.xml").replace("</worksheet>", "<mergeCells><mergeCell ref=\"A1:B1\"/></mergeCells></worksheet>"));
        assertFalse(parse(merged).errors.isEmpty());
    }
    @Test public void rejectsDtdAndOutOfRangeSharedString() throws Exception {
        Map<String, String> dtd = workbook(HEADER);
        dtd.put("xl/workbook.xml", "<!DOCTYPE workbook [<!ENTITY x SYSTEM \"file:///secret\">]>" + dtd.get("xl/workbook.xml"));
        rejects(dtd);
        rejects(workbook(HEADER + row("<c t=\"s\"><v>9</v></c>" + cell("Mon") + cell("1"))));
    }
    @Test public void rejectsNonUtf8DtdEncodingBypass() throws Exception {
        Map<String, String> files = workbook(HEADER);
        files.put("xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"Cp037\"?><!DOCTYPE workbook [<!ENTITY x \"injected\">]>" + files.get("xl/workbook.xml"));
        try { XlsxTimetableParser.parse(zip(files, java.nio.charset.Charset.forName("Cp037")), 16, 500); fail("DTD encoding bypass"); }
        catch (ImportException expected) { assertFalse(expected.getMessage().isEmpty()); }
    }
    @Test public void rejectsInvalidCellAddressesAndRowOrdering() throws Exception {
        for (String address : new String[]{"A0junk", "A0", "A-1", "A2junk", "A3"}) {
            rejects(workbook(HEADER + "<row r=\"2\"><c r=\"" + address + "\" t=\"inlineStr\"><is><t>Math</t></is></c>" + cell("Mon") + cell("1") + "</row>"));
        }
        rejects(workbook("<row r=\"2\"/><row r=\"1\"/>"));
        rejects(workbook("<row r=\"0\"/>"));
    }
    @Test public void uncachedFormulaOnlySheetIsNotSilentlySkipped() throws Exception {
        assertFalse(parse(workbook(row("<c><f>1+1</f></c>"))).errors.isEmpty());
    }
    @Test public void retainsSparseSourceRowNumbersInErrors() throws Exception {
        TextTimetableParser.Result result = parse(workbook(HEADER + "<row r=\"9\">" + cell("Broken") + "</row>"));
        assertFalse(result.errors.isEmpty());
        assertTrue(result.errors.toString(), result.errors.get(0).contains("9"));
    }
    @Test public void rejectsZipBombsExcessiveRowsAndInvalidCellReferences() throws Exception {
        Map<String, String> bomb = workbook(HEADER);
        bomb.put("xl/sharedStrings.xml", "x".repeat(2 * 1024 * 1024 + 1));
        rejects(bomb);
        rejects(workbook("<row/>".repeat(2001)));
        rejects(workbook(row("<c r=\"ZZZZZZZZZZZZZ1\"><v>1</v></c>")));
    }
    @Test public void doesNotSilentlyImportMalformedRowsOrUnrelatedSheets() throws Exception {
        TextTimetableParser.Result result = parse(workbook(HEADER + row(cell("Math") + cell("Mon") + cell("1-2")) + row(cell("Broken"))));
        assertEquals(1, result.courses.size());
        assertFalse(result.errors.isEmpty());
        try { TimetableFileParser.parse(zip(workbook(HEADER + row(cell("Math") + cell("Mon") + cell("1-2")) + row(cell("Broken")))), "test.xlsx", 16, 1024, 500); fail(); }
        catch (ImportException expected) { assertFalse(expected.getMessage().isEmpty()); }
    }
    @Test public void preSeparatedWorkbookCellsNeverTreatPunctuationAsCsvMetadata() throws Exception {
        TextTimetableParser.Result result = parse(workbook(HEADER
                + row(cell("Math; Logic, A|B") + cell("Mon") + cell("1") + cell("1-2周;4-8周"))
                + row(cell("sep=;") + cell("Tue") + cell("2") + cell(""))));
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(2, result.courses.size());
        assertEquals("Math; Logic, A|B", result.courses.get(0).name);
        assertEquals("1-2周;4-8周", result.courses.get(0).weeks);
        assertEquals("sep=;", result.courses.get(1).name);
    }
    @Test public void formattedEmptyTrailingCellsDoNotBecomeUnexpectedColumns() throws Exception {
        TextTimetableParser.Result result = parse(workbook(HEADER
                + row(cell("Math") + cell("Mon") + cell("1") + cell("1-16")
                        + "<c r=\"H2\" s=\"1\"/>")));
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(1, result.courses.size());
        assertEquals("Math", result.courses.get(0).name);
        assertEquals("1-16", result.courses.get(0).weeks);
    }
    @Test public void populatedTrailingCellsStillFailRatherThanDiscardData() throws Exception {
        TextTimetableParser.Result result = parse(workbook(HEADER
                + row(cell("Math") + cell("Mon") + cell("1") + cell("1-16")
                        + "<c r=\"H2\" t=\"inlineStr\"><is><t>unexpected</t></is></c>")));
        assertFalse(result.errors.isEmpty());
        assertTrue(result.courses.isEmpty());
    }
    @Test public void hashPrefixedWorkbookCourseIsNotDiscardedAsComment() throws Exception {
        TextTimetableParser.Result result = parse(workbook(HEADER
                + row(cell("# Seminar") + cell("Mon") + cell("1"))
                + row(cell("Logic") + cell("Tue") + cell("2"))));
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(2, result.courses.size());
        assertEquals("# Seminar", result.courses.get(0).name);
    }

    private static void rejects(Map<String, String> files) throws Exception {
        try { parse(files); fail("Expected import error"); }
        catch (ImportException expected) { assertFalse(expected.getMessage().isEmpty()); }
    }
}
