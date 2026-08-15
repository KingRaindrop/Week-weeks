package com.example.meinstundenplan;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从校历页面文本中提取开学日 / 散学日。纯 Java，无 Android 依赖，可独立测试。
 *
 * 策略：
 * - 在关键词（注册/正式上课/开学…、散学/暑假/寒假…）前后 ±60 字符窗口内找日期；
 * - 日期格式支持「YYYY年M月D日 / YYYY-MM-DD / YYYY.M.D / M月D日（无年份）」；
 * - 月份必须落在语义允许范围（开学 1/2/3/8/9 月，散学 1/2/6/7/12 月），
 *   避免"注册"窗口误取到暑假日期、或"散学"窗口误取到秋季开学日期；
 * - 无年份日期用页面基准年份：学年（2025-2026 → 2026）→ 页面最后一个年份 → 当年；
 * - 多个候选时，开学日取最晚（教职工报到早于学生注册），散学日取最早（散学早于暑假开始）。
 */
final class CalendarDateExtractor {
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(?:(20\\d{2})\\s*[年./\\-]\\s*)?(\\d{1,2})\\s*[月./\\-]\\s*(\\d{1,2})\\s*日?"
    );
    private static final Pattern YEAR_PATTERN = Pattern.compile("(20\\d{2})\\s*[-—–至~～]\\s*(20\\d{2})");
    private static final Pattern ANY_YEAR_PATTERN = Pattern.compile("20\\d{2}");

    static final String[] START_KEYWORDS = {"注册", "正式上课", "开始上课", "开学", "第一周", "报到"};
    static final String[] END_KEYWORDS = {"散学", "学期结束", "学期末", "暑假", "寒假", "放假"};
    private static final int[] START_MONTHS = {1, 2, 3, 8, 9};
    private static final int[] END_MONTHS = {1, 2, 6, 7, 12};

    private static final int WINDOW_CHARS = 60;

    private CalendarDateExtractor() {
    }

    /** 提取开学日（取最晚候选），找不到返回 null。 */
    static Calendar extractStart(String text) {
        return extract(text, START_KEYWORDS, true, START_MONTHS);
    }

    /** 提取散学日（取最早候选），找不到返回 null。 */
    static Calendar extractEnd(String text) {
        return extract(text, END_KEYWORDS, false, END_MONTHS);
    }

    private static Calendar extract(String text, String[] keywords, boolean takeLatest, int[] allowedMonths) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", "");
        List<Calendar> candidates = new ArrayList<>();
        for (String keyword : keywords) {
            int index = normalized.indexOf(keyword);
            if (index < 0) {
                continue;
            }
            int from = Math.max(0, index - WINDOW_CHARS);
            int to = Math.min(normalized.length(), index + WINDOW_CHARS);
            Matcher matcher = DATE_PATTERN.matcher(normalized.substring(from, to));
            while (matcher.find()) {
                try {
                    Integer year = matcher.group(1) == null ? null : Integer.parseInt(matcher.group(1));
                    int month = Integer.parseInt(matcher.group(2));
                    int day = Integer.parseInt(matcher.group(3));
                    if (month < 1 || month > 12 || day < 1 || day > 31) {
                        continue;
                    }
                    if (!monthAllowed(month, allowedMonths)) {
                        continue;
                    }
                    if (year == null) {
                        year = baseYear(normalized);
                        if (year == null) {
                            continue;
                        }
                    }
                    Calendar calendar = Calendar.getInstance();
                    calendar.clear();
                    calendar.set(year, month - 1, day);
                    candidates.add(calendar);
                } catch (NumberFormatException ignored) {
                    // 继续尝试下一个匹配
                }
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        Calendar best = candidates.get(0);
        for (Calendar candidate : candidates) {
            if (takeLatest ? candidate.after(best) : candidate.before(best)) {
                best = candidate;
            }
        }
        return best;
    }

    private static boolean monthAllowed(int month, int[] allowed) {
        for (int value : allowed) {
            if (value == month) {
                return true;
            }
        }
        return false;
    }

    /** 页面基准年份：学年（2025-2026 → 2026）→ 页面最后一个年份 → 当年。 */
    private static Integer baseYear(String normalized) {
        Matcher yearMatcher = YEAR_PATTERN.matcher(normalized);
        if (yearMatcher.find()) {
            try {
                return Integer.parseInt(yearMatcher.group(2));
            } catch (NumberFormatException ignored) {
            }
        }
        Matcher anyYear = ANY_YEAR_PATTERN.matcher(normalized);
        Integer last = null;
        while (anyYear.find()) {
            try {
                last = Integer.parseInt(anyYear.group());
            } catch (NumberFormatException ignored) {
            }
        }
        if (last != null) {
            return last;
        }
        return Calendar.getInstance().get(Calendar.YEAR);
    }
}
