package org.ingres.perf.logic;

import java.util.function.Consumer;

/** Collapses rapid submissions: only the last value is delivered after the quiet delay. */
public final class Debouncer {
    private final Scheduler scheduler;
    private final long delayMs;
    private final Consumer<String> onValue;
    private String pending;
    private long generation;

    public Debouncer(Scheduler scheduler, long delayMs, Consumer<String> onValue) {
        this.scheduler = scheduler;
        this.delayMs = delayMs;
        this.onValue = onValue;
    }

    public synchronized void submit(String value) {
        if (value == null) return; // never deliver null
        pending = value;
        final long g = ++generation;
        scheduler.cancel();
        scheduler.post(new Runnable() {
            @Override public void run() { fire(g); }
        }, delayMs);
    }

    /** Delivers only for the current generation and only once; stale/duplicate runs are no-ops. */
    private void fire(long g) {
        String v;
        synchronized (this) {
            if (g != generation || pending == null) return;
            v = pending;
            pending = null;
        }
        onValue.accept(v);
    }
}
