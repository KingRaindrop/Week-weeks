package com.weekweeks.app;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class CourseColorRulesTest {
    private static final int BLUE = 0xFF446FA6;
    private static final int GREEN = 0xFF5B7E54;
    private static final int MUTED = 0xFF9CB2C1;

    private static final class Row implements CourseColorRules.Item {
        final String name;
        final int day;
        final boolean temporary;
        int color;
        Row(String name, int day, int color, boolean temporary) {
            this.name = name; this.day = day; this.color = color; this.temporary = temporary;
        }
        Row copy() { return new Row(name, day, color, temporary); }
        @Override public String colorName() { return name; }
        @Override public int colorValue() { return color; }
        @Override public void setColorValue(int value) { color = value; }
        @Override public boolean temporaryColor() { return temporary; }
    }

    @Test public void sameNameAcrossDaysSharesFirstColorAndKeepsOtherFields() {
        Row a = new Row("数据结构", 0, BLUE, false);
        Row b = new Row("数据结构", 3, GREEN, false);
        assertTrue(CourseColorRules.synchronize(Arrays.asList(a, b)));
        assertEquals(BLUE, b.color);
        assertEquals(3, b.day);
        assertEquals("数据结构", b.name);
    }

    @Test public void migrationPrefersFirstMutedPresetAndIsStable() {
        Row a = new Row("数学", 0, BLUE, false);
        Row b = new Row("数学", 2, MUTED, false);
        Row c = new Row("数学", 4, 0xFF5C7080, false);
        List<Row> rows = Arrays.asList(a, b, c);
        assertEquals(Integer.valueOf(MUTED), CourseColorRules.findColor(rows, "数学"));
        assertTrue(CourseColorRules.synchronize(rows));
        for (Row row : rows) assertEquals(MUTED, row.color);
        assertFalse(CourseColorRules.synchronize(rows));
    }

    @Test public void explicitLegacyColorOverridesMutedAndSurvivesRestart() {
        List<Row> rows = Arrays.asList(new Row("数学", 0, MUTED, false), new Row("数学", 2, MUTED, false));
        CourseColorRules.applyColor(rows, "数学", GREEN);
        assertFalse(CourseColorRules.synchronize(rows));
        for (Row row : rows) assertEquals(GREEN, row.color);
    }

    @Test public void normalizationMatchesChineseWhitespaceAndEnglishCase() {
        Row a = new Row("  数据结构  ", 0, BLUE, false);
        Row b = new Row("数据结构", 1, GREEN, false);
        Row c = new Row(" Computer   Science ", 2, BLUE, false);
        Row d = new Row("computer science", 3, GREEN, false);
        List<Row> rows = Arrays.asList(a, b, c, d);
        CourseColorRules.synchronize(rows);
        assertEquals(BLUE, b.color);
        assertEquals(BLUE, d.color);
        CourseColorRules.applyColor(rows, "COMPUTER\tSCIENCE", MUTED);
        assertEquals(MUTED, c.color);
        assertEquals(MUTED, d.color);
        assertEquals(BLUE, b.color);
    }

    @Test public void temporaryCoursesAreNeverInheritedOrRecolored() {
        Row temporary = new Row("数学", 0, MUTED, true);
        Row regular = new Row("数学", 2, BLUE, false);
        List<Row> rows = Arrays.asList(temporary, regular);
        assertEquals(Integer.valueOf(BLUE), CourseColorRules.findColor(rows, "数学"));
        assertFalse(CourseColorRules.synchronize(rows));
        CourseColorRules.applyColor(rows, "数学", GREEN);
        assertEquals(MUTED, temporary.color);
        assertEquals(GREEN, regular.color);
    }

    @Test public void blankNamesAreNotCombined() {
        Row a = new Row("", 0, BLUE, false);
        Row b = new Row("  ", 1, GREEN, false);
        Row c = new Row(null, 2, MUTED, false);
        List<Row> rows = Arrays.asList(a, b, c);
        assertNull(CourseColorRules.findColor(rows, " "));
        assertFalse(CourseColorRules.synchronize(rows));
        CourseColorRules.applyColor(rows, null, BLUE);
        assertEquals(GREEN, b.color);
        assertEquals(MUTED, c.color);
    }

    @Test public void unrelatedNamesKeepTheirOwnColors() {
        Row a = new Row("高等数学", 0, BLUE, false);
        Row b = new Row("高等数学实验", 1, GREEN, false);
        List<Row> rows = Arrays.asList(a, b);
        CourseColorRules.applyColor(rows, "高等数学", MUTED);
        assertEquals(MUTED, a.color);
        assertEquals(GREEN, b.color);
        assertNull(CourseColorRules.findColor(rows, "英语"));
    }

    @Test public void failedSaveDoesNotPublishGroupRecoloring() {
        List<Row> live = new ArrayList<>(Arrays.asList(new Row("数学", 0, BLUE, false), new Row("数学", 3, BLUE, false)));
        List<Row> candidate = new ArrayList<>();
        for (Row row : live) candidate.add(row.copy());
        CourseColorRules.applyColor(candidate, "数学", MUTED);
        assertFalse(ListTransaction.commit(live, candidate, rows -> false));
        for (Row row : live) assertEquals(BLUE, row.color);
        assertTrue(ListTransaction.commit(live, candidate, rows -> true));
        for (Row row : live) assertEquals(MUTED, row.color);
    }

    @Test public void randomColorsShareNormalizedNamesAndDoNotRepeatWithinPalette() {
        List<Row> rows = Arrays.asList(new Row(" Math ", 0, BLUE, false),
                new Row("math", 3, GREEN, false), new Row("英语", 1, BLUE, false),
                new Row("物理", 2, BLUE, false), new Row("临时", 0, MUTED, true),
                new Row("  ", 0, MUTED, false));
        assertEquals(3, CourseColorRules.randomize(rows, new int[]{BLUE, GREEN, MUTED}, new java.util.Random(12)));
        assertEquals(rows.get(0).color, rows.get(1).color);
        assertEquals(3, new java.util.HashSet<>(Arrays.asList(rows.get(0).color, rows.get(2).color, rows.get(3).color)).size());
        assertEquals(MUTED, rows.get(4).color);
        assertEquals(MUTED, rows.get(5).color);
        assertEquals(3, rows.get(1).day);
        assertFalse(CourseColorRules.synchronize(rows));
    }

    @Test public void randomPaletteCyclesSafelyAndDoesNotModifyPalette() {
        int[] palette = {BLUE, GREEN, BLUE};
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < 9; i++) rows.add(new Row("课程" + i, i, MUTED, false));
        assertEquals(9, CourseColorRules.randomize(rows, palette, new java.util.Random(2)));
        for (Row row : rows) assertTrue(row.color == BLUE || row.color == GREEN);
        for (int i = 0; i < 8; i += 2) assertNotEquals(rows.get(i).color, rows.get(i + 1).color);
        assertArrayEquals(new int[]{BLUE, GREEN, BLUE}, palette);
    }

    @Test public void randomColorsPreferClearHuesEachCycleWithoutLosingGrayPresets() {
        int[] palette = {0xFFB4B8BA, BLUE, 0xFFE8B4A8, 0xFFF5F0E6};
        int[] before = palette.clone();
        for (int seed = 0; seed < 30; seed++) {
            List<Row> rows = new ArrayList<>();
            for (int i = 0; i < 8; i++) rows.add(new Row("课程" + i, 0, GREEN, false));
            assertEquals(8, CourseColorRules.randomize(rows, palette, new java.util.Random(seed)));
            for (int start : new int[]{0, 4}) {
                java.util.Set<Integer> cycle = new java.util.HashSet<>();
                for (int i = 0; i < 4; i++) {
                    int color = rows.get(start + i).color;
                    assertEquals(i < 2, CoursePalette.preferredForRandom(color));
                    assertTrue(cycle.add(color));
                }
            }
        }
        assertArrayEquals(before, palette);
    }

    @Test public void clearHueTierStillShufflesRatherThanAlwaysUsingSameOrder() {
        java.util.Set<Integer> firstColors = new java.util.HashSet<>();
        for (int seed = 0; seed < 50; seed++) {
            Row row = new Row("数学", 0, MUTED, false);
            CourseColorRules.randomize(Arrays.asList(row),
                    new int[]{BLUE, GREEN, 0xFFE8B4A8, 0xFFB4B8BA}, new java.util.Random(seed));
            assertTrue(CoursePalette.preferredForRandom(row.color));
            firstColors.add(row.color);
        }
        assertTrue(firstColors.size() > 1);
    }

    @Test public void curatedPoolCyclesWithoutRepeatsAndSharesNames() {
        int[] before = CoursePalette.RANDOM_COLORS.clone();
        java.util.Set<Integer> firstColors = new java.util.HashSet<>();
        for (int seed = 0; seed < 100; seed++) {
            List<Row> rows = new ArrayList<>();
            for (int i = 0; i < 25; i++) rows.add(new Row("课程" + i, 0, BLUE, false));
            rows.add(new Row("课程0", 4, GREEN, false));
            assertEquals(25, CourseColorRules.randomize(rows, CoursePalette.RANDOM_COLORS, new java.util.Random(seed)));
            firstColors.add(rows.get(0).color);
            assertEquals(rows.get(0).color, rows.get(25).color);
            for (int start : new int[]{0, 12}) {
                java.util.Set<Integer> cycle = new java.util.HashSet<>();
                for (int i = 0; i < 12; i++) assertTrue(cycle.add(rows.get(start + i).color));
                for (int color : before) assertTrue(cycle.contains(color));
            }
        }
        assertEquals(12, firstColors.size());
        assertArrayEquals(before, CoursePalette.RANDOM_COLORS);
    }

    @Test public void emptyPaletteAndEmptyCoursesAreNoOps() {
        Row row = new Row("数学", 0, MUTED, false);
        assertEquals(0, CourseColorRules.randomize(Arrays.asList(row), new int[0], new java.util.Random(1)));
        assertEquals(MUTED, row.color);
        assertEquals(0, CourseColorRules.randomize(new ArrayList<Row>(), new int[]{BLUE}, new java.util.Random(1)));
    }

    @Test public void failedSaveDoesNotPublishRandomColors() {
        List<Row> live = new ArrayList<>(Arrays.asList(new Row("数学", 0, MUTED, false)));
        List<Row> candidate = new ArrayList<>(Arrays.asList(live.get(0).copy()));
        CourseColorRules.randomize(candidate, new int[]{GREEN}, new java.util.Random(1));
        assertFalse(ListTransaction.commit(live, candidate, rows -> false));
        assertEquals(MUTED, live.get(0).color);
        assertTrue(ListTransaction.commit(live, candidate, rows -> true));
        assertEquals(GREEN, live.get(0).color);
    }

    @Test public void emptyListsAreSafe() {
        List<Row> rows = new ArrayList<>();
        assertNull(CourseColorRules.findColor(rows, "数学"));
        assertFalse(CourseColorRules.synchronize(rows));
        CourseColorRules.applyColor(rows, "数学", MUTED);
        assertTrue(rows.isEmpty());
    }
}
