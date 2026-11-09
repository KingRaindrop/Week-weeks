package com.weekweeks.app;

/** Removing a used or earlier slot renumbers existing courses; only unused trailing slots are safe. */
final class PeriodEditRules {
    private PeriodEditRules() { }
    static boolean canDelete(int zeroBasedIndex, int count, int highestUsedPeriod) {
        return count > 1 && zeroBasedIndex >= 0 && zeroBasedIndex < count
                && zeroBasedIndex >= Math.max(0, highestUsedPeriod);
    }
}
