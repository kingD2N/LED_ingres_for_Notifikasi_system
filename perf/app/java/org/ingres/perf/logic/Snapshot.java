package org.ingres.perf.logic;

/** Immutable capture of the current kernel values (CPU min/max/governor per cluster, GPU min/max). */
public final class Snapshot {
    private final Profile values;

    private Snapshot(Profile values) { this.values = values; }

    public static Snapshot of(Profile values) {
        return new Snapshot(values == null ? Profile.empty() : values);
    }

    /** Reads current values; unreadable or unparsable nodes become 0 / null. */
    public static Snapshot read(NodeReader r) {
        Profile.Builder b = new Profile.Builder();
        for (int c = 0; c < Profile.CLUSTERS; c++) {
            b.cluster(c, (int) Math.min(Integer.MAX_VALUE, num(r, PerfNodes.cpuMin(c))),
                    (int) Math.min(Integer.MAX_VALUE, num(r, PerfNodes.cpuMax(c))),
                    text(r, PerfNodes.cpuGov(c)));
        }
        b.gpu(num(r, PerfNodes.GPU_MIN), num(r, PerfNodes.GPU_MAX));
        return new Snapshot(b.build());
    }

    private static String text(NodeReader r, String path) {
        try {
            String s = r == null ? null : r.read(path);
            if (s == null) return null;
            s = s.trim();
            return s.isEmpty() ? null : s;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static long num(NodeReader r, String path) {
        String s = text(r, path);
        if (s == null) return 0L;
        try {
            return Math.max(0L, Long.parseLong(s));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public int minKhz(int cluster) { return values.minKhz(cluster); }
    public int maxKhz(int cluster) { return values.maxKhz(cluster); }
    public String governor(int cluster) { return values.governor(cluster); }
    public long gpuMinHz() { return values.gpuMinHz(); }
    public long gpuMaxHz() { return values.gpuMaxHz(); }

    /** The captured values as a Profile (for persisting with {@link ProfileCodec}). */
    public Profile values() { return values; }

    /**
     * False when a node the kernel advertises could not be read: for a cluster with a known
     * frequency list min or max is 0, or its governor is missing while a governor list is
     * known; same for the GPU (min/max) when its frequency list is known. Tuning with such a
     * snapshot would leave nothing to restore, so callers abort instead. Null limits = usable.
     */
    public boolean isUsableFor(Limits limits) {
        if (limits == null) return true;
        for (int c = 0; c < Profile.CLUSTERS; c++) {
            boolean known = limits.cpuFreqsKhz(c).length > 0;
            if (known && (minKhz(c) == 0 || maxKhz(c) == 0)) return false;
            String g = governor(c);
            if (limits.cpuGovernors(c).length > 0 && (g == null || g.isEmpty())) return false;
        }
        return limits.gpuFreqsHz().length == 0 || (gpuMinHz() > 0 && gpuMaxHz() > 0);
    }
}
