package com.weekweeks.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure presentation rules for the independent event strip above the period grid. */
final class WeekEventLayout {
    static final int PREVIEW_COUNT = 2;

    interface Item {
        int eventDay();
        int eventStartMinutes();
        long eventId();
    }

    private WeekEventLayout() {}

    static <T extends Item> List<T> forDay(List<T> events, int day) {
        List<T> result = new ArrayList<>();
        if (day < 0 || day > 6 || events == null) return result;
        for (T event : events) {
            if (event != null && event.eventDay() == day) result.add(event);
        }
        Collections.sort(result, (first, second) -> {
            int byTime = Integer.compare(first.eventStartMinutes(), second.eventStartMinutes());
            return byTime != 0 ? byTime : Long.compare(first.eventId(), second.eventId());
        });
        return result;
    }

    static int visibleCount(int count, boolean expanded) {
        return expanded ? Math.max(0, count) : Math.min(PREVIEW_COUNT, Math.max(0, count));
    }

    static int hiddenCount(int count, boolean expanded) {
        return Math.max(0, count) - visibleCount(count, expanded);
    }
}
