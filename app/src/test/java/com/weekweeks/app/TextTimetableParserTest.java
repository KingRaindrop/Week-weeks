package com.weekweeks.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TextTimetableParserTest {
    @Test
    public void builtInExampleParsesCompletely() {
        TextTimetableParser.Result result = TextTimetableParser.parse(TextTimetableParser.EXAMPLE, 10, 200);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(5, result.courses.size());
        PdfCourseParser.ParsedCourse first = result.courses.get(0);
        assertEquals("高等数学", first.name);
        assertEquals(0, first.day);
        assertEquals(1, first.period);
        assertEquals(2, first.endPeriod);
        assertEquals("1-16周", first.weeks);
        assertEquals("教学楼A101", first.room);
        assertEquals("张老师", first.teacher);
        PdfCourseParser.ParsedCourse pe = result.courses.get(3);
        assertEquals("", pe.weeks);
        assertEquals("", pe.teacher);
        assertEquals(9, result.courses.get(4).period);
    }

    @Test
    public void acceptsTabsFullWidthBarsAndOptionalFields() {
        String text = "课程名\t星期\t节次\n线性代数\t星期三\t3\n"
                + "离散数学 ｜ 礼拜五 ｜ 第1-2节 ｜ 1-2周,4-12周\n"
                + "体育 | 7 | 5~6\n"
                + "English | Mon | 1至2 | | Room 1 | Smith";
        TextTimetableParser.Result result = TextTimetableParser.parse(text, 10, 200);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(4, result.courses.size());
        assertEquals(2, result.courses.get(0).day);
        assertEquals(3, result.courses.get(0).period);
        assertEquals(3, result.courses.get(0).endPeriod);
        assertEquals(4, result.courses.get(1).day);
        assertEquals("1-2周,4-12周", result.courses.get(1).weeks);
        assertEquals(6, result.courses.get(2).day);
        assertEquals(6, result.courses.get(2).endPeriod);
        assertEquals(0, result.courses.get(3).day);
        assertEquals("Smith", result.courses.get(3).teacher);
    }

    @Test
    public void reportsLineNumberedErrorsAndKeepsValidLines() {
        String text = "# 注释\n"
                + "高数 | 周八 | 1-2\n"
                + "英语 | 周一 | 4-3\n"
                + "物理 | 周一 | 11-12\n"
                + "化学 | 周二 | 1-2 | 第一周\n"
                + "只有名字\n"
                + " | 周一 | 1\n"
                + "生物 | 周二 | 1-2 | 1-16周 | 实验楼 | 赵老师";
        TextTimetableParser.Result result = TextTimetableParser.parse(text, 10, 200);
        assertEquals(1, result.courses.size());
        assertEquals("生物", result.courses.get(0).name);
        assertEquals(6, result.errors.size());
        assertTrue(result.errors.get(0).startsWith("第 2 行"));
        assertTrue(result.errors.get(0).contains("星期"));
        assertTrue(result.errors.get(1).contains("结束节次"));
        assertTrue(result.errors.get(2).contains("第 10 节"));
        assertTrue(result.errors.get(3).contains("上课周"));
        assertTrue(result.errors.get(4).contains("三项"));
        assertTrue(result.errors.get(5).contains("课程名"));
    }

    @Test
    public void enforcesInputLimits() {
        assertEquals(1, TextTimetableParser.parse("", 10, 200).errors.size());
        StringBuilder huge = new StringBuilder();
        while (huge.length() <= TextTimetableParser.MAX_TEXT_CHARS) huge.append("课 | 周一 | 1\n");
        TextTimetableParser.Result tooLong = TextTimetableParser.parse(huge.toString(), 10, 200);
        assertTrue(tooLong.courses.isEmpty());
        assertTrue(tooLong.errors.get(0).contains("分批"));

        TextTimetableParser.Result capped = TextTimetableParser.parse("a | 周一 | 1\nb | 周一 | 2\nc | 周一 | 3", 10, 2);
        assertEquals(2, capped.courses.size());
        assertTrue(capped.errors.get(0).contains("上限"));

        String longName = new String(new char[61]).replace('\0', '课');
        assertTrue(TextTimetableParser.parse(longName + " | 周一 | 1", 10, 200).courses.isEmpty());
        assertEquals(1, TextTimetableParser.parse("a | 周一 | 1 | | | | extra", 10, 200).errors.size());
    }

    @Test
    public void parsesDayVariants() {
        String[][] cases = {{"周一", "0"}, {"星期二", "1"}, {"礼拜三", "2"}, {"4", "3"}, {"Fri", "4"},
                {"周六", "5"}, {"周日", "6"}, {"周天", "6"}, {"星期天", "6"}, {"sunday", "6"}, {"周八", "-1"}, {"", "-1"}};
        for (String[] c : cases) {
            assertEquals(c[0], Integer.parseInt(c[1]), TextTimetableParser.parseDay(c[0]));
        }
    }
}
