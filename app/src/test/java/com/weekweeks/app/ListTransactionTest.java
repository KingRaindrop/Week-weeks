package com.weekweeks.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ListTransactionTest {
    @Test
    public void successfulWritePublishesCandidate() {
        List<String> current = new ArrayList<>(Arrays.asList("a", "b"));
        List<String> candidate = new ArrayList<>(Arrays.asList("a", "b", "c"));
        assertTrue(ListTransaction.commit(current, candidate, snapshot -> true));
        assertEquals(Arrays.asList("a", "b", "c"), current);
    }

    @Test
    public void failedWriteKeepsCurrentUntouched() {
        List<String> current = new ArrayList<>(Arrays.asList("a", "b"));
        List<String> candidate = new ArrayList<>(Arrays.asList("x"));
        assertFalse(ListTransaction.commit(current, candidate, snapshot -> false));
        assertEquals(Arrays.asList("a", "b"), current);
    }

    @Test
    public void writerReceivesCandidateSnapshot() {
        List<String> current = new ArrayList<>();
        List<String> candidate = new ArrayList<>(Arrays.asList("a", "b"));
        List<List<String>> written = new ArrayList<>();
        ListTransaction.commit(current, candidate, snapshot -> written.add(new ArrayList<>(snapshot)));
        assertEquals(1, written.size());
        assertEquals(Arrays.asList("a", "b"), written.get(0));
    }

    @Test
    public void selfCommitForMigrationKeepsContents() {
        List<String> current = new ArrayList<>(Arrays.asList("a", "b"));
        assertTrue(ListTransaction.commit(current, current, snapshot -> true));
        assertEquals(Arrays.asList("a", "b"), current);
    }
}
