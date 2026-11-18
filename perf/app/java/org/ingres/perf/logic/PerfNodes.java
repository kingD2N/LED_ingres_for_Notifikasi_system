package org.ingres.perf.logic;

/** Sysfs paths. Cluster index 0=little (policy0), 1=big (policy4), 2=prime (policy7). */
public final class PerfNodes {
    private static final String CPU = "/sys/devices/system/cpu/cpufreq/policy";
    private static final int[] POLICY = {0, 4, 7};
    private static final String KGSL = "/sys/class/kgsl/kgsl-3d0/devfreq/";

    public static final String GPU_MIN = KGSL + "min_freq";
    public static final String GPU_MAX = KGSL + "max_freq";
    public static final String GPU_AVAIL = KGSL + "available_frequencies";

    private PerfNodes() {}

    private static String node(int cluster, String leaf) {
        if (cluster < 0 || cluster >= POLICY.length) {
            throw new IllegalArgumentException("cluster " + cluster);
        }
        return CPU + POLICY[cluster] + "/" + leaf;
    }

    public static String cpuMin(int cluster) { return node(cluster, "scaling_min_freq"); }
    public static String cpuMax(int cluster) { return node(cluster, "scaling_max_freq"); }
    public static String cpuGov(int cluster) { return node(cluster, "scaling_governor"); }
    public static String cpuAvailFreqs(int cluster) { return node(cluster, "scaling_available_frequencies"); }
    public static String cpuAvailGovs(int cluster) { return node(cluster, "scaling_available_governors"); }
}
