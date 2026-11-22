package com.weekweeks.app;

import org.junit.Test;

import static org.junit.Assert.*;

public class TabNavigationStateTest {
    private static final int WIDTH = 400;
    private static final float MIN_VELOCITY = 500f, MIN_DISTANCE = 24f;
    // Explicit expected edges keep the test independent of adjacent()'s implementation.
    private static final int[][] EDGES = {
            {0, 1, 1}, {1, 1, 2}, {2, 1, 0},
            {0, -1, 2}, {1, -1, 0}, {2, -1, 1}
    };

    @Test public void startsOnWeekWithNoPendingNavigation() {
        assertIdle(new TabNavigationState(), TabNavigationState.WEEK);
    }

    @Test public void allSixAdjacentEdgesWrapInTheExpectedOrder() {
        for (int[] edge : EDGES)
            assertEquals(edge[2], TabNavigationState.adjacent(edge[0], edge[1]));
    }

    @Test public void distanceThresholdCommitsAllSixEdgesOnlyAfterFinish() {
        for (int[] edge : EDGES) {
            TabNavigationState state = dragging(edge[0], -edge[1] * 100f);
            assertEquals(edge[2], state.target());
            assertEquals(TabNavigationState.Phase.DRAGGING, state.phase());
            assertFalse(state.commits());
            assertTrue(release(state, 0f));
            assertEquals(edge[0], state.page());
            assertEquals(edge[2], state.target());
            assertEquals(TabNavigationState.Phase.SETTLING, state.phase());
            assertTrue(state.commits());
            state.finish();
            assertIdle(state, edge[2]);
        }
    }

    @Test public void minimumDistanceAndSameDirectionFlingCommitAllSixEdges() {
        for (int[] edge : EDGES) {
            TabNavigationState state = dragging(edge[0], -edge[1] * MIN_DISTANCE);
            assertTrue(release(state, -edge[1] * MIN_VELOCITY));
            state.finish();
            assertIdle(state, edge[2]);
        }
    }

    @Test public void slowShortDragsReboundAndRetainNeighbourForAnimation() {
        for (int[] edge : EDGES) {
            TabNavigationState state = dragging(edge[0], -edge[1] * 99.9f);
            assertFalse(release(state, -edge[1] * (MIN_VELOCITY - 1f)));
            assertEquals(TabNavigationState.Phase.SETTLING, state.phase());
            assertEquals(edge[2], state.target());
            assertFalse(state.commits());
            state.finish();
            assertIdle(state, edge[0]);
        }
    }

    @Test public void tinyFastFlickCannotBypassMinimumDistance() {
        for (int direction : new int[]{-1, 1}) {
            TabNavigationState state = dragging(1, direction * (MIN_DISTANCE - 0.1f));
            assertFalse(release(state, direction * Float.MAX_VALUE));
            state.finish();
            assertIdle(state, 1);
        }
    }

    @Test public void zeroDisplacementCannotNavigateEvenWithZeroMinimumDistance() {
        for (float velocity : new float[]{-Float.MAX_VALUE, 0f, Float.MAX_VALUE}) {
            TabNavigationState state = dragging(2, -0f);
            assertFalse(state.release(velocity, MIN_VELOCITY, 0f, WIDTH));
            assertEquals(2, state.target());
            state.finish();
            assertIdle(state, 2);
        }
    }

    @Test public void clearReverseFlingOverridesLongDistanceOnAllSixEdges() {
        for (int[] edge : EDGES) {
            TabNavigationState state = dragging(edge[0], -edge[1] * 300f);
            assertFalse(release(state, edge[1] * MIN_VELOCITY));
            assertFalse(state.commits());
            state.finish();
            assertIdle(state, edge[0]);
        }
    }

    @Test public void smallReverseVelocityDoesNotOverrideACompletedDistanceDrag() {
        for (int direction : new int[]{-1, 1}) {
            TabNavigationState state = dragging(1, direction * 100f);
            assertTrue(release(state, -direction * (MIN_VELOCITY - 1f)));
        }
    }

    @Test public void pullingBackUsesFinalDistanceRatherThanFarthestDistance() {
        TabNavigationState state = dragging(0, -300f);
        state.drag(-50f, WIDTH);
        assertFalse(release(state, 0f));
        state.finish();
        assertIdle(state, 0);
    }

    @Test public void crossingOriginSwitchesNeighbourAndReleasesInFinalDirection() {
        for (int[] edge : EDGES) {
            TabNavigationState state = dragging(edge[0], edge[1] * 200f);
            state.drag(0f, WIDTH);
            assertEquals(edge[0], state.target());
            state.drag(-edge[1] * 100f, WIDTH);
            assertEquals(edge[2], state.target());
            assertTrue(release(state, -edge[1] * MIN_VELOCITY));
            state.finish();
            assertIdle(state, edge[2]);
        }
    }

    @Test public void dragClampsOverscrollToOneWidthInBothDirections() {
        TabNavigationState state = dragging(0, -Float.MAX_VALUE);
        assertEquals(-400f, state.offset(), 0f);
        assertEquals(1, state.target());
        state.drag(Float.MAX_VALUE, WIDTH);
        assertEquals(400f, state.offset(), 0f);
        assertEquals(2, state.target());
        state.drag(-0f, WIDTH);
        assertEquals(Float.floatToIntBits(0f), Float.floatToIntBits(state.offset()));
        assertEquals(0, state.target());
    }

    @Test public void releaseUsesCurrentWidthAfterLayoutChange() {
        TabNavigationState shrunk = dragging(0, -300f);
        assertTrue(shrunk.release(0f, MIN_VELOCITY, MIN_DISTANCE, 80));
        assertEquals(-80f, shrunk.offset(), 0f);
        shrunk.finish();
        assertIdle(shrunk, 1);

        TabNavigationState grown = dragging(0, -100f);
        assertFalse(grown.release(0f, MIN_VELOCITY, MIN_DISTANCE, 800));
        grown.finish();
        assertIdle(grown, 0);
    }

    @Test public void cancelDuringAnyPhaseKeepsOriginalPageOnEveryEdge() {
        for (int[] edge : EDGES) {
            for (int phase = 0; phase < 3; phase++) {
                TabNavigationState state = new TabNavigationState();
                state.select(edge[0]);
                if (phase > 0) {
                    state.beginDrag();
                    state.drag(-edge[1] * 200f, WIDTH);
                }
                if (phase > 1) assertTrue(release(state, 0f));
                state.cancel();
                state.cancel();
                state.finish();
                assertIdle(state, edge[0]);
            }
        }
    }

    @Test public void directSelectionCancelsAnyPendingDragOrRelease() {
        for (int selected = 0; selected < 3; selected++) {
            for (int phase = 0; phase < 3; phase++) {
                TabNavigationState state = new TabNavigationState();
                if (phase > 0) {
                    state.beginDrag();
                    state.drag(-200f, WIDTH);
                }
                if (phase > 1) assertTrue(release(state, 0f));
                state.select(selected);
                state.finish();
                assertIdle(state, selected);
            }
        }
    }

    @Test public void beginDragRestartsWithoutCommittingAnInterruptedRelease() {
        TabNavigationState state = dragging(2, -200f);
        state.beginDrag();
        assertDraggingAtOrigin(state, 2);
        state.drag(-200f, WIDTH);
        assertTrue(release(state, 0f));
        state.beginDrag();
        assertDraggingAtOrigin(state, 2);
        state.finish(); // A cancelled animation may still deliver its completion callback.
        assertDraggingAtOrigin(state, 2);
        state.drag(100f, WIDTH);
        assertTrue(release(state, 0f));
        state.finish();
        assertIdle(state, 1);
    }

    @Test public void repeatedReleaseCannotChangeFrozenDecisionOrOffset() {
        for (boolean commit : new boolean[]{false, true}) {
            float displacement = commit ? -200f : -20f;
            TabNavigationState state = dragging(0, displacement);
            assertEquals(commit, release(state, 0f));
            assertFalse(release(state, Float.MAX_VALUE));
            state.drag(300f, WIDTH);
            assertEquals(commit, state.commits());
            assertEquals(displacement, state.offset(), 0f);
            assertEquals(1, state.target());
            assertEquals(TabNavigationState.Phase.SETTLING, state.phase());
            state.finish();
            state.finish();
            assertIdle(state, commit ? 1 : 0);
        }
    }

    @Test public void outOfPhaseCallsDoNotInventOrFinishAGesture() {
        TabNavigationState state = new TabNavigationState();
        state.drag(-200f, WIDTH);
        assertFalse(release(state, -1000f));
        state.finish();
        assertIdle(state, 0);
        state.beginDrag();
        state.drag(100f, WIDTH);
        state.finish();
        assertEquals(TabNavigationState.Phase.DRAGGING, state.phase());
        assertEquals(100f, state.offset(), 0f);
        assertEquals(2, state.target());
    }

    @Test public void invalidPagesAndDirectionsAreRejectedWithoutCancellingPendingCommit() {
        TabNavigationState state = dragging(1, -200f);
        assertTrue(release(state, 0f));
        for (int page : new int[]{Integer.MIN_VALUE, -1, 3, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> state.select(page));
            assertThrows(IllegalArgumentException.class, () -> TabNavigationState.adjacent(page, 1));
        }
        for (int direction : new int[]{Integer.MIN_VALUE, -2, 0, 2, Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> TabNavigationState.adjacent(1, direction));
        assertEquals(1, state.page());
        assertTrue(state.commits());
        state.finish();
        assertIdle(state, 2);
    }

    @Test public void invalidDragInputsAreRejectedAtomically() {
        TabNavigationState state = dragging(1, -80f);
        for (float value : new float[]{Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> state.drag(value, WIDTH));
        for (int width : new int[]{Integer.MIN_VALUE, -1, 0})
            assertThrows(IllegalArgumentException.class, () -> state.drag(200f, width));
        assertEquals(TabNavigationState.Phase.DRAGGING, state.phase());
        assertEquals(1, state.page());
        assertEquals(2, state.target());
        assertEquals(-80f, state.offset(), 0f);
        assertFalse(state.commits());
    }

    @Test public void invalidReleaseInputsAreRejectedAtomically() {
        TabNavigationState state = dragging(2, 150f);
        for (float value : new float[]{Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> state.release(value, MIN_VELOCITY, MIN_DISTANCE, WIDTH));
            assertThrows(IllegalArgumentException.class,
                    () -> state.release(0f, value, MIN_DISTANCE, WIDTH));
            assertThrows(IllegalArgumentException.class,
                    () -> state.release(0f, MIN_VELOCITY, value, WIDTH));
        }
        for (float velocity : new float[]{-1f, 0f})
            assertThrows(IllegalArgumentException.class,
                    () -> state.release(0f, velocity, MIN_DISTANCE, WIDTH));
        assertThrows(IllegalArgumentException.class,
                () -> state.release(0f, MIN_VELOCITY, -1f, WIDTH));
        for (int width : new int[]{Integer.MIN_VALUE, -1, 0})
            assertThrows(IllegalArgumentException.class,
                    () -> state.release(0f, MIN_VELOCITY, MIN_DISTANCE, width));
        assertEquals(TabNavigationState.Phase.DRAGGING, state.phase());
        assertEquals(2, state.page());
        assertEquals(1, state.target());
        assertEquals(150f, state.offset(), 0f);
        assertFalse(state.commits());
        assertTrue(release(state, 0f));
        state.finish();
        assertIdle(state, 1);
    }

    @Test public void invalidInputsAreStillRejectedOutsideDraggingPhase() {
        TabNavigationState state = new TabNavigationState();
        assertThrows(IllegalArgumentException.class, () -> state.drag(Float.NaN, WIDTH));
        assertThrows(IllegalArgumentException.class,
                () -> state.release(0f, MIN_VELOCITY, MIN_DISTANCE, 0));
        assertIdle(state, 0);
    }

    private static TabNavigationState dragging(int page, float offset) {
        TabNavigationState state = new TabNavigationState();
        state.select(page);
        state.beginDrag();
        state.drag(offset, WIDTH);
        return state;
    }

    private static boolean release(TabNavigationState state, float velocity) {
        return state.release(velocity, MIN_VELOCITY, MIN_DISTANCE, WIDTH);
    }

    private static void assertDraggingAtOrigin(TabNavigationState state, int page) {
        assertEquals(page, state.page());
        assertEquals(page, state.target());
        assertEquals(TabNavigationState.Phase.DRAGGING, state.phase());
        assertEquals(0f, state.offset(), 0f);
        assertFalse(state.commits());
    }

    private static void assertIdle(TabNavigationState state, int page) {
        assertEquals(page, state.page());
        assertEquals(page, state.target());
        assertEquals(TabNavigationState.Phase.IDLE, state.phase());
        assertEquals(0f, state.offset(), 0f);
        assertFalse(state.commits());
    }
}
