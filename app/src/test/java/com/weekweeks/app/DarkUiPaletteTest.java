package com.weekweeks.app;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

public class DarkUiPaletteTest {
    private static final int[] LEGACY_COURSE_COLORS = {
            0xFF347B80, 0xFF446FA6, 0xFF5B7E54, 0xFF846497, 0xFFAE695C,
            0xFFAC7E3A, 0xFF43876C, 0xFF596D9E, 0xFF915B77, 0xFF76714F
    };

    private String readMainSource(String relative) throws Exception {
        Path path = Path.of("src/main/" + relative);
        if (!Files.exists(path)) path = Path.of("app").resolve(path);
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test public void neutralTextIsReadableOnEverySurface() {
        for (int surface : new int[]{DarkUiPalette.BACKGROUND, DarkUiPalette.RECESSED,
                DarkUiPalette.SURFACE, DarkUiPalette.DIALOG_SURFACE, DarkUiPalette.RAISED}) {
            for (int ink : new int[]{DarkUiPalette.INK, DarkUiPalette.SECONDARY, DarkUiPalette.HINT})
                assertTrue(CoursePalette.contrast(ink, surface) >= 4.5);
        }
    }

    @Test public void everyAccentAndContainerHasReadableLabels() {
        for (int[] pair : UiAccentPalette.COLORS) {
            int accent = pair[1];
            for (int bg : new int[]{DarkUiPalette.BACKGROUND, DarkUiPalette.RECESSED,
                    DarkUiPalette.SURFACE, DarkUiPalette.DIALOG_SURFACE,
                    DarkUiPalette.RAISED, DarkUiPalette.tint(accent), DarkUiPalette.track(accent)})
                assertTrue(Integer.toHexString(accent), CoursePalette.contrast(accent, bg) >= 4.5);
            for (int bg : new int[]{DarkUiPalette.heroStart(accent), DarkUiPalette.heroEnd(accent)}) {
                assertTrue(CoursePalette.contrast(DarkUiPalette.INK, bg) >= 4.5);
                assertTrue(CoursePalette.contrast(DarkUiPalette.SECONDARY, bg) >= 4.5);
            }
            assertTrue(CoursePalette.contrast(DarkUiPalette.buttonInk(accent), accent) >= 4.5);
            assertTrue(CoursePalette.contrast(DarkUiPalette.chipInk(accent, true), accent) >= 4.5);
            assertEquals(0xFFFFFFFF, DarkUiPalette.chipInk(pair[0], false));
        }
        assertTrue(CoursePalette.contrast(DarkUiPalette.buttonInk(0xFFFF8A80), 0xFFFF8A80) >= 4.5);
    }

    @Test public void tintedNightGlassFinishedCardsPreserveHueRecedeAndStayReadable() {
        for (int seed : CoursePalette.appendColors(LEGACY_COURSE_COLORS)) {
            int active = CoursePalette.courseSurface(seed, true);
            int nightGlass = DarkUiPalette.tintedFinishedFill(active);
            int stroke = DarkUiPalette.tintedFinishedStroke(active);
            assertEquals(0x88, (stroke >>> 24) & 0xFF);
            double lSurface = CoursePalette.contrast(DarkUiPalette.SURFACE, 0xFF000000);
            double lNightGlass = CoursePalette.contrast(nightGlass, 0xFF000000);
            double lActive = CoursePalette.contrast(active, 0xFF000000);
            assertTrue(lSurface < lNightGlass);
            assertTrue(lNightGlass < lActive);
            // Non-neutral courses must retain genuine color chroma instead of turning into gray slabs.
            int r = (active >>> 16) & 255, g = (active >>> 8) & 255, b = active & 255;
            int activeSpan = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
            int nr = (nightGlass >>> 16) & 255, ng = (nightGlass >>> 8) & 255, nb = nightGlass & 255;
            int glassSpan = Math.max(nr, Math.max(ng, nb)) - Math.min(nr, Math.min(ng, nb));
            if (activeSpan > 8) {
                assertTrue(Integer.toHexString(seed), glassSpan >= 20);
            } else {
                assertEquals(0, glassSpan);
            }
            int primaryInk = DarkUiPalette.tintedFinishedInk(
                    active, nightGlass, CoursePalette.finishedInk(nightGlass, true));
            int secondaryInk = DarkUiPalette.tintedFinishedSecondaryInk(
                    active, nightGlass, CoursePalette.finishedSecondaryInk(nightGlass, true));
            assertTrue(CoursePalette.contrast(primaryInk, nightGlass) >= 4.5);
            assertTrue(CoursePalette.contrast(secondaryInk, nightGlass) >= 4.5);
        }
    }

    @Test public void darkModeViewsUseUnifiedTokensAndContrastSafeInk() throws Exception {
        String sheet = readMainSource("java/com/weekweeks/app/PastelCourseSheet.java");
        String timetable = readMainSource("java/com/weekweeks/app/PastelTimetableView.java");
        String activity = readMainSource("java/com/weekweeks/app/MainActivity.java");
        assertTrue(sheet.contains("DarkUiPalette.chipInk(coral, dark)"));
        assertTrue(timetable.contains("DarkUiPalette.chipInk(coral, dark)"));
        assertFalse(sheet.contains("#1A2030"));
        assertFalse(sheet.contains("#3A4560"));
        assertFalse(timetable.contains("#1A2030"));
        assertTrue(activity.contains("showThemeModeDialog()"));
    }

    @Test public void selectedSwatchNameKeepsReadableLightModeInk() throws Exception {
        String sheet = readMainSource("java/com/weekweeks/app/PastelCourseSheet.java");
        assertTrue(sheet.contains("text(name, 11, selected && dark ? coral : ink, selected)"));
        assertTrue(sheet.contains("ink = palette.text"));
        UiSemanticPalette palette = UiSemanticPalette.of(false, UiAccentPalette.COLORS[0][0]);
        assertTrue(CoursePalette.contrast(palette.text, palette.inset) >= 4.5);
    }

    @Test public void mistPresetsHaveExactDesignedSurfaces() {
        int[][] expected = {
                {0xFFA8BFE8,0xFF293447,0xFF293950,0xFF202B3C},
                {0xFFB0C9B5,0xFF2B3830,0xFF303F36,0xFF252F29},
                {0xFFC4B6DD,0xFF353044,0xFF3B344B,0xFF2B2736},
                {0xFF9FCFC9,0xFF273B3A,0xFF2B4140,0xFF223130},
                {0xFFD9B0BD,0xFF403039,0xFF48353E,0xFF33282E},
                {0xFFD7C1A1,0xFF3D352B,0xFF453C30,0xFF302B24}
        };
        for (int i = 0; i < expected.length; i++) {
            int[] e = expected[i];
            assertEquals(e[0], UiAccentPalette.COLORS[16+i][1]);
            assertEquals(e[1], DarkUiPalette.tint(e[0]));
            assertEquals(e[2], DarkUiPalette.heroStart(e[0]));
            assertEquals(e[3], DarkUiPalette.heroEnd(e[0]));
        }
    }

    @Test public void everyNewNameIsLocalizedAndIndicesAreAppended() {
        int before = UiLang.current();
        try {
            for (int language : new int[]{UiLang.HANS, UiLang.HANT, UiLang.EN}) {
                UiLang.set(language);
                for (int i = 16; i < 22; i++) assertFalse(UiAccentPalette.name(i).isEmpty());
            }
            UiLang.set(UiLang.EN);
            assertEquals("Mist Blue", UiAccentPalette.name(16));
            assertEquals("Oat Tea", UiAccentPalette.name(21));
        } finally { UiLang.set(before); }
    }
}
