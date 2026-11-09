package com.weekweeks.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class PeriodEditRulesTest {
    @Test public void rejectsMiddleRemovalEvenWhenRemainingCountIsEnough() {
        assertFalse(PeriodEditRules.canDelete(1, 11, 8));
    }
    @Test public void rejectsUsedLastSlot() {
        assertFalse(PeriodEditRules.canDelete(7, 8, 8));
    }
    @Test public void permitsUnusedTrailingSlots() {
        assertTrue(PeriodEditRules.canDelete(8, 11, 8));
        assertTrue(PeriodEditRules.canDelete(10, 11, 8));
    }
    @Test public void emptyTimetableCanRemoveAnySlotButNotFinalSlot() {
        assertTrue(PeriodEditRules.canDelete(0, 11, 0));
        assertFalse(PeriodEditRules.canDelete(0, 1, 0));
    }
    @Test public void rejectsInvalidIndices() {
        assertFalse(PeriodEditRules.canDelete(-1, 11, 0));
        assertFalse(PeriodEditRules.canDelete(11, 11, 0));
    }
}
