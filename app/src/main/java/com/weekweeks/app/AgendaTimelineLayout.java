package com.weekweeks.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/** Pure presentation geometry for events on a shared weekly minute axis. */
final class AgendaTimelineLayout {
    private static final int MINIMUM_SPAN = 30;
    private static final int DAY_END = 24 * 60;

    interface Item {
        int eventDay();
        int eventStartMinutes();
        int eventEndMinutes();
        long eventId();
    }

    static final class Layout<T extends Item> {
        final int startMinute, endMinute;
        final List<Group<T>> groups;

        private Layout(int startMinute, int endMinute, List<Group<T>> groups) {
            this.startMinute = startMinute;
            this.endMinute = endMinute;
            this.groups = Collections.unmodifiableList(new ArrayList<>(groups));
        }
    }

    static final class Group<T extends Item> {
        final int day, startMinute, endMinute, laneCount;
        final List<Placement<T>> placements;

        private Group(int day, int startMinute, int endMinute, int laneCount, List<Placement<T>> placements) {
            this.day = day;
            this.startMinute = startMinute;
            this.endMinute = endMinute;
            this.laneCount = laneCount;
            this.placements = Collections.unmodifiableList(new ArrayList<>(placements));
        }
    }

    static final class Placement<T extends Item> {
        final T item;
        final int startMinute, endMinute, lane;

        private Placement(T item, int startMinute, int endMinute, int lane) {
            this.item = item;
            this.startMinute = startMinute;
            this.endMinute = endMinute;
            this.lane = lane;
        }
    }

    private AgendaTimelineLayout() { }

    /**
     * Intervals are half-open. A short or untimed-end event occupies at least thirty
     * visual minutes, including any space past midnight; its start never moves.
     */
    static <T extends Item> Layout<T> layout(List<T> source) {
        List<Candidate<T>> sorted = new ArrayList<>();
        if (source != null) for (T item : source) {
            if (item == null) continue;
            int day = item.eventDay(), start = item.eventStartMinutes(), end = item.eventEndMinutes();
            if (day < 0 || day > 6 || start < 0 || start >= DAY_END
                    || (end != -1 && (end <= start || end >= DAY_END))) continue;
            sorted.add(new Candidate<>(item, day, start,
                    end == -1 ? start + MINIMUM_SPAN : Math.max(end, start + MINIMUM_SPAN), item.eventId()));
        }
        // Collections.sort is stable: equal day/start/ID rows retain their source order.
        Collections.sort(sorted, (left, right) -> {
            int byDay = Integer.compare(left.day, right.day);
            if (byDay != 0) return byDay;
            int byStart = Integer.compare(left.start, right.start);
            return byStart != 0 ? byStart : Long.compare(left.id, right.id);
        });
        List<Group<T>> groups = new ArrayList<>();
        if (sorted.isEmpty()) return new Layout<>(8 * 60, 20 * 60, groups);
        int firstMinute = DAY_END, lastMinute = 0;
        GroupBuilder<T> current = null;
        for (Candidate<T> item : sorted) {
            firstMinute = Math.min(firstMinute, item.start);
            lastMinute = Math.max(lastMinute, item.end);
            if (current == null || current.day != item.day || item.start >= current.end) {
                if (current != null) groups.add(current.finish());
                current = new GroupBuilder<>(item.day, item.start);
            }
            current.add(item);
        }
        groups.add(current.finish());
        int start = firstMinute / 60 * 60;
        int end = Math.min(DAY_END, (lastMinute + 59) / 60 * 60);
        return new Layout<>(start, Math.max(start + 60, end), groups);
    }

    /** Snapshot getters once, without changing the item or its original collection. */
    private static final class Candidate<T extends Item> {
        final T item;
        final int day, start, end;
        final long id;
        Candidate(T item, int day, int start, int end, long id) {
            this.item = item; this.day = day; this.start = start; this.end = end; this.id = id;
        }
    }

    private static final class OccupiedLane {
        final int end, lane;
        OccupiedLane(int end, int lane) { this.end = end; this.lane = lane; }
    }

    private static final class GroupBuilder<T extends Item> {
        final int day, start;
        int end, laneCount;
        final List<Placement<T>> placements = new ArrayList<>();
        final PriorityQueue<Integer> freeLanes = new PriorityQueue<>();
        final PriorityQueue<OccupiedLane> occupied = new PriorityQueue<>(11, (left, right) -> {
            int byEnd = Integer.compare(left.end, right.end);
            return byEnd != 0 ? byEnd : Integer.compare(left.lane, right.lane);
        });

        GroupBuilder(int day, int start) { this.day = day; this.start = start; this.end = start; }

        void add(Candidate<T> item) {
            while (!occupied.isEmpty() && occupied.peek().end <= item.start)
                freeLanes.add(occupied.remove().lane);
            // A new lane is needed only when every existing lane still overlaps this event.
            int lane = freeLanes.isEmpty() ? laneCount++ : freeLanes.remove();
            placements.add(new Placement<>(item.item, item.start, item.end, lane));
            occupied.add(new OccupiedLane(item.end, lane));
            end = Math.max(end, item.end);
        }

        Group<T> finish() { return new Group<>(day, start, end, laneCount, placements); }
    }
}
