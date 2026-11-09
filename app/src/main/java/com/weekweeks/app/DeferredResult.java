package com.weekweeks.app;

/** One result per request; stale/destroyed owners never receive background results. */
final class DeferredResult<T> {
    private long generation;
    private boolean resumed;
    private boolean destroyed;
    private T pending;

    synchronized long begin() {
        if (destroyed) {
            throw new IllegalStateException("Owner already destroyed");
        }
        pending = null;
        return ++generation;
    }

    synchronized boolean isCurrent(long token) {
        return !destroyed && token == generation;
    }

    synchronized boolean offer(long token, T result) {
        if (!isCurrent(token)) {
            return false;
        }
        pending = result;
        return true;
    }

    synchronized void setResumed(boolean value) {
        resumed = value && !destroyed;
    }

    synchronized T take() {
        if (!resumed || destroyed) {
            return null;
        }
        T result = pending;
        pending = null;
        return result;
    }

    synchronized void cancel() {
        generation++;
        pending = null;
    }

    synchronized void destroy() {
        cancel();
        resumed = false;
        destroyed = true;
    }
}
