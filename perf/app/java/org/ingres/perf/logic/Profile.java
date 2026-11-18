package org.ingres.perf.logic;

import java.util.Arrays;

/**
 * Immutable per-app CPU/GPU profile. Clusters: 0=little, 1=big, 2=prime.
 * CPU freqs in kHz, GPU in Hz. 0 / null = not set.
 */
public final class Profile {
    public static final int CLUSTERS = 3;
    private static final Profile EMPTY = new Builder().build();

    private final int[] min;
    private final int[] max;
    private final String[] gov;
    private final long gpuMin;
    private final long gpuMax;

    private Profile(int[] min, int[] max, String[] gov, long gpuMin, long gpuMax) {
        this.min = min;
        this.max = max;
        this.gov = gov;
        this.gpuMin = gpuMin;
        this.gpuMax = gpuMax;
    }

    public static Profile empty() { return EMPTY; }

    public boolean isEmpty() {
        for (int i = 0; i < CLUSTERS; i++) {
            if (min[i] != 0 || max[i] != 0 || gov[i] != null) return false;
        }
        return gpuMin == 0 && gpuMax == 0;
    }

    public int minKhz(int cluster) { return inRange(cluster) ? min[cluster] : 0; }
    public int maxKhz(int cluster) { return inRange(cluster) ? max[cluster] : 0; }
    public String governor(int cluster) { return inRange(cluster) ? gov[cluster] : null; }
    public long gpuMinHz() { return gpuMin; }
    public long gpuMaxHz() { return gpuMax; }

    private static boolean inRange(int cluster) { return cluster >= 0 && cluster < CLUSTERS; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Profile)) return false;
        Profile p = (Profile) o;
        return gpuMin == p.gpuMin && gpuMax == p.gpuMax
                && Arrays.equals(min, p.min) && Arrays.equals(max, p.max)
                && Arrays.equals(gov, p.gov);
    }

    @Override public int hashCode() {
        int h = Arrays.hashCode(min);
        h = 31 * h + Arrays.hashCode(max);
        h = 31 * h + Arrays.hashCode(gov);
        h = 31 * h + Long.hashCode(gpuMin);
        h = 31 * h + Long.hashCode(gpuMax);
        return h;
    }

    @Override public String toString() { return "Profile[" + ProfileCodec.encode(this) + "]"; }

    public static final class Builder {
        private final int[] min = new int[CLUSTERS];
        private final int[] max = new int[CLUSTERS];
        private final String[] gov = new String[CLUSTERS];
        private long gpuMin;
        private long gpuMax;

        public Builder cluster(int cluster, int minKhz, int maxKhz, String governor) {
            if (!inRange(cluster)) throw new IllegalArgumentException("cluster " + cluster);
            min[cluster] = Math.max(0, minKhz);
            max[cluster] = Math.max(0, maxKhz);
            gov[cluster] = (governor == null || governor.isEmpty()) ? null : governor;
            return this;
        }

        public Builder gpu(long minHz, long maxHz) {
            gpuMin = Math.max(0L, minHz);
            gpuMax = Math.max(0L, maxHz);
            return this;
        }

        public Profile build() {
            return new Profile(min.clone(), max.clone(), gov.clone(), gpuMin, gpuMax);
        }
    }
}
