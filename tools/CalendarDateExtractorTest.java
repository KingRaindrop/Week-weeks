package com.example.meinstundenplan;

import java.util.Calendar;

/** CalendarDateExtractor 的回归测试：校历开学日/散学日提取。 */
public class CalendarDateExtractorTest {
    private static int exitCode = 0;

    private static String fmt(Calendar c) {
        return c == null ? "null" : String.format("%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private static void check(String label, Calendar actual, String expected) {
        String actualStr = fmt(actual);
        boolean ok = actualStr.equals(expected);
        System.out.println((ok ? "PASS " : "FAIL ") + label + ": got " + actualStr + ", expected " + expected);
        if (!ok) {
            exitCode = 1;
        }
    }

    public static void main(String[] args) {
        // 1. 教职工报到（2-28）早于学生注册（3-2）：开学取最晚
        check("start-register", CalendarDateExtractor.extractStart("2026年2月28日教职工报到，2026年3月2日学生注册、开学"), "2026-03-02");
        // 2. "正式上课"关键词
        check("start-classes", CalendarDateExtractor.extractStart("2026年3月2日正式上课，校历第1周开始"), "2026-03-02");
        // 3. 散学
        check("end-close", CalendarDateExtractor.extractEnd("2026年7月19日散学，暑假从7月20日开始"), "2026-07-19");
        // 4. 无年份日期（7月20日）→ 页面基准年份
        check("end-summer", CalendarDateExtractor.extractEnd("7月20日开始放暑假，下学期8月31日报到"), "2026-07-20");
        // 5. 无关键词 → null
        check("no-keyword", CalendarDateExtractor.extractStart("这是一个普通页面，没有任何校历信息"), "null");
        // 6. 有关键词但附近无日期 → null
        check("keyword-no-date", CalendarDateExtractor.extractStart("本学期开学时间另行通知"), "null");
        // 7. 日期在关键词之前
        check("date-before-kw", CalendarDateExtractor.extractStart("2026年9月6日老生注册报到"), "2026-09-06");
        // 8. 跨年学期末（寒假）
        check("cross-year", CalendarDateExtractor.extractEnd("2026年12月28日寒假开始，2027年2月28日开学"), "2026-12-28");
        // 9. 横线日期格式
        check("dash-format", CalendarDateExtractor.extractStart("开学时间：2026-03-02，报到日"), "2026-03-02");
        // 10. 非法月份 → null
        check("invalid-month", CalendarDateExtractor.extractStart("2026年13月40日报到"), "null");
        // 11. 紧凑真实页面：月份过滤保证开学/散学语义不串
        String page = "嘉应学院2025-2026学年校历 一、2025年9月1日老生注册 二、2026年3月2日学生注册，3月3日正式上课 三、2026年7月19日散学 四、2026年7月20日开始放暑假";
        check("compact-start", CalendarDateExtractor.extractStart(page), "2026-03-03");
        check("compact-end", CalendarDateExtractor.extractEnd(page), "2026-07-19");
        // 12. 散学早于暑假开始（取最早）
        check("end-min", CalendarDateExtractor.extractEnd("2026年7月19日散学，7月20日开始放暑假"), "2026-07-19");
        // 13. 上学期秋季开学（8-31/9-1）与春季开学（3-2）：开学取最晚=春季
        check("fall-start", CalendarDateExtractor.extractStart("2025年8月31日老生注册，2025年9月1日开学，2026年3月2日开学"), "2026-03-02");
        // 14. 月份过滤排除暑假日期进入开学候选
        check("filter-summer", CalendarDateExtractor.extractStart("2026年7月20日暑假开始，2026年9月1日开学"), "2026-09-01");
        // 15. null 输入 → null
        check("null-input", CalendarDateExtractor.extractStart(null), "null");

        System.out.println(exitCode == 0 ? "CalendarDateExtractorTest: all checks passed" : "CalendarDateExtractorTest: SOME FAILED");
        System.exit(exitCode);
    }
}
