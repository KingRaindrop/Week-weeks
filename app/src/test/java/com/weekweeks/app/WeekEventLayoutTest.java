package com.weekweeks.app;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class WeekEventLayoutTest {
    private static final class Event implements WeekEventLayout.Item {
        final int day, minute;
        final long id;
        Event(long id, int day, int minute) { this.id = id; this.day = day; this.minute = minute; }
        public int eventDay() { return day; }
        public int eventStartMinutes() { return minute; }
        public long eventId() { return id; }
    }

    @Test public void groupsByWeekdayWithoutShiftingToDateDisplayOrder() {
        Event monday = new Event(1, 0, 720), sunday = new Event(2, 6, 480);
        List<Event> input = Arrays.asList(sunday, monday);
        assertEquals(Collections.singletonList(monday), WeekEventLayout.forDay(input, 0));
        assertEquals(Collections.singletonList(sunday), WeekEventLayout.forDay(input, 6));
        assertTrue(WeekEventLayout.forDay(input, 3).isEmpty());
    }

    @Test public void sortsTimeThenStableIdWithoutMutatingInput() {
        Event later = new Event(3, 2, 900), earlySecond = new Event(2, 2, 480), earlyFirst = new Event(1, 2, 480);
        List<Event> input = Arrays.asList(later, earlySecond, earlyFirst);
        assertEquals(Arrays.asList(earlyFirst, earlySecond, later), WeekEventLayout.forDay(input, 2));
        assertEquals(later, input.get(0));
    }

    @Test public void invalidAndEmptyDaysRemainEmpty() {
        assertTrue(WeekEventLayout.forDay(null, 0).isEmpty());
        List<Event> input = Arrays.asList(null, new Event(1, -1, 0), new Event(2, 7, 0));
        assertTrue(WeekEventLayout.forDay(input, 0).isEmpty());
        assertTrue(WeekEventLayout.forDay(input, -1).isEmpty());
        assertTrue(WeekEventLayout.forDay(input, 7).isEmpty());
    }

    @Test public void previewsTwoAndReportsExactOverflow() {
        assertEquals(0, WeekEventLayout.visibleCount(0, false));
        assertEquals(1, WeekEventLayout.visibleCount(1, false));
        assertEquals(2, WeekEventLayout.visibleCount(2, false));
        assertEquals(2, WeekEventLayout.visibleCount(7, false));
        assertEquals(5, WeekEventLayout.hiddenCount(7, false));
        assertEquals(0, WeekEventLayout.hiddenCount(2, false));
    }

    @Test public void expansionShowsAllAndCollapseRestoresPreview() {
        assertEquals(7, WeekEventLayout.visibleCount(7, true));
        assertEquals(0, WeekEventLayout.hiddenCount(7, true));
        assertEquals(2, WeekEventLayout.visibleCount(7, false));
        assertEquals(0, WeekEventLayout.visibleCount(-1, true));
        assertEquals(0, WeekEventLayout.hiddenCount(-1, false));
    }
}
