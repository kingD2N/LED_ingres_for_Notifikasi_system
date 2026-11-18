package org.ingres.perf.logic;

/** Delayed-task abstraction (Handler on Android, fake in host tests). */
public interface Scheduler {
    void post(Runnable r, long delayMs);

    void cancel();
}
