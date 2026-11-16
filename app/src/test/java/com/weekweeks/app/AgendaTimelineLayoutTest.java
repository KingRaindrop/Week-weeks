package com.weekweeks.app;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import static org.junit.Assert.*;

public class AgendaTimelineLayoutTest {
    private static final class Event implements AgendaTimelineLayout.Item {
        int day, start, end;
        long id;
        Event(int day, int start, int end, long id) {
            this.day = day; this.start = start; this.end = end; this.id = id;
        }
        public int eventDay() { return day; }
        public int eventStartMinutes() { return start; }
        public int eventEndMinutes() { return end; }
        public long eventId() { return id; }
    }

    @Test public void nullAndEmptySourcesUseTheDefaultEightToTwentyRange() {
        for (List<Event> source : Arrays.asList(null, Collections.<Event>emptyList(), Arrays.asList((Event) null))) {
            AgendaTimelineLayout.Layout<Event> result = AgendaTimelineLayout.layout(source);
            assertEquals(480, result.startMinute);
            assertEquals(1200, result.endMinute);
            assertTrue(result.groups.isEmpty());
        }
    }

    @Test public void durationAboveTheMinimumRemainsProportional() {
        Event hour = new Event(0, 480, 540, 1), twoHours = new Event(0, 540, 660, 2);
        AgendaTimelineLayout.Layout<Event> result = AgendaTimelineLayout.layout(Arrays.asList(twoHours, hour));
        List<AgendaTimelineLayout.Placement<Event>> items = placements(result);
        assertEquals(60, items.get(0).endMinute - items.get(0).startMinute);
        assertEquals(120, items.get(1).endMinute - items.get(1).startMinute);
        assertEquals(2 * (items.get(0).endMinute - items.get(0).startMinute),
                items.get(1).endMinute - items.get(1).startMinute);
        assertEquals(480, result.startMinute);
        assertEquals(660, result.endMinute);
    }

    @Test public void orderIsDayThenStartThenIdAndEqualKeysRemainStable() {
        Event laterDay = new Event(1, 0, 60, 0), laterTime = new Event(0, 510, 570, Long.MIN_VALUE);
        Event highId = new Event(0, 480, 600, Long.MAX_VALUE), lowId = new Event(0, 480, 540, Long.MIN_VALUE);
        Event equalFirst = new Event(0, 480, 550, 3), equalSecond = new Event(0, 480, 560, 3);
        List<AgendaTimelineLayout.Placement<Event>> items = placements(AgendaTimelineLayout.layout(
                Arrays.asList(laterDay, equalFirst, highId, laterTime, equalSecond, lowId)));
        assertEquals(Arrays.asList(lowId, equalFirst, equalSecond, highId, laterTime, laterDay), originals(items));
        for (int lane = 0; lane < 4; lane++) assertEquals(lane, items.get(lane).lane);
    }

    @Test public void halfOpenAdjacentEventsAndDifferentDaysStartSeparateGroups() {
        AgendaTimelineLayout.Layout<Event> result = AgendaTimelineLayout.layout(Arrays.asList(
                new Event(1, 480, 600, 3), new Event(0, 540, 600, 2), new Event(0, 480, 540, 1)));
        assertEquals(3, result.groups.size());
        for (AgendaTimelineLayout.Group<Event> group : result.groups) {
            assertEquals(1, group.laneCount);
            assertEquals(0, group.placements.get(0).lane);
        }
    }

    @Test public void connectedOverlapsReuseReleasedLanesAndHaveOneMinimumLaneCount() {
        AgendaTimelineLayout.Group<Event> group = AgendaTimelineLayout.layout(Arrays.asList(
                new Event(0, 600, 660, 5), new Event(0, 550, 620, 4), new Event(0, 530, 560, 3),
                new Event(0, 500, 530, 2), new Event(0, 480, 600, 1))).groups.get(0);
        assertEquals(480, group.startMinute);
        assertEquals(660, group.endMinute);
        assertEquals(3, group.laneCount);
        assertEquals(Arrays.asList(0, 1, 1, 2, 0), lanes(group));
        assertMinimumCollisionFreeLanes(group);
    }

    @Test public void aTransitiveOverlapIsOneGroupEvenWhenItsEndsDoNotOverlap() {
        AgendaTimelineLayout.Layout<Event> result = AgendaTimelineLayout.layout(Arrays.asList(
                new Event(0, 480, 540, 1), new Event(0, 520, 580, 2), new Event(0, 560, 620, 3)));
        assertEquals(1, result.groups.size());
        assertEquals(2, result.groups.get(0).laneCount);
        assertEquals(Arrays.asList(0, 1, 0), lanes(result.groups.get(0)));
    }

    @Test public void shortBlocksUseVisualSpansForBothGroupingAndColumnConflicts() {
        AgendaTimelineLayout.Layout<Event> result = AgendaTimelineLayout.layout(Arrays.asList(
                new Event(0, 480, 485, 1), new Event(0, 490, 495, 2), new Event(0, 510, 540, 3)));
        assertEquals(1, result.groups.size());
        AgendaTimelineLayout.Group<Event> group = result.groups.get(0);
        assertEquals(2, group.laneCount);
        assertEquals(Arrays.asList(0, 1, 0), lanes(group));
        assertEquals(510, group.placements.get(0).endMinute);
        assertEquals(520, group.placements.get(1).endMinute);
        assertMinimumCollisionFreeLanes(group);
    }

    @Test public void missingEndsAndSubThirtyMinuteEventsUseOnlyAVisualMinimum() {
        Event missing = new Event(0, 480, -1, 1), shortEvent = new Event(0, 540, 569, 2);
        Event exact = new Event(0, 600, 630, 3), longer = new Event(0, 660, 691, 4);
        List<AgendaTimelineLayout.Placement<Event>> items = placements(AgendaTimelineLayout.layout(
                Arrays.asList(missing, shortEvent, exact, longer)));
        assertEquals(Arrays.asList(30, 30, 30, 31), durations(items));
        assertEquals(-1, missing.end);
        assertEquals(569, shortEvent.end);
        assertEquals(630, exact.end);
        assertEquals(691, longer.end);
    }

    @Test public void midnightUsesAZeroOriginAndAtLeastOneHourOfAxis() {
        AgendaTimelineLayout.Layout<Event> result = AgendaTimelineLayout.layout(
                Arrays.asList(new Event(0, 0, 5, 1)));
        assertEquals(0, result.startMinute);
        assertEquals(60, result.endMinute);
        assertEquals(0, result.groups.get(0).placements.get(0).startMinute);
        assertEquals(30, result.groups.get(0).endMinute);
    }

    @Test public void lastMinuteKeepsItsCoordinateAndExtendsIntoBottomPadding() {
        Event late = new Event(6, 1439, -1, 1);
        AgendaTimelineLayout.Layout<Event> result = AgendaTimelineLayout.layout(Arrays.asList(late));
        assertEquals(1380, result.startMinute);
        assertEquals(1440, result.endMinute);
        AgendaTimelineLayout.Group<Event> group = result.groups.get(0);
        assertEquals(1439, group.startMinute);
        assertEquals(1469, group.endMinute);
        assertEquals(1439, group.placements.get(0).startMinute);
        assertEquals(1469, group.placements.get(0).endMinute);
        assertEquals(-1, late.end);
        AgendaTimelineLayout.Layout<Event> shortLate = AgendaTimelineLayout.layout(
                Arrays.asList(new Event(0, 1430, 1439, 2)));
        assertEquals(1460, shortLate.groups.get(0).endMinute);
        assertEquals(1440, shortLate.endMinute);
    }

    @Test public void axisRoundsOutwardAcrossAllDaysInsteadOfUsingSourceOrder() {
        AgendaTimelineLayout.Layout<Event> result = AgendaTimelineLayout.layout(Arrays.asList(
                new Event(0, 907, 971, 1), new Event(6, 487, 492, 2), new Event(3, 630, 690, 3)));
        assertEquals(480, result.startMinute);
        assertEquals(1020, result.endMinute);
        assertEquals(907, result.groups.get(0).startMinute);
        AgendaTimelineLayout.Layout<Event> wholeDay = AgendaTimelineLayout.layout(Arrays.asList(
                new Event(0, 1439, -1, 4), new Event(6, 0, -1, 5)));
        assertEquals(0, wholeDay.startMinute);
        assertEquals(1440, wholeDay.endMinute);
    }

    @Test public void invalidRowsAreFilteredWithoutTreatingOtherNegativeEndsAsMissing() {
        Event valid = new Event(0, 480, 510, 0);
        List<Event> invalid = Arrays.asList(null,
                new Event(-1, 480, 510, 1), new Event(7, 480, 510, 2),
                new Event(0, -1, 510, 3), new Event(0, 1440, -1, 4),
                new Event(0, Integer.MAX_VALUE, -1, 5), new Event(0, Integer.MIN_VALUE, -1, 6),
                new Event(0, 480, -2, 7), new Event(0, 480, 480, 8),
                new Event(0, 480, 479, 9), new Event(0, 480, 1440, 10),
                new Event(0, 480, Integer.MAX_VALUE, 11));
        AgendaTimelineLayout.Layout<Event> empty = AgendaTimelineLayout.layout(invalid);
        assertTrue(empty.groups.isEmpty());
        assertEquals(480, empty.startMinute);
        assertEquals(1200, empty.endMinute);
        List<Event> mixed = new ArrayList<>(invalid); mixed.add(valid);
        assertEquals(Arrays.asList(valid), originals(placements(AgendaTimelineLayout.layout(mixed))));
    }

    @Test public void sourceOrderValuesAndReturnedGeometryRemainIndependent() {
        Event last = new Event(6, 700, 701, 8), first = new Event(0, 480, -1, 2);
        List<Event> source = new ArrayList<>(Arrays.asList(last, first));
        AgendaTimelineLayout.Layout<Event> result = AgendaTimelineLayout.layout(source);
        assertEquals(Arrays.asList(last, first), source);
        assertEquals(701, last.end);
        assertEquals(-1, first.end);
        assertSame(first, result.groups.get(0).placements.get(0).item);
        source.clear(); first.start = 900;
        assertEquals(2, result.groups.size());
        assertEquals(480, result.groups.get(0).placements.get(0).startMinute);
        try { result.groups.clear(); fail("Layout groups must be read-only"); }
        catch (UnsupportedOperationException expected) { }
        try { result.groups.get(0).placements.clear(); fail("Placements must be read-only"); }
        catch (UnsupportedOperationException expected) { }
    }

    @Test(timeout = 2000) public void threeHundredSimultaneousEventsEachHaveOneDistinctLane() {
        List<Event> source = new ArrayList<>();
        for (int id = 299; id >= 0; id--) source.add(new Event(0, 600, 660, id));
        AgendaTimelineLayout.Layout<Event> result = AgendaTimelineLayout.layout(source);
        assertEquals(1, result.groups.size());
        AgendaTimelineLayout.Group<Event> group = result.groups.get(0);
        assertEquals(300, group.laneCount);
        assertEquals(300, group.placements.size());
        for (int id = 0; id < 300; id++) {
            assertEquals(id, group.placements.get(id).item.id);
            assertEquals(id, group.placements.get(id).lane);
        }
    }

    @Test(timeout = 2000) public void mixedThreeHundredEventLayoutIsDeterministicAndUsesTheMinimumLanes() {
        List<Event> source = new ArrayList<>();
        for (int id = 0; id < 300; id++) {
            int start = (id * 37) % 1400;
            source.add(new Event(id % 7, start, id % 4 == 0 ? -1 : Math.min(1439, start + 1 + id % 90), id));
        }
        Collections.shuffle(source, new Random(7));
        AgendaTimelineLayout.Layout<Event> first = AgendaTimelineLayout.layout(source);
        assertEquals(300, placements(first).size());
        for (AgendaTimelineLayout.Group<Event> group : first.groups) assertMinimumCollisionFreeLanes(group);
        Collections.shuffle(source, new Random(19));
        AgendaTimelineLayout.Layout<Event> second = AgendaTimelineLayout.layout(source);
        List<AgendaTimelineLayout.Placement<Event>> left = placements(first), right = placements(second);
        for (int i = 0; i < left.size(); i++) {
            assertSame(left.get(i).item, right.get(i).item);
            assertEquals(left.get(i).lane, right.get(i).lane);
            assertEquals(left.get(i).startMinute, right.get(i).startMinute);
            assertEquals(left.get(i).endMinute, right.get(i).endMinute);
        }
    }

    private static void assertMinimumCollisionFreeLanes(AgendaTimelineLayout.Group<Event> group) {
        int peak = 0;
        for (AgendaTimelineLayout.Placement<Event> left : group.placements) {
            int overlapping = 0;
            assertTrue(left.lane >= 0 && left.lane < group.laneCount);
            assertEquals(group.day, left.item.day);
            for (AgendaTimelineLayout.Placement<Event> right : group.placements) {
                if (right.startMinute <= left.startMinute && right.endMinute > left.startMinute) overlapping++;
                if (left != right && left.lane == right.lane)
                    assertTrue(left.endMinute <= right.startMinute || right.endMinute <= left.startMinute);
            }
            peak = Math.max(peak, overlapping);
        }
        assertEquals(peak, group.laneCount);
    }

    private static List<AgendaTimelineLayout.Placement<Event>> placements(AgendaTimelineLayout.Layout<Event> layout) {
        List<AgendaTimelineLayout.Placement<Event>> result = new ArrayList<>();
        for (AgendaTimelineLayout.Group<Event> group : layout.groups) result.addAll(group.placements);
        return result;
    }
    private static List<Event> originals(List<AgendaTimelineLayout.Placement<Event>> placements) {
        List<Event> result = new ArrayList<>();
        for (AgendaTimelineLayout.Placement<Event> placement : placements) result.add(placement.item);
        return result;
    }
    private static List<Integer> lanes(AgendaTimelineLayout.Group<Event> group) {
        List<Integer> result = new ArrayList<>();
        for (AgendaTimelineLayout.Placement<Event> placement : group.placements) result.add(placement.lane);
        return result;
    }
    private static List<Integer> durations(List<AgendaTimelineLayout.Placement<Event>> placements) {
        List<Integer> result = new ArrayList<>();
        for (AgendaTimelineLayout.Placement<Event> placement : placements)
            result.add(placement.endMinute - placement.startMinute);
        return result;
    }
}
