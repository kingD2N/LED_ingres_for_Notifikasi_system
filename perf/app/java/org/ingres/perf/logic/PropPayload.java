package org.ingres.perf.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the system-property payload consumed by init. Always contains every key, but only
 * OWNED fields get a value: a field is the target's value when set, else the base snapshot
 * value when the profile currently in effect ({@code owned}) had set it (so it is restored),
 * else "" (init skips it; scaling_min/max_freq read back the combined effective limits, so
 * writing unowned values back would pin boost/thermal/other tools' values as ours).
 * "*_floor" is the lowest kernel frequency, non-empty only when that cluster/GPU min is
 * written, so init can write floor -> max -> min without the kernel rejecting min>max.
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

    /**
     * @param target validated profile to apply (null = empty: restore)
     * @param base   snapshot taken before tuning
     * @param owned  profile currently in effect (null = none): its set fields that the
     *               target leaves unset are restored from {@code base}
     */
    public static Map<String, String> build(Profile target, Snapshot base, Limits limits, int seq,
                                            Profile owned) {
        Profile t = target == null ? Profile.empty() : target;
        Profile o = owned == null ? Profile.empty() : owned;
        Snapshot b = base == null ? Snapshot.of(Profile.empty()) : base;
        Map<String, String> m = new LinkedHashMap<>();
        for (int c = 0; c < Profile.CLUSTERS; c++) {
            String p = PREFIX + "cpu" + POLICY[c] + "_";
            long min = t.minKhz(c) > 0 ? t.minKhz(c) : o.minKhz(c) > 0 ? b.minKhz(c) : 0;
            long max = t.maxKhz(c) > 0 ? t.maxKhz(c) : o.maxKhz(c) > 0 ? b.maxKhz(c) : 0;
            String gov = t.governor(c) != null ? t.governor(c)
                    : o.governor(c) != null ? b.governor(c) : null;
            if (min > 0 && max > 0 && min > max) min = max;
            int[] freqs = limits == null ? new int[0] : limits.cpuFreqsKhz(c);
            long low = freqs.length > 0 ? lowest(freqs) : b.minKhz(c);
            long floor = min > 0 ? Math.min(min, low) : 0;
            m.put(p + "min", num(min));
            m.put(p + "max", num(max));
            m.put(p + "gov", gov == null ? "" : gov);
            m.put(p + "floor", num(floor));
        }
        long gmin = t.gpuMinHz() > 0 ? t.gpuMinHz() : o.gpuMinHz() > 0 ? b.gpuMinHz() : 0;
        long gmax = t.gpuMaxHz() > 0 ? t.gpuMaxHz() : o.gpuMaxHz() > 0 ? b.gpuMaxHz() : 0;
        if (gmin > 0 && gmax > 0 && gmin > gmax) gmin = gmax;
        long[] g = limits == null ? new long[0] : limits.gpuFreqsHz();
        long gfloor = gmin > 0 ? Math.min(gmin, g.length > 0 ? lowest(g) : b.gpuMinHz()) : 0;
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
