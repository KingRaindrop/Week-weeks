package com.weekweeks.app;

/** App-relative text scaling; the platform's own font-size preference is preserved. */
final class FontSizeRules {
    static final int MIN = 85;
    static final int MAX = 125;
    static final int STEP = 5;
    static final int DEFAULT = 100;

    private FontSizeRules() { }

    static int normalize(int percent) {
        int bounded = Math.max(MIN, Math.min(MAX, percent));
        return MIN + Math.round((bounded - MIN) / (float) STEP) * STEP;
    }

    static int fromProgress(int progress) {
        return MIN + Math.max(0, Math.min((MAX - MIN) / STEP, progress)) * STEP;
    }

    static int toProgress(int percent) {
        return (normalize(percent) - MIN) / STEP;
    }

    static float combinedScale(float systemScale, int percent) {
        float base = Float.isNaN(systemScale) || Float.isInfinite(systemScale) || systemScale <= 0
                ? 1f : systemScale;
        return base * normalize(percent) / 100f;
    }
}
