package org.ingres.perf.logic;

import java.util.function.Consumer;

/** Collapses rapid submissions: only the last value is delivered after the quiet delay. */
public final class Debouncer {
    private final Scheduler scheduler;
    private final long delayMs;
    private final Consumer<String> onValue;
    private String pending;

    public Debouncer(Scheduler scheduler, long delayMs, Consumer<String> onValue) {
        this.scheduler = scheduler;
        this.delayMs = delayMs;
        this.onValue = onValue;
    }

    public synchronized void submit(String value) {
        pending = value;
        scheduler.cancel();
        scheduler.post(new Runnable() {
            @Override public void run() { fire(); }
        }, delayMs);
    }

    private void fire() {
        String v;
        synchronized (this) {
            v = pending;
            pending = null;
        }
        onValue.accept(v);
    }
}
