package com.weekweeks.app;

import android.graphics.Color;

/**
 * Accent-dependent colours of the pastel UI, derived from the user's 「界面色调」 preset.
 * The signature preset (coral) keeps the original navy hero and lavender tints; every
 * other preset re-tints them with its own hue so the whole screen follows the choice.
 */
final class PastelAccent {
    static final int CORAL_LIGHT = UiAccentPalette.CORAL_LIGHT;
    static final int CORAL_DARK = UiAccentPalette.CORAL_DARK;

    /** Buttons, selected tabs/segments, today, timeline, focus strokes. */
    final int accent;
    /** Soft container behind chips, badges and icon buttons. */
    final int tint;
    /** Week/list segmented control track. */
    final int track;
    /** Hint banner background. */
    final int banner;
    final int heroStart, heroEnd, heroText, heroIcon, bookFill;

    private PastelAccent(int accent, int tint, int track, int banner,
                         int heroStart, int heroEnd, int heroText, int heroIcon, int bookFill) {
        this.accent = accent;
        this.tint = tint;
        this.track = track;
        this.banner = banner;
        this.heroStart = heroStart;
        this.heroEnd = heroEnd;
        this.heroText = heroText;
        this.heroIcon = heroIcon;
        this.bookFill = bookFill;
    }

    static PastelAccent classic(boolean dark) {
        return new PastelAccent(
                dark ? CORAL_DARK : CORAL_LIGHT,
                dark ? 0xFF273147 : 0xFFECEEF9,
                dark ? 0xFF2B2939 : 0xFFECECF6,
                dark ? 0xFF282E49 : 0xFFE9EAFB,
                dark ? 0xFF293857 : 0xFF263D78,
                dark ? 0xFF17223A : 0xFF354D8C,
                0xFFD6DFF3, 0xFFC8D9F9, 0xFF536C96);
    }

    static PastelAccent of(int accent, boolean dark) {
        if (accent == CORAL_LIGHT || accent == CORAL_DARK) return classic(dark);
        float[] hsv = new float[3];
        Color.colorToHSV(accent, hsv);
        float h = hsv[0];
        return new PastelAccent(
                accent | 0xFF000000,
                dark ? hsv(h, .42f, .29f) : hsv(h, .06f, .98f),
                dark ? hsv(h, .30f, .23f) : hsv(h, .05f, .97f),
                dark ? hsv(h, .42f, .29f) : hsv(h, .07f, .99f),
                dark ? hsv(h, .53f, .34f) : hsv(h, .68f, .47f),
                dark ? hsv(h, .60f, .23f) : hsv(h, .62f, .55f),
                hsv(h, .12f, .96f),
                hsv(h, .19f, .98f),
                hsv(h, .44f, .59f));
    }

    private static int hsv(float h, float s, float v) {
        return Color.HSVToColor(new float[]{h, s, v});
    }
}
