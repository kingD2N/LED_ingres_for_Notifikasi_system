package org.ingres.perf.logic;

import java.util.ArrayList;
import java.util.List;

/** Kernel-advertised limits (available freqs/governors). Empty array = unreadable/unknown. */
public final class Limits {
    private static final int[] NO_INT = new int[0];
    private static final String[] NO_STR = new String[0];
    private static final long[] NO_LONG = new long[0];

    private final int[][] cpuFreqs;
    private final String[][] cpuGovs;
    private final long[] gpuFreqs;

    private Limits(int[][] cpuFreqs, String[][] cpuGovs, long[] gpuFreqs) {
        this.cpuFreqs = cpuFreqs;
        this.cpuGovs = cpuGovs;
        this.gpuFreqs = gpuFreqs;
    }

    public static Limits read(NodeReader r) {
        int[][] f = new int[Profile.CLUSTERS][];
        String[][] g = new String[Profile.CLUSTERS][];
        for (int i = 0; i < Profile.CLUSTERS; i++) {
            long[] l = parseLongs(safeRead(r, PerfNodes.cpuAvailFreqs(i)));
            int[] a = new int[l.length];
            int n = 0;
            for (long v : l) if (v <= Integer.MAX_VALUE) a[n++] = (int) v;
            f[i] = n == a.length ? a : java.util.Arrays.copyOf(a, n);
            g[i] = parseWords(safeRead(r, PerfNodes.cpuAvailGovs(i)));
        }
        return new Limits(f, g, parseLongs(safeRead(r, PerfNodes.GPU_AVAIL)));
    }

    private static String safeRead(NodeReader r, String path) {
        try {
            return r == null ? null : r.read(path);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static long[] parseLongs(String s) {
        if (s == null) return NO_LONG;
        List<Long> out = new ArrayList<>();
        for (String t : s.trim().split("\\s+")) {
            if (t.isEmpty()) continue;
            try {
                long v = Long.parseLong(t);
                if (v > 0) out.add(v);
            } catch (NumberFormatException ignored) {
                // skip junk token
            }
        }
        long[] a = new long[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }

    private static String[] parseWords(String s) {
        if (s == null) return NO_STR;
        List<String> out = new ArrayList<>();
        for (String t : s.trim().split("\\s+")) if (!t.isEmpty()) out.add(t);
        return out.toArray(new String[0]);
    }

    public int[] cpuFreqsKhz(int cluster) {
        return cluster >= 0 && cluster < Profile.CLUSTERS ? cpuFreqs[cluster].clone() : NO_INT;
    }

    public String[] cpuGovernors(int cluster) {
        return cluster >= 0 && cluster < Profile.CLUSTERS ? cpuGovs[cluster].clone() : NO_STR;
    }

    public long[] gpuFreqsHz() { return gpuFreqs.clone(); }
}
