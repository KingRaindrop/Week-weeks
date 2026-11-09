package com.weekweeks.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PreferenceTransactionTest {
    /** Mimics SharedPreferences: memory is updated even when the disk write fails. */
    private static final class FakeStore implements PreferenceTransaction.Store {
        final Map<String, Object> memory = new HashMap<>();
        int failWrites;
        int writes;
        Map<String, Object> lastPuts = new HashMap<>();
        Set<String> lastRemoves = new java.util.HashSet<>();

        @Override
        public Map<String, ?> getAll() {
            return new HashMap<>(memory);
        }

        @Override
        public boolean write(Map<String, Object> puts, Set<String> removes) {
            writes++;
            lastPuts = new HashMap<>(puts);
            lastRemoves = new java.util.HashSet<>(removes);
            memory.putAll(puts);
            for (String key : removes) {
                memory.remove(key);
            }
            if (failWrites > 0) {
                failWrites--;
                return false;
            }
            return true;
        }
    }

    @Test
    public void successfulCommitKeepsNewValues() {
        FakeStore store = new FakeStore();
        store.memory.put("a", "old");
        assertTrue(new PreferenceTransaction().put("a", "new").commit(store));
        assertEquals("new", store.memory.get("a"));
        assertEquals(1, store.writes);
    }

    @Test
    public void failedCommitRestoresPreviousValues() {
        FakeStore store = new FakeStore();
        store.memory.put("a", "old");
        store.memory.put("b", 3);
        store.failWrites = 1;
        assertFalse(new PreferenceTransaction().put("a", "new").remove("b").commit(store));
        assertEquals("old", store.memory.get("a"));
        assertEquals(3, store.memory.get("b"));
    }

    @Test
    public void rollbackRestoresCacheEvenWhenRollbackWriteFails() {
        FakeStore store = new FakeStore();
        store.memory.put("a", "old");
        store.failWrites = 2;
        assertFalse(new PreferenceTransaction().put("a", "new").commit(store));
        assertEquals("old", store.memory.get("a"));
        assertEquals(2, store.writes);
    }

    @Test
    public void failedPutOfMissingKeyRemovesIt() {
        FakeStore store = new FakeStore();
        store.failWrites = 1;
        assertFalse(new PreferenceTransaction().put("fresh", 1L).commit(store));
        assertFalse(store.memory.containsKey("fresh"));
    }

    @Test
    public void failedRemoveOfMissingKeyStaysMissing() {
        FakeStore store = new FakeStore();
        store.failWrites = 1;
        assertFalse(new PreferenceTransaction().remove("ghost").commit(store));
        assertFalse(store.memory.containsKey("ghost"));
    }

    @Test
    public void rollbackDoesNotTouchOtherKeys() {
        FakeStore store = new FakeStore();
        store.memory.put("a", "old");
        store.memory.put("other", "keep");
        store.failWrites = 1;
        new PreferenceTransaction().put("a", "new").commit(store);
        assertEquals("old", store.memory.get("a"));
        assertEquals("keep", store.memory.get("other"));
        assertFalse(store.lastPuts.containsKey("other"));
        assertFalse(store.lastRemoves.contains("other"));
    }
}
