package org.ingres.perf.logic;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Plain host test runner (no JUnit). Run: java -cp out org.ingres.perf.logic.HostTests */
public class HostTests {
    private static int total = 0;
    private static int failed = 0;

    static void check(String name, boolean cond) {
        total++;
        if (!cond) {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    /** Fake NodeReader backed by a map; unknown paths read as null. */
    static NodeReader fake(final Map<String, String> m) {
        return new NodeReader() {
            @Override public String read(String path) { return m.get(path); }
        };
    }

    static Limits sampleLimits() {
        Map<String, String> m = new HashMap<>();
        m.put(PerfNodes.cpuAvailFreqs(0), "300000 691200 1804800\n");
        m.put(PerfNodes.cpuAvailFreqs(1), "710400 1555200 2419200");
        m.put(PerfNodes.cpuAvailFreqs(2), "844800 2995200");
        m.put(PerfNodes.cpuAvailGovs(0), "walt schedutil performance powersave\n");
        m.put(PerfNodes.cpuAvailGovs(1), "walt schedutil performance");
        m.put(PerfNodes.cpuAvailGovs(2), "walt performance");
        m.put(PerfNodes.GPU_AVAIL, "285000000 818000000 818000000 999000000\n");
        return Limits.read(fake(m));
    }

    static void codecRoundTrip() {
        Profile p = new Profile.Builder()
                .cluster(0, 691200, 1804800, "schedutil")
                .cluster(1, 0, 2419200, null)
                .cluster(2, 844800, 0, "performance")
                .gpu(285000000L, 818000000L)
                .build();
        ProfileCodec c = new ProfileCodec();
        Profile q = c.decode(c.encode(p));
        check("codecRoundTrip", p.equals(q) && c.encode(p).equals(c.encode(q))
                && q.minKhz(0) == 691200 && q.maxKhz(1) == 2419200
                && "performance".equals(q.governor(2)) && q.governor(1) == null
                && q.gpuMinHz() == 285000000L && q.gpuMaxHz() == 818000000L);
    }

    static void codecGarbageGivesEmpty() {
        ProfileCodec c = new ProfileCodec();
        check("codecGarbageGivesEmpty",
                c.decode("").isEmpty() && c.decode("x=").isEmpty()
                && c.decode("c0max=abc").isEmpty() && c.decode(null).isEmpty()
                && c.decode(";;;===").isEmpty() && c.decode("c9max=100").isEmpty()
                && c.decode("c0max=-5").isEmpty());
    }

    static void decodeAllSkipsBadLines() {
        ProfileCodec c = new ProfileCodec();
        Profile a = new Profile.Builder().cluster(0, 0, 1804800, null).build();
        Profile d = new Profile.Builder().gpu(0, 818000000L).build();
        Map<String, Profile> in = new LinkedHashMap<>();
        in.put("*", d);
        in.put("com.example.game", a);
        String text = c.encodeAll(in);
        String dirty = "garbage line\n\n=c0max=1\ncom.bad=c0max=abc\n" + text + "nopkg\n";
        Map<String, Profile> out = c.decodeAll(dirty);
        check("decodeAllSkipsBadLines", out.size() == 2
                && a.equals(out.get("com.example.game")) && d.equals(out.get("*"))
                && c.decodeAll(null).isEmpty() && c.decodeAll("").isEmpty());
    }

    static void validatorDropsValueNotInList() {
        Limits l = sampleLimits();
        Profile p = new Profile.Builder()
                .cluster(0, 691200, 9999999, "schedutil")
                .cluster(1, 710400, 2419200, "bogus")
                .gpu(285000000L, 1L)
                .build();
        Profile v = Validator.validate(p, l);
        check("validatorDropsValueNotInList",
                v.maxKhz(0) == 0 && v.minKhz(0) == 691200 && "schedutil".equals(v.governor(0))
                && v.minKhz(1) == 710400 && v.maxKhz(1) == 2419200 && v.governor(1) == null
                && v.gpuMinHz() == 285000000L && v.gpuMaxHz() == 0);
    }

    static void validatorDropsMinWhenAboveMax() {
        Limits l = sampleLimits();
        Profile p = new Profile.Builder()
                .cluster(0, 1804800, 691200, null)
                .gpu(818000000L, 285000000L)
                .build();
        Profile v = Validator.validate(p, l);
        check("validatorDropsMinWhenAboveMax",
                v.minKhz(0) == 0 && v.maxKhz(0) == 691200
                && v.gpuMinHz() == 0 && v.gpuMaxHz() == 285000000L);
    }

    static void limitsEmptyWhenNodeUnreadable() {
        Limits l = Limits.read(new NodeReader() {
            @Override public String read(String path) { return null; }
        });
        boolean ok = l.gpuFreqsHz().length == 0;
        for (int i = 0; i < 3; i++) {
            ok &= l.cpuFreqsKhz(i).length == 0 && l.cpuGovernors(i).length == 0;
        }
        check("limitsEmptyWhenNodeUnreadable", ok);
    }

    static void validatorEmptyLimitsDropsField() {
        Limits l = Limits.read(fake(new HashMap<String, String>()));
        Profile p = new Profile.Builder()
                .cluster(0, 691200, 1804800, "schedutil")
                .gpu(285000000L, 818000000L)
                .build();
        check("validatorEmptyLimitsDropsField", Validator.validate(p, l).isEmpty());
    }

    static void nodePaths() {
        check("nodePaths",
                PerfNodes.cpuMin(0).equals("/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq")
                && PerfNodes.cpuMax(1).equals("/sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq")
                && PerfNodes.cpuGov(2).equals("/sys/devices/system/cpu/cpufreq/policy7/scaling_governor")
                && PerfNodes.cpuAvailFreqs(0).endsWith("policy0/scaling_available_frequencies")
                && PerfNodes.cpuAvailGovs(2).endsWith("policy7/scaling_available_governors")
                && PerfNodes.GPU_MIN.equals("/sys/class/kgsl/kgsl-3d0/devfreq/min_freq")
                && PerfNodes.GPU_MAX.equals("/sys/class/kgsl/kgsl-3d0/devfreq/max_freq")
                && PerfNodes.GPU_AVAIL.equals("/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies"));
    }

    public static void main(String[] args) {
        codecRoundTrip();
        codecGarbageGivesEmpty();
        decodeAllSkipsBadLines();
        validatorDropsValueNotInList();
        validatorDropsMinWhenAboveMax();
        limitsEmptyWhenNodeUnreadable();
        validatorEmptyLimitsDropsField();
        nodePaths();
        if (failed > 0) {
            System.out.println(failed + " FAILED of " + total);
            System.exit(1);
        }
        System.out.println("ALL PASS (" + total + ")");
    }
}
