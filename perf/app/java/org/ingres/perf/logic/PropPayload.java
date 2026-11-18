package org.ingres.perf.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the system-property payload consumed by init. Always contains every key;
 * empty target fields are filled from the base snapshot. "*_floor" is the lowest kernel
 * frequency so init can write floor -> max -> min without the kernel rejecting min>max.
 */
public final class PropPayload {
    public static final String PREFIX = "sys.ingres.perf.";
    public static final String APPLY = PREFIX + "apply";
    private static final int[] POLICY = {0, 4, 7};
    private static final List<String> KEYS = buildKeys();

    private PropPayload() {}

    private static List<String> buildKeys() {
        List<String> k = new ArrayList<>();
        for (int p : POLICY) {
            k.add(PREFIX + "cpu" + p + "_min");
            k.add(PREFIX + "cpu" + p + "_max");
            k.add(PREFIX + "cpu" + p + "_gov");
            k.add(PREFIX + "cpu" + p + "_floor");
        }
        k.add(PREFIX + "gpu_min");
        k.add(PREFIX + "gpu_max");
        k.add(PREFIX + "gpu_floor");
        k.add(APPLY);
        return Collections.unmodifiableList(k);
    }

    /** All property keys, with {@link #APPLY} last. */
    public static List<String> keys() { return KEYS; }

    public static Map<String, String> build(Profile target, Snapshot base, Limits limits, int seq) {
        Profile t = target == null ? Profile.empty() : target;
        Map<String, String> m = new LinkedHashMap<>();
        for (int c = 0; c < Profile.CLUSTERS; c++) {
            String p = PREFIX + "cpu" + POLICY[c] + "_";
            int min = t.minKhz(c) > 0 ? t.minKhz(c) : base.minKhz(c);
            int max = t.maxKhz(c) > 0 ? t.maxKhz(c) : base.maxKhz(c);
            String gov = t.governor(c) != null ? t.governor(c) : base.governor(c);
            int[] freqs = limits.cpuFreqsKhz(c);
            long floor = freqs.length > 0 ? lowest(freqs) : base.minKhz(c);
            m.put(p + "min", num(min));
            m.put(p + "max", num(max));
            m.put(p + "gov", gov == null ? "" : gov);
            m.put(p + "floor", num(floor));
        }
        long gmin = t.gpuMinHz() > 0 ? t.gpuMinHz() : base.gpuMinHz();
        long gmax = t.gpuMaxHz() > 0 ? t.gpuMaxHz() : base.gpuMaxHz();
        long[] g = limits.gpuFreqsHz();
        long gfloor = g.length > 0 ? lowest(g) : base.gpuMinHz();
        m.put(PREFIX + "gpu_min", num(gmin));
        m.put(PREFIX + "gpu_max", num(gmax));
        m.put(PREFIX + "gpu_floor", num(gfloor));
        m.put(APPLY, Integer.toString(seq));
        return m;
    }

    private static String num(long v) { return v > 0 ? Long.toString(v) : ""; }

    private static long lowest(int[] a) {
        long m = Long.MAX_VALUE;
        for (int v : a) if (v < m) m = v;
        return m;
    }

    private static long lowest(long[] a) {
        long m = Long.MAX_VALUE;
        for (long v : a) if (v < m) m = v;
        return m;
    }
}
