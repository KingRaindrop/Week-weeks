package com.weekweeks.app;

import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

/** Behavioral contract for the extracted model; intentionally independent of Android and MainActivity. */
public class CourseTest {
    @After public void resetLanguage() { UiLang.set(UiLang.HANS); }

    @Test public void defaultsMatchTheOriginalNestedModel() {
        Course course = new Course();
        assertEquals(0L, course.id);
        assertEquals(0, course.day);
        assertEquals(0, course.period);
        assertEquals(1, course.endPeriod);
        assertEquals("", course.name);
        assertEquals("", course.weeks);
        assertEquals("", course.room);
        assertEquals("", course.teacher);
        assertEquals(0xFF347B80, Course.DEFAULT_COLOR);
        assertEquals(Course.DEFAULT_COLOR, course.color);
        assertFalse(course.temporary);
        assertFalse(course.invalidWeeks);
        assertEquals(-1, course.customStartMinutes);
        assertEquals(-1, course.customEndMinutes);
    }

    @Test public void copyPreservesEveryFieldAndIsIndependent() {
        Course original = new Course();
        original.id = 9_123_456_789L;
        original.day = 6;
        original.period = 8;
        original.endPeriod = 10;
        original.name = "Advanced Math";
        original.weeks = "1-16单周";
        original.room = "A101";
        original.teacher = "Teacher";
        original.color = 0xFF446FA6;
        original.temporary = true;
        original.invalidWeeks = true;
        original.customStartMinutes = 600;
        original.customEndMinutes = 735;
        Course copy = original.copy();
        assertNotSame(original, copy);
        assertEquals(original.id, copy.id);
        assertEquals(original.day, copy.day);
        assertEquals(original.period, copy.period);
        assertEquals(original.endPeriod, copy.endPeriod);
        assertEquals(original.name, copy.name);
        assertEquals(original.weeks, copy.weeks);
        assertEquals(original.room, copy.room);
        assertEquals(original.teacher, copy.teacher);
        assertEquals(original.color, copy.color);
        assertEquals(original.temporary, copy.temporary);
        assertEquals(original.invalidWeeks, copy.invalidWeeks);
        assertEquals(original.customStartMinutes, copy.customStartMinutes);
        assertEquals(original.customEndMinutes, copy.customEndMinutes);
        copy.name = "Changed";
        copy.room = "B202";
        copy.color = Course.DEFAULT_COLOR;
        copy.temporary = false;
        assertEquals("Advanced Math", original.name);
        assertEquals("A101", original.room);
        assertEquals(0xFF446FA6, original.color);
        assertTrue(original.temporary);
    }

    @Test public void copyDoesNotNormalizeNullOrUnusualValues() {
        Course course = new Course();
        course.name = null; course.weeks = null; course.room = null; course.teacher = null;
        course.day = -7; course.period = 0; course.endPeriod = -2;
        course.color = 123; course.customStartMinutes = -99; course.customEndMinutes = 2000;
        Course copy = course.copy();
        assertNull(copy.name); assertNull(copy.weeks); assertNull(copy.room); assertNull(copy.teacher);
        assertEquals(-7, copy.day); assertEquals(0, copy.period); assertEquals(-2, copy.endPeriod);
        assertEquals(123, copy.color); assertEquals(-99, copy.customStartMinutes); assertEquals(2000, copy.customEndMinutes);
    }

    @Test public void importInterfaceExposesLiveFieldsAndUpdatesOnlyLocation() {
        Course course = new Course();
        course.id = 91; course.day = 4; course.period = 3; course.endPeriod = 4;
        course.name = "Math"; course.weeks = "1-16"; course.room = "A101"; course.teacher = "Li";
        course.color = 42; course.temporary = true; course.invalidWeeks = true;
        course.customStartMinutes = 500; course.customEndMinutes = 600;
        ImportPlanner.Item item = course;
        assertEquals(4, item.importDay()); assertEquals(3, item.importStart()); assertEquals(4, item.importEnd());
        assertEquals("Math", item.importName()); assertEquals("1-16", item.importWeeks());
        assertEquals("A101", item.importRoom()); assertEquals("Li", item.importTeacher());
        item.applyImportedLocation("B202", "Wang");
        assertEquals("B202", course.room); assertEquals("Wang", course.teacher);
        assertEquals(91, course.id); assertEquals(4, course.day); assertEquals(3, course.period); assertEquals(4, course.endPeriod);
        assertEquals("Math", course.name); assertEquals("1-16", course.weeks); assertEquals(42, course.color);
        assertTrue(course.temporary); assertTrue(course.invalidWeeks);
        assertEquals(500, course.customStartMinutes); assertEquals(600, course.customEndMinutes);
        item.applyImportedLocation(null, null);
        assertNull(item.importRoom()); assertNull(item.importTeacher());
        course.day = 6; course.name = "Renamed";
        assertEquals(6, item.importDay()); assertEquals("Renamed", item.importName());
    }

    @Test public void colorInterfacePreservesUnvalidatedColorAndTemporaryFlag() {
        Course course = new Course();
        CourseColorRules.Item item = course;
        course.name = "Physics";
        assertEquals("Physics", item.colorName());
        assertEquals(Course.DEFAULT_COLOR, item.colorValue());
        item.setColorValue(0x01234567);
        assertEquals(0x01234567, course.color);
        assertEquals(0x01234567, item.colorValue());
        assertFalse(item.temporaryColor());
        course.temporary = true;
        assertTrue(item.temporaryColor());
    }

    @Test public void simplifiedPeriodAndBadgeTextMatchExistingWording() {
        UiLang.set(UiLang.HANS);
        Course course = new Course(); course.period = 3; course.endPeriod = 3;
        assertEquals("第3节", course.periodLabel());
        assertEquals("第\n3\n节", course.periodBadgeText());
        course.endPeriod = 5;
        assertEquals("第3-5节", course.periodLabel());
        assertEquals("第\n3-5\n节", course.periodBadgeText());
    }

    @Test public void traditionalPeriodAndBadgeTextMatchExistingWording() {
        UiLang.set(UiLang.HANT);
        Course course = new Course(); course.period = 2; course.endPeriod = 2;
        assertEquals("第2節", course.periodLabel());
        assertEquals("第\n2\n節", course.periodBadgeText());
        course.endPeriod = 4;
        assertEquals("第2-4節", course.periodLabel());
        assertEquals("第\n2-4\n節", course.periodBadgeText());
    }

    @Test public void englishPeriodAndBadgeTextPreserveEnDash() {
        UiLang.set(UiLang.EN);
        Course course = new Course(); course.period = 7; course.endPeriod = 7;
        assertEquals("Period 7", course.periodLabel());
        assertEquals("Period\n7", course.periodBadgeText());
        course.endPeriod = 9;
        assertEquals("Periods 7–9", course.periodLabel());
        assertEquals("Periods\n7–9", course.periodBadgeText());
        course.endPeriod = 1; // Existing behavior: reversed intervals still use the single-period label.
        assertEquals("Period 7", course.periodLabel());
        assertEquals("Period\n7", course.periodBadgeText());
    }

    @Test public void secondaryTextPreservesFieldOrderSeparatorAndWhitespace() {
        UiLang.set(UiLang.HANS);
        Course course = new Course();
        course.weeks = "1-16周"; course.room = " A101 "; course.teacher = "Li";
        assertEquals("1-16周 ·  A101  · Li", course.secondaryText());
        course.invalidWeeks = true;
        assertEquals("⚠ 上课周格式需修正 · 1-16周 ·  A101  · Li", course.secondaryText());
        course.weeks = null; course.room = "";
        assertEquals("⚠ 上课周格式需修正 · Li", course.secondaryText());
    }

    @Test public void secondaryFallbackAndWarningAreLocalized() {
        Course course = new Course();
        UiLang.set(UiLang.HANS);
        assertEquals("暂无上课周、地点和老师信息", course.secondaryText());
        course.invalidWeeks = true;
        assertEquals("⚠ 上课周格式需修正", course.secondaryText());
        UiLang.set(UiLang.HANT);
        assertEquals("⚠ 上課週格式需修正", course.secondaryText());
        course.invalidWeeks = false;
        assertEquals("暫無上課週、地點和老師資訊", course.secondaryText());
        UiLang.set(UiLang.EN);
        assertEquals("No weeks, location or teacher", course.secondaryText());
        course.invalidWeeks = true;
        assertEquals("⚠ Weeks need fixing", course.secondaryText());
        course.weeks = null; course.room = null; course.teacher = null;
        assertEquals("⚠ Weeks need fixing", course.secondaryText());
        course.invalidWeeks = false;
        assertEquals("No weeks, location or teacher", course.secondaryText());
    }
}
