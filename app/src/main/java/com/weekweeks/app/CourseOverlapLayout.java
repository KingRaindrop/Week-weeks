package com.weekweeks.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Groups connected period overlaps in one already-filtered teaching week. */
final class CourseOverlapLayout {
    interface Item {
        int overlapDay();
        int overlapStart();
        int overlapEnd();
    }

    static final class Group<T extends Item> {
        final int day, start;
        int end;
        final List<T> items = new ArrayList<>();
        Group(T item, int lastPeriod) {
            day = item.overlapDay(); start = item.overlapStart();
            end = Math.min(item.overlapEnd(), lastPeriod);
            items.add(item);
        }
    }

    private CourseOverlapLayout() { }

    static <T extends Item> List<Group<T>> group(List<T> source, int lastPeriod) {
        List<T> sorted = new ArrayList<>();
        for (T item : source) {
            if (item != null && item.overlapDay() >= 0 && item.overlapDay() < 7
                    && item.overlapStart() >= 1 && item.overlapStart() <= lastPeriod
                    && item.overlapEnd() >= item.overlapStart()) sorted.add(item);
        }
        Collections.sort(sorted, (left, right) -> {
            int day = Integer.compare(left.overlapDay(), right.overlapDay());
            if (day != 0) return day;
            int start = Integer.compare(left.overlapStart(), right.overlapStart());
            return start != 0 ? start : Integer.compare(left.overlapEnd(), right.overlapEnd());
        });
        List<Group<T>> groups = new ArrayList<>();
        Group<T> current = null;
        for (T item : sorted) {
            if (current == null || item.overlapDay() != current.day || item.overlapStart() > current.end) {
                current = new Group<>(item, lastPeriod);
                groups.add(current);
            } else {
                current.items.add(item);
                current.end = Math.max(current.end, Math.min(item.overlapEnd(), lastPeriod));
            }
        }
        return groups;
    }
}
