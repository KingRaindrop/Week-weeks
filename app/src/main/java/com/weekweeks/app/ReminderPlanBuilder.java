package com.weekweeks.app;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Builds a schedule without touching Android services, storage or live UI state. */
final class ReminderPlanBuilder {
    /** Adapter for the current semester, period times and localized display labels. */
    interface CourseDetails {
        long[] triggerTimes(Course course, long now);
        String timeLabel(Course course);
        int leadMinutes(Course course);
    }

    private ReminderPlanBuilder() { }

    static List<ReminderScheduler.Entry> build(List<Course> courses,
            List<PersonalEvent> events, long now, CourseDetails details) {
        List<ReminderScheduler.Entry> entries = new ArrayList<>();
        Set<Integer> usedRequestCodes = new HashSet<>();
        for (Course course : courses) {
            if (course.temporary || course.name == null || course.name.trim().isEmpty()) continue;
            long[] triggers = details.triggerTimes(course, now);
            if (triggers.length == 0) continue;
            int code = reserve(courseRequestCode(course), usedRequestCodes);
            entries.add(new ReminderScheduler.Entry(code, course.name, details.timeLabel(course),
                    course.room, details.leadMinutes(course), triggers));
        }
        for (PersonalEvent event : events) {
            long triggerAt = event.triggerAtMillis();
            if (triggerAt <= now) continue;
            int code = reserve(eventRequestCode(event), usedRequestCodes);
            entries.add(new ReminderScheduler.Entry(code, event.title, event.timeLabel(),
                    event.location, event.remindMinutes, new long[]{triggerAt},
                    ReminderScheduler.KIND_EVENT));
        }
        return entries;
    }

    private static int reserve(int code, Set<Integer> used) {
        while (used.contains(code)) code = code == Integer.MAX_VALUE ? 1 : code + 1;
        used.add(code);
        return code;
    }

    static int courseRequestCode(Course course) {
        if (course == null) return 1;
        long id = course.id;
        if (id != 0L) return (int) (id ^ (id >>> 32)) & 0x7fffffff;
        // Preserve the legacy fallback for unassigned IDs, including day and period span.
        int hash = 17;
        hash = 31 * hash + course.day;
        hash = 31 * hash + course.period;
        hash = 31 * hash + Math.max(0, course.endPeriod - course.period);
        hash = 31 * hash + (course.weeks == null ? 0 : course.weeks.hashCode());
        hash = 31 * hash + (course.name == null ? 0 : course.name.hashCode());
        int result = hash & 0x7fffffff;
        return result == 0 ? 1 : result;
    }

    /** Keep the event hash distinct from the course hash for equal creation timestamps. */
    static int eventRequestCode(PersonalEvent event) {
        int code = (int) ((event.id * 0x9E3779B97F4A7C15L) >>> 33);
        return code == 0 ? 1 : code;
    }
}
