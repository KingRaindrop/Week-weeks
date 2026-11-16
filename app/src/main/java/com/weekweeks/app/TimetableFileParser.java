package com.weekweeks.app;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Content-signature routing, strict decoding, and all-or-nothing file validation. */
final class TimetableFileParser {
    static final class Result {
        final List<PdfCourseParser.ParsedCourse> courses;
        final String source;
        final String debugLog;
        Result(List<PdfCourseParser.ParsedCourse> courses, String source, String debugLog) {
            this.courses = courses;
            this.source = source;
            this.debugLog = debugLog;
        }
    }

    static Result parse(byte[] bytes, String filename, int maxPeriods, int maxStreamBytes, int maxCourses) throws ImportException {
        if (bytes == null || bytes.length == 0) throw new ImportException(UiLang.t("课表文件为空", "課表檔案為空", "Timetable file is empty"));
        if (starts(bytes, 0x25, 0x50, 0x44, 0x46)) {
            PdfCourseParser.ParseResult result = PdfCourseParser.parseCourses(bytes, maxPeriods, maxStreamBytes, maxCourses);
            return new Result(result.courses, "PDF", result.debugLog);
        }
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (starts(bytes, 0x50, 0x4b, 0x03, 0x04)) {
            TextTimetableParser.Result result = XlsxTimetableParser.parse(bytes, maxPeriods, maxCourses);
            return validated(result, "XLSX");
        }
        if (starts(bytes, 0xd0, 0xcf, 0x11, 0xe0) || name.endsWith(".xls")) {
            throw new ImportException(UiLang.t("暂不支持旧版 XLS，请另存为 XLSX 或 CSV 后导入", "暫不支援舊版 XLS，請另存為 XLSX 或 CSV 後匯入", "Legacy XLS is not supported. Save as XLSX or CSV first"));
        }
        if (name.endsWith(".pdf") || name.endsWith(".xlsx")) throw new ImportException(UiLang.t("文件内容与扩展名不符或文件已损坏", "檔案內容與副檔名不符或檔案已損壞", "File content does not match its extension or is damaged"));
        String text = decode(bytes);
        if (text.indexOf('\0') >= 0) throw new ImportException(UiLang.t("不支持此二进制文件，请选择 PDF、XLSX、CSV、TSV 或 TXT", "不支援此二進位檔案，請選擇 PDF、XLSX、CSV、TSV 或 TXT", "Unsupported binary file. Choose PDF, XLSX, CSV, TSV or TXT"));
        return validated(TextTimetableParser.parse(text, maxPeriods, maxCourses),
                name.endsWith(".csv") ? "CSV" : name.endsWith(".tsv") ? "TSV" : "TXT");
    }

    private static Result validated(TextTimetableParser.Result result, String source) throws ImportException {
        if (!result.errors.isEmpty()) {
            StringBuilder message = new StringBuilder();
            for (int i = 0; i < result.errors.size() && i < 5; i++) {
                if (i > 0) message.append('\n');
                message.append(result.errors.get(i));
            }
            if (result.errors.size() > 5) message.append(UiLang.t("\n其余错误：", "\n其餘錯誤：", "\nAdditional errors: ")).append(result.errors.size() - 5);
            throw new ImportException(message.toString());
        }
        return new Result(result.courses, source, "");
    }

    private static String decode(byte[] bytes) throws ImportException {
        try {
            if (starts(bytes, 0xff, 0xfe)) return strict(bytes, 2, StandardCharsets.UTF_16LE);
            if (starts(bytes, 0xfe, 0xff)) return strict(bytes, 2, StandardCharsets.UTF_16BE);
            if (starts(bytes, 0xef, 0xbb, 0xbf)) return strict(bytes, 3, StandardCharsets.UTF_8);
            try { return strict(bytes, 0, StandardCharsets.UTF_8); }
            catch (CharacterCodingException error) { return strict(bytes, 0, Charset.forName("GB18030")); }
        } catch (CharacterCodingException error) {
            throw new ImportException(UiLang.t("无法解码文本，请保存为 UTF-8 后重试", "無法解碼文字，請儲存為 UTF-8 後重試", "Cannot decode text. Save as UTF-8 and retry"));
        }
    }

    private static String strict(byte[] bytes, int offset, Charset charset) throws CharacterCodingException {
        return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
    }
    private static boolean starts(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) if ((bytes[i] & 255) != signature[i]) return false;
        return true;
    }
    private TimetableFileParser() { }
}
