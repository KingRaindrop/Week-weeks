package com.weekweeks.app;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UiSemanticPaletteTest {
    @Test
    public void changingAccentPreservesNeutralAndErrorRoles() {
        for (boolean dark : new boolean[]{false, true}) {
            int[] expected = independentRoles(UiSemanticPalette.of(dark, UiAccentPalette.COLORS[0][dark ? 1 : 0]));
            for (int[] pair : UiAccentPalette.COLORS) {
                int accent = pair[dark ? 1 : 0];
                UiSemanticPalette palette = UiSemanticPalette.of(dark, accent);
                assertArrayEquals(label(dark, accent), expected, independentRoles(palette));
                assertEquals(label(dark, accent), accent, palette.accent);
            }
        }
    }

    @Test
    public void primaryAndSecondaryTextStayReadableOnEveryNeutralSurface() {
        for (boolean dark : new boolean[]{false, true}) {
            for (int[] pair : UiAccentPalette.COLORS) {
                UiSemanticPalette palette = UiSemanticPalette.of(dark, pair[dark ? 1 : 0]);
                for (int background : new int[]{palette.background, palette.surface, palette.inset}) {
                    assertReadable(label(dark, palette.accent) + " text", palette.text, background);
                    assertReadable(label(dark, palette.accent) + " secondary", palette.secondary, background);
                }
            }
        }
    }

    @Test
    public void errorsRemainReadableWithoutDependingOnSelectedAccent() {
        for (boolean dark : new boolean[]{false, true}) {
            for (int[] pair : UiAccentPalette.COLORS) {
                UiSemanticPalette palette = UiSemanticPalette.of(dark, pair[dark ? 1 : 0]);
                for (int background : new int[]{palette.background, palette.surface, palette.inset}) {
                    assertReadable(label(dark, palette.accent) + " inline error", palette.error, background);
                }
                assertReadable(label(dark, palette.accent) + " contained error",
                        palette.onErrorContainer, palette.errorContainer);
            }
        }
    }

    @Test
    public void everyProjectAccentHasReadableInkIncludingTransparentSeeds() {
        for (boolean dark : new boolean[]{false, true}) {
            for (int[] pair : UiAccentPalette.COLORS) {
                int accent = pair[dark ? 1 : 0];
                UiSemanticPalette opaque = UiSemanticPalette.of(dark, accent);
                assertReadable(label(dark, accent) + " onAccent", opaque.onAccent, opaque.accent);
                for (int alpha : new int[]{0, 0x80}) {
                    int seed = (alpha << 24) | (accent & 0x00FFFFFF);
                    UiSemanticPalette normalized = UiSemanticPalette.of(dark, seed);
                    assertEquals(label(dark, seed), opaque.accent, normalized.accent);
                    assertEquals(label(dark, seed), opaque.onAccent, normalized.onAccent);
                    assertArrayEquals(label(dark, seed), independentRoles(opaque), independentRoles(normalized));
                    assertReadable(label(dark, seed) + " normalized onAccent",
                            normalized.onAccent, normalized.accent);
                }
            }
        }
    }

    private static int[] independentRoles(UiSemanticPalette palette) {
        return new int[]{palette.background, palette.surface, palette.inset, palette.divider,
                palette.text, palette.secondary, palette.error, palette.errorContainer, palette.onErrorContainer};
    }

    private static void assertReadable(String label, int foreground, int background) {
        assertEquals(label + " foreground must be opaque", 255, foreground >>> 24);
        assertEquals(label + " background must be opaque", 255, background >>> 24);
        double contrast = CoursePalette.contrast(foreground, background);
        assertTrue(label + " #" + Integer.toHexString(foreground) + " on #"
                + Integer.toHexString(background) + " contrast=" + contrast, contrast >= 4.5);
    }

    private static String label(boolean dark, int accent) {
        return (dark ? "dark" : "light") + " accent=#" + Integer.toHexString(accent);
    }
}
