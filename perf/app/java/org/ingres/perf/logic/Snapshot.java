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
}
