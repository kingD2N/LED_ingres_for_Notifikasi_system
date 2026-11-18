package org.ingres.perf.logic;

import java.util.Map;

/** Receives the full property map for every apply/restore. */
public interface Sink {
    void write(Map<String, String> props);
}
