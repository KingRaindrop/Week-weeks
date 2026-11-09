package com.weekweeks.app;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.InflaterInputStream;

final class PdfCourseParser {
    static final class ParseResult {
        final List<ParsedCourse> courses;
        final String debugLog;

        ParseResult(List<ParsedCourse> courses, String debugLog) {
            this.courses = courses;
            this.debugLog = debugLog;
        }
    }

    static final class ParsedCourse {
        int day;
        int period;
        int endPeriod = 1;
        String name = "";
        String weeks = "";
        String room = "";
        String teacher = "";
    }

    static final class PdfTextItem {
        final double x;
        final double y;
        final String text;
        final int streamId;
        final boolean verticalText;

        PdfTextItem(double x, double y, String text, int streamId, boolean verticalText) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.streamId = streamId;
            this.verticalText = verticalText;
        }
    }

    private static final class EncodedTextItem {
        final double x;
        final double y;
        final byte[] bytes;
        final boolean verticalText;

        EncodedTextItem(double x, double y, byte[] bytes, boolean verticalText) {
            this.x = x;
            this.y = y;
            this.bytes = bytes;
            this.verticalText = verticalText;
        }
    }

    private static final class PdfLiteral {
        final byte[] bytes;
        final int nextIndex;

        PdfLiteral(byte[] bytes, int nextIndex) {
            this.bytes = bytes;
            this.nextIndex = nextIndex;
        }
    }

    private static final String[] DAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private static final double DAY_FIRST_COLUMN_X = 99.08;
    private static final double DAY_COLUMN_WIDTH = 103.85;
    private static final int MAX_FIELD_LENGTH = 60;

    private PdfCourseParser() {
    }

    static ParseResult parseCourses(byte[] pdfBytes, int maxPeriods, int maxStreamBytes, int maxCourses)
            throws ImportException {
        StringBuilder debugLog = new StringBuilder();
        List<PdfTextItem> textItems = extractAllPdfText(pdfBytes, maxStreamBytes, debugLog);
        List<ParsedCourse> courses = coursesFromPdfItems(textItems, maxPeriods, maxCourses, debugLog);
        return new ParseResult(courses, debugLog.toString());
    }

    private static byte[] readWithLimit(InputStream inputStream, int maxBytes, String limitMessage)
            throws IOException, ImportException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            if (output.size() + read > maxBytes) {
                throw new ImportException(limitMessage);
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String cleanField(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= MAX_FIELD_LENGTH ? trimmed : trimmed.substring(0, MAX_FIELD_LENGTH);
    }


    private static List<PdfTextItem> extractAllPdfText(byte[] pdfBytes, int maxStreamBytes, StringBuilder debugLog) throws ImportException {
        if (pdfBytes.length < 8 || pdfBytes[0] != '%' || pdfBytes[1] != 'P' || pdfBytes[2] != 'D' || pdfBytes[3] != 'F') {
            throw new ImportException(UiLang.t("选择的文件不是有效 PDF", "選擇的檔案不是有效的 PDF",
                    "The selected file is not a valid PDF"));
        }

        List<PdfTextItem> textItems = new ArrayList<>();
        byte[] streamToken = "stream".getBytes(StandardCharsets.ISO_8859_1);
        byte[] endStreamToken = "endstream".getBytes(StandardCharsets.ISO_8859_1);
        int searchFrom = 0;
        int parsedBytes = 0;
        int streamCount = 0;

        while (searchFrom < pdfBytes.length) {
            int streamStart = indexOf(pdfBytes, streamToken, searchFrom);
            if (streamStart < 0) {
                break;
            }
            int contentStart = skipPdfLineBreak(pdfBytes, streamStart + streamToken.length);
            int streamEnd = indexOf(pdfBytes, endStreamToken, contentStart);
            if (streamEnd < 0) {
                break;
            }
            int contentEnd = trimPdfLineBreak(pdfBytes, contentStart, streamEnd);
            if (contentEnd > contentStart) {
                byte[] rawStream = Arrays.copyOfRange(pdfBytes, contentStart, contentEnd);
                byte[] stream = inflateOrRaw(rawStream, maxStreamBytes);
                parsedBytes += stream.length;
                if (parsedBytes > maxStreamBytes) {
                    throw new ImportException(UiLang.t("PDF 内容过大，已拒绝导入",
                            "PDF 內容過大，已拒絕匯入", "PDF content is too large to import"));
                }
                int beforeCount = textItems.size();
                parsePdfTextItems(stream, textItems, streamStart);
                int addedCount = textItems.size() - beforeCount;
                if (addedCount > 0) {
                    streamCount++;
                    debugLog.append(String.format(
                            Locale.CHINA,
                            "Stream %d: 添加了 %d 个文本项",
                            streamCount,
                            addedCount
                    )).append('\n');
                }
            }
            searchFrom = streamEnd + endStreamToken.length;
        }

        debugLog.append(String.format(
                Locale.CHINA,
                "总计: 解析了 %d 个stream，提取了 %d 个文本项",
                streamCount,
                textItems.size()
        )).append('\n');

        if (textItems.isEmpty()) {
            throw new ImportException(UiLang.t("PDF 中没有可解析的文字课程表",
                    "PDF 中沒有可解析的文字課表", "No text timetable found in this PDF"));
        }

        return textItems;
    }

    private static byte[] inflateOrRaw(byte[] rawStream, int maxStreamBytes) throws ImportException {
        try {
            return readWithLimit(new InflaterInputStream(new ByteArrayInputStream(rawStream)), maxStreamBytes,
                    UiLang.t("PDF 压缩内容过大，已拒绝导入", "PDF 壓縮內容過大，已拒絕匯入",
                            "Compressed PDF content is too large to import"));
        } catch (ImportException error) {
            throw error;
        } catch (Exception ignored) {
            return rawStream;
        }
    }

    private static void parsePdfTextItems(byte[] stream, List<PdfTextItem> textItems, int streamId) {
        double currentX = 0;
        double currentY = 0;
        boolean verticalText = false;
        List<EncodedTextItem> encodedItems = new ArrayList<>();
        int index = 0;
        while (index < stream.length) {
            if (index + 1 < stream.length && stream[index] == 'T' && stream[index + 1] == 'm') {
                double[] matrix = readLastNumbersBefore(stream, index, 6);
                if (matrix != null) {
                    currentX = matrix[4];
                    currentY = matrix[5];
                    verticalText = Math.abs(matrix[1]) > Math.abs(matrix[0]);
                }
                index += 2;
                continue;
            }

            if (stream[index] == '(') {
                PdfLiteral literal = readPdfLiteral(stream, index);
                int afterLiteral = skipWhitespace(stream, literal.nextIndex);
                if (afterLiteral + 1 < stream.length && stream[afterLiteral] == 'T' && stream[afterLiteral + 1] == 'j') {
                    encodedItems.add(new EncodedTextItem(currentX, currentY, literal.bytes, verticalText));
                    index = afterLiteral + 2;
                    continue;
                }
                index = literal.nextIndex;
                continue;
            }

            if (stream[index] == '[') {
                PdfLiteral arrayText = readPdfTextArray(stream, index);
                int afterArray = skipWhitespace(stream, arrayText.nextIndex);
                if (afterArray + 1 < stream.length && stream[afterArray] == 'T' && stream[afterArray + 1] == 'J') {
                    encodedItems.add(new EncodedTextItem(currentX, currentY, arrayText.bytes, verticalText));
                    index = afterArray + 2;
                    continue;
                }
                index = arrayText.nextIndex;
                continue;
            }

            index++;
        }
        boolean preferUtf16 = hasUtf16TimetableEvidence(encodedItems);
        for (EncodedTextItem item : encodedItems) {
            String text = decodePdfText(item.bytes, preferUtf16).trim();
            if (!text.isEmpty()) {
                textItems.add(new PdfTextItem(item.x, item.y, text, streamId, item.verticalText));
            }
        }
    }

    private static List<ParsedCourse> coursesFromPdfItems(List<PdfTextItem> items, int maxPeriods, int maxCourses, StringBuilder debugLog) {
        List<ParsedCourse> parsed = new ArrayList<>();
        int matchCount = 0;
        int skipCount = 0;
        Map<Integer, Boolean> rowAxes = inferRowAxes(items, maxPeriods);

        for (int i = 0; i < items.size() && parsed.size() < maxCourses; i++) {
            PdfTextItem item = items.get(i);
            TimetableRules.PeriodMatch periodMatch =
                    TimetableRules.matchPeriodWeeks(item.text, maxPeriods);
            if (!periodMatch.matched) {
                continue;
            }

            matchCount++;
            boolean rowAxis = Boolean.TRUE.equals(rowAxes.get(item.streamId));
            int day = rowAxis ? dayFromX(item.y) : dayFromX(item.x);
            debugLog.append(String.format(Locale.CHINA, "\n[匹配%d] 文本: %s\n", matchCount, item.text));
            debugLog.append(String.format(Locale.ROOT, "  位置: x=%.2f, y=%.2f\n", item.x, item.y));
            debugLog.append(String.format(Locale.CHINA, "  解析: 星期=%d, 节次=%s-%s, 周次=%s\n",
                day, periodMatch.startPeriod, periodMatch.endPeriod, periodMatch.trailingText));

            if (!periodMatch.valid) {
                debugLog.append("  结果: 节次超出当前设置或范围倒置，跳过\n");
                skipCount++;
                continue;
            }

            if (day < 0) {
                debugLog.append("  结果: X坐标不匹配任何星期，跳过\n");
                skipCount++;
                continue;
            }

            String block = courseBlockAfter(items, i, rowAxis);
            String name = courseNameBefore(items, i, rowAxis);
            if (name.isEmpty()) {
                name = courseNameFromPageTail(items, i, rowAxis, rowAxes, block);
            }
            debugLog.append(String.format(Locale.CHINA, "  课程名: %s\n", name));

            if (name.isEmpty()) {
                debugLog.append("  结果: 课程名称为空，跳过\n");
                skipCount++;
                continue;
            }

            int period = periodMatch.startPeriod;
            int endPeriod = periodMatch.endPeriod;

            ParsedCourse course = new ParsedCourse();
            course.day = day;
            course.period = period;
            course.endPeriod = endPeriod;
            course.name = cleanField(name);

            // 清理weeks字段：移除时间信息（如"及18:30-20:10"）
            // Weeks may wrap into subsequent text items, including after a parity marker.
            // Re-match the complete block rather than accepting a valid but truncated prefix.
            String rawWeeks = TimetableRules.matchPeriodWeeks(block, maxPeriods).trailingText;
            if (rawWeeks != null) {
                // 移除附带的时间格式（如“及18:30-20:10”），保留“1-6周及9-17周”。
                rawWeeks = rawWeeks.replaceAll(
                        "\\s*及?\\s*\\d{1,2}:\\d{2}\\s*-\\s*\\d{1,2}:\\d{2}",
                        ""
                ).trim();
            }
            course.weeks = cleanField(cleanPdfWeeks(rawWeeks));
            TimetableRules.WeekParseResult weekResult = TimetableRules.parseWeeks(course.weeks);
            if (!weekResult.valid) {
                debugLog.append("  结果: 上课周格式无法识别，跳过\n");
                skipCount++;
                continue;
            }

            course.room = cleanField(extractPdfField(block, "/场地:"));
            course.teacher = cleanField(extractPdfField(block, "/教师:"));

            if (!course.name.isEmpty()) {
                parsed.add(course);
                debugLog.append(String.format(Locale.CHINA, "  结果: 成功添加 - %s (%s 第%d-%d节 %s)\n",
                    course.name, DAYS[course.day], course.period, course.endPeriod, course.weeks));
            }
        }

        debugLog.append(String.format(Locale.CHINA, "\n总结: 匹配到%d个，成功%d个，跳过%d个\n", matchCount, parsed.size(), skipCount));

        return parsed;
    }

    private static String cleanPdfWeeks(String rawWeeks) {
        if (rawWeeks == null) {
            return "";
        }
        String cleaned = rawWeeks.replaceFirst("^[\\uff09)\\s:\\uff1a]+", "").trim();
        return cleaned
                .replaceAll("\\s*及?\\s*\\d{1,2}:\\d{2}\\s*-\\s*\\d{1,2}:\\d{2}", "")
                .trim();
    }

    private static String courseNameBefore(List<PdfTextItem> items, int metadataIndex, boolean rowAxis) {
        return courseNameBefore(items, metadataIndex, items.get(metadataIndex), rowAxis, false);
    }

    private static String courseNameBefore(List<PdfTextItem> items, int endIndex,
            PdfTextItem metadata, boolean rowAxis, boolean sameStreamOnly) {
        List<String> parts = new ArrayList<>();
        for (int i = endIndex - 1; i >= 0; i--) {
            PdfTextItem previous = items.get(i);
            if (sameStreamOnly && previous.streamId != metadata.streamId) {
                break;
            }
            if (!sameCourseAxis(previous, metadata, rowAxis)) {
                // 课程可能跨 PDF 分页：课程名留在上一页底部，节次信息出现在下一页顶部，
                // 中间会夹杂其他列的文本，跳过它们继续沿同一列/行回溯。
                continue;
            }
            if (!looksLikeCourseNameLine(previous.text)) {
                break;
            }
            parts.add(0, previous.text);
        }
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            name.append(part);
        }
        String result = name.toString()
                .replace("★", "")
                .replace("○", "")
                .replace("●", "")
                .replace("◇", "")
                .replace("◆", "")
                .trim();

        // 移除结尾可能粘连的星期和节次信息
        result = result.replaceAll("周[一二三四五六日]第\\d{1,2}-?\\d{0,2}节$", "");
        result = result.replaceAll("第\\d{1,2}-?\\d{0,2}节$", "");

        return result.trim();
    }

    private static String courseNameFromPageTail(List<PdfTextItem> items, int metadataIndex,
            boolean rowAxis, Map<Integer, Boolean> rowAxes, String block) {
        PdfTextItem metadata = items.get(metadataIndex);
        // Only a block at the start of its page/axis can continue a previous page.
        for (int i = metadataIndex - 1; i >= 0; i--) {
            PdfTextItem previous = items.get(i);
            if (previous.streamId == metadata.streamId
                    && sameCourseAxis(previous, metadata, rowAxis)) {
                return "";
            }
        }
        String teachingClass = extractPdfField(block, "/教学班:");
        if (teachingClass.isEmpty()) {
            return "";
        }
        // PDF object order is not page order. A dangling title can occur in a later
        // stream, and that page can have a different text rotation. Require its own
        // teaching-class field to corroborate the title rather than borrowing a name.
        Map<Integer, Integer> tails = new HashMap<>();
        double axis = rowAxis ? metadata.y : metadata.x;
        for (int i = 0; i < items.size(); i++) {
            PdfTextItem candidate = items.get(i);
            boolean candidateRowAxis = Boolean.TRUE.equals(rowAxes.get(candidate.streamId));
            double candidateAxis = candidateRowAxis ? candidate.y : candidate.x;
            if (candidate.streamId != metadata.streamId && Math.abs(candidateAxis - axis) < 3.0) {
                tails.put(candidate.streamId, i);
            }
        }
        String recovered = "";
        for (int tailIndex : tails.values()) {
            PdfTextItem tail = items.get(tailIndex);
            boolean tailRowAxis = Boolean.TRUE.equals(rowAxes.get(tail.streamId));
            String candidate = courseNameBefore(items, tailIndex + 1, tail, tailRowAxis, true);
            if (!candidate.isEmpty() && (teachingClass.equals(candidate)
                    || teachingClass.startsWith(candidate + "-"))) {
                if (!recovered.isEmpty() && !recovered.equals(candidate)) {
                    return "";
                }
                recovered = candidate;
            }
        }
        return recovered;
    }

    private static String courseBlockAfter(List<PdfTextItem> items, int metadataIndex, boolean rowAxis) {
        PdfTextItem metadata = items.get(metadataIndex);
        StringBuilder block = new StringBuilder();
        for (int i = metadataIndex; i < items.size(); i++) {
            PdfTextItem current = items.get(i);
            if (current.streamId != metadata.streamId) {
                break;
            }
            if (!sameCourseAxis(current, metadata, rowAxis)) {
                continue;
            }
            if (i > metadataIndex && (startsNewCourseTitle(current.text)
                    || TimetableRules.matchPeriodWeeks(current.text, 99).matched)) {
                break;
            }
            if (i > metadataIndex && block.indexOf("/") < 0
                    && looksLikeCourseNameLine(current.text)
                    && !current.text.matches("[0-9\\s周单双()（）,，;；、及和~～—–至到\\-]+")) {
                break;
            }
            block.append(current.text);
        }
        return block.toString();
    }

    private static boolean looksLikeCourseNameLine(String text) {
        String value = text.trim();
        return !value.isEmpty()
                && !value.contains("/")
                && !value.startsWith(":")
                && !value.startsWith("：")
                && !value.contains("节)")
                && !value.contains("节）")
                && !value.matches(".*第\\d{1,2}-?\\d{0,2}节.*") // 排除包含"第X节"的行
                && !value.matches("周[一二三四五六日].*") // 排除以"周X"开头的行
                && !value.matches("\\d+")
                && !value.startsWith("打印时间")
                && !value.startsWith(": 体育课");
    }

    private static boolean startsNewCourseTitle(String text) {
        return (text.contains("★")
                || text.contains("○")
                || text.contains("●")
                || text.contains("◇")
                || text.contains("◆"))
                && !text.contains("教学班");
    }

    private static boolean sameColumn(PdfTextItem left, PdfTextItem right) {
        return Math.abs(left.x - right.x) < 3.0;
    }

    private static boolean sameRow(PdfTextItem left, PdfTextItem right) {
        return Math.abs(left.y - right.y) < 3.0;
    }

    private static boolean sameCourseAxis(PdfTextItem left, PdfTextItem right, boolean rowAxis) {
        return rowAxis ? sameRow(left, right) : sameColumn(left, right);
    }

    private static Map<Integer, Boolean> inferRowAxes(List<PdfTextItem> items, int maxPeriods) {
        Map<Integer, int[]> votes = new HashMap<>();
        for (int i = 0; i < items.size(); i++) {
            PdfTextItem metadata = items.get(i);
            TimetableRules.PeriodMatch match = TimetableRules.matchPeriodWeeks(metadata.text, maxPeriods);
            if (!match.matched || !match.valid) {
                continue;
            }
            int[] score = votes.get(metadata.streamId);
            if (score == null) {
                score = new int[2];
                votes.put(metadata.streamId, score);
            }
            if (metadata.verticalText && dayFromX(metadata.y) >= 0) {
                // A rotated text baseline is stronger evidence than incidental alignment.
                score[1] += 2;
                continue;
            }
            boolean columnTitle = dayFromX(metadata.x) >= 0 && hasTitleOnAxis(items, i, false);
            boolean rowTitle = dayFromX(metadata.y) >= 0 && hasTitleOnAxis(items, i, true);
            // Only unambiguous title -> metadata relationships vote. Other columns may
            // share a baseline, so a single neighbouring item cannot select an axis.
            if (columnTitle != rowTitle) {
                score[rowTitle ? 1 : 0]++;
            }
        }
        Map<Integer, Boolean> result = new HashMap<>();
        for (Map.Entry<Integer, int[]> vote : votes.entrySet()) {
            int[] score = vote.getValue();
            // Keep the established column layout when evidence is absent or tied.
            result.put(vote.getKey(), score[1] > score[0]);
        }
        return result;
    }

    private static boolean hasTitleOnAxis(List<PdfTextItem> items, int metadataIndex, boolean rowAxis) {
        PdfTextItem metadata = items.get(metadataIndex);
        for (int i = metadataIndex - 1; i >= 0; i--) {
            PdfTextItem candidate = items.get(i);
            if (sameCourseAxis(candidate, metadata, rowAxis)) {
                if (sameCourseAxis(candidate, metadata, !rowAxis)) {
                    // A title at the same point lies on both axes and proves neither layout.
                    return false;
                }
                // Match courseNameBefore's cross-stream lookup, including split pages.
                return looksLikeCourseNameLine(candidate.text);
            }
        }
        return false;
    }

    private static int dayFromX(double x) {
        int day = (int) Math.round((x - DAY_FIRST_COLUMN_X) / DAY_COLUMN_WIDTH);
        if (day < 0 || day >= DAYS.length) {
            return -1;
        }
        double expectedX = DAY_FIRST_COLUMN_X + day * DAY_COLUMN_WIDTH;
        return Math.abs(x - expectedX) <= 35.0 ? day : -1;
    }

    private static String extractPdfField(String block, String marker) {
        int start = block.indexOf(marker);
        if (start < 0) {
            return "";
        }
        start += marker.length();
        int end = block.indexOf("/", start);
        if (end < 0) {
            end = block.length();
        }
        return block.substring(start, end).trim();
    }

    private static PdfLiteral readPdfLiteral(byte[] stream, int startIndex) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int depth = 1;
        int index = startIndex + 1;
        while (index < stream.length && depth > 0) {
            int value = stream[index] & 0xff;
            if (value == '\\') {
                index++;
                if (index >= stream.length) {
                    break;
                }
                int escaped = stream[index] & 0xff;
                switch (escaped) {
                    case 'n':
                        output.write('\n');
                        index++;
                        break;
                    case 'r':
                        output.write('\r');
                        index++;
                        break;
                    case 't':
                        output.write('\t');
                        index++;
                        break;
                    case 'b':
                        output.write('\b');
                        index++;
                        break;
                    case 'f':
                        output.write('\f');
                        index++;
                        break;
                    case '(':
                    case ')':
                    case '\\':
                        output.write(escaped);
                        index++;
                        break;
                    case '\r':
                        index++;
                        if (index < stream.length && stream[index] == '\n') {
                            index++;
                        }
                        break;
                    case '\n':
                        index++;
                        break;
                    default:
                        if (escaped >= '0' && escaped <= '7') {
                            int octal = escaped - '0';
                            index++;
                            for (int i = 0; i < 2 && index < stream.length; i++) {
                                int digit = stream[index] & 0xff;
                                if (digit < '0' || digit > '7') {
                                    break;
                                }
                                octal = octal * 8 + digit - '0';
                                index++;
                            }
                            output.write(octal & 0xff);
                        } else {
                            output.write(escaped);
                            index++;
                        }
                        break;
                }
            } else if (value == '(') {
                depth++;
                output.write(value);
                index++;
            } else if (value == ')') {
                depth--;
                if (depth > 0) {
                    output.write(value);
                }
                index++;
            } else {
                output.write(value);
                index++;
            }
        }
        return new PdfLiteral(output.toByteArray(), index);
    }

    private static PdfLiteral readPdfTextArray(byte[] stream, int startIndex) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int index = startIndex + 1;
        while (index < stream.length) {
            int value = stream[index] & 0xff;
            if (value == ']') {
                index++;
                break;
            }
            if (value == '(') {
                PdfLiteral literal = readPdfLiteral(stream, index);
                output.write(literal.bytes, 0, literal.bytes.length);
                index = literal.nextIndex;
                continue;
            }
            if (value == '<' && index + 1 < stream.length && stream[index + 1] != '<') {
                PdfLiteral hex = readPdfHexString(stream, index);
                output.write(hex.bytes, 0, hex.bytes.length);
                index = hex.nextIndex;
                continue;
            }
            index++;
        }
        return new PdfLiteral(output.toByteArray(), index);
    }

    private static PdfLiteral readPdfHexString(byte[] stream, int startIndex) {
        ByteArrayOutputStream hex = new ByteArrayOutputStream();
        int index = startIndex + 1;
        while (index < stream.length) {
            int value = stream[index] & 0xff;
            if (value == '>') {
                index++;
                break;
            }
            if (isHexByte(value)) {
                hex.write(value);
            }
            index++;
        }

        byte[] hexBytes = hex.toByteArray();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int i = 0; i < hexBytes.length; i += 2) {
            int high = hexValue(hexBytes[i]);
            int low = i + 1 < hexBytes.length ? hexValue(hexBytes[i + 1]) : 0;
            output.write(((high << 4) | low) & 0xff);
        }
        return new PdfLiteral(output.toByteArray(), index);
    }

    private static boolean hasUtf16TimetableEvidence(List<EncodedTextItem> items) {
        for (EncodedTextItem item : items) {
            if (utf16BomCharset(item.bytes) != null) {
                return true;
            }
            // Text that is not clean UTF-8 but is clean, readable UTF-16BE marks the whole
            // stream as UTF-16, so short names whose UTF-16 bytes look like ASCII decode correctly.
            if (isEvenLength(item.bytes) && strictDecode(item.bytes, 0, StandardCharsets.UTF_8) == null) {
                String utf16 = strictDecode(item.bytes, 0, StandardCharsets.UTF_16BE);
                if (utf16 != null && hasReadableText(utf16)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String decodePdfText(byte[] bytes, boolean preferUtf16) {
        Charset bomCharset = utf16BomCharset(bytes);
        if (bomCharset != null) {
            String text = strictDecode(bytes, 2, bomCharset);
            if (text != null) {
                return text;
            }
        }
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xEF && (bytes[1] & 0xff) == 0xBB && (bytes[2] & 0xff) == 0xBF) {
            String text = strictDecode(bytes, 3, StandardCharsets.UTF_8);
            if (text != null) {
                return text;
            }
        }
        if (preferUtf16 && isEvenLength(bytes)) {
            String utf16 = strictDecode(bytes, 0, StandardCharsets.UTF_16BE);
            if (utf16 != null && hasReadableText(utf16)) {
                return utf16;
            }
        }
        String utf8 = strictDecode(bytes, 0, StandardCharsets.UTF_8);
        if (utf8 != null) {
            return utf8;
        }
        if (isEvenLength(bytes)) {
            String utf16 = strictDecode(bytes, 0, StandardCharsets.UTF_16BE);
            if (utf16 != null && hasReadableText(utf16)) {
                return utf16;
            }
        }
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    private static boolean isEvenLength(byte[] bytes) {
        return bytes.length >= 2 && bytes.length % 2 == 0;
    }

    private static Charset utf16BomCharset(byte[] bytes) {
        if (bytes.length < 2) {
            return null;
        }
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        if (first == 0xFE && second == 0xFF) {
            return StandardCharsets.UTF_16BE;
        }
        if (first == 0xFF && second == 0xFE) {
            return StandardCharsets.UTF_16LE;
        }
        return null;
    }

    /** Decodes without replacement; returns null for malformed input or control characters. */
    private static String strictDecode(byte[] bytes, int offset, Charset charset) {
        String text;
        try {
            text = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                    .toString();
        } catch (CharacterCodingException error) {
            return null;
        }
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            boolean control = (value < 0x20 && value != '\t' && value != '\n' && value != '\r')
                    || (value >= 0x7f && value < 0xa0);
            if (control || value == '\uFFFD') {
                return null;
            }
        }
        return text;
    }

    private static boolean hasReadableText(String text) {
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            if ((value >= 0x4e00 && value <= 0x9fff) || Character.isLetterOrDigit(value)) {
                return true;
            }
        }
        return false;
    }

    private static double[] readLastNumbersBefore(byte[] data, int operatorIndex, int count) {
        double[] numbers = new double[count];
        int found = 0;
        int index = operatorIndex - 1;
        while (found < count && index >= 0) {
            while (index >= 0 && isWhitespace(data[index])) {
                index--;
            }
            int end = index + 1;
            while (index >= 0 && isNumberByte(data[index])) {
                index--;
            }
            int start = index + 1;
            if (start >= end) {
                return null;
            }
            try {
                String token = new String(data, start, end - start, StandardCharsets.ISO_8859_1);
                numbers[count - found - 1] = Double.parseDouble(token);
                found++;
            } catch (NumberFormatException error) {
                return null;
            }
        }
        return found == count ? numbers : null;
    }

    private static boolean isNumberByte(byte value) {
        return (value >= '0' && value <= '9') || value == '-' || value == '+' || value == '.';
    }

    private static boolean isHexByte(int value) {
        return (value >= '0' && value <= '9')
                || (value >= 'a' && value <= 'f')
                || (value >= 'A' && value <= 'F');
    }

    private static int hexValue(byte value) {
        if (value >= '0' && value <= '9') {
            return value - '0';
        }
        if (value >= 'a' && value <= 'f') {
            return value - 'a' + 10;
        }
        if (value >= 'A' && value <= 'F') {
            return value - 'A' + 10;
        }
        return 0;
    }

    private static boolean isWhitespace(byte value) {
        return value == ' ' || value == '\n' || value == '\r' || value == '\t' || value == '\f' || value == 0;
    }

    private static int skipWhitespace(byte[] data, int index) {
        int cursor = index;
        while (cursor < data.length && isWhitespace(data[cursor])) {
            cursor++;
        }
        return cursor;
    }

    private static int skipPdfLineBreak(byte[] data, int index) {
        int cursor = index;
        if (cursor < data.length && data[cursor] == '\r') {
            cursor++;
            if (cursor < data.length && data[cursor] == '\n') {
                cursor++;
            }
        } else if (cursor < data.length && data[cursor] == '\n') {
            cursor++;
        }
        return cursor;
    }

    private static int trimPdfLineBreak(byte[] data, int start, int end) {
        int cursor = end;
        while (cursor > start && (data[cursor - 1] == '\n' || data[cursor - 1] == '\r')) {
            cursor--;
        }
        return cursor;
    }

    private static int indexOf(byte[] data, byte[] pattern, int from) {
        for (int i = Math.max(0, from); i <= data.length - pattern.length; i++) {
            boolean matched = true;
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return i;
            }
        }
        return -1;
    }
}
