package com.weekweeks.app;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class FontSizeRulesTest {
    @Test public void boundsAndDefaultAreStable() {
        assertEquals(85, FontSizeRules.MIN);
        assertEquals(125, FontSizeRules.MAX);
        assertEquals(100, FontSizeRules.DEFAULT);
        assertEquals(100, FontSizeRules.normalize(100));
    }

    @Test public void clampsCorruptOrOutOfRangePreferences() {
        assertEquals(85, FontSizeRules.normalize(Integer.MIN_VALUE));
        assertEquals(125, FontSizeRules.normalize(Integer.MAX_VALUE));
        assertEquals(85, FontSizeRules.normalize(0));
        assertEquals(125, FontSizeRules.normalize(999));
    }

    @Test public void usesFivePercentSteps() {
        assertEquals(100, FontSizeRules.normalize(102));
        assertEquals(105, FontSizeRules.normalize(103));
        for (int p = 85; p <= 125; p += 5) assertEquals(p, FontSizeRules.normalize(p));
    }

    @Test public void sliderRoundTripsAllNinePositions() {
        for (int i = 0; i <= 8; i++) {
            int p = FontSizeRules.fromProgress(i);
            assertEquals(85 + 5 * i, p);
            assertEquals(i, FontSizeRules.toProgress(p));
        }
        assertEquals(85, FontSizeRules.fromProgress(-1));
        assertEquals(125, FontSizeRules.fromProgress(Integer.MAX_VALUE));
    }

    @Test public void keepsSystemScaleAndNeverCompoundsTheAppMultiplier() {
        assertEquals(1.3f, FontSizeRules.combinedScale(1.3f, 100), .0001f);
        assertEquals(1.625f, FontSizeRules.combinedScale(1.3f, 125), .0001f);
        assertEquals(.85f, FontSizeRules.combinedScale(1f, 85), .0001f);
        assertEquals(1.25f, FontSizeRules.combinedScale(1f, 125), .0001f);
    }

    @Test public void invalidSystemScaleFallsBackToOne() {
        for (float scale : new float[]{Float.NaN, Float.POSITIVE_INFINITY, 0, -1}) {
            assertEquals(1f, FontSizeRules.combinedScale(scale, 100), .0001f);
        }
    }
}
