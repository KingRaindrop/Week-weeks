package com.weekweeks.app;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** Bounded, non-evaluating reader for course lists and explicit weekly grids in XLSX. No macros or formulas execute. */
final class XlsxTimetableParser {
    private static final int MAX_XML_BYTES = 2 * 1024 * 1024;
    private static final int MAX_TOTAL_BYTES = 16 * 1024 * 1024;
    private static final int MAX_ROWS = 2000;
    private static final int MAX_COLUMNS = 64;

    static TextTimetableParser.Result parse(byte[] bytes, int maxPeriods, int maxCourses) throws ImportException {
        try {
            Map<String, byte[]> files = unzip(bytes);
            if (!files.containsKey("xl/workbook.xml")) throw new SAXException("Missing workbook");
            List<String> strings = new ArrayList<>();
            if (files.containsKey("xl/sharedStrings.xml")) {
                xml(files.get("xl/sharedStrings.xml"), new DefaultHandler() {
                    StringBuilder value;
                    boolean text, phonetic;
                    public void startElement(String uri, String local, String q, Attributes a) throws SAXException {
                        if (local.equals("si")) value = new StringBuilder();
                        if (local.equals("rPh")) phonetic = true;
                        if (local.equals("t")) text = !phonetic;
                    }
                    public void characters(char[] ch, int start, int length) throws SAXException {
                        if (text && value != null) {
                            value.append(ch, start, length);
                            if (value.length() > TextTimetableParser.MAX_TEXT_CHARS) throw new SAXException("String too long");
                        }
                    }
                    public void endElement(String uri, String local, String q) throws SAXException {
                        if (local.equals("t")) text = false;
                        if (local.equals("rPh")) phonetic = false;
                        if (local.equals("si")) {
                            strings.add(value.toString());
                            if (strings.size() > 20000) throw new SAXException("Too many strings");
                            value = null;
                        }
                    }
                });
            }
            Map<String, String> targets = new HashMap<>();
            byte[] rels = files.get("xl/_rels/workbook.xml.rels");
            if (rels == null) throw new SAXException("Missing workbook relationships");
            xml(rels, new DefaultHandler() {
                public void startElement(String uri, String local, String q, Attributes a) throws SAXException {
                    if (!local.equals("Relationship")) return;
                    String target = a.getValue("Target");
                    String type = a.getValue("Type");
                    if (type != null && type.endsWith("/worksheet") && !"External".equals(a.getValue("TargetMode"))) {
                        if (target == null || target.contains("..") || target.contains("\\")) throw new SAXException("Invalid sheet path");
                        targets.put(a.getValue("Id"), target.startsWith("/") ? target.substring(1) : "xl/" + target);
                    }
                }
            });
            List<String[]> sheets = new ArrayList<>();
            xml(files.get("xl/workbook.xml"), new DefaultHandler() {
                public void startElement(String uri, String local, String q, Attributes a) throws SAXException {
                    if (local.equals("sheet") && !"hidden".equals(a.getValue("state")) && !"veryHidden".equals(a.getValue("state"))) {
                        String id = a.getValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
                        if (id == null) id = a.getValue("r:id");
                        String target = targets.get(id);
                        if (target == null) throw new SAXException("Missing sheet relationship");
                        sheets.add(new String[]{a.getValue("name"), target});
                    }
                }
            });
            List<PdfCourseParser.ParsedCourse> courses = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            for (String[] sheet : sheets) {
                byte[] data = files.get(sheet[1]);
                if (data == null) throw new SAXException("Missing sheet data");
                SheetReader reader = new SheetReader(strings);
                xml(data, reader);
                boolean populated = false;
                for (List<String> row : reader.rows) for (String cell : row) if (!cell.trim().isEmpty()) populated = true;
                if (reader.unsupportedValue) {
                    errors.add(sheet[0] + ": " + UiLang.t("含公式、布尔值或错误值，请导出为纯值课表", "含公式、布林值或錯誤值，請匯出為純值課表", "The worksheet contains formulas, booleans or errors. Export a values-only timetable"));
                    continue;
                }
                if (!populated && reader.merges.isEmpty()) continue;
                TextTimetableParser.Result result = TextTimetableParser.parseRows(reader.rows, maxPeriods, Math.max(0, maxCourses - courses.size()), reader.merges);
                courses.addAll(result.courses);
                for (String error : result.errors) errors.add(sheet[0] + ": " + error);
            }
            if (courses.isEmpty() && errors.isEmpty()) errors.add(UiLang.t("工作簿中没有可导入的课程", "活頁簿中沒有可匯入的課程", "No courses found in workbook"));
            return new TextTimetableParser.Result(courses, errors);
        } catch (Exception error) {
            throw new ImportException(UiLang.t("无法读取 XLSX：文件损坏、结构不受支持或超过安全上限", "無法讀取 XLSX：檔案損壞、結構不受支援或超過安全上限", "Cannot read XLSX: damaged, unsupported or exceeds safety limits"));
        }
    }

    private static Map<String, byte[]> unzip(byte[] bytes) throws Exception {
        Map<String, byte[]> files = new HashMap<>();
        Set<String> seen = new HashSet<>();
        int total = 0, count = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > 256 || !seen.add(entry.getName())) throw new SAXException("Too many or duplicate entries");
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int size = 0, n;
                boolean relevant = entry.getName().startsWith("xl/") && (entry.getName().endsWith(".xml") || entry.getName().endsWith(".rels"));
                while ((n = zip.read(buffer)) != -1) {
                    total += n;
                    size += n;
                    if (total > MAX_TOTAL_BYTES || (relevant && size > MAX_XML_BYTES)) throw new SAXException("Archive limit");
                    if (relevant) out.write(buffer, 0, n);
                }
                if (relevant) files.put(entry.getName(), out.toByteArray());
            }
        }
        return files;
    }

    private static void xml(byte[] bytes, DefaultHandler handler) throws Exception {
        // XLSX XML must be UTF-8 here. Reject DTDs before SAX, including UTF-16 NUL encodings.
        String raw = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        if (raw.indexOf('\0') >= 0 || raw.contains("<!DOCTYPE") || raw.contains("<!ENTITY")) throw new SAXException("DTD not allowed");
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        org.xml.sax.XMLReader reader = factory.newSAXParser().getXMLReader();
        reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
        reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        reader.setEntityResolver((publicId, systemId) -> { throw new SAXException("External entity not allowed"); });
        reader.setContentHandler(handler);
        reader.setErrorHandler(handler);
        if (raw.startsWith("\uFEFF")) raw = raw.substring(1);
        // A character source pins the decoded encoding; SAX must not reinterpret raw bytes.
        reader.parse(new InputSource(new java.io.StringReader(raw)));
    }

    private static final Pattern MERGE_REF = Pattern.compile("([A-Z]{1,2})([1-9][0-9]{0,3}):([A-Z]{1,2})([1-9][0-9]{0,3})");
    private static int columnIndex(String label) {
        int n=0;
        for (int i=0;i<label.length();i++) n=n*26+label.charAt(i)-'A'+1;
        return n-1;
    }
    private static WeeklyGridParser.Merge merge(String ref) throws SAXException {
        Matcher m=MERGE_REF.matcher(ref==null?"":ref);
        if (!m.matches()) throw new SAXException("Invalid merge reference");
        int left=columnIndex(m.group(1)), right=columnIndex(m.group(3));
        int top=Integer.parseInt(m.group(2)), bottom=Integer.parseInt(m.group(4));
        if (left>right || right>=MAX_COLUMNS || top>bottom || bottom>MAX_ROWS) throw new SAXException("Merge range outside sheet budget");
        return new WeeklyGridParser.Merge(ref,top,bottom,left,right);
    }

    private static final class SheetReader extends DefaultHandler {
        final List<String> strings;
        final List<List<String>> rows = new ArrayList<>();
        List<String> row;
        String type;
        StringBuilder value;
        final List<WeeklyGridParser.Merge> merges = new ArrayList<>();
        boolean capture, unsupportedValue, phonetic;
        int column, rowNumber;
        SheetReader(List<String> strings) { this.strings = strings; }
        public void startElement(String uri, String local, String q, Attributes a) throws SAXException {
            if (local.equals("f")) unsupportedValue = true;
            if (local.equals("mergeCell")) {
                if (merges.size()>=MAX_ROWS) throw new SAXException("Too many merge ranges");
                merges.add(merge(a.getValue("ref")));
            }
            if (local.equals("row")) {
                if (row != null) throw new SAXException("Nested rows");
                String reference = a.getValue("r");
                int nextRow = rowNumber + 1;
                if (reference != null) {
                    if (!reference.matches("[1-9][0-9]{0,8}")) throw new SAXException("Invalid row reference");
                    nextRow = Integer.parseInt(reference);
                }
                if (nextRow <= rowNumber || nextRow > MAX_ROWS) throw new SAXException("Invalid row order or row limit");
                while (rows.size() < nextRow - 1) rows.add(new ArrayList<>());
                rowNumber = nextRow;
                row = new ArrayList<>();
                rows.add(row);
                column = -1;
            }
            if (local.equals("c")) {
                if (row == null) throw new SAXException("Cell outside row");
                String reference = a.getValue("r");
                int next = column + 1;
                if (reference != null) {
                    if (!reference.matches("[A-Z]+[1-9][0-9]{0,8}")) throw new SAXException("Invalid cell reference");
                    next = 0;
                    int i = 0;
                    while (i < reference.length() && reference.charAt(i) >= 'A' && reference.charAt(i) <= 'Z') {
                        next = next * 26 + reference.charAt(i++) - 'A' + 1;
                        if (next > MAX_COLUMNS) throw new SAXException("Column limit");
                    }
                    if (Integer.parseInt(reference.substring(i)) != rowNumber) throw new SAXException("Cell row does not match parent");
                    next--;
                }
                if (next <= column || next >= MAX_COLUMNS) throw new SAXException("Invalid column");
                column = next;
                type = a.getValue("t");
                value = new StringBuilder();
            }
            if (local.equals("rPh")) phonetic = true;
            if (local.equals("v") || local.equals("t")) capture = !phonetic;
        }
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (capture && value != null) {
                value.append(ch, start, length);
                if (value.length() > TextTimetableParser.MAX_TEXT_CHARS) throw new SAXException("Cell too long");
            }
        }
        public void endElement(String uri, String local, String q) throws SAXException {
            if (local.equals("v") || local.equals("t")) capture = false;
            if (local.equals("rPh")) phonetic = false;
            if (local.equals("c")) {
                String text = value.toString();
                if ("s".equals(type)) {
                    int index;
                    try { index = Integer.parseInt(text); } catch (NumberFormatException e) { throw new SAXException("Invalid string reference"); }
                    if (index < 0 || index >= strings.size()) throw new SAXException("Invalid string reference");
                    text = strings.get(index);
                } else if ("e".equals(type) || "b".equals(type)) unsupportedValue = true;
                while (row.size() <= column) row.add("");
                row.set(column, text);
                value = null;
            }
            if (local.equals("row")) {
                // Excel retains cells that have formatting but no value. They do not
                // add columns to the imported table; retain interior gaps and every value.
                while (!row.isEmpty() && row.get(row.size() - 1).trim().isEmpty()) {
                    row.remove(row.size() - 1);
                }
                row = null;
            }
        }
    }
    private XlsxTimetableParser() { }
}
