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
        final boolean requiresDayGrid;

        PdfTextItem(double x, double y, String text, int streamId, boolean verticalText, boolean requiresDayGrid) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.streamId = streamId;
            this.verticalText = verticalText;
            this.requiresDayGrid = requiresDayGrid;
        }
    }

    private static final class EncodedTextItem {
        final double x;
        final double y;
        final byte[] bytes;
        final boolean verticalText;
        final PdfDocumentStreams.UnicodeMap font;
        final String fontResource;
        final boolean requiresDayGrid;

        EncodedTextItem(double x, double y, byte[] bytes, boolean verticalText,
                PdfDocumentStreams.UnicodeMap font, String fontResource, boolean requiresDayGrid) {
            this.x = x;
            this.y = y;
            this.bytes = bytes;
            this.verticalText = verticalText;
            this.font = font;
            this.fontResource = fontResource;
            this.requiresDayGrid = requiresDayGrid;
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
        int decodedTextChars = 0;
        List<PdfDocumentStreams.Page> pages = PdfDocumentStreams.read(pdfBytes, maxStreamBytes);
        if (pages != null) {
            for (PdfDocumentStreams.Page page : pages) {
                decodedTextChars += parsePdfTextItems(page.content, textItems, page.id,
                        page.fonts, page.imageObjects, maxStreamBytes - decodedTextChars);
            }
            debugLog.append("PDF pages: ").append(pages.size())
                    .append(", text items: ").append(textItems.size()).append('\n');
            if (textItems.isEmpty()) throw PdfDocumentStreams.invalid("no text timetable found");
            return textItems;
        }
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
                decodedTextChars += parsePdfTextItems(stream, textItems, streamStart,
                        java.util.Collections.emptyMap(), java.util.Collections.emptySet(), maxStreamBytes - decodedTextChars);
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

    private static int parsePdfTextItems(byte[] stream, List<PdfTextItem> textItems, int streamId,
            Map<String, PdfDocumentStreams.UnicodeMap> fonts, java.util.Set<String> imageObjects,
            int maxTextChars) throws ImportException {
        double x = 0, y = 0, a = 1, b = 0, c = 0, d = 1, leading = 0;
        double[] ctm = {1, 0, 0, 1, 0, 0};
        List<double[]> graphicsStack = new ArrayList<>();
        List<PdfDocumentStreams.UnicodeMap> fontStack = new ArrayList<>();
        List<String> fontResourceStack = new ArrayList<>();
        List<Double> leadingStack = new ArrayList<>();
        boolean inText = false;
        PdfDocumentStreams.UnicodeMap font = null;
        String fontResource = ""; // Legacy fixtures omit Tf; keep their shared evidence unchanged.
        List<EncodedTextItem> encodedItems = new ArrayList<>();
        List<Object> operands = new ArrayList<>();
        PdfDocumentStreams.Scanner scanner = new PdfDocumentStreams.Scanner(stream, 0);
        while (scanner.hasNext()) {
            Object token = scanner.next();
            if (!(token instanceof String)) {
                operands.add(token);
                if (operands.size() > 1000) throw PdfDocumentStreams.invalid("too many text operands");
                continue;
            }
            String op = (String) token;
            if ("Do".equals(op)) {
                if (operands.size() != 1 || !(operands.get(0) instanceof PdfDocumentStreams.Name)
                        || !imageObjects.contains(((PdfDocumentStreams.Name) operands.get(0)).value))
                    throw PdfDocumentStreams.invalid("Form or unknown XObject text is not supported");
            } else if ("BI".equals(op)) throw PdfDocumentStreams.invalid("inline images are not supported");
            else if ("q".equals(op)) {
                if (graphicsStack.size() >= 64) throw PdfDocumentStreams.invalid("graphics stack exceeds limit");
                graphicsStack.add(ctm.clone()); fontStack.add(font); fontResourceStack.add(fontResource);
                leadingStack.add(leading);
            } else if ("Q".equals(op)) {
                if (graphicsStack.isEmpty()) throw PdfDocumentStreams.invalid("unbalanced graphics stack");
                ctm = graphicsStack.remove(graphicsStack.size() - 1);
                font = fontStack.remove(fontStack.size() - 1);
                fontResource = fontResourceStack.remove(fontResourceStack.size() - 1);
                leading = leadingStack.remove(leadingStack.size() - 1);
            } else if ("cm".equals(op)) {
                double[] matrix = numbers(operands, 6);
                boolean straight = Math.abs(matrix[1]) < 0.000001 && Math.abs(matrix[2]) < 0.000001;
                boolean quarterTurn = Math.abs(matrix[0]) < 0.000001 && Math.abs(matrix[3]) < 0.000001;
                if ((!straight && !quarterTurn) || Math.abs(matrix[0] * matrix[3] - matrix[1] * matrix[2]) < 0.000001)
                    throw PdfDocumentStreams.invalid("skewed or singular content transformation is not supported");
                ctm = concatenate(ctm, matrix);
            }
            if ("BT".equals(op)) { inText = true; x = y = 0; a = d = 1; b = c = 0; }
            else if ("ET".equals(op)) inText = false;
            else if ("Tf".equals(op) && operands.size() == 2
                    && operands.get(0) instanceof PdfDocumentStreams.Name) {
                fontResource = ((PdfDocumentStreams.Name) operands.get(0)).value;
                font = fonts.get(fontResource);
            } else if (inText) {
                if ("Tm".equals(op)) {
                    double[] values = numbers(operands, 6);
                    a = values[0]; b = values[1]; c = values[2]; d = values[3]; x = values[4]; y = values[5];
                } else if ("Td".equals(op) || "TD".equals(op)) {
                    double[] values = numbers(operands, 2);
                    x += a * values[0] + c * values[1]; y += b * values[0] + d * values[1];
                    if ("TD".equals(op)) leading = -values[1];
                } else if ("TL".equals(op)) leading = numbers(operands, 1)[0];
                else if ("T*".equals(op) || "'".equals(op) || "\"".equals(op)) {
                    x -= c * leading; y -= d * leading;
                }
                if ("Tj".equals(op) || "TJ".equals(op) || "'".equals(op) || "\"".equals(op)) {
                    Object value = operands.isEmpty() ? null : operands.get(operands.size() - 1);
                    ByteArrayOutputStream text = new ByteArrayOutputStream();
                    if ("TJ".equals(op) && value instanceof List) {
                        for (Object part : (List<?>) value) {
                            if (part instanceof byte[]) text.write((byte[]) part, 0, ((byte[]) part).length);
                            else if (!(part instanceof Number)) throw PdfDocumentStreams.invalid("invalid TJ array");
                        }
                    } else if (value instanceof byte[]) text.write((byte[]) value, 0, ((byte[]) value).length);
                    else throw PdfDocumentStreams.invalid("invalid text string operand");
                    double deviceX = ctm[0] * x + ctm[2] * y + ctm[4];
                    double deviceY = ctm[1] * x + ctm[3] * y + ctm[5];
                    boolean vertical = Math.abs(ctm[1] * a + ctm[3] * b) > Math.abs(ctm[0] * a + ctm[2] * b);
                    double oldDay = Math.abs(b) > Math.abs(a) ? y : x;
                    double newDay = vertical ? deviceY : deviceX;
                    encodedItems.add(new EncodedTextItem(deviceX, deviceY, text.toByteArray(), vertical, font,
                            fontResource, Math.abs(newDay - oldDay) > 0.01));
                }
            }
            operands.clear();
        }
        java.util.Set<String> utf16Fonts = new java.util.HashSet<>();
        for (EncodedTextItem item : encodedItems) {
            if (item.font == null && hasUtf16TimetableEvidence(item.bytes)) utf16Fonts.add(item.fontResource);
        }
        int decodedChars = 0;
        for (EncodedTextItem item : encodedItems) {
            String text = item.font == null ? decodePdfText(item.bytes, utf16Fonts.contains(item.fontResource))
                    : item.font.decode(item.bytes, maxTextChars - decodedChars);
            if (text.length() > maxTextChars - decodedChars)
                throw PdfDocumentStreams.invalid("decoded PDF text exceeds limit");
            decodedChars += text.length();
            text = text.trim();
            if (!text.isEmpty()) textItems.add(new PdfTextItem(item.x, item.y, text, streamId, item.verticalText, item.requiresDayGrid));
        }
        return decodedChars;
    }

    private static double[] concatenate(double[] left, double[] right) throws ImportException {
        double[] result = {
                left[0] * right[0] + left[2] * right[1], left[1] * right[0] + left[3] * right[1],
                left[0] * right[2] + left[2] * right[3], left[1] * right[2] + left[3] * right[3],
                left[0] * right[4] + left[2] * right[5] + left[4],
                left[1] * right[4] + left[3] * right[5] + left[5]};
        for (double value : result) if (!Double.isFinite(value)) throw PdfDocumentStreams.invalid("nonfinite content transformation");
        return result;
    }

    private static double[] numbers(List<Object> operands, int count) throws ImportException {
        if (operands.size() != count) throw PdfDocumentStreams.invalid("invalid text positioning operands");
        double[] values = new double[count];
        for (int i = 0; i < count; i++) {
            if (!(operands.get(i) instanceof Number)) throw PdfDocumentStreams.invalid("expected positioning number");
            values[i] = ((Number) operands.get(i)).doubleValue();
        }
        return values;
    }

    private static List<ParsedCourse> coursesFromPdfItems(List<PdfTextItem> items, int maxPeriods, int maxCourses, StringBuilder debugLog) throws ImportException {
        List<ParsedCourse> parsed = new ArrayList<>();
        int matchCount = 0;
        int skipCount = 0;
        Map<Integer, Boolean> rowAxes = inferRowAxes(items, maxPeriods);
        Map<Integer, DayGrid> dayGrids = inferDayGrids(items);
        for (Map.Entry<Integer, DayGrid> entry : dayGrids.entrySet()) {
            rowAxes.put(entry.getKey(), entry.getValue().rowAxis);
        }

        for (int i = 0; i < items.size(); i++) {
            PdfTextItem item = items.get(i);
            TimetableRules.PeriodMatch periodMatch =
                    TimetableRules.matchPeriodWeeks(item.text, maxPeriods);
            if (!periodMatch.matched) {
                continue;
            }

            matchCount++;
            boolean rowAxis = Boolean.TRUE.equals(rowAxes.get(item.streamId));
            DayGrid grid = dayGrids.get(item.streamId);
            debugLog.append(grid == null ? "weekday-grid: legacy-fallback\n" : "weekday-grid: header-calibrated\n");
            if (grid == null && item.requiresDayGrid)
                throw PdfDocumentStreams.invalid("transformed weekday coordinates require clear weekday headers");
            double dayPosition = rowAxis ? item.y : item.x;
            int day = grid == null ? dayFromX(dayPosition) : grid.dayAt(dayPosition);
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
            course.weeks = cleanPdfWeeks(rawWeeks);
            TimetableRules.WeekParseResult weekResult = TimetableRules.parseWeeks(course.weeks);
            if (!weekResult.valid) {
                debugLog.append("  结果: 上课周格式无法识别，跳过\n");
                skipCount++;
                continue;
            }
            // Validate the whole expression before bounding its stored representation.
            // Cutting it at 60 characters can silently erase later teaching weeks or
            // hide an invalid suffix. At most 30 selected weeks fit in compact ranges.
            if (course.weeks.length() > MAX_FIELD_LENGTH) {
                course.weeks = compactWeeks(weekResult);
            }

            course.room = cleanField(extractPdfField(block, "/场地:"));
            course.teacher = cleanField(extractPdfField(block, "/教师:"));

            if (!course.name.isEmpty()) {
                if (parsed.size() >= maxCourses) throw PdfDocumentStreams.invalid("course count exceeds import limit");
                parsed.add(course);
                debugLog.append(String.format(Locale.CHINA, "  结果: 成功添加 - %s (%s 第%d-%d节 %s)\n",
                    course.name, DAYS[course.day], course.period, course.endPeriod, course.weeks));
            }
        }

        debugLog.append(String.format(Locale.CHINA, "\n总结: 匹配到%d个，成功%d个，跳过%d个\n", matchCount, parsed.size(), skipCount));

        return parsed;
    }

    private static String compactWeeks(TimetableRules.WeekParseResult parsed) {
        if (!parsed.explicit) return "";
        StringBuilder result = new StringBuilder();
        for (int week = 1; week <= TimetableRules.MAX_WEEK; week++) {
            if (!parsed.weeks[week]) continue;
            int start = week;
            while (week < TimetableRules.MAX_WEEK && parsed.weeks[week + 1]) week++;
            if (result.length() > 0) result.append(',');
            result.append(start);
            if (week > start) result.append('-').append(week);
        }
        return result.toString();
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
                && weekdayHeader(value) < 0
                && !value.contains("/")
                && !value.startsWith(":")
                && !value.startsWith("：")
                && !value.matches("^(?:学分|學分|分|教学班|教學班|考核方式)[:：].*")
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

    private static final class DayGrid {
        final double first, step;
        final boolean rowAxis;
        DayGrid(double first, double step, boolean rowAxis) {
            this.first = first; this.step = step; this.rowAxis = rowAxis;
        }
        int dayAt(double position) {
            int day = (int) Math.round((position - first) / step);
            if (day < 0 || day >= DAYS.length) return -1;
            return Math.abs(position - first - day * step) <= Math.abs(step) * 0.49 ? day : -1;
        }
    }

    private static Map<Integer, DayGrid> inferDayGrids(List<PdfTextItem> items) {
        Map<Integer, List<PdfTextItem>> headers = new HashMap<>();
        for (PdfTextItem item : items) {
            if (weekdayHeader(item.text) >= 0) {
                List<PdfTextItem> page = headers.get(item.streamId);
                if (page == null) { page = new ArrayList<>(); headers.put(item.streamId, page); }
                page.add(item);
            }
        }
        Map<Integer, DayGrid> grids = new HashMap<>();
        for (Map.Entry<Integer, List<PdfTextItem>> page : headers.entrySet()) {
            DayGrid columns = fitDayGrid(page.getValue(), false);
            DayGrid rows = fitDayGrid(page.getValue(), true);
            // Ambiguous/disagreeing header layouts retain the established legacy grid.
            if ((columns == null) != (rows == null)) grids.put(page.getKey(), columns != null ? columns : rows);
        }
        return grids;
    }

    private static DayGrid fitDayGrid(List<PdfTextItem> headers, boolean rowAxis) {
        if (headers.size() < 2) return null;
        PdfTextItem first = headers.get(0), second = null;
        int firstDay = weekdayHeader(first.text);
        for (PdfTextItem item : headers) if (weekdayHeader(item.text) != firstDay) { second = item; break; }
        if (second == null) return null;
        double firstPosition = rowAxis ? first.y : first.x;
        double secondPosition = rowAxis ? second.y : second.x;
        double step = (secondPosition - firstPosition) / (weekdayHeader(second.text) - firstDay);
        if (Math.abs(step) < 15 || Math.abs(step) > 500) return null;
        double origin = firstPosition - firstDay * step;
        double baseline = rowAxis ? first.x : first.y;
        for (PdfTextItem item : headers) {
            double position = rowAxis ? item.y : item.x;
            double cross = rowAxis ? item.x : item.y;
            if (Math.abs(position - origin - weekdayHeader(item.text) * step) > 3
                    || Math.abs(cross - baseline) > 3) return null;
        }
        return new DayGrid(origin, step, rowAxis);
    }

    private static int weekdayHeader(String text) {
        String value = text.replaceAll("\\s+", "");
        String day = value.replaceFirst("^(?:星期|礼拜|禮拜|周|週)", "");
        if (day.equals(value) || day.length() != 1) return -1;
        int result = "一二三四五六日".indexOf(day);
        return result < 0 && "天".equals(day) ? 6 : result;
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

    private static boolean hasUtf16TimetableEvidence(byte[] bytes) {
        if (utf16BomCharset(bytes) != null) return true;
        // Share evidence only within one Tf resource on this page. A CJK font's UTF-16
        // bytes must not reinterpret even-length Latin text from another font as CJK.
        if (isEvenLength(bytes) && strictDecode(bytes, 0, StandardCharsets.UTF_8) == null) {
            String utf16 = strictDecode(bytes, 0, StandardCharsets.UTF_16BE);
            return utf16 != null && hasReadableText(utf16);
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
    static String strictDecode(byte[] bytes, int offset, Charset charset) {
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
