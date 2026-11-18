package org.ingres.perf.logic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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

    // ---------------- Task 2: payload, state machine, debouncer ----------------

    static final int[] POLICY = {0, 4, 7};

    /** Snapshot of "current kernel state": min=lowest freq, max=highest, gov=walt. */
    static Snapshot sampleSnapshot() {
        Map<String, String> m = new HashMap<>();
        m.put(PerfNodes.cpuMin(0), "300000\n");
        m.put(PerfNodes.cpuMax(0), "1804800\n");
        m.put(PerfNodes.cpuGov(0), "walt\n");
        m.put(PerfNodes.cpuMin(1), "710400");
        m.put(PerfNodes.cpuMax(1), "2419200");
        m.put(PerfNodes.cpuGov(1), "walt");
        m.put(PerfNodes.cpuMin(2), "844800");
        m.put(PerfNodes.cpuMax(2), "2995200");
        m.put(PerfNodes.cpuGov(2), "walt");
        m.put(PerfNodes.GPU_MIN, "285000000\n");
        m.put(PerfNodes.GPU_MAX, "999000000\n");
        return Snapshot.read(fake(m));
    }

    static Map<String, String> restorePayload(Snapshot s, Limits l, int seq) {
        return PropPayload.build(Profile.empty(), s, l, seq);
    }

    static void snapshotReadsNodes() {
        Snapshot s = sampleSnapshot();
        Snapshot e = Snapshot.read(fake(new HashMap<String, String>()));
        check("snapshotReadsNodes", s.minKhz(0) == 300000 && s.maxKhz(0) == 1804800
                && "walt".equals(s.governor(0)) && s.maxKhz(1) == 2419200 && s.minKhz(2) == 844800
                && s.gpuMinHz() == 285000000L && s.gpuMaxHz() == 999000000L
                && e.minKhz(0) == 0 && e.governor(0) == null && e.gpuMaxHz() == 0L);
    }

    static void payloadFillsEmptyFieldsFromSnapshot() {
        Profile t = new Profile.Builder().cluster(1, 0, 1555200, null).build();
        Map<String, String> m = PropPayload.build(t, sampleSnapshot(), sampleLimits(), 5);
        check("payloadFillsEmptyFieldsFromSnapshot",
                "1555200".equals(m.get("sys.ingres.perf.cpu4_max"))
                && "710400".equals(m.get("sys.ingres.perf.cpu4_min"))
                && "walt".equals(m.get("sys.ingres.perf.cpu4_gov"))
                && "300000".equals(m.get("sys.ingres.perf.cpu0_min"))
                && "1804800".equals(m.get("sys.ingres.perf.cpu0_max"))
                && "walt".equals(m.get("sys.ingres.perf.cpu0_gov"))
                && "844800".equals(m.get("sys.ingres.perf.cpu7_min"))
                && "2995200".equals(m.get("sys.ingres.perf.cpu7_max"))
                && "285000000".equals(m.get("sys.ingres.perf.gpu_min"))
                && "999000000".equals(m.get("sys.ingres.perf.gpu_max"))
                && m.size() == 16);
        // target values win over snapshot
        Profile t2 = new Profile.Builder().cluster(0, 691200, 1804800, "schedutil")
                .gpu(285000000L, 818000000L).build();
        Map<String, String> m2 = PropPayload.build(t2, sampleSnapshot(), sampleLimits(), 1);
        check("payloadTargetOverridesSnapshot",
                "691200".equals(m2.get("sys.ingres.perf.cpu0_min"))
                && "schedutil".equals(m2.get("sys.ingres.perf.cpu0_gov"))
                && "818000000".equals(m2.get("sys.ingres.perf.gpu_max")));
        // still empty after snapshot fill -> "" (not "0")
        Map<String, String> m3 = PropPayload.build(Profile.empty(),
                Snapshot.read(fake(new HashMap<String, String>())), sampleLimits(), 2);
        check("payloadUnknownFieldsAreEmptyString",
                "".equals(m3.get("sys.ingres.perf.cpu0_min"))
                && "".equals(m3.get("sys.ingres.perf.cpu7_gov"))
                && "".equals(m3.get("sys.ingres.perf.gpu_max")));
    }

    static void payloadHasFloorAndApplySeq() {
        Map<String, String> m = PropPayload.build(Profile.empty(), sampleSnapshot(), sampleLimits(), 42);
        check("payloadHasFloorAndApplySeq",
                "300000".equals(m.get("sys.ingres.perf.cpu0_floor"))
                && "710400".equals(m.get("sys.ingres.perf.cpu4_floor"))
                && "844800".equals(m.get("sys.ingres.perf.cpu7_floor"))
                && "285000000".equals(m.get("sys.ingres.perf.gpu_floor"))
                && "42".equals(m.get("sys.ingres.perf.apply")));
        List<String> keys = PropPayload.keys();
        check("payloadKeysApplyLastAndComplete",
                keys.size() == 16 && "sys.ingres.perf.apply".equals(keys.get(15))
                && keys.contains("sys.ingres.perf.cpu7_floor") && keys.contains("sys.ingres.perf.gpu_floor")
                && new ArrayList<String>(m.keySet()).equals(keys));
        // empty kernel list -> floor from snapshot value
        Limits none = Limits.read(fake(new HashMap<String, String>()));
        Map<String, String> n = PropPayload.build(Profile.empty(), sampleSnapshot(), none, 3);
        check("payloadFloorFallsBackToSnapshotMin",
                "300000".equals(n.get("sys.ingres.perf.cpu0_floor"))
                && "285000000".equals(n.get("sys.ingres.perf.gpu_floor")));
    }

    /** Fixture: state machine with recording sink, counting snapshot supplier. */
    static final class Fx {
        final List<Map<String, String>> writes = new ArrayList<>();
        int snapCalls = 0;
        int failNext = 0; // next N Sink.write calls throw (nothing recorded)
        Snapshot snap = sampleSnapshot();
        final Limits limits = sampleLimits();
        java.util.Set<String> installed = new java.util.HashSet<>(Arrays.asList("a", "b", "x", "d"));
        final PerfStateMachine sm;

        Fx() {
            sm = new PerfStateMachine(
                    new Sink() { @Override public void write(Map<String, String> p) {
                            if (failNext > 0) { failNext--; throw new IllegalStateException("sink down"); }
                            writes.add(p);
                        } },
                    new java.util.function.Supplier<Snapshot>() {
                        @Override public Snapshot get() { snapCalls++; return snap; } },
                    new java.util.function.Supplier<Limits>() {
                        @Override public Limits get() { return limits; } },
                    new java.util.function.Predicate<String>() {
                        @Override public boolean test(String s) { return installed.contains(s); } });
        }
    }

    /** Write i, or an empty map when absent (so check() fails instead of the runner throwing). */
    static Map<String, String> at(Fx f, int i) {
        return i < f.writes.size() ? f.writes.get(i) : new LinkedHashMap<String, String>();
    }

    static Profile bigMax(int khz) { return new Profile.Builder().cluster(1, 0, khz, null).build(); }

    static Map<String, Profile> pm(Object... kv) {
        Map<String, Profile> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Profile) kv[i + 1]);
        return m;
    }

    static void smAppliesAppProfileOnForeground() {
        Fx f = new Fx();
        f.sm.onBootCompleted();
        f.sm.setProfiles(pm("a", bigMax(1555200)));
        check("smNoWriteWithoutProfile", f.writes.isEmpty());
        f.sm.onForeground("a");
        Map<String, String> w = f.writes.isEmpty() ? null : f.writes.get(0);
        check("smAppliesAppProfileOnForeground", f.writes.size() == 1 && w != null
                && "1555200".equals(w.get("sys.ingres.perf.cpu4_max"))
                && "710400".equals(w.get("sys.ingres.perf.cpu4_min"))
                && "1".equals(w.get("sys.ingres.perf.apply"))
                && w.equals(PropPayload.build(bigMax(1555200), f.snap, f.limits, 1)));
        f.sm.onForeground("a"); // same app again: no duplicate write
        check("smSameAppNoDuplicateWrite", f.writes.size() == 1);
    }

    static void smTakesSnapshotOnlyOnce() {
        Fx f = new Fx();
        f.sm.onBootCompleted();
        f.sm.setProfiles(pm("a", bigMax(1555200), "b", bigMax(710400)));
        f.sm.onForeground("a");
        f.sm.onForeground("b");
        f.sm.onForeground("a");
        check("smTakesSnapshotOnlyOnce", f.snapCalls == 1 && f.writes.size() == 3
                && "1555200".equals(f.writes.get(0).get("sys.ingres.perf.cpu4_max"))
                && "710400".equals(f.writes.get(1).get("sys.ingres.perf.cpu4_max"))
                && "1555200".equals(f.writes.get(2).get("sys.ingres.perf.cpu4_max"))
                && "2".equals(f.writes.get(1).get("sys.ingres.perf.apply"))
                && "3".equals(f.writes.get(2).get("sys.ingres.perf.apply")));
    }

    static void smRestoresSnapshotWhenLeavingToUnprofiledApp() {
        Fx f = new Fx();
        f.sm.onBootCompleted();
        f.sm.setProfiles(pm("a", bigMax(1555200)));
        f.sm.onForeground("a");
        f.sm.onForeground("x");
        check("smRestoresSnapshotWhenLeavingToUnprofiledApp", f.writes.size() == 2
                && f.writes.get(1).equals(restorePayload(f.snap, f.limits, 2))
                && "2419200".equals(f.writes.get(1).get("sys.ingres.perf.cpu4_max")));
        f.sm.onForeground("x"); // already restored: nothing
        check("smNoRestoreTwice", f.writes.size() == 2);
        // snapshot was dropped: a new tuned session takes a fresh one
        f.sm.onForeground("a");
        check("smSnapshotRetakenAfterRestore", f.snapCalls == 2 && f.writes.size() == 3);
    }

    static void smFallsBackToDefaultProfile() {
        Fx f = new Fx();
        f.sm.onBootCompleted();
        Profile d = new Profile.Builder().cluster(0, 0, 1804800, "schedutil").build();
        f.sm.setProfiles(pm("*", d, "a", Profile.empty()));
        // boot applied Default? setProfiles after boot re-evaluates: Default applied once
        int base = f.writes.size();
        f.sm.onForeground("x");
        f.sm.onForeground("a"); // empty stored profile also falls back to Default
        Map<String, String> w = at(f, 0);
        check("smFallsBackToDefaultProfile", base == 1 && f.writes.size() == 1
                && "schedutil".equals(w.get("sys.ingres.perf.cpu0_gov"))
                && w.equals(PropPayload.build(d, f.snap, f.limits, 1))
                && !f.sm.profiles().containsKey("a") && f.sm.profiles().containsKey("*"));
    }

    static void smRestoresToDefaultWhenLeavingAppProfile() {
        Fx f = new Fx();
        Profile d = new Profile.Builder().cluster(0, 0, 1804800, "schedutil").build();
        f.sm.setProfiles(pm("*", d, "a", bigMax(1555200)));
        check("smNoWriteBeforeBoot", f.writes.isEmpty());
        f.sm.onForeground("a");
        check("smNoWriteForegroundBeforeBoot", f.writes.isEmpty());
        f.sm.onBootCompleted(); // lastPkg a -> app profile
        f.sm.onForeground("x");  // leave to unprofiled -> Default (not raw snapshot)
        check("smRestoresToDefaultWhenLeavingAppProfile", f.snapCalls == 1 && f.writes.size() == 2
                && "1555200".equals(f.writes.get(0).get("sys.ingres.perf.cpu4_max"))
                && f.writes.get(1).equals(PropPayload.build(d, f.snap, f.limits, 2))
                && "schedutil".equals(f.writes.get(1).get("sys.ingres.perf.cpu0_gov"))
                && "2419200".equals(f.writes.get(1).get("sys.ingres.perf.cpu4_max")));
        // Default removed while tuned -> restore the pre-tuning snapshot
        f.sm.setProfiles(pm("a", bigMax(1555200)));
        check("smDefaultRemovedRestoresSnapshot", f.writes.size() == 3
                && f.writes.get(2).equals(restorePayload(f.snap, f.limits, 3)));
    }

    static void smScreenOffRestoresBase() {
        // Default exists -> base is Default (snapshot kept)
        Fx f = new Fx();
        f.sm.onBootCompleted();
        Profile d = new Profile.Builder().cluster(0, 0, 691200, null).build();
        f.sm.setProfiles(pm("*", d, "a", bigMax(1555200)));
        f.sm.onForeground("a");
        f.sm.onScreenOff();
        check("smScreenOffRestoresBase", f.writes.size() == 3
                && at(f, 2).equals(PropPayload.build(d, f.snap, f.limits, 3))
                && "691200".equals(at(f, 2).get("sys.ingres.perf.cpu0_max"))
                && "2419200".equals(at(f, 2).get("sys.ingres.perf.cpu4_max")));
        f.sm.onScreenOff();
        check("smScreenOffTwiceNoWrite", f.writes.size() == 3);
        f.sm.onForeground("a"); // screen back on
        check("smForegroundAfterScreenOffReapplies", f.writes.size() == 4 && f.snapCalls == 1
                && "1555200".equals(at(f, 3).get("sys.ingres.perf.cpu4_max"))
                && "1804800".equals(at(f, 3).get("sys.ingres.perf.cpu0_max")));
    }

    static void smScreenOffWithoutDefaultRestoresSnapshot() {
        Fx f = new Fx();
        f.sm.onBootCompleted();
        f.sm.setProfiles(pm("a", bigMax(1555200)));
        f.sm.onForeground("a");
        f.sm.onScreenOff();
        check("smScreenOffWithoutDefaultRestoresSnapshot", f.writes.size() == 2
                && at(f, 1).equals(restorePayload(f.snap, f.limits, 2))
                && "2419200".equals(at(f, 1).get("sys.ingres.perf.cpu4_max")));
        f.sm.onScreenOff();
        check("smScreenOffWithoutDefaultTwiceNoWrite", f.writes.size() == 2);
        f.sm.onForeground("a");
        check("smScreenOnAfterRestoreRetakesSnapshot", f.writes.size() == 3 && f.snapCalls == 2);
    }

    static void smApplyFailureKeepsStateRetryable() {
        Fx f = new Fx();
        f.sm.onBootCompleted();
        f.sm.setProfiles(pm("a", bigMax(1555200)));
        f.failNext = 1;
        boolean threw = false;
        try { f.sm.onForeground("a"); } catch (RuntimeException e) { threw = true; }
        check("smApplyFailurePropagates", threw && f.writes.isEmpty());
        // retry of the same call must write again (dedupe must not block it)
        f.sm.onForeground("a");
        check("smApplyFailureThenRetryWrites", f.writes.size() == 1
                && at(f, 0).equals(PropPayload.build(bigMax(1555200), f.snap, f.limits, 2)));
        // a failed first apply must not leave a half-committed "tuned" snapshot:
        // leaving to an unprofiled app now restores the original values exactly once
        f.sm.onForeground("x");
        check("smApplyFailureNoHalfCommit", f.writes.size() == 2
                && at(f, 1).equals(restorePayload(f.snap, f.limits, 3)));
    }

    static void smRestoreFailureKeepsOriginalSnapshot() {
        Fx f = new Fx();
        f.sm.onBootCompleted();
        f.sm.setProfiles(pm("a", bigMax(1555200)));
        f.sm.onForeground("a");
        Snapshot original = f.snap;
        // kernel now shows tuned values; a lost snapshot would capture these as the base
        f.snap = Snapshot.of(bigMax(1555200));
        f.failNext = 1;
        boolean threw = false;
        try { f.sm.onForeground("x"); } catch (RuntimeException e) { threw = true; }
        check("smRestoreFailurePropagates", threw && f.writes.size() == 1);
        f.sm.onForeground("x"); // retry
        check("smRestoreFailureThenRetryUsesOriginalSnapshot", f.writes.size() == 2
                && at(f, 1).equals(restorePayload(original, f.limits, 3))
                && "2419200".equals(at(f, 1).get("sys.ingres.perf.cpu4_max"))
                && f.snapCalls == 1);
        // switch failure A -> B keeps A in effect; retry writes B
        Fx g = new Fx();
        g.sm.onBootCompleted();
        g.sm.setProfiles(pm("a", bigMax(1555200), "b", bigMax(710400)));
        g.sm.onForeground("a");
        g.failNext = 1;
        try { g.sm.onForeground("b"); } catch (RuntimeException e) { /* expected */ }
        g.sm.onForeground("b");
        check("smSwitchFailureThenRetryWrites", g.writes.size() == 2 && g.snapCalls == 1
                && "710400".equals(at(g, 1).get("sys.ingres.perf.cpu4_max")));
    }

    static void smDisabledRestoresAndIgnores() {
        Fx f = new Fx();
        f.sm.onBootCompleted();
        Profile d = new Profile.Builder().cluster(0, 0, 691200, null).build();
        f.sm.setProfiles(pm("*", d, "a", bigMax(1555200)));
        f.sm.onForeground("a");
        int n = f.writes.size();
        f.sm.setEnabled(false);
        check("smDisabledRestoresAndIgnores",
                f.writes.size() == n + 1
                && f.writes.get(n).equals(restorePayload(f.snap, f.limits, n + 1)));
        f.sm.onForeground("a");
        f.sm.onScreenOff();
        f.sm.setProfiles(pm("*", d));
        f.sm.onForeground("x");
        check("smDisabledIgnoresEvents", f.writes.size() == n + 1);
        f.sm.setEnabled(true); // re-evaluates last foreground (x, unprofiled -> Default)
        check("smEnableReevaluatesDefault", f.writes.size() == n + 2
                && f.writes.get(n + 1).equals(PropPayload.build(d, f.snap, f.limits, n + 2)));
    }

    static void smBootAppliesDefaultOnlyAfterBootCompleted() {
        Fx f = new Fx();
        Profile d = new Profile.Builder().cluster(0, 0, 691200, null).build();
        f.sm.setProfiles(pm("*", d));
        f.sm.onForeground("x");
        f.sm.setEnabled(true);
        check("smNoWriteBeforeBootCompleted", f.writes.isEmpty() && f.snapCalls == 0);
        f.sm.onBootCompleted();
        check("smBootAppliesDefaultOnlyAfterBootCompleted", f.writes.size() == 1
                && f.writes.get(0).equals(PropPayload.build(d, f.snap, f.limits, 1)));
        f.sm.onBootCompleted();
        check("smBootCompletedTwiceNoDoubleApply", f.writes.size() == 1);
        // no Default -> boot writes nothing
        Fx g = new Fx();
        g.sm.setProfiles(pm("a", bigMax(1555200)));
        g.sm.onBootCompleted();
        check("smBootWithoutDefaultWritesNothing", g.writes.isEmpty() && g.snapCalls == 0);
    }

    static void smInvalidValuesAreDroppedAtApplyTime() {
        Fx f = new Fx();
        f.sm.onBootCompleted();
        // 1234 kHz is not in the kernel list; 1555200 is
        Profile p = new Profile.Builder().cluster(1, 1234, 1555200, "bogus").build();
        f.sm.setProfiles(pm("a", p));
        f.sm.onForeground("a");
        Map<String, String> w = at(f, 0);
        check("smInvalidValuesAreDroppedAtApplyTime",
                "1555200".equals(w.get("sys.ingres.perf.cpu4_max"))
                && "710400".equals(w.get("sys.ingres.perf.cpu4_min"))
                && "walt".equals(w.get("sys.ingres.perf.cpu4_gov")));
        // profile entirely invalid -> nothing to apply, nothing written
        Fx g = new Fx();
        g.sm.onBootCompleted();
        g.sm.setProfiles(pm("a", bigMax(777)));
        g.sm.onForeground("a");
        check("smAllInvalidProfileWritesNothing", g.writes.isEmpty() && g.snapCalls == 0);
    }

    static void smDropsUninstalledPackages() {
        Fx f = new Fx();
        Profile d = bigMax(710400);
        f.sm.setProfiles(pm("*", d, "a", bigMax(1555200), "gone", bigMax(1555200)));
        Map<String, Profile> got = f.sm.profiles();
        check("smDropsUninstalledPackages", got.size() == 2 && got.containsKey("a")
                && got.containsKey("*") && !got.containsKey("gone"));
        f.installed.remove("a"); // uninstalled later: profiles() is live
        check("smDropsPackageUninstalledLater", f.sm.profiles().size() == 1
                && f.sm.profiles().containsKey("*"));
    }

    static void debouncerLastWins() {
        final List<Runnable> pending = new ArrayList<>();
        final int[] cancels = {0};
        final long[] lastDelay = {-1};
        Scheduler sch = new Scheduler() {
            @Override public void post(Runnable r, long d) { pending.add(r); lastDelay[0] = d; }
            @Override public void cancel() { cancels[0]++; pending.clear(); }
        };
        final List<String> got = new ArrayList<>();
        Debouncer db = new Debouncer(sch, 300L, new java.util.function.Consumer<String>() {
            @Override public void accept(String s) { got.add(s); } });
        db.submit("a");
        db.submit("b");
        db.submit("c");
        boolean noneYet = got.isEmpty() && pending.size() == 1;
        for (Runnable r : new ArrayList<>(pending)) r.run();
        check("debouncerLastWins", noneYet && got.equals(Arrays.asList("c"))
                && cancels[0] == 3 && lastDelay[0] == 300L);
        pending.clear();
        db.submit("d");
        for (Runnable r : new ArrayList<>(pending)) r.run();
        check("debouncerFiresAgainAfterSettling", got.equals(Arrays.asList("c", "d")));
    }

    static void debouncerStaleRunnableNeverDeliversNullOrDuplicate() {
        // cancel() cannot stop a runnable that already started: model by never removing posted ones
        final List<Runnable> posted = new ArrayList<>();
        Scheduler sch = new Scheduler() {
            @Override public void post(Runnable r, long d) { posted.add(r); }
            @Override public void cancel() { /* too late for already-started runnables */ }
        };
        final List<String> got = new ArrayList<>();
        Debouncer db = new Debouncer(sch, 300L, new java.util.function.Consumer<String>() {
            @Override public void accept(String s) { got.add(s); } });
        db.submit("a");
        Runnable r1 = posted.get(0);
        db.submit("b");
        Runnable r2 = posted.get(1);
        r1.run(); // stale (superseded) runnable fires first
        r2.run();
        r1.run();
        r2.run(); // duplicate fire
        check("debouncerStaleRunnableNeverDeliversNullOrDuplicate",
                got.equals(Arrays.asList("b")) && !got.contains(null));
        db.submit(null); // null is never delivered
        for (Runnable r : new ArrayList<>(posted)) r.run();
        check("debouncerNullSubmitIgnored", got.equals(Arrays.asList("b")));
    }


    /** Index of the first rc "write <node> ..." line for node (-1 if none), after fromIdx. */
    static int writeIdx(List<String> lines, String node, String valueKey, int fromIdx) {
        for (int i = Math.max(fromIdx, 0); i < lines.size(); i++) {
            String[] w = lines.get(i).trim().split("\\s+");
            if (w.length >= 3 && w[0].equals("write") && w[1].equals(node)
                    && (valueKey == null || w[2].equals("${" + valueKey + "}"))) {
                return i;
            }
        }
        return -1;
    }

    /** Checks the init rc against PropPayload keys, PerfNodes paths and the write order. */
    static void rcMatchesPayloadAndNodes(String rcPath) {
        List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get(rcPath), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            check("rcReadable(" + rcPath + ")", false);
            return;
        }
        int triggers = 0;
        for (String l : lines) {
            if (l.trim().equals("on property:" + PropPayload.APPLY + "=*")) triggers++;
        }
        check("rcHasExactlyOneApplyTrigger", triggers == 1);

        StringBuilder writes = new StringBuilder();
        for (String l : lines) {
            if (l.trim().startsWith("write ")) writes.append(l).append('\n');
        }
        for (String key : PropPayload.keys()) {
            if (key.equals(PropPayload.APPLY)) continue;
            check("rcWritesKey " + key, writes.indexOf("${" + key + "}") >= 0);
        }

        String pre = PropPayload.PREFIX;
        int[] policy = {0, 4, 7};
        int lastGov = -1;
        int firstFreq = Integer.MAX_VALUE;
        for (int c = 0; c < 3; c++) {
            String p = pre + "cpu" + policy[c] + "_";
            int gov = writeIdx(lines, PerfNodes.cpuGov(c), p + "gov", 0);
            check("rcWritesGov cpu" + policy[c], gov >= 0);
            lastGov = Math.max(lastGov, gov);
            int floor = writeIdx(lines, PerfNodes.cpuMin(c), p + "floor", 0);
            int max = writeIdx(lines, PerfNodes.cpuMax(c), p + "max", 0);
            int min = writeIdx(lines, PerfNodes.cpuMin(c), p + "min", 0);
            check("rcOrder cpu" + policy[c] + " floor<max<min",
                    floor >= 0 && max > floor && min > max);
            if (floor >= 0) firstFreq = Math.min(firstFreq, floor);
            if (max >= 0) firstFreq = Math.min(firstFreq, max);
            if (min >= 0) firstFreq = Math.min(firstFreq, min);
        }
        int gfloor = writeIdx(lines, PerfNodes.GPU_MIN, pre + "gpu_floor", 0);
        int gmax = writeIdx(lines, PerfNodes.GPU_MAX, pre + "gpu_max", 0);
        int gmin = writeIdx(lines, PerfNodes.GPU_MIN, pre + "gpu_min", 0);
        check("rcOrder gpu floor<max<min", gfloor >= 0 && gmax > gfloor && gmin > gmax);
        if (gfloor >= 0) firstFreq = Math.min(firstFreq, gfloor);
        check("rcGovernorsBeforeAllFreqWrites", lastGov >= 0 && lastGov < firstFreq);
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
        snapshotReadsNodes();
        payloadFillsEmptyFieldsFromSnapshot();
        payloadHasFloorAndApplySeq();
        smAppliesAppProfileOnForeground();
        smTakesSnapshotOnlyOnce();
        smRestoresSnapshotWhenLeavingToUnprofiledApp();
        smFallsBackToDefaultProfile();
        smRestoresToDefaultWhenLeavingAppProfile();
        smScreenOffRestoresBase();
        smScreenOffWithoutDefaultRestoresSnapshot();
        smApplyFailureKeepsStateRetryable();
        smRestoreFailureKeepsOriginalSnapshot();
        smDisabledRestoresAndIgnores();
        smBootAppliesDefaultOnlyAfterBootCompleted();
        smInvalidValuesAreDroppedAtApplyTime();
        smDropsUninstalledPackages();
        debouncerLastWins();
        debouncerStaleRunnableNeverDeliversNullOrDuplicate();
        if (args.length > 0) rcMatchesPayloadAndNodes(args[0]);
        if (failed > 0) {
            System.out.println(failed + " FAILED of " + total);
            System.exit(1);
        }
        System.out.println("ALL PASS (" + total + ")");
    }
}
