package com.weekweeks.app;

/** Interface roles; course classification colors remain owned by CoursePalette. */
final class UiSemanticPalette {
    final int background, surface, inset, divider, text, secondary;
    final int accent, onAccent, error, errorContainer, onErrorContainer;

    private UiSemanticPalette(boolean dark, int accent) {
        background = dark ? DarkUiPalette.BACKGROUND : 0xFFF6F7F9;
        surface = dark ? DarkUiPalette.SURFACE : 0xFFFFFFFF;
        inset = dark ? DarkUiPalette.RAISED : 0xFFF0F2F5;
        divider = dark ? DarkUiPalette.SUBTLE_LINE : 0xFFE4E7EC;
        text = dark ? DarkUiPalette.INK : 0xFF25314D;
        secondary = dark ? DarkUiPalette.SECONDARY : 0xFF626A7A;
        this.accent = accent | 0xFF000000;
        onAccent = CoursePalette.readableInk(this.accent);
        error = dark ? 0xFFF2B8B5 : 0xFFB3261E;
        errorContainer = dark ? 0xFF452726 : 0xFFFCE8E6;
        onErrorContainer = dark ? 0xFFF9DEDC : 0xFF8C1D18;
    }

    static UiSemanticPalette of(boolean dark, int accent) {
        return new UiSemanticPalette(dark, accent);
    }
}
