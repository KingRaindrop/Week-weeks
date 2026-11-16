package com.weekweeks.app;

/** UI-thread confined, Android-free ownership for one primary modal. */
final class ModalCoordinator {
    enum State { IDLE, OPENING, OPEN, CLOSING }
    static final class Token {
        final long id;
        final String key;
        private Token(long id, String key) { this.id = id; this.key = key; }
    }
    private State state = State.IDLE;
    private Token owner;
    private long sequence;
    private Runnable next;
    private boolean handoff;
    private boolean destroyed;
    private Runnable idleListener;

    State state() { return state; }
    /** Called synchronously whenever ownership is released with no successor; never after destroy(). */
    void setIdleListener(Runnable listener) { idleListener = listener; }
    private void becomeIdle() {
        owner = null;
        state = State.IDLE;
        Runnable listener = idleListener;
        if (listener != null && !destroyed) listener.run();
    }
    boolean canOpen() { return !destroyed && (state == State.IDLE || handoff); }
    Token tryAcquire(String key) {
        if (!canOpen()) return null;
        handoff = false;
        owner = new Token(++sequence, key);
        state = State.OPENING;
        return owner;
    }
    boolean owns(Token token) { return token != null && token == owner && !destroyed; }
    void opened(Token token) { if (owns(token) && state == State.OPENING) state = State.OPEN; }
    void closing(Token token) { if (owns(token)) state = State.CLOSING; }
    boolean switchAfterDismiss(Token token, Runnable action) {
        if (!owns(token) || state == State.CLOSING || action == null) return false;
        next = action;
        state = State.CLOSING;
        return true;
    }
    void dismissed(Token token) {
        if (!owns(token)) return;
        Runnable action = next;
        next = null;
        if (action == null) {
            becomeIdle();
            return;
        }
        // No queued UI event can enter between dismissal and the reserved successor.
        // Keep ownership CLOSING until that successor synchronously claims its token.
        handoff = true;
        try { action.run(); }
        finally {
            handoff = false;
            if (owner == token) becomeIdle();
        }
    }
    void destroy() {
        destroyed = true;
        idleListener = null;
        handoff = false;
        next = null;
        owner = null;
        state = State.IDLE;
    }
}
