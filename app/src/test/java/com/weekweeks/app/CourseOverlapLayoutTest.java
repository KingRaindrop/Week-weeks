package com.weekweeks.app;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class CourseOverlapLayoutTest {
    private static class Slot implements CourseOverlapLayout.Item {
        final int day, start, end;
        Slot(int day, int start, int end) { this.day=day; this.start=start; this.end=end; }
        public int overlapDay() { return day; }
        public int overlapStart() { return start; }
        public int overlapEnd() { return end; }
    }

    @Test public void exactAndNestedOverlapsStayInOneReachableGroup() {
        Slot outer = new Slot(0, 1, 4), inner = new Slot(0, 2, 3), duplicate = new Slot(0, 1, 4);
        List<CourseOverlapLayout.Group<Slot>> groups = CourseOverlapLayout.group(Arrays.asList(inner, outer, duplicate), 11);
        assertEquals(1, groups.size());
        assertEquals(3, groups.get(0).items.size());
        assertEquals(1, groups.get(0).start);
        assertEquals(4, groups.get(0).end);
        assertTrue(groups.get(0).items.containsAll(Arrays.asList(outer, inner, duplicate)));
    }

    @Test public void transitivelyConnectedSpansDoNotCreateOverlappingGroupCards() {
        List<CourseOverlapLayout.Group<Slot>> groups = CourseOverlapLayout.group(Arrays.asList(
                new Slot(0, 4, 6), new Slot(0, 1, 2), new Slot(0, 2, 4), new Slot(0, 7, 8)), 11);
        assertEquals(2, groups.size());
        assertEquals(3, groups.get(0).items.size());
        assertEquals(6, groups.get(0).end);
        assertEquals(7, groups.get(1).start);
    }

    @Test public void adjacentPeriodsAndDifferentDaysStaySeparate() {
        List<CourseOverlapLayout.Group<Slot>> groups = CourseOverlapLayout.group(Arrays.asList(
                new Slot(1, 1, 2), new Slot(0, 3, 4), new Slot(0, 1, 2)), 11);
        assertEquals(3, groups.size());
        for (CourseOverlapLayout.Group<Slot> group : groups) assertEquals(1, group.items.size());
    }

    @Test public void invalidRowsAreIgnoredAndEndsAreClippedToConfiguredPeriods() {
        List<CourseOverlapLayout.Group<Slot>> groups = CourseOverlapLayout.group(Arrays.asList(
                null, new Slot(-1, 1, 2), new Slot(7, 1, 2), new Slot(0, 0, 1),
                new Slot(0, 12, 13), new Slot(0, 3, 2), new Slot(0, 10, 20)), 11);
        assertEquals(1, groups.size());
        assertEquals(11, groups.get(0).end);
    }

    @Test public void groupingDoesNotReorderSourceAndEmptyInputIsAllowed() {
        Slot last = new Slot(6, 9, 10), first = new Slot(0, 1, 2);
        List<Slot> input = new ArrayList<>(Arrays.asList(last, first));
        CourseOverlapLayout.group(input, 11);
        assertSame(last, input.get(0));
        assertTrue(CourseOverlapLayout.group(new ArrayList<Slot>(), 11).isEmpty());
    }
}
