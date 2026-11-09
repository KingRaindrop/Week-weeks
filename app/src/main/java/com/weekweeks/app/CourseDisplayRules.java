package com.weekweeks.app;

/** Presentation-only rules; never changes the stored course teacher. */
final class CourseDisplayRules {
    private CourseDisplayRules() { }

    static String teacherLabel(boolean show, String teacher) {
        if (!show || teacher == null || teacher.trim().isEmpty()) return "";
        return teacher.trim();
    }
}
