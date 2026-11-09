package com.weekweeks.app;

import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class UiAccentPaletteTest {
    @Test public void exactlySixteenNamedOpaqueUniquePairs() {
        assertEquals(16, UiAccentPalette.NAMES.length);
        assertEquals(16, UiAccentPalette.COLORS.length);
        Set<String> names = new HashSet<>();
        Set<Integer> light = new HashSet<>(), dark = new HashSet<>();
        for (int i = 0; i < 16; i++) {
            assertTrue(names.add(UiAccentPalette.NAMES[i]));
            assertFalse(UiAccentPalette.NAMES[i].trim().isEmpty());
            int[] pair = UiAccentPalette.COLORS[i];
            assertEquals(2, pair.length);
            assertEquals(255, pair[0] >>> 24);
            assertEquals(255, pair[1] >>> 24);
            assertTrue(light.add(pair[0]));
            assertTrue(dark.add(pair[1]));
        }
    }

    @Test public void existingSavedIndicesAndSignatureCoralStayUnchanged() {
        int[][] previous = {
                {0xFF1E68D2, 0xFF69B2FF}, {0xFF0E9F8A, 0xFF4FD1BC},
                {0xFF2E9E44, 0xFF6FD08A}, {0xFF7A4FE0, 0xFFB59AFF},
                {0xFFE8722E, 0xFFFFA35C}, {0xFFD64550, 0xFFFF8A94},
                {0xFF4050D8, 0xFF8A97FF}, {0xFFF45A50, 0xFFFF9D98}
        };
        for (int i = 0; i < previous.length; i++) assertArrayEquals(previous[i], UiAccentPalette.COLORS[i]);
        assertEquals(7, UiAccentPalette.CORAL_INDEX);
        assertEquals("珊瑚", UiAccentPalette.NAMES[UiAccentPalette.CORAL_INDEX]);
        assertEquals(UiAccentPalette.CORAL_LIGHT, UiAccentPalette.COLORS[7][0]);
        assertEquals(UiAccentPalette.CORAL_DARK, UiAccentPalette.COLORS[7][1]);
    }

    @Test public void requestedComfortThemesAreAppendedInOrder() {
        String[] expected = {"暗夜", "玫瑰花园", "湖光", "日落霞光", "森林低语", "海风", "薰衣草梦", "杏茶时光"};
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], UiAccentPalette.NAMES[i + 8]);
    }

    @Test public void newLightAccentsAndDarkAccentsHaveReadableContrast() {
        for (int i = 8; i < 16; i++) {
            int light = UiAccentPalette.COLORS[i][0], dark = UiAccentPalette.COLORS[i][1];
            assertTrue(UiAccentPalette.NAMES[i] + " light", CoursePalette.contrast(light, 0xFFFFFFFF) >= 4.5);
            assertTrue(UiAccentPalette.NAMES[i] + " dark", CoursePalette.contrast(dark, 0xFF111622) >= 4.5);
            assertTrue(CoursePalette.contrast(dark, 0xFF000000) > CoursePalette.contrast(light, 0xFF000000));
        }
    }

    @Test public void allSwatchCheckmarksHaveReadableInk() {
        for (int[] pair : UiAccentPalette.COLORS) {
            for (int color : pair) assertTrue(CoursePalette.contrast(CoursePalette.readableInk(color), color) >= 4.5);
        }
    }

    @Test public void interfaceColorsDoNotExtendOrRewriteCoursePalette() {
        assertEquals(32, CoursePalette.MUTED_COLORS.length);
        assertEquals(0xFF9CB2C1, CoursePalette.MUTED_COLORS[0]);
        assertEquals(0xFF5C7080, CoursePalette.MUTED_COLORS[31]);
        for (int i = 8; i < 16; i++) assertFalse(CoursePalette.isMuted(UiAccentPalette.COLORS[i][0]));
    }
}
