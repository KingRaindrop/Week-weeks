package com.weekweeks.app;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Name-scoped course colors; callers stage copies before persisting changes. */
final class CourseColorRules {
    interface Item {
        String colorName();
        int colorValue();
        void setColorValue(int value);
        boolean temporaryColor();
    }

    private CourseColorRules() { }

    static Integer findColor(List<? extends Item> items, String name) {
        String key = TimetableRules.normalizedCourseText(name);
        if (key.isEmpty()) return null;
        Integer selected = null;
        for (Item item : items) {
            if (item.temporaryColor() || !key.equals(TimetableRules.normalizedCourseText(item.colorName()))) continue;
            int color = item.colorValue();
            if (selected == null) selected = color;
            // During migration prefer an explicit muted preset over legacy automatic colors.
            if (CoursePalette.isMuted(color)) return color;
        }
        return selected;
    }

    static boolean synchronize(List<? extends Item> items) {
        Map<String, Integer> colors = new LinkedHashMap<>();
        for (Item item : items) {
            String key = TimetableRules.normalizedCourseText(item.colorName());
            if (item.temporaryColor() || key.isEmpty()) continue;
            Integer previous = colors.get(key);
            if (previous == null || (!CoursePalette.isMuted(previous) && CoursePalette.isMuted(item.colorValue()))) {
                colors.put(key, item.colorValue());
            }
        }
        boolean changed = false;
        for (Item item : items) {
            if (item.temporaryColor()) continue;
            Integer color = colors.get(TimetableRules.normalizedCourseText(item.colorName()));
            if (color != null && color != item.colorValue()) {
                item.setColorValue(color);
                changed = true;
            }
        }
        return changed;
    }

    /** Shuffles each palette cycle without replacement; returns the number of named groups. */
    static int randomize(List<? extends Item> items, int[] palette, java.util.Random random) {
        if (palette.length == 0) return 0;
        java.util.ArrayList<Integer> pool = new java.util.ArrayList<>();
        for (int color : palette) if (!pool.contains(color)) pool.add(color);
        Map<String, Integer> colors = new LinkedHashMap<>();
        int index = 0;
        for (Item item : items) {
            String key = TimetableRules.normalizedCourseText(item.colorName());
            if (item.temporaryColor() || key.isEmpty() || colors.containsKey(key)) continue;
            if (index % pool.size() == 0) {
                java.util.Collections.shuffle(pool, random);
                // Curated hues already share a tonal range; do not bias toward raw saturation.
                if (!java.util.Arrays.equals(palette, CoursePalette.RANDOM_COLORS)) {
                    java.util.Collections.sort(pool, (a, b) -> Boolean.compare(
                            CoursePalette.preferredForRandom(b), CoursePalette.preferredForRandom(a)));
                }
            }
            colors.put(key, pool.get(index++ % pool.size()));
        }
        for (Item item : items) {
            if (item.temporaryColor()) continue;
            Integer color = colors.get(TimetableRules.normalizedCourseText(item.colorName()));
            if (color != null) item.setColorValue(color);
        }
        return colors.size();
    }

    static void applyColor(List<? extends Item> items, String name, int color) {
        String key = TimetableRules.normalizedCourseText(name);
        if (key.isEmpty()) return;
        for (Item item : items) {
            if (!item.temporaryColor() && key.equals(TimetableRules.normalizedCourseText(item.colorName()))) {
                item.setColorValue(color);
            }
        }
    }
}
