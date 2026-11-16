package com.weekweeks.app;

/** Neutral dark surfaces and hand-tuned mist accents. No course colors or timing rules. */
final class DarkUiPalette {
    static final int BACKGROUND = 0xFF15171C;
    static final int RECESSED = 0xFF191C22;
    static final int SURFACE = 0xFF1D2026;
    static final int DIALOG_SURFACE = 0xFF21252D;
    static final int RAISED = 0xFF262A32;
    static final int LINE = 0xFF353A44;
    static final int SUBTLE_LINE = 0x8C353A44;
    static final int HANDLE = 0xFF434956;
    static final int INK = 0xFFE8EAF0;
    static final int SECONDARY = 0xFFADB3C0;
    static final int HINT = 0xFF8D95A5;
    static final int BUTTON_INK = 0xFF172234;

    // Accent, selected container, hero start, hero end. Order matches the appended presets.
    private static final int[][] MIST = {
            {0xFFA8BFE8, 0xFF293447, 0xFF293950, 0xFF202B3C},
            {0xFFB0C9B5, 0xFF2B3830, 0xFF303F36, 0xFF252F29},
            {0xFFC4B6DD, 0xFF353044, 0xFF3B344B, 0xFF2B2736},
            {0xFF9FCFC9, 0xFF273B3A, 0xFF2B4140, 0xFF223130},
            {0xFFD9B0BD, 0xFF403039, 0xFF48353E, 0xFF33282E},
            {0xFFD7C1A1, 0xFF3D352B, 0xFF453C30, 0xFF302B24}
    };

    private DarkUiPalette() { }

    static int tint(int accent) { return tone(accent, 1, 0.13); }
    static int heroStart(int accent) { return tone(accent, 2, 0.19); }
    static int heroEnd(int accent) { return tone(accent, 3, 0.08); }
    static int track(int accent) { return blend(SURFACE, tint(accent), 0.45); }
    static int bookFill(int accent) { return blend(SURFACE, accent, 0.34); }

    static int buttonInk(int fill) {
        return CoursePalette.contrast(BUTTON_INK, fill) >= 4.5
                ? BUTTON_INK : CoursePalette.readableInk(fill);
    }

    /** Selected chips and timeline pills: dark ink on luminous dark-mode accents, white in light mode. */
    static int chipInk(int fill, boolean dark) {
        return dark ? buttonInk(fill) : 0xFFFFFFFF;
    }

    /**
     * Night-glass fill for finished courses (Scheme 1): preserves the active course hue and rich
     * saturation while sinking lightness from 0.32 to 0.225 into {@link #SURFACE}, avoiding muddy
     * desaturated gray masks even when an entire week of classes has finished.
     */
    static int tintedFinishedFill(int activeSurface) {
        double r = ((activeSurface >> 16) & 255) / 255.0;
        double g = ((activeSurface >> 8) & 255) / 255.0;
        double b = (activeSurface & 255) / 255.0;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        double delta = max - min;
        double targetLightness = 0.225;
        if (delta <= 8.0 / 255.0) return hsl(0, 0, targetLightness);
        double lightness = (max + min) / 2.0;
        double saturation = delta / (1 - Math.abs(2 * lightness - 1));
        double hue;
        if (max == r) hue = ((g - b) / delta + 6) % 6;
        else if (max == g) hue = (b - r) / delta + 2;
        else hue = (r - g) / delta + 4;
        double keptSaturation = Math.max(0.22, Math.min(0.36, saturation * 0.92));
        return hsl(hue, keptSaturation, targetLightness);
    }

    /** Luminous same-hue 1dp glass border for finished dark-mode course cards. */
    static int tintedFinishedStroke(int activeSurface) {
        int lifted = blend(activeSurface, 0xFFFFFFFF, 0.16);
        return (lifted & 0x00FFFFFF) | 0x88000000;
    }

    /** Softly hue-tinted primary label ink on a finished night-glass card, guaranteed WCAG AA. */
    static int tintedFinishedInk(int activeSurface, int background, int fallbackInk) {
        int tinted = blend(activeSurface, 0xFFFFFFFF, 0.72);
        if (CoursePalette.contrast(tinted, background) >= 4.5) return tinted;
        return CoursePalette.contrast(fallbackInk, background) >= 4.5
                ? fallbackInk : CoursePalette.readableInk(background);
    }

    /** Softly hue-tinted secondary ink (room/teacher/badge) on a finished night-glass card, WCAG AA. */
    static int tintedFinishedSecondaryInk(int activeSurface, int background, int fallbackSecondary) {
        int tinted = blend(activeSurface, fallbackSecondary, 0.76);
        if (CoursePalette.contrast(tinted, background) >= 4.5) return tinted;
        return CoursePalette.contrast(fallbackSecondary, background) >= 4.5
                ? fallbackSecondary : CoursePalette.readableInk(background);
    }

    private static int hsl(double hue, double saturation, double lightness) {
        double chroma = (1 - Math.abs(2 * lightness - 1)) * saturation;
        double x = chroma * (1 - Math.abs(hue % 2 - 1));
        double m = lightness - chroma / 2;
        double r = 0, g = 0, b = 0;
        if (hue < 1) { r = chroma; g = x; }
        else if (hue < 2) { r = x; g = chroma; }
        else if (hue < 3) { g = chroma; b = x; }
        else if (hue < 4) { g = x; b = chroma; }
        else if (hue < 5) { r = x; b = chroma; }
        else { r = chroma; b = x; }
        return 0xFF000000 | (int) Math.round((r + m) * 255) << 16
                | (int) Math.round((g + m) * 255) << 8 | (int) Math.round((b + m) * 255);
    }

    private static int tone(int accent, int role, double amount) {
        for (int[] row : MIST) if (row[0] == accent) return row[role];
        // Existing saved accents keep their identity, but no longer tint whole panels heavily.
        return blend(SURFACE, accent, amount);
    }

    private static int blend(int a, int b, double amount) {
        int color = 0xFF000000;
        for (int shift : new int[]{16, 8, 0}) {
            int channel = (int)Math.round(((a >> shift) & 255) * (1 - amount)
                    + ((b >> shift) & 255) * amount);
            color |= channel << shift;
        }
        return color;
    }
}
