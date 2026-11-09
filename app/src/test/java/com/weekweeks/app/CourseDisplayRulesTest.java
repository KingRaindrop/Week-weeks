package com.weekweeks.app;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class CourseDisplayRulesTest {
    @Test public void disabledHidesTeacherWithoutChangingSource() {
        String source = " 王老师 ";
        assertEquals("", CourseDisplayRules.teacherLabel(false, source));
        assertEquals(" 王老师 ", source);
    }

    @Test public void enabledShowsOnlyTrimmedTeacherName() {
        assertEquals("王老师", CourseDisplayRules.teacherLabel(true, " 王老师 "));
        assertEquals("Alice Smith", CourseDisplayRules.teacherLabel(true, "Alice Smith"));
    }

    @Test public void missingTeacherNeverAddsPlaceholder() {
        assertEquals("", CourseDisplayRules.teacherLabel(true, null));
        assertEquals("", CourseDisplayRules.teacherLabel(true, ""));
        assertEquals("", CourseDisplayRules.teacherLabel(true, " \t\n"));
    }

    @Test public void togglingRestoresOriginalTeacher() {
        String teacher = "李老师、张老师";
        assertEquals(teacher, CourseDisplayRules.teacherLabel(true, teacher));
        assertEquals("", CourseDisplayRules.teacherLabel(false, teacher));
        assertEquals(teacher, CourseDisplayRules.teacherLabel(true, teacher));
    }
}
