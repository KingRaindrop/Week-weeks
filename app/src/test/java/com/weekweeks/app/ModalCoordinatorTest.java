package com.weekweeks.app;

import org.junit.Test;
import static org.junit.Assert.*;

/** Deterministic ownership tests: no Android, clock or debounce needed. */
public class ModalCoordinatorTest {
    @Test public void openingOpenAndClosingAllRejectSameAndDifferentBlocks() {
        ModalCoordinator c = new ModalCoordinator();
        assertEquals(ModalCoordinator.State.IDLE, c.state());
        ModalCoordinator.Token a = c.tryAcquire("course:1");
        assertNotNull(a);
        for (ModalCoordinator.State phase : new ModalCoordinator.State[]{
                ModalCoordinator.State.OPENING, ModalCoordinator.State.OPEN, ModalCoordinator.State.CLOSING}) {
            assertEquals(phase, c.state());
            assertNull(c.tryAcquire("course:1"));
            assertNull(c.tryAcquire("course:2"));
            assertNull(c.tryAcquire("event:1"));
            assertNull(c.tryAcquire("add"));
            if (phase == ModalCoordinator.State.OPENING) c.opened(a);
            else if (phase == ModalCoordinator.State.OPEN) c.closing(a);
        }
        c.dismissed(a);
        assertEquals(ModalCoordinator.State.IDLE, c.state());
        assertNotNull(c.tryAcquire("event:1"));
    }

    @Test public void detailEditorHandoffWaitsForDismissAndIgnoresOldCallbacks() {
        ModalCoordinator c = new ModalCoordinator();
        ModalCoordinator.Token old = c.tryAcquire("detail");
        c.opened(old);
        ModalCoordinator.Token[] editor = {null};
        int[] calls = {0};
        assertTrue(c.switchAfterDismiss(old, () -> {
            calls[0]++;
            assertEquals(ModalCoordinator.State.CLOSING, c.state());
            editor[0] = c.tryAcquire("editor");
            assertNotNull(editor[0]);
            assertNull(c.tryAcquire("intruder"));
            c.opened(editor[0]);
        }));
        assertEquals(0, calls[0]);
        assertFalse(c.switchAfterDismiss(old, () -> fail("duplicate forward")));
        assertNull(c.tryAcquire("underlying add"));
        c.opened(old); // A delayed animation callback must not reopen a closing sheet.
        assertEquals(ModalCoordinator.State.CLOSING, c.state());
        c.dismissed(old);
        assertEquals(1, calls[0]);
        c.dismissed(old);
        c.closing(old);
        c.opened(old);
        assertTrue(c.owns(editor[0]));
        assertEquals(ModalCoordinator.State.OPEN, c.state());
        c.closing(editor[0]);
        c.dismissed(editor[0]);
        assertTrue(c.canOpen());
    }

    @Test public void failedOrUnavailableSuccessorDoesNotStrandOwnership() {
        ModalCoordinator c = new ModalCoordinator();
        ModalCoordinator.Token old = c.tryAcquire("detail");
        c.switchAfterDismiss(old, () -> { /* underlying item was removed */ });
        c.dismissed(old);
        assertEquals(ModalCoordinator.State.IDLE, c.state());
        ModalCoordinator.Token next = c.tryAcquire("detail2");
        c.switchAfterDismiss(next, () -> { throw new IllegalStateException("destroyed window"); });
        try { c.dismissed(next); fail("must preserve exception"); }
        catch (IllegalStateException expected) { }
        assertTrue(c.canOpen());
    }

    @Test public void destructionCancelsPendingHandoffAndCannotBeRearmed() {
        ModalCoordinator c = new ModalCoordinator();
        ModalCoordinator.Token old = c.tryAcquire("detail");
        c.switchAfterDismiss(old, () -> fail("must not open on destroyed Activity"));
        c.destroy();
        c.dismissed(old);
        assertFalse(c.canOpen());
        assertNull(c.tryAcquire("restore"));
        ModalCoordinator replacement = new ModalCoordinator();
        assertNotNull(replacement.tryAcquire("restored editor"));
    }

    @Test public void idleListenerFiresOnlyWhenOwnershipIsReallyReleased() {
        ModalCoordinator c = new ModalCoordinator();
        int[] idle = {0};
        c.setIdleListener(() -> {
            idle[0]++;
            assertTrue(c.canOpen());
        });
        ModalCoordinator.Token review = c.tryAcquire("import_review");
        c.opened(review);
        assertTrue(c.switchAfterDismiss(review, () -> {
            ModalCoordinator.Token result = c.tryAcquire("import_result");
            assertNotNull(result);
            c.opened(result);
        }));
        c.dismissed(review);
        assertEquals("handoff to a successor is not idle", 0, idle[0]);
        // The successor now owns the window; deferred work must still wait.
        assertNull(c.tryAcquire("deferred"));
        c.destroy();
        assertEquals("destroyed coordinator never notifies", 0, idle[0]);

        ModalCoordinator d = new ModalCoordinator();
        d.setIdleListener(() -> idle[0]++);
        ModalCoordinator.Token plain = d.tryAcquire("popup");
        d.dismissed(plain);
        assertEquals(1, idle[0]);
        ModalCoordinator.Token empty = d.tryAcquire("handoff_without_successor");
        d.switchAfterDismiss(empty, () -> { });
        d.dismissed(empty);
        assertEquals(2, idle[0]);
    }

    @Test public void unrelatedCoordinatorTokenCannotReleaseOwner() {
        ModalCoordinator a = new ModalCoordinator(), b = new ModalCoordinator();
        ModalCoordinator.Token owner = a.tryAcquire("same");
        ModalCoordinator.Token alien = b.tryAcquire("same");
        a.dismissed(alien);
        a.closing(alien);
        assertTrue(a.owns(owner));
        assertNull(a.tryAcquire("same"));
    }
}
