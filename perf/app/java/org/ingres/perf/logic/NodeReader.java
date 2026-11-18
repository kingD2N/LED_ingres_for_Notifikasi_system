package org.ingres.perf.logic;

/** Reads a sysfs/text node. Returns null when the node cannot be read. */
public interface NodeReader {
    String read(String path);
}
