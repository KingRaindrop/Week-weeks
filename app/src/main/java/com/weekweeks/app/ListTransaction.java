package com.weekweeks.app;

import java.util.ArrayList;
import java.util.List;

/** Publish a staged edit only after persistence succeeds. Call on the owning UI thread. */
final class ListTransaction {
    interface Writer<T> {
        boolean write(List<T> candidate);
    }

    private ListTransaction() {
    }

    static <T> boolean commit(List<T> current, List<T> candidate, Writer<T> writer) {
        // Also supports migration, where the candidate may be the current list itself.
        List<T> snapshot = new ArrayList<>(candidate);
        if (!writer.write(snapshot)) {
            return false;
        }
        current.clear();
        current.addAll(snapshot);
        return true;
    }
}
