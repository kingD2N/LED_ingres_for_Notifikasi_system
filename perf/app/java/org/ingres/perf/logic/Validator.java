package org.ingres.perf.logic;

/** Drops profile values the kernel does not advertise; empty list drops the field. */
public final class Validator {
    private Validator() {}

    public static Profile validate(Profile p, Limits l) {
        if (p == null) return Profile.empty();
        Profile.Builder b = new Profile.Builder();
        for (int c = 0; c < Profile.CLUSTERS; c++) {
            int[] freqs = l.cpuFreqsKhz(c);
            int min = contains(freqs, p.minKhz(c)) ? p.minKhz(c) : 0;
            int max = contains(freqs, p.maxKhz(c)) ? p.maxKhz(c) : 0;
            if (min > 0 && max > 0 && min > max) min = 0;
            String gov = p.governor(c);
            if (gov != null && !contains(l.cpuGovernors(c), gov)) gov = null;
            b.cluster(c, min, max, gov);
        }
        long[] g = l.gpuFreqsHz();
        long gmin = contains(g, p.gpuMinHz()) ? p.gpuMinHz() : 0;
        long gmax = contains(g, p.gpuMaxHz()) ? p.gpuMaxHz() : 0;
        if (gmin > 0 && gmax > 0 && gmin > gmax) gmin = 0;
        return b.gpu(gmin, gmax).build();
    }

    private static boolean contains(int[] a, int v) {
        if (v <= 0) return false;
        for (int x : a) if (x == v) return true;
        return false;
    }

    private static boolean contains(long[] a, long v) {
        if (v <= 0) return false;
        for (long x : a) if (x == v) return true;
        return false;
    }

    private static boolean contains(String[] a, String v) {
        for (String x : a) if (x.equals(v)) return true;
        return false;
    }
}
