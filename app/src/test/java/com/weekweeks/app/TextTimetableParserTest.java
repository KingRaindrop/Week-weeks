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
    public void csvHandlesBomQuotedCommasAndEscapedQuotes() {
        String text = "\uFEFFCourse,Day,Periods,Weeks,Room,Teacher\r\n"
                + "\"Data, \"\"Science\"\"\",Monday,1-2,\"1-2周,4-12周\",\"A,101\",Smith\r\n";
        TextTimetableParser.Result result = TextTimetableParser.parse(text, 10, 200);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(1, result.courses.size());
        PdfCourseParser.ParsedCourse course = result.courses.get(0);
        assertEquals("Data, \"Science\"", course.name);
        assertEquals("1-2周,4-12周", course.weeks);
        assertEquals("A,101", course.room);
        assertEquals("Smith", course.teacher);
    }

    @Test
    public void headerlessCsvAcceptsOptionalTrailingFields() {
        TextTimetableParser.Result result = TextTimetableParser.parse(
                "Math,Mon,1-2\nEnglish,Tuesday,3,1-16,,Smith", 10, 200);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(2, result.courses.size());
        assertEquals("", result.courses.get(0).weeks);
        assertEquals("Smith", result.courses.get(1).teacher);
    }

    @Test
    public void csvMultilineRecordsKeepPhysicalErrorLineNumbers() {
        TextTimetableParser.Result result = TextTimetableParser.parse(
                "Course,Day,Periods\n\"Math\nand logic\",Mon,1\nBad,Mondayish,1\nGood,Fri,2", 10, 200);
        assertEquals(2, result.courses.size());
        assertEquals("Math\nand logic", result.courses.get(0).name);
        assertEquals(1, result.errors.size());
        assertTrue(result.errors.toString(), result.errors.get(0).startsWith("第 4 行"));
    }

    @Test
    public void headerlessCsvCanStartWithMultilineQuotedCourse() {
        TextTimetableParser.Result result = TextTimetableParser.parse("\"Math\nLogic\",Mon,1", 10, 200);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals("Math\nLogic", result.courses.get(0).name);
    }

    @Test
    public void tsvHeaderMappingAndPipesWithinCellsAreSupported() {
        TextTimetableParser.Result result = TextTimetableParser.parse(
                "Teacher\tWeeks\tDay of week\tCourse Name\tLocation\tPeriods\n"
                + "\"Smith, Jane\"\t1-16\tThurs.\tMath | Logic\tA|101\t3-4", 10, 200);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        PdfCourseParser.ParsedCourse course = result.courses.get(0);
        assertEquals("Math | Logic", course.name);
        assertEquals("A|101", course.room);
        assertEquals(3, course.day);
        assertEquals("Smith, Jane", course.teacher);
    }

    @Test
    public void reorderedTraditionalHeadersAndValuesAreNormalizedForDownstreamRules() {
        TextTimetableParser.Result result = TextTimetableParser.parse(
                "節次,任課教師,星期幾,課程名稱,上課週次,上課地點\n"
                + "第1-2節,陳老師,禮拜二,數學,1-16週單週,A101", 10, 200);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        PdfCourseParser.ParsedCourse course = result.courses.get(0);
        assertEquals("數學", course.name);
        assertEquals(1, course.day);
        assertEquals("1-16周单周", course.weeks);
        assertTrue(TimetableRules.parseWeeks(course.weeks).valid);
    }

    @Test
    public void headerCanIncludeMetadataButExtraUnheadedCellsAreErrors() {
        TextTimetableParser.Result result = TextTimetableParser.parse(
                "课程代码,地点,星期,课程名称,节次\n001,A101,周三,物理,2\n002,B,周四,化学,3,unexpected", 10, 200);
        assertEquals(1, result.courses.size());
        assertEquals("物理", result.courses.get(0).name);
        assertEquals(1, result.errors.size());
        assertTrue(result.errors.get(0).contains("列数超过表头"));
    }

    @Test
    public void rowApiSupportsReorderedStartEndPeriodsAndNullBlankCells() {
        java.util.List<java.util.List<String>> rows = java.util.Arrays.asList(
                java.util.Arrays.asList("\uFEFFTeacher", "End Period", "Course", "Day", "Start Period", "Weeks"),
                java.util.Arrays.asList(null, "4", "Math", "Mon", "3", "1-16"),
                java.util.Arrays.asList("Li", "", "English", "Tue", "5"));
        TextTimetableParser.Result result = TextTimetableParser.parseRows(rows, 10, 200);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(2, result.courses.size());
        assertEquals(3, result.courses.get(0).period);
        assertEquals(4, result.courses.get(0).endPeriod);
        assertEquals(5, result.courses.get(1).endPeriod);
        assertEquals("", result.courses.get(0).teacher);
        assertEquals("\uFEFFTeacher", rows.get(0).get(0)); // The shared API does not mutate caller cells.
    }

    @Test
    public void rowApiReportsNullRowsAndRejectsVisualGrids() {
        TextTimetableParser.Result result = TextTimetableParser.parseRows(java.util.Arrays.asList(
                java.util.Arrays.asList("Math", "Mon", "1"), null,
                java.util.Arrays.asList("English", "Tue", "2")), 10, 200);
        assertEquals(2, result.courses.size());
        assertEquals(1, result.errors.size());
        assertTrue(result.errors.get(0).startsWith("第 2 行"));
        TextTimetableParser.Result grid = TextTimetableParser.parseRows(java.util.Arrays.asList(
                java.util.Arrays.asList("节次", "周一", "周二"),
                java.util.Arrays.asList("1", "Math", "English")), 10, 200);
        assertTrue(grid.courses.isEmpty());
        assertEquals(2, grid.errors.size());
    }

    @Test
    public void missingDuplicateAndConflictingHeaderColumnsFailClosed() {
        for (String header : new String[]{"Course,Day,Teacher", "Course,Day,Periods,Weekday",
                "Course,Day,Periods,Start Period", "Course,Day,End Period"}) {
            TextTimetableParser.Result result = TextTimetableParser.parse(header + "\nMath,Mon,1,2", 10, 200);
            assertTrue(header, result.courses.isEmpty());
            assertEquals(header, 1, result.errors.size());
            assertTrue(header, result.errors.get(0).contains("表头"));
        }
    }

    @Test
    public void malformedCsvQuotingIsNotSilentlyDropped() {
        String text = "Course,Day,Periods\nBad\"name,Mon,1\n\"Bad\"suffix,Tue,2\nGood,Fri,3";
        TextTimetableParser.Result result = TextTimetableParser.parse(text, 10, 200);
        assertEquals(1, result.courses.size());
        assertEquals("Good", result.courses.get(0).name);
        assertEquals(2, result.errors.size());
        assertTrue(result.errors.get(0).startsWith("第 2 行"));
        assertTrue(result.errors.get(1).startsWith("第 3 行"));
        TextTimetableParser.Result unclosed = TextTimetableParser.parse("Course,Day,Periods\n\"Bad,Mon,1", 10, 200);
        assertTrue(unclosed.courses.isEmpty());
        assertEquals(1, unclosed.errors.size());
        assertTrue(unclosed.errors.get(0).contains("引号"));
    }

    @Test
    public void unquotedCsvWeekCommasAreErrorsNotGuessedBackTogether() {
        TextTimetableParser.Result result = TextTimetableParser.parse(
                "Course,Day,Periods,Weeks\nBad,Mon,1,1-2,4-8\nGood,Tue,2,\"1-2,4-8\"", 10, 200);
        assertEquals(1, result.courses.size());
        assertEquals("Good", result.courses.get(0).name);
        assertEquals(1, result.errors.size());
    }

    @Test
    public void strictDaysPeriodsAndWeeksRejectRatherThanGuess() {
        String text = "Course,Day,Periods,Weeks\n"
                + "Bad,Mondayish,1,1-16\nBad,2026-11-16,1,1-16\nBad,Mon,0,1-16\n"
                + "Bad,Mon,1.5,1-16\nBad,Mon,4-2,1-16\nBad,Mon,11,1-16\n"
                + "Bad,Mon,1,0-16\nBad,Mon,1,1-31\nBad,Mon,1,first week\nGood,Mon,1,1-16";
        TextTimetableParser.Result result = TextTimetableParser.parse(text, 10, 200);
        assertEquals(1, result.courses.size());
        assertEquals(9, result.errors.size());
        assertEquals(-1, TextTimetableParser.parseDay("Mondayish"));
        assertEquals(-1, TextTimetableParser.parseDay("Sunday evening"));
    }

    @Test
    public void separatePeriodColumnsStillEnforceIntegerAndBounds() {
        TextTimetableParser.Result result = TextTimetableParser.parse(
                "Course,Day,Start Period,End Period\n"
                + "Bad,Mon,1-2,3\nBad,Mon,2,1\nBad,Mon,1,11\nBad,Mon,1,2.5\nGood,Mon,1,2", 10, 200);
        assertEquals(1, result.courses.size());
        assertEquals(4, result.errors.size());
    }

    @Test
    public void commentsBomAndBlankRecordsDoNotHideInvalidData() {
        TextTimetableParser.Result result = TextTimetableParser.parse(
                "\uFEFF# comment with | and unmatched \"\n\nCourse,Day,Periods\n, , \nBad\nGood,Mon,1", 10, 200);
        assertEquals(1, result.courses.size());
        assertEquals(1, result.errors.size());
        assertTrue(result.errors.get(0).startsWith("第 5 行"));
    }

    @Test
    public void courseNamesBeginningWithOldHeaderPrefixAreNotDiscarded() {
        TextTimetableParser.Result result = TextTimetableParser.parse(
                "课程名称研究 | 周一 | 1\n课程名 | 周二 | 2\n\"Quoted\" | 周三 | 3", 10, 200);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(3, result.courses.size());
        assertEquals("课程名称研究", result.courses.get(0).name);
        assertEquals("\"Quoted\"", result.courses.get(2).name);
    }

    @Test
    public void englishWeekKeywordsAreValidatedAndNormalized() {
        TextTimetableParser.Result result = TextTimetableParser.parse(
                "Course,Day,Periods,Weeks\nMath,Mon,1,1-16 weeks odd\nPE,Fri,2,every week", 10, 200);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertTrue(TimetableRules.parseWeeks(result.courses.get(0).weeks).valid);
        assertEquals("每周", result.courses.get(1).weeks);
    }

    @Test
    public void rowApiEnforcesCapacityAndTextLimits() {
        TextTimetableParser.Result capped = TextTimetableParser.parseRows(java.util.Arrays.asList(
                java.util.Arrays.asList("Course", "Day", "Periods"),
                java.util.Arrays.asList("Math", "Mon", "1"), java.util.Arrays.asList("English", "Tue", "2")), 10, 1);
        assertEquals(1, capped.courses.size());
        assertEquals(1, capped.errors.size());
        assertTrue(capped.errors.get(0).contains("第 3 行"));
        String huge = new String(new char[TextTimetableParser.MAX_TEXT_CHARS + 1]).replace('\0', 'x');
        TextTimetableParser.Result oversized = TextTimetableParser.parseRows(java.util.Collections.singletonList(
                java.util.Arrays.asList(huge, "Mon", "1")), 10, 200);
        assertTrue(oversized.courses.isEmpty());
        assertEquals(1, oversized.errors.size());
    }

    @Test
    public void chineseStartEndHeadersAndQuotedTsvCellsAreSupported() {
        TextTimetableParser.Result result = TextTimetableParser.parse(
                "课程名称\t星期\t开始节次\t结束节次\t老师\n"
                + "\"数学\n逻辑\"\t周一\t第1节\t第2节\t\"张\"\"老师\"\n", 10, 200);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(1, result.courses.size());
        assertEquals("数学\n逻辑", result.courses.get(0).name);
        assertEquals("张\"老师", result.courses.get(0).teacher);
        assertEquals(2, result.courses.get(0).endPeriod);
    }

    @Test
    public void tabOnlyBlankLinesAndSinglePipeInCsvNameDoNotConfuseDetection() {
        TextTimetableParser.Result result = TextTimetableParser.parse("\t\t\nMath|Logic,Mon,1", 10, 200);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals("Math|Logic", result.courses.get(0).name);
    }

    @Test
    public void malformedFirstRowDoesNotDisableNextExplicitHeaderMapping() {
        TextTimetableParser.Result result = TextTimetableParser.parseRows(java.util.Arrays.asList(
                null, java.util.Arrays.asList("Day", "Periods", "Name"),
                java.util.Arrays.asList("Monday", "1", "Math")), 10, 200);
        assertEquals(1, result.courses.size());
        assertEquals("Math", result.courses.get(0).name);
        assertEquals(1, result.errors.size());
    }

    @Test
    public void semicolonCsvUsesQuotedFieldsAndLeavesWeekCommasIntact() {
        TextTimetableParser.Result result = TextTimetableParser.parse(
                "Course;Day;Periods;Weeks;Room;Teacher\r\n"
                + "\"Math; Logic\";Mon;1-2;1-4周,8-16周;\"A;101\";\"Smith \"\"J\"\"\"", 10, 200);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(1, result.courses.size());
        assertEquals("Math; Logic", result.courses.get(0).name);
        assertEquals("1-4周,8-16周", result.courses.get(0).weeks);
        assertEquals("A;101", result.courses.get(0).room);
        assertEquals("Smith \"J\"", result.courses.get(0).teacher);
    }

    @Test
    public void excelSeparatorDeclarationDoesNotBecomeACourseOrShiftLineNumbers() {
        TextTimetableParser.Result result = TextTimetableParser.parse(
                "\uFEFFsep=;\r\nDay;Periods;Course\r\nMon;1;Math\r\nMondayish;2;Bad", 10, 200);
        assertEquals(1, result.courses.size());
        assertEquals("Math", result.courses.get(0).name);
        assertEquals(1, result.errors.size());
        assertTrue(result.errors.toString(), result.errors.get(0).startsWith("第 4 行"));
    }

    @Test
    public void headerlessCsvWithTwoLiteralPipesIsNotMistakenForLegacyPipeRows() {
        TextTimetableParser.Result result = TextTimetableParser.parse("Math|Logic|Lab,Mon,1", 10, 200);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals("Math|Logic|Lab", result.courses.get(0).name);
    }

    @Test
    public void headerlessSemicolonCsvDoesNotSplitCommasOrPipesInsideFields() {
        TextTimetableParser.Result result = TextTimetableParser.parse(
                "Math;Mon;1;1-2周,4-8周;A|B|C;Smith, Jane", 10, 200);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals("A|B|C", result.courses.get(0).room);
        assertEquals("Smith, Jane", result.courses.get(0).teacher);
    }

    @Test
    public void supportedSeparatorDeclarationsAreCaseInsensitiveAndKeepQuotedRecords() {
        for (char separator : new char[]{',', ';', '\t', '|', '｜'}) {
            String text = "# comment\n\nSeP=" + separator + "\nCourse" + separator + "Day" + separator + "Periods\n"
                    + "\"Math\nLogic\"" + separator + "Mon" + separator + "1";
            TextTimetableParser.Result result = TextTimetableParser.parse(text, 10, 200);
            assertTrue("separator=" + separator + " " + result.errors, result.errors.isEmpty());
            assertEquals("Math\nLogic", result.courses.get(0).name);
        }
    }

    @Test
    public void malformedOrUnsupportedSeparatorDeclarationsFailRatherThanGuess() {
        for (String directive : new String[]{"sep=", "sep=;;", "sep=:", "sep= , ;"}) {
            TextTimetableParser.Result result = TextTimetableParser.parse(directive + "\nMath,Mon,1", 10, 200);
            assertTrue(directive, result.courses.isEmpty());
            assertEquals(directive, 1, result.errors.size());
            assertTrue(directive, result.errors.get(0).contains("sep="));
        }
    }

    @Test
    public void declarationIsOnlyAcceptedBeforeDataAndMustMatchActualSeparator() {
        for (String text : new String[]{
                "sep=;\nCourse,Day,Periods\nMath,Mon,1",
                "sep=;\nCourse;Day;Periods\nsep=;\nMath;Mon;1",
                "Course;Day;Periods\nMath;Mon;1\nsep=;"}) {
            TextTimetableParser.Result result = TextTimetableParser.parse(text, 10, 200);
            assertTrue(text, !result.errors.isEmpty());
        }
    }

    @Test
    public void semicolonMultilineMalformedQuotesReportPhysicalSourceLine() {
        TextTimetableParser.Result result = TextTimetableParser.parse(
                "sep=;\nCourse;Day;Periods\n\"Math\nLogic\";Mon;1\n\"Bad\"suffix;Tue;2\nGood;Fri;3", 10, 200);
        assertEquals(2, result.courses.size());
        assertEquals(1, result.errors.size());
        assertTrue(result.errors.toString(), result.errors.get(0).startsWith("第 5 行"));
        assertTrue(result.errors.get(0).contains("引号"));
    }

    @Test
    public void legacyPipeAndTsvKeepSemicolonsAndCommaWeekListsAsCellContent() {
        TextTimetableParser.Result result = TextTimetableParser.parse(
                "Math; Logic | Mon | 1 | 1,3,5周 | A;101 | Smith, Jane\n"
                + "Data; Structures\tTue\t2\t1,3,5周\tA|B|C\tLi; Wang", 10, 200);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals(2, result.courses.size());
        assertEquals("Math; Logic", result.courses.get(0).name);
        assertEquals("A|B|C", result.courses.get(1).room);
    }

    @Test
    public void semicolonAndSeparatorMetadataDoNotBypassExistingLimits() {
        TextTimetableParser.Result capped = TextTimetableParser.parse("sep=;\nA;Mon;1\nB;Tue;2", 10, 1);
        assertEquals(1, capped.courses.size());
        assertEquals(1, capped.errors.size());
        assertTrue(capped.errors.get(0).contains("上限"));
        TextTimetableParser.Result huge = TextTimetableParser.parse("sep=;\n" + " ".repeat(TextTimetableParser.MAX_TEXT_CHARS), 10, 200);
        assertTrue(huge.courses.isEmpty());
        assertEquals(1, huge.errors.size());
        assertTrue(huge.errors.get(0).contains("分批"));
    }

    @Test
    public void quotedHashCourseNamesAreDataAcrossDelimitedFormats() {
        for (char delimiter : new char[]{',', ';', '\t'}) {
            String text = "Course" + delimiter + "Day" + delimiter + "Periods\n"
                    + "\"# Number theory\"" + delimiter + "Mon" + delimiter + "1\n"
                    + "Logic" + delimiter + "Tue" + delimiter + "2";
            TextTimetableParser.Result result = TextTimetableParser.parse(text, 10, 200);
            assertTrue(result.errors.toString(), result.errors.isEmpty());
            assertEquals("delimiter=" + delimiter, 2, result.courses.size());
            assertEquals("# Number theory", result.courses.get(0).name);
        }
    }

    @Test
    public void headerlessQuotedHashNameDoesNotHideDelimiterOrMalformedRow() {
        TextTimetableParser.Result result = TextTimetableParser.parse("\"# Seminar\";Mon;1", 10, 200);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals("# Seminar", result.courses.get(0).name);
        TextTimetableParser.Result invalid = TextTimetableParser.parse(
                "Course,Day,Periods\n\"# Seminar\",Mondayish,1\nLogic,Tue,2", 10, 200);
        assertEquals(1, invalid.errors.size());
        assertTrue(invalid.errors.get(0).startsWith("第 2 行"));
    }

    @Test
    public void rowApiKeepsHashPrefixedDataButStillAcceptsStandaloneComments() {
        TextTimetableParser.Result result = TextTimetableParser.parseRows(java.util.Arrays.asList(
                java.util.Arrays.asList("# comment", "", ""),
                java.util.Arrays.asList("Teacher", "Course", "Day", "Periods"),
                java.util.Arrays.asList("#12", "Math", "Mon", "1")), 10, 200);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        assertEquals("#12", result.courses.get(0).teacher);
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
