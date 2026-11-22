package com.weekweeks.app;

import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class WeeklyGridImportTest {
    @Test public void pastedTsvUsesExplicitWeekdaysAndLeavesEmptySlotsEmpty() {
        String text="\tThu\tMon\tSun\n第01-02节\tArt/A\t\t\n3\t\t\"Logic\nWeeks: 2-8 even\nRoom: A|101\nTeacher: May\"\t";
        TextTimetableParser.Result r=TextTimetableParser.parse(text,12,20);
        assertTrue(r.errors.toString(),r.errors.isEmpty());assertEquals(2,r.courses.size());
        PdfCourseParser.ParsedCourse a=r.courses.get(0), b=r.courses.get(1);
        assertEquals("Art/A",a.name);assertEquals(3,a.day);assertEquals(1,a.period);assertEquals(2,a.endPeriod);
        assertEquals("Logic",b.name);assertEquals(0,b.day);assertEquals(3,b.period);assertEquals(3,b.endPeriod);
        assertEquals("A|101",b.room);assertEquals("May",b.teacher);assertTrue(TimetableRules.parseWeeks(b.weeks).weeks[8]);
        assertFalse(TimetableRules.parseWeeks(b.weeks).weeks[3]);
    }
    @Test public void titleAndMultilineQuotedCsvKeepTwoCoursesInOneSlot() throws Exception {
        String text="# note\r\n秋季课程表\r\n节次;周三;周一\r\n1;\"设计, A\n1-8周(单)\n\n设计 B\n周次：1-8周(双)\";\r\n2;;另一个课";
        TimetableFileParser.Result r=TimetableFileParser.parse(text.getBytes(StandardCharsets.UTF_8),"grid.csv",8,1024,10);
        assertEquals(3,r.courses.size());assertEquals("设计, A",r.courses.get(0).name);assertEquals("设计 B",r.courses.get(1).name);
        assertEquals(2,r.courses.get(1).day);assertTrue(TimetableRules.parseWeeks(r.courses.get(1).weeks).weeks[6]);assertEquals(0,r.courses.get(2).day);
    }
    @Test public void rejectsUnclearOrInvalidGridAsAWholeWithCoordinates() {
        String head="节次,周一,周三\n1,已有课,\n";
        for(String[] c:new String[][]{{head+"2,\"Lab\nSmith\",","B3"},{head+"2,,\"Lab\n周次:31周\"","C3"},
                {head+"2,,\"Lab\nRoom:X\nRoom:Y\"","C3"},{head+"3-2,Lab,","A3"},{head+"1,Lab,","A3"},
                {head+"2,31周,","B3"},{head+"2,---,","B3"},{head+"2,,,Lab","D3"},{"节次,周一,周一\n1,A,B","C1"},{head+"1 2,Lab,","A3"}}){
            TextTimetableParser.Result r=TextTimetableParser.parse(c[0],16,10);assertTrue(c[0],r.courses.isEmpty());
            assertTrue(r.errors.toString(),r.errors.toString().contains(c[1]));
        }
        TextTimetableParser.Result capped=TextTimetableParser.parse("Period,Mon,Fri\n1,A,B",16,1);
        assertTrue(capped.courses.isEmpty());assertFalse(capped.errors.isEmpty());
    }
    @Test public void titlesContainingWeekWordsAndSlashesRemainLiteral() {
        String text = "# a comment with an unmatched \"\n课程表\n節次\t週一\t週五\n第０１－０２節\t"
                + "周易／教室：Ａ/101／授課教師：周老師\tTwo Weeks/A";
        TextTimetableParser.Result r = TextTimetableParser.parse(text, 16, 20);
        assertTrue(r.errors.toString(), r.errors.isEmpty());
        assertEquals(2, r.courses.size());
        assertEquals("周易", r.courses.get(0).name); assertEquals("Ａ/101", r.courses.get(0).room);
        assertEquals("周老師", r.courses.get(0).teacher); assertEquals("Two Weeks/A", r.courses.get(1).name);
    }

    @Test public void slashAtEndOfLineIsLiteralBeforeALabelOnTheNextLine() {
        TextTimetableParser.Result r = TextTimetableParser.parse("Period\tMon\tWed\n1\t\"Studio/\nRoom: East/\nTeacher: Lee\"\t", 16, 20);
        assertTrue(r.errors.toString(), r.errors.isEmpty());
        assertEquals("Studio/", r.courses.get(0).name);
        assertEquals("East/", r.courses.get(0).room);
    }

    @Test public void aListCourseNamedPeriodWithOtherWeekdayWordsIsStillAList() {
        TextTimetableParser.Result r = TextTimetableParser.parse("Period,Mon,1,,Fri,Thursday", 16, 20);
        assertTrue(r.errors.toString(), r.errors.isEmpty());
        assertEquals("Period", r.courses.get(0).name); assertEquals("Fri", r.courses.get(0).room);
        assertEquals("Thursday", r.courses.get(0).teacher);
    }

    @Test public void positionalListsRemainListsAndNeverJoinDigitsAcrossSpaces() {
        TextTimetableParser.Result r=TextTimetableParser.parse("Week Plan,Monday,1\nFine,Wed,０３－０４\nBad,Tue,1 2",16,20);
        assertEquals(2,r.courses.size());assertEquals("Week Plan",r.courses.get(0).name);assertEquals(3,r.courses.get(1).period);
        assertEquals(4,r.courses.get(1).endPeriod);assertEquals(1,r.errors.size());assertTrue(r.errors.get(0).contains("3"));
    }
}
