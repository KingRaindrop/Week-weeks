package com.weekweeks.app;

/** UI-thread confined, Android-free state for cyclic navigation between the three main pages. */
final class TabNavigationState {
    static final int WEEK = 0, LIST = 1, MINE = 2;
    enum Phase { IDLE, DRAGGING, SETTLING }

    private int page = WEEK;
    private int target = WEEK;
    private Phase phase = Phase.IDLE;
    private float offset;
    private boolean commits;

    int page() { return page; }
    /** The visible neighbour, retained during a rebound until finish() or cancel(). */
    int target() { return target; }
    Phase phase() { return phase; }
    float offset() { return offset; }
    boolean commits() { return commits; }

    /** Positive direction advances WEEK -> LIST -> MINE -> WEEK; negative goes backward. */
    static int adjacent(int page, int direction) {
        requirePage(page);
        if (direction != -1 && direction != 1)
            throw new IllegalArgumentException("direction must be -1 or 1");
        return (page + direction + 3) % 3;
    }

    void select(int page) {
        requirePage(page);
        this.page = page;
        cancel();
    }

    /** An interrupted animation never changes the starting page of the new gesture. */
    void beginDrag() {
        cancel();
        phase = Phase.DRAGGING;
    }

    /** Offset is absolute displacement from the gesture start, not an incremental delta. */
    void drag(float offset, int width) {
        requireFinite(offset, "offset");
        requireWidth(width);
        if (phase != Phase.DRAGGING) return;
        updateOffset(offset, width);
    }

    /**
     * Freeze the release decision for the settle animation. A qualifying reverse fling
     * overrides distance, so pulling back toward the original page does not advance it.
     * Invalid parameters throw without mutation; repeated/out-of-phase releases are ignored.
     */
    boolean release(float velocityX, float minFlingVelocity, float minFlingDistance, int width) {
        requireFinite(velocityX, "velocityX");
        requireFinite(minFlingVelocity, "minFlingVelocity");
        requireFinite(minFlingDistance, "minFlingDistance");
        if (minFlingVelocity <= 0f)
            throw new IllegalArgumentException("minFlingVelocity must be positive");
        if (minFlingDistance < 0f)
            throw new IllegalArgumentException("minFlingDistance must not be negative");
        requireWidth(width);
        if (phase != Phase.DRAGGING) return false;

        // Layout may have changed since the last move; the new width is authoritative.
        updateOffset(offset, width);
        float distance = Math.abs(offset);
        boolean fast = Math.abs(velocityX) >= minFlingVelocity;
        boolean sameDirection = offset != 0f && velocityX != 0f
                && (offset < 0f) == (velocityX < 0f);
        boolean reverseFling = fast && offset != 0f && !sameDirection;
        commits = offset != 0f && !reverseFling
                && (distance >= width * 0.25f
                    || (fast && sameDirection && distance >= minFlingDistance));
        phase = Phase.SETTLING;
        return commits;
    }

    /** Complete at most one released gesture; stale callbacks cannot end a new drag. */
    void finish() {
        if (phase != Phase.SETTLING) return;
        if (commits) page = target;
        cancel();
    }

    /** Discard both an active drag and an uncompleted release, preserving the original page. */
    void cancel() {
        target = page;
        offset = 0f;
        commits = false;
        phase = Phase.IDLE;
    }

    private void updateOffset(float value, int width) {
        offset = Math.max(-((float) width), Math.min((float) width, value));
        if (offset == 0f) offset = 0f; // Keep the neutral state free of negative zero.
        target = offset == 0f ? page : adjacent(page, offset < 0f ? 1 : -1);
    }

    private static void requirePage(int page) {
        if (page < WEEK || page > MINE)
            throw new IllegalArgumentException("page must be WEEK, LIST or MINE");
    }

    private static void requireWidth(int width) {
        if (width <= 0) throw new IllegalArgumentException("width must be positive");
    }

    private static void requireFinite(float value, String name) {
        if (Float.isNaN(value) || Float.isInfinite(value))
            throw new IllegalArgumentException(name + " must be finite");
    }
}
