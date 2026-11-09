package com.weekweeks.app;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class CoursePaletteTest {
    // Exact legacy stored RGBs from MainActivity.COURSE_COLORS, not interface accents.
    private static final int[] LEGACY_COLORS = {
            0xFF347B80, 0xFF446FA6, 0xFF5B7E54, 0xFF846497, 0xFFAE695C,
            0xFFAC7E3A, 0xFF43876C, 0xFF596D9E, 0xFF915B77, 0xFF76714F
    };

    private static final String[] EXPECTED_NAMES = {
            "雾霾蓝", "灰豆绿", "燕麦白", "浅灰紫", "鼠尾草绿", "陶土粉", "驼灰", "雾粉",
            "湖水蓝", "薄荷灰", "奶油黄", "浅杏色", "灰蓝绿", "亚麻色", "干枯玫瑰", "灰薰衣草",
            "沙色", "青瓷绿", "雾霭灰", "暖灰", "淡天蓝", "鼠尾草灰", "浅珊瑚", "香槟色",
            "灰绿松", "淡紫灰", "米灰", "苔藓绿", "浅砖粉", "雾蓝灰", "柔沙绿", "深灰蓝"
    };
    private static final String[] EXPECTED_HEX = {
            "9CB2C1", "9AA894", "F5F0E6", "B2A8B8", "A0AE99", "CDA59C", "B7A696", "DEC4C4",
            "89ADB8", "BED2C4", "F6E9BE", "EED6B9", "78979A", "D6CCB8", "B78A8D", "AEA4BC",
            "CDBAA0", "AAC4B8", "B4B8BA", "A8A098", "B8D2E2", "919F91", "E8B4A8", "EBDBC3",
            "8AA8A0", "C4BACA", "E2DCD0", "7C8C74", "C69892", "8296A6", "B9C2AA", "5C7080"
    };

    @Test
    public void thirtyTwoColorsMatchUserOrderExactly() {
        assertEquals(32, CoursePalette.MUTED_COLORS.length);
        assertEquals(32, CoursePalette.MUTED_NAMES.length);
        assertArrayEquals(EXPECTED_NAMES, CoursePalette.MUTED_NAMES);
        for (int i = 0; i < EXPECTED_HEX.length; i++) {
            int expected = 0xFF000000 | Integer.parseInt(EXPECTED_HEX[i], 16);
            assertEquals(EXPECTED_NAMES[i], expected, CoursePalette.MUTED_COLORS[i]);
        }
    }

    @Test
    public void colorsAreOpaqueAndUnique() {
        Set<Integer> colors = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < CoursePalette.MUTED_COLORS.length; i++) {
            int color = CoursePalette.MUTED_COLORS[i];
            assertEquals(0xFF, (color >>> 24) & 0xFF);
            assertTrue(colors.add(color));
            assertTrue(names.add(CoursePalette.MUTED_NAMES[i]));
            assertTrue(CoursePalette.isMuted(color));
        }
        assertFalse(CoursePalette.isMuted(0xFF347B80));
        assertFalse(CoursePalette.isMuted(0x009CB2C1));
    }

    @Test
    public void appendColorsKeepsLegacyPrefixAndInput() {
        int[] legacy = {0xFF347B80, 0xFF446FA6, 0xFF5B7E54};
        int[] before = legacy.clone();
        int[] result = CoursePalette.appendColors(legacy);
        assertArrayEquals(before, legacy);
        assertNotSame(legacy, result);
        assertEquals(legacy.length + 32, result.length);
        for (int i = 0; i < legacy.length; i++) assertEquals(legacy[i], result[i]);
        for (int i = 0; i < 32; i++) assertEquals(CoursePalette.MUTED_COLORS[i], result[legacy.length + i]);
    }

    @Test
    public void appendNamesKeepsLegacyPrefixAndInput() {
        String[] legacy = {"青色", "蓝色"};
        String[] before = legacy.clone();
        String[] result = CoursePalette.appendNames(legacy);
        assertArrayEquals(before, legacy);
        assertNotSame(legacy, result);
        assertEquals(legacy.length + 32, result.length);
        assertEquals("青色", result[0]);
        assertEquals("蓝色", result[1]);
        for (int i = 0; i < 32; i++) assertEquals(CoursePalette.MUTED_NAMES[i], result[legacy.length + i]);
    }

    @Test
    public void lightSurfaceIsExactAndDarkSurfaceIsDarker() {
        for (int color : CoursePalette.MUTED_COLORS) {
            assertEquals(color, CoursePalette.mutedSurface(color, false));
            int dark = CoursePalette.mutedSurface(color, true);
            assertEquals(0xFF, (dark >>> 24) & 0xFF);
            assertTrue(Integer.toHexString(color),
                    CoursePalette.contrast(dark, 0xFF000000) < CoursePalette.contrast(color, 0xFF000000));
        }
        // #9CB2C1 * 50% + #151B28 * 50% = (88.5, 102.5, 116.5) -> (89, 103, 117).
        assertEquals(0xFF596775, CoursePalette.mutedSurface(0xFF9CB2C1, true));
    }

    @Test
    public void allColorsHaveReadableTextInBothModes() {
        for (int color : CoursePalette.MUTED_COLORS) {
            for (boolean dark : new boolean[]{false, true}) {
                int surface = CoursePalette.mutedSurface(color, dark);
                int ink = CoursePalette.readableInk(surface);
                assertTrue(ink == 0xFF000000 || ink == 0xFFFFFFFF);
                assertTrue(Integer.toHexString(color) + (dark ? " dark" : " light"),
                        CoursePalette.contrast(ink, surface) >= 4.5);
            }
        }
    }

    @Test
    public void everyMutedColorFadesTowardWhiteWhenFinishedInLightMode() {
        for (int color : CoursePalette.MUTED_COLORS) {
            int finished = CoursePalette.finishedSurface(CoursePalette.mutedSurface(color, false), false);
            assertTrue(Integer.toHexString(color),
                    CoursePalette.contrast(finished, 0xFF000000) > CoursePalette.contrast(color, 0xFF000000));
            assertTrue(CoursePalette.contrast(CoursePalette.finishedInk(finished, false), finished) >= 4.5);
            // Compatibility API accepts raw dark seeds, so retain the contrast fallback.
            int expectedInk = CoursePalette.contrast(0xFF566176, finished) >= 4.5
                    ? 0xFF566176 : CoursePalette.readableInk(finished);
            assertEquals(expectedInk, CoursePalette.finishedInk(finished, false));
        }
    }

    @Test
    public void everyMutedColorRecedesButRemainsReadableWhenFinishedInDarkMode() {
        for (int color : CoursePalette.MUTED_COLORS) {
            int surface = CoursePalette.mutedSurface(color, true);
            int finished = CoursePalette.finishedSurface(surface, true);
            assertTrue(CoursePalette.contrast(finished, 0xFF000000) < CoursePalette.contrast(surface, 0xFF000000));
            assertTrue(CoursePalette.contrast(CoursePalette.finishedInk(finished, true), finished) >= 4.5);
        }
    }

    @Test
    public void changingSelectedColorStillAppliesCompletionAfterTheNewSurface() {
        int oldSurface = CoursePalette.mutedSurface(0xFFF5F0E6, false);
        int newSurface = CoursePalette.mutedSurface(0xFF5C7080, false);
        int finished = CoursePalette.finishedSurface(newSurface, false);
        assertTrue(finished != newSurface);
        assertTrue(finished != CoursePalette.finishedSurface(oldSurface, false));
        assertEquals(0xFFCED4D9, finished);
        assertTrue(CoursePalette.contrast(CoursePalette.finishedInk(finished, false), finished) >= 4.5);
    }

    @Test
    public void randomPreferenceExcludesNearGrayButKeepsClearWarmAndCoolHues() {
        assertFalse(CoursePalette.preferredForRandom(0xFFB4B8BA)); // 雾霭灰
        assertFalse(CoursePalette.preferredForRandom(0xFFF5F0E6)); // 燕麦白
        assertFalse(CoursePalette.preferredForRandom(0xFF5C7080)); // 深灰蓝
        assertTrue(CoursePalette.preferredForRandom(0xFF89ADB8)); // 湖水蓝
        assertTrue(CoursePalette.preferredForRandom(0xFFE8B4A8)); // 浅珊瑚
        assertTrue(CoursePalette.preferredForRandom(0xFFF6E9BE)); // 奶油黄
    }

    @Test
    public void completionUsesThirtyPercentOpacityInLightAndFifteenPercentFadeInDark() {
        for (int color : CoursePalette.MUTED_COLORS) {
            for (boolean dark : new boolean[]{false, true}) {
                int surface = CoursePalette.mutedSurface(color, dark);
                int finished = CoursePalette.finishedSurface(surface, dark);
                int target = dark ? CoursePalette.DARK_BASE : 0xFFFFFFFF;
                for (int shift : new int[]{16, 8, 0}) {
                    int sourceChannel = (surface >>> shift) & 255;
                    int targetChannel = (target >>> shift) & 255;
                    int resultChannel = (finished >>> shift) & 255;
                    double fade = dark ? 0.15 : 0.70;
                    assertEquals((int) Math.round(sourceChannel * (1 - fade) + targetChannel * fade), resultChannel);
                    double oldDistance = Math.abs(targetChannel - sourceChannel) * (dark ? 0.60 : 0.15);
                    assertTrue(Math.abs(targetChannel - resultChannel) + 0.5 >= oldDistance);
                }
                assertTrue(CoursePalette.contrast(CoursePalette.finishedInk(finished, dark), finished) >= 4.5);
            }
        }
    }

    @Test
    public void darkMutedCardsAreBrighterThanPreviousVersionWithoutChangingStoredColors() {
        for (int color : CoursePalette.MUTED_COLORS) {
            int old = 0xFF000000;
            for (int shift : new int[]{16, 8, 0}) {
                int source = (color >>> shift) & 255;
                int base = (CoursePalette.DARK_BASE >>> shift) & 255;
                old |= (int) Math.round(source * .35 + base * .65) << shift;
            }
            int brighter = CoursePalette.mutedSurface(color, true);
            assertTrue(CoursePalette.contrast(brighter, 0xFF000000) > CoursePalette.contrast(old, 0xFF000000));
            assertTrue(CoursePalette.contrast(CoursePalette.finishedSurface(brighter, true), 0xFF000000)
                    > CoursePalette.contrast(CoursePalette.finishedSurface(old, true), 0xFF000000));
            assertEquals(color, CoursePalette.mutedSurface(color, false));
        }
        assertEquals(.52f, CoursePalette.LEGACY_DARK_VALUE, .0001f);
    }

    @Test
    public void brighterNeutralCardsAlwaysHaveReadableActiveText() {
        for (int channel = 0; channel <= 255; channel++) {
            int surface = 0xFF000000 | channel << 16 | channel << 8 | channel;
            for (boolean dark : new boolean[]{false, true}) {
                assertTrue(CoursePalette.contrast(CoursePalette.activeInk(surface, dark), surface) >= 4.5);
            }
        }
        assertEquals(0xFF000000, CoursePalette.activeInk(0xFF858585, true));
        assertEquals(0xFFEEF2FF, CoursePalette.activeInk(0xFF151B28, true));
    }

    @Test
    public void courseDetailBadgesKeepReadableTextOnEveryPreset() {
        for (int color : CoursePalette.MUTED_COLORS) {
            for (boolean dark : new boolean[]{false, true}) {
                int badge = CoursePalette.detailBadgeSurface(CoursePalette.mutedSurface(color, dark), dark);
                assertEquals(255, badge >>> 24);
                assertTrue(CoursePalette.contrast(CoursePalette.activeInk(badge, dark), badge) >= 4.5);
            }
        }
    }

    @Test
    public void allFortyTwoSurfacesHaveNormalizedBoundsPreservedHueAndStableInk() {
        int[] all = CoursePalette.appendColors(LEGACY_COLORS);
        assertEquals(42, all.length);
        for (int color : all) {
            double[] original = hslOf(color);
            boolean neutral = channelSpan(color) <= 8 || original[1] < .08;
            for (boolean dark : new boolean[]{false, true}) {
                int surface = CoursePalette.courseSurface(color, dark);
                double[] actual = hslOf(surface);
                assertEquals(255, surface >>> 24);
                assertEquals(surface, CoursePalette.courseSurface(color, dark));
                assertEquals(dark ? .32 : .83, actual[2], 1.0 / 255);
                if (neutral) {
                    assertEquals(0, channelSpan(surface));
                } else {
                    // RGB quantization can shift recovered HSL saturation slightly.
                    assertTrue(actual[1] >= (dark ? .25 : .40) - .015);
                    assertTrue(actual[1] <= (dark ? .40 : .55) + .015);
                    double hueDistance = Math.abs(original[0] - actual[0]);
                    assertTrue(Integer.toHexString(color), Math.min(hueDistance, 360 - hueDistance) < 2.0);
                }
                int expectedInk = dark ? 0xFFEEF2FF : 0xFF34405A;
                assertEquals(expectedInk, CoursePalette.activeInk(surface, dark));
                assertTrue(Integer.toHexString(color), CoursePalette.contrast(expectedInk, surface) >= 4.5);
            }
        }
    }

    @Test
    public void unifiedMappingNeverMutatesStoredPaletteOrCompatibilityMapping() {
        int[] mutedBefore = CoursePalette.MUTED_COLORS.clone();
        int[] legacyBefore = LEGACY_COLORS.clone();
        int[] randomBefore = CoursePalette.RANDOM_COLORS.clone();
        for (int color : CoursePalette.appendColors(LEGACY_COLORS)) {
            CoursePalette.courseSurface(color, false);
            CoursePalette.courseSurface(color, true);
        }
        assertArrayEquals(mutedBefore, CoursePalette.MUTED_COLORS);
        assertArrayEquals(legacyBefore, LEGACY_COLORS);
        assertArrayEquals(randomBefore, CoursePalette.RANDOM_COLORS);
        thirtyTwoColorsMatchUserOrderExactly();
        assertEquals(0xFF5C7080, CoursePalette.mutedSurface(0xFF5C7080, false));
        assertEquals(0xFF596775, CoursePalette.mutedSurface(0xFF9CB2C1, true));
        assertTrue(CoursePalette.courseSurface(0xFF5C7080, false) != 0xFF5C7080);
    }

    @Test
    public void everyUnifiedPresetCompletesGentlyWithConsistentSoftInk() {
        // Includes all 32 muted colors and the 10 legacy colors under the same rules.
        for (int color : CoursePalette.appendColors(LEGACY_COLORS)) {
            for (boolean dark : new boolean[]{false, true}) {
                int surface = CoursePalette.courseSurface(color, dark);
                int finished = CoursePalette.finishedSurface(surface, dark);
                int target = dark ? CoursePalette.DARK_BASE : 0xFFFFFFFF;
                double fade = dark ? .15 : .70;
                assertTrue(surface != finished);
                for (int shift : new int[]{16, 8, 0}) {
                    int sourceChannel = (surface >>> shift) & 255;
                    int targetChannel = (target >>> shift) & 255;
                    assertEquals((int) Math.round(sourceChannel * (1 - fade) + targetChannel * fade),
                            (finished >>> shift) & 255);
                }
                double beforeLuminance = CoursePalette.contrast(surface, 0xFF000000);
                double afterLuminance = CoursePalette.contrast(finished, 0xFF000000);
                assertTrue(dark ? afterLuminance < beforeLuminance : afterLuminance > beforeLuminance);
                int expectedInk = dark ? 0xFFCBD5E1 : 0xFF566176;
                assertEquals(expectedInk, CoursePalette.finishedInk(finished, dark));
                assertTrue(CoursePalette.contrast(expectedInk, finished) >= 4.5);
            }
        }
    }

    @Test
    public void allNeutralSeedsStayNeutralWithoutInventedHue() {
        for (int channel = 0; channel < 256; channel++) {
            int color = 0xFF000000 | channel << 16 | channel << 8 | channel;
            assertEquals(0xFFD4D4D4, CoursePalette.courseSurface(color, false));
            assertEquals(0xFF525252, CoursePalette.courseSurface(color, true));
        }
        assertEquals(0, channelSpan(CoursePalette.courseSurface(0xFFB4B8BA, false)));
        assertEquals(0, channelSpan(CoursePalette.courseSurface(0xFFB4B8BA, true)));
        // Even non-opaque seeds produce opaque, deterministic display surfaces.
        assertEquals(CoursePalette.courseSurface(0xFF446FA6, true), CoursePalette.courseSurface(0x00446FA6, true));
    }

    @Test
    public void curatedRandomPoolContainsOnlyDistinctExistingColorsAcrossHueFamilies() {
        assertTrue(CoursePalette.RANDOM_COLORS.length >= 12 && CoursePalette.RANDOM_COLORS.length <= 16);
        Set<Integer> existing = new HashSet<>(), seen = new HashSet<>(), hueFamilies = new HashSet<>();
        Set<Integer> lightSurfaces = new HashSet<>(), darkSurfaces = new HashSet<>();
        for (int color : CoursePalette.appendColors(LEGACY_COLORS)) existing.add(color);
        for (int color : CoursePalette.RANDOM_COLORS) {
            assertTrue(existing.contains(color));
            assertTrue(seen.add(color));
            assertTrue(channelSpan(color) > 8);
            assertTrue(hslOf(color)[1] >= .08);
            hueFamilies.add((int) (hslOf(color)[0] / 60));
            assertTrue(lightSurfaces.add(CoursePalette.courseSurface(color, false)));
            assertTrue(darkSurfaces.add(CoursePalette.courseSurface(color, true)));
        }
        // Cover all six broad hue families while retaining at least eight degrees
        // between chosen seeds; this avoids multiple nearly identical gray-blue choices.
        assertEquals(6, hueFamilies.size());
        for (int family : new int[]{0, 1, 2, 3, 4, 5}) assertTrue(hueFamilies.contains(family));
        for (int i = 0; i < CoursePalette.RANDOM_COLORS.length; i++) {
            for (int j = 0; j < i; j++) {
                double distance = Math.abs(hslOf(CoursePalette.RANDOM_COLORS[i])[0]
                        - hslOf(CoursePalette.RANDOM_COLORS[j])[0]);
                assertTrue(Math.min(distance, 360 - distance) >= 8);
            }
        }
    }

    private static int channelSpan(int color) {
        int r = (color >>> 16) & 255, g = (color >>> 8) & 255, b = color & 255;
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
    }

    /** Independently recover HSL from output RGB for design-bound assertions. */
    private static double[] hslOf(int color) {
        double r = ((color >>> 16) & 255) / 255.0;
        double g = ((color >>> 8) & 255) / 255.0;
        double b = (color & 255) / 255.0;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        double l = (max + min) / 2, delta = max - min;
        if (delta == 0) return new double[]{0, 0, l};
        double s = delta / (1 - Math.abs(2 * l - 1));
        double h = max == r ? (g - b) / delta : max == g ? 2 + (b - r) / delta : 4 + (r - g) / delta;
        return new double[]{(h * 60 + 360) % 360, s, l};
    }

    @Test
    public void contrastMatchesWcagReferenceValues() {
        assertEquals(21.0, CoursePalette.contrast(0xFF000000, 0xFFFFFFFF), 1e-9);
        assertEquals(1.0, CoursePalette.contrast(0xFF9CB2C1, 0xFF9CB2C1), 1e-9);
        assertEquals(CoursePalette.contrast(0xFF5C7080, 0xFFFFFFFF),
                CoursePalette.contrast(0xFFFFFFFF, 0xFF5C7080), 1e-12);
        assertEquals(0xFF000000, CoursePalette.readableInk(0xFFF5F0E6));
        assertEquals(0xFFFFFFFF, CoursePalette.readableInk(0xFF151B28));
    }
}
