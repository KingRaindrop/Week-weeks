package com.weekweeks.app;

import java.util.Arrays;

/**
 * Muted course colors appended after the legacy palette. Pure Java (no Android types), so
 * colors are packed ARGB ints and the helpers are unit-testable on the JVM.
 */
final class CoursePalette {
    /** User-specified order; appended after legacy colors so stored indices stay valid. */
    static final int[] MUTED_COLORS = {
            0xFF9CB2C1, 0xFF9AA894, 0xFFF5F0E6, 0xFFB2A8B8,
            0xFFA0AE99, 0xFFCDA59C, 0xFFB7A696, 0xFFDEC4C4,
            0xFF89ADB8, 0xFFBED2C4, 0xFFF6E9BE, 0xFFEED6B9,
            0xFF78979A, 0xFFD6CCB8, 0xFFB78A8D, 0xFFAEA4BC,
            0xFFCDBAA0, 0xFFAAC4B8, 0xFFB4B8BA, 0xFFA8A098,
            0xFFB8D2E2, 0xFF919F91, 0xFFE8B4A8, 0xFFEBDBC3,
            0xFF8AA8A0, 0xFFC4BACA, 0xFFE2DCD0, 0xFF7C8C74,
            0xFFC69892, 0xFF8296A6, 0xFFB9C2AA, 0xFF5C7080
    };

    static final String[] MUTED_NAMES = {
            "雾霾蓝", "灰豆绿", "燕麦白", "浅灰紫",
            "鼠尾草绿", "陶土粉", "驼灰", "雾粉",
            "湖水蓝", "薄荷灰", "奶油黄", "浅杏色",
            "灰蓝绿", "亚麻色", "干枯玫瑰", "灰薰衣草",
            "沙色", "青瓷绿", "雾霭灰", "暖灰",
            "淡天蓝", "鼠尾草灰", "浅珊瑚", "香槟色",
            "灰绿松", "淡紫灰", "米灰", "苔藓绿",
            "浅砖粉", "雾蓝灰", "柔沙绿", "深灰蓝"
    };

    /** Curated stored colors for automatic assignment; manual selection still offers all 42.
     * Rose, terracotta, amber, cream, yellow-green, sage, leaf, jade, teal, blue, violet, pink.
     * Deliberately excludes near-neutral grays and multiple variants of the same blue-gray.
     */
    static final int[] RANDOM_COLORS = {
            0xFFB78A8D, 0xFFAE695C, 0xFFAC7E3A, 0xFFF6E9BE,
            0xFFB9C2AA, 0xFFA0AE99, 0xFF5B7E54, 0xFF43876C,
            0xFF347B80, 0xFF446FA6, 0xFF846497, 0xFF915B77
    };

    /** Dark-mode sheet background that muted colors are blended into. */
    static final int DARK_BASE = 0xFF151B28;
    private static final double DARK_COLOR_WEIGHT = 0.50;
    static final float LEGACY_DARK_VALUE = 0.52f;
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    private static final String[] MUTED_NAMES_HANT = {
            "霧霾藍", "灰豆綠", "燕麥白", "淺灰紫",
            "鼠尾草綠", "陶土粉", "駝灰", "霧粉",
            "湖水藍", "薄荷灰", "奶油黃", "淺杏色",
            "灰藍綠", "亞麻色", "乾枯玫瑰", "灰薰衣草",
            "沙色", "青瓷綠", "霧靄灰", "暖灰",
            "淡天藍", "鼠尾草灰", "淺珊瑚", "香檳色",
            "灰綠松", "淡紫灰", "米灰", "苔蘚綠",
            "淺磚粉", "霧藍灰", "柔沙綠", "深灰藍"
    };
    private static final String[] MUTED_NAMES_EN = {
            "Haze Blue", "Bean Green", "Oat White", "Pale Mauve",
            "Sage Green", "Clay Pink", "Taupe", "Mist Pink",
            "Lake Blue", "Mint Gray", "Butter Yellow", "Apricot",
            "Gray Teal", "Linen", "Dried Rose", "Gray Lavender",
            "Sand", "Celadon", "Fog Gray", "Warm Gray",
            "Sky Blue", "Sage Gray", "Pale Coral", "Champagne",
            "Gray Turquoise", "Lilac Gray", "Greige", "Moss Green",
            "Brick Pink", "Mist Slate", "Soft Olive", "Deep Slate"
    };

    private CoursePalette() { }

    /** Display name of muted color {@code index} (0-based within {@link #MUTED_NAMES}) in the current {@link UiLang}. */
    static String name(int index) {
        if (index < 0 || index >= MUTED_NAMES.length) return "";
        return UiLang.t(MUTED_NAMES[index], MUTED_NAMES_HANT[index], MUTED_NAMES_EN[index]);
    }

    /** Returns a new array: legacy colors first, then the muted colors. The input is not modified. */
    static int[] appendColors(int[] legacy) {
        int[] base = legacy == null ? new int[0] : legacy;
        int[] result = Arrays.copyOf(base, base.length + MUTED_COLORS.length);
        System.arraycopy(MUTED_COLORS, 0, result, base.length, MUTED_COLORS.length);
        return result;
    }

    /** Returns a new array: legacy names first, then the muted names in the current {@link UiLang}. */
    static String[] appendNames(String[] legacy) {
        String[] base = legacy == null ? new String[0] : legacy;
        String[] result = Arrays.copyOf(base, base.length + MUTED_NAMES.length);
        for (int i = 0; i < MUTED_NAMES.length; i++) result[base.length + i] = name(i);
        return result;
    }

    static boolean isMuted(int color) {
        for (int muted : MUTED_COLORS) if (muted == color) return true;
        return false;
    }

    /** Light mode: exact preset. Dark mode: 50% color over 50% {@link #DARK_BASE}, opaque. */
    static int mutedSurface(int color, boolean dark) {
        if (!dark) return color;
        return 0xFF000000
                | blend(red(color), red(DARK_BASE)) << 16
                | blend(green(color), green(DARK_BASE)) << 8
                | blend(blue(color), blue(DARK_BASE));
    }

    /**
     * Unified display surface for both legacy and muted stored colors. The stored value is a
     * hue seed, never rewritten: HSL hue is preserved, lightness is 0.83 (light) / 0.32 (dark),
     * and saturation is clamped to 0.40–0.55 / 0.25–0.40 respectively. Near-neutrals (RGB
     * channel span <= 8/255, or HSL saturation < 0.08) stay neutral instead of acquiring an
     * arbitrary hue. These bounds support the same active ink throughout the 42-color palette.
     * This is intentionally separate from the exact-RGB compatibility API mutedSurface().
     */
    static int courseSurface(int color, boolean dark) {
        double r = red(color) / 255.0, g = green(color) / 255.0, b = blue(color) / 255.0;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        double delta = max - min;
        double sourceLightness = (max + min) / 2.0;
        double saturation = delta == 0 ? 0 : delta / (1 - Math.abs(2 * sourceLightness - 1));
        double lightness = dark ? 0.32 : 0.83;
        if (delta <= 8.0 / 255.0 || saturation < 0.08) return hsl(0, 0, lightness);
        double hue;
        if (max == r) hue = ((g - b) / delta + 6) % 6;
        else if (max == g) hue = (b - r) / delta + 2;
        else hue = (r - g) / delta + 4;
        saturation = Math.max(dark ? 0.25 : 0.40, Math.min(dark ? 0.40 : 0.55, saturation));
        return hsl(hue, saturation, lightness);
    }

    /** Hue is in six sectors; all other HSL components are in [0, 1]. */
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

    /** Prefer clearer hues for automatic assignment; manual presets retain their exact values. */
    static boolean preferredForRandom(int color) {
        int max = Math.max(red(color), Math.max(green(color), blue(color)));
        int min = Math.min(red(color), Math.min(green(color), blue(color)));
        int chroma = max - min;
        return chroma >= 40 && chroma >= max * 0.18;
    }

    /** Light mode: equivalent to 30% background opacity over white; keep text independent.
     * Precomposing prevents grid lines showing through and keeps list/grid surfaces identical.
     * Dark mode retains its existing gentle fade.
     */
    static int finishedSurface(int surface, boolean dark) {
        int target = dark ? DARK_BASE : WHITE;
        return mix(surface, target, dark ? 0.15 : 0.70);
    }

    /** Preserve the usual course ink where possible; brighter neutral cards may need black. */
    static int activeInk(int background, boolean dark) {
        int ink = dark ? 0xFFEEF2FF : 0xFF34405A;
        return contrast(ink, background) >= 4.5 ? ink : readableInk(background);
    }

    /** Opaque equivalent of the detail chip's translucent white overlay. */
    static int detailBadgeSurface(int background, boolean dark) {
        return mix(background, WHITE, (dark ? 46.0 : 150.0) / 255.0);
    }

    /** De-emphasize finished labels without losing readable contrast. */
    static int finishedInk(int background, boolean dark) {
        int mutedInk = dark ? 0xFFCBD5E1 : 0xFF566176;
        return contrast(mutedInk, background) >= 4.5 ? mutedInk : readableInk(background);
    }

    private static int mix(int a, int b, double amount) {
        return 0xFF000000
                | (int) Math.round(red(a) * (1 - amount) + red(b) * amount) << 16
                | (int) Math.round(green(a) * (1 - amount) + green(b) * amount) << 8
                | (int) Math.round(blue(a) * (1 - amount) + blue(b) * amount);
    }

    /** Black or white, whichever has the higher WCAG contrast against the background. */
    static int readableInk(int background) {
        return contrast(BLACK, background) >= contrast(WHITE, background) ? BLACK : WHITE;
    }

    /** WCAG 2.x contrast ratio (1–21) of two colors; alpha is ignored. */
    static double contrast(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(int color) {
        return 0.2126 * linear(red(color)) + 0.7152 * linear(green(color)) + 0.0722 * linear(blue(color));
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static int blend(int color, int base) {
        return (int) Math.round(color * DARK_COLOR_WEIGHT + base * (1 - DARK_COLOR_WEIGHT));
    }

    private static int red(int color) { return (color >> 16) & 0xFF; }
    private static int green(int color) { return (color >> 8) & 0xFF; }
    private static int blue(int color) { return color & 0xFF; }
}
