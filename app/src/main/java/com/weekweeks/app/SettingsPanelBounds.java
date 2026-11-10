package com.weekweeks.app;

/** Pixel-only sizing rules; caller removes system insets and outside margins first. */
final class SettingsPanelBounds {
    private SettingsPanelBounds() {}

    static int width(int availableWidth, int preferredMaxWidth) {
        return Math.max(0, Math.min(availableWidth, preferredMaxWidth));
    }

    static int height(int availableHeight) {
        return Math.max(0, (int) (availableHeight * 0.8f));
    }
}
