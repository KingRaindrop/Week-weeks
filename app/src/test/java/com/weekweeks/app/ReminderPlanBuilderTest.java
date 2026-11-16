package com.weekweeks.app;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class ReminderPlanBuilderTest {
    private static final long NOW = 100L;
    private static final ReminderPlanBuilder.CourseDetails DETAILS = new ReminderPlanBuilder.CourseDetails() {
        @Override public long[] triggerTimes(Course course, long now) {
            return course.invalidWeeks ? new long[0] : new long[]{now + 200, now + 100};
        }
        @Override public String timeLabel(Course course) { return "08:00-09:30"; }
        @Override public int leadMinutes(Course course) { return 15; }
    };

    private Course course(long id) {
        Course course = new Course();
        course.id = id; course.name = "Math"; course.room = "A101";
        course.period = 1; course.endPeriod = 2; course.weeks = "1-16";
        return course;
    }
    private PersonalEvent event(long id) {
        PersonalEvent event = new PersonalEvent();
        event.id = id; event.title = "Library"; event.location = "B2";
        event.year = 2030; event.month = 1; event.day = 2;
        event.startMinutes = 600; event.remindMinutes = 10;
        return event;
    }
    private List<ReminderScheduler.Entry> build(List<Course> courses, List<PersonalEvent> events) {
        return ReminderPlanBuilder.build(courses, events, NOW, DETAILS);
    }

    @Test public void emptyInputsProduceEmptyPlan() {
        assertTrue(build(Collections.emptyList(), Collections.emptyList()).isEmpty());
    }
    @Test public void coursePayloadAndSortedOccurrencesArePreserved() {
        ReminderScheduler.Entry entry = build(Collections.singletonList(course(9)), Collections.emptyList()).get(0);
        assertEquals(9, entry.requestCode);
        assertEquals("Math", entry.name); assertEquals("A101", entry.room);
        assertEquals("08:00-09:30", entry.time); assertEquals(15, entry.leadMinutes);
        assertEquals(ReminderScheduler.KIND_COURSE, entry.kind);
        assertArrayEquals(new long[]{200, 300}, entry.triggerTimes);
    }
    @Test public void temporaryBlankNullAndNoOccurrencesAreSkipped() {
        Course temporary = course(1); temporary.temporary = true;
        Course blank = course(2); blank.name = "  ";
        Course unnamed = course(3); unnamed.name = null;
        Course invalid = course(4); invalid.invalidWeeks = true;
        assertTrue(build(Arrays.asList(temporary, blank, unnamed, invalid), Collections.emptyList()).isEmpty());
    }
    @Test public void duplicateCourseIdsReserveDifferentCodesInInputOrder() {
        List<ReminderScheduler.Entry> entries = build(Arrays.asList(course(9), course(9), course(10)), Collections.emptyList());
        assertEquals(9, entries.get(0).requestCode);
        assertEquals(10, entries.get(1).requestCode);
        assertEquals(11, entries.get(2).requestCode);
    }
    @Test public void collisionAtMaxValueWrapsToOne() {
        List<ReminderScheduler.Entry> entries = build(Arrays.asList(course(Integer.MAX_VALUE), course(Integer.MAX_VALUE)), Collections.emptyList());
        assertEquals(Integer.MAX_VALUE, entries.get(0).requestCode);
        assertEquals(1, entries.get(1).requestCode);
    }
    @Test public void eventsShareCollisionNamespaceWithCourses() {
        PersonalEvent event = event(123);
        int code = ReminderPlanBuilder.eventRequestCode(event);
        List<ReminderScheduler.Entry> entries = build(Collections.singletonList(course(code)), Collections.singletonList(event));
        assertEquals(code, entries.get(0).requestCode);
        assertEquals(code == Integer.MAX_VALUE ? 1 : code + 1, entries.get(1).requestCode);
        ReminderScheduler.Entry entry = entries.get(1);
        assertEquals(ReminderScheduler.KIND_EVENT, entry.kind);
        assertEquals(event.title, entry.name); assertEquals(event.location, entry.room);
        assertEquals(event.timeLabel(), entry.time); assertEquals(10, entry.leadMinutes);
        assertArrayEquals(new long[]{event.triggerAtMillis()}, entry.triggerTimes);
    }
    @Test public void hiddenEventsStillRemindButDisabledAndExpiredDoNot() {
        PersonalEvent hidden = event(1); hidden.showInTimetable = false;
        PersonalEvent disabled = event(2); disabled.remindMinutes = PersonalEvent.NO_REMINDER;
        PersonalEvent expired = event(3); expired.year = 1970; expired.month = 1; expired.day = 1;
        List<ReminderScheduler.Entry> entries = ReminderPlanBuilder.build(Collections.emptyList(),
                Arrays.asList(hidden, disabled, expired), hidden.triggerAtMillis() - 1, DETAILS);
        assertEquals(1, entries.size());
        assertEquals(ReminderPlanBuilder.eventRequestCode(hidden), entries.get(0).requestCode);
        assertTrue(ReminderPlanBuilder.build(Collections.emptyList(), Collections.singletonList(hidden),
                hidden.triggerAtMillis(), DETAILS).isEmpty());
    }
    @Test public void fallbackHashKeepsDaySpanAndNullHandling() {
        assertEquals(1, ReminderPlanBuilder.courseRequestCode(null));
        Course first = course(0); first.name = null; first.weeks = null;
        int hash = 17;
        hash = 31 * hash + first.day; hash = 31 * hash + first.period;
        hash = 31 * hash + 1; hash = 31 * hash; hash = 31 * hash;
        int expected = hash & 0x7fffffff;
        assertEquals(expected == 0 ? 1 : expected, ReminderPlanBuilder.courseRequestCode(first));
        Course second = first.copy(); second.day++;
        assertNotEquals(ReminderPlanBuilder.courseRequestCode(first), ReminderPlanBuilder.courseRequestCode(second));
    }
    @Test public void nonzeroIdHashRetainsLegacyZeroResult() {
        assertEquals(0, ReminderPlanBuilder.courseRequestCode(course(0x100000001L)));
    }
    @Test public void buildingDoesNotMutateSourceListsOrModels() {
        Course course = course(4); PersonalEvent event = event(5);
        List<Course> courses = new ArrayList<>(Collections.singletonList(course));
        List<PersonalEvent> events = new ArrayList<>(Collections.singletonList(event));
        List<ReminderScheduler.Entry> result = build(courses, events);
        result.clear();
        assertEquals(1, courses.size()); assertSame(course, courses.get(0));
        assertEquals("Math", course.name); assertEquals(4, course.id);
        assertEquals(1, events.size()); assertSame(event, events.get(0));
        assertEquals(10, event.remindMinutes);
    }
}
