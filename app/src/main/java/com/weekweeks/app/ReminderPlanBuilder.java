package com.weekweeks.app;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Builds a schedule without touching Android services, storage or live UI state. */
final class ReminderPlanBuilder {
    /** Adapter for the current semester, period times and localized display labels. */
    interface CourseDetails {
        default boolean activeChange(Course series, LessonSchedule.Change change) { return false; }
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
            addCourse(entries, usedRequestCodes, course, details, now, courseRequestCode(course));
            for(LessonSchedule.Change change:course.changes.values()) {
                if(change.target==0 || !details.activeChange(course,change)) continue;
                Course moved=LessonSchedule.project(course,change.source,change);
                addCourse(entries,usedRequestCodes,moved,details,now,occurrenceCode(courseRequestCode(course),change.source));
            }
        }
        for (PersonalEvent event : events) {
            int baseCode=eventRequestCode(event);
            if(event.repeat==EventSeries.ONCE) { addEvent(entries,usedRequestCodes,event,now,baseCode); continue; }
            if(event.remindMinutes>=0) {
                RepeatAlarm rule=RepeatAlarm.from(event);
                if(rule.next(now,event.remindMinutes)>0) entries.add(new ReminderScheduler.Entry(reserve(baseCode,usedRequestCodes),
                        event.title,event.timeLabel(),event.location,event.remindMinutes,new long[0],ReminderScheduler.KIND_EVENT,rule));
            }
            for(int source:event.changes.keySet()) {
                PersonalEvent moved=EventSeries.occurrence(event,source);
                if(moved!=null) addEvent(entries,usedRequestCodes,moved,now,occurrenceCode(baseCode,source));
            }
        }
        return entries;
    }

    private static void addCourse(List<ReminderScheduler.Entry> out,Set<Integer> used,Course c,CourseDetails details,long now,int code) {
        long[] triggers=details.triggerTimes(c,now); if(triggers.length==0) return;
        out.add(new ReminderScheduler.Entry(reserve(code,used),c.name,details.timeLabel(c),c.room,details.leadMinutes(c),triggers));
    }
    private static void addEvent(List<ReminderScheduler.Entry> out,Set<Integer> used,PersonalEvent e,long now,int code) {
        if(!e.hasValidLocalTimes() || e.triggerAtMillis()<=now) return;
        out.add(new ReminderScheduler.Entry(reserve(code,used),e.title,e.timeLabel(),e.location,e.remindMinutes,
                new long[]{e.triggerAtMillis()},ReminderScheduler.KIND_EVENT));
    }
    private static int occurrenceCode(int base,int source) { return (31*base+source) & Integer.MAX_VALUE; }

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
