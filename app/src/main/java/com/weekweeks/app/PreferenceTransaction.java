package com.weekweeks.app;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A batch of preference writes that is rolled back in memory when the commit fails.
 *
 * <p>SharedPreferences updates its in-process cache before the disk write, so a failed
 * {@code commit()} would otherwise leave new values visible until the process dies. On
 * failure the touched keys are written back to their previous values (or removed if they
 * did not exist). The rollback write also updates the cache even if its own disk write
 * fails. Keys outside this transaction are never touched.</p>
 */
final class PreferenceTransaction {
    interface Store {
        Map<String, ?> getAll();

        /** Applies puts then removes in one editor and returns the commit result. */
        boolean write(Map<String, Object> puts, Set<String> removes);
    }

    private final Map<String, Object> puts = new LinkedHashMap<>();
    private final Set<String> removes = new LinkedHashSet<>();

    PreferenceTransaction put(String key, Object value) {
        if (value == null) {
            return remove(key);
        }
        removes.remove(key);
        puts.put(key, value);
        return this;
    }

    PreferenceTransaction remove(String key) {
        puts.remove(key);
        removes.add(key);
        return this;
    }

    boolean commit(Store store) {
        Map<String, ?> before = store.getAll();
        if (before == null) {
            before = Collections.emptyMap();
        }
        boolean committed;
        try {
            committed = store.write(puts, removes);
        } catch (RuntimeException error) {
            committed = false;
        }
        if (committed) {
            return true;
        }
        Map<String, Object> restorePuts = new LinkedHashMap<>();
        Set<String> restoreRemoves = new LinkedHashSet<>();
        Set<String> touched = new LinkedHashSet<>(puts.keySet());
        touched.addAll(removes);
        for (String key : touched) {
            if (before.containsKey(key) && before.get(key) != null) {
                restorePuts.put(key, before.get(key));
            } else {
                restoreRemoves.add(key);
            }
        }
        try {
            store.write(restorePuts, restoreRemoves);
        } catch (RuntimeException ignored) {
            // Best effort: the in-memory cache has already been restored by the editor.
        }
        return false;
    }
}
