package com.weekweeks.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DeferredResultTest {
    @Test
    public void resultIsHeldUntilResumed() {
        DeferredResult<String> result = new DeferredResult<>();
        long token = result.begin();
        assertTrue(result.offer(token, "done"));
        assertNull(result.take());
        result.setResumed(true);
        assertEquals("done", result.take());
        assertNull(result.take());
    }

    @Test
    public void staleTokenIsRejected() {
        DeferredResult<String> result = new DeferredResult<>();
        result.setResumed(true);
        long first = result.begin();
        long second = result.begin();
        assertFalse(result.offer(first, "stale"));
        assertTrue(result.offer(second, "fresh"));
        assertEquals("fresh", result.take());
    }

    @Test
    public void cancelDropsPendingAndInvalidatesToken() {
        DeferredResult<String> result = new DeferredResult<>();
        result.setResumed(true);
        long token = result.begin();
        result.cancel();
        assertFalse(result.isCurrent(token));
        assertFalse(result.offer(token, "late"));
        assertNull(result.take());
    }

    @Test
    public void destroyedOwnerNeverReceivesResults() {
        DeferredResult<String> result = new DeferredResult<>();
        long token = result.begin();
        result.destroy();
        assertFalse(result.offer(token, "late"));
        result.setResumed(true);
        assertNull(result.take());
    }

    @Test(expected = IllegalStateException.class)
    public void beginAfterDestroyFails() {
        DeferredResult<String> result = new DeferredResult<>();
        result.destroy();
        result.begin();
    }
}
