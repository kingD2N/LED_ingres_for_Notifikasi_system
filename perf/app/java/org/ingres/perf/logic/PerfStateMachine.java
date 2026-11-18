package org.ingres.perf.logic;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Decides what to write to the Sink. Fallback on foreground: app profile -> Default ("*")
 * -> restore base snapshot. The snapshot is taken lazily right before the first apply of a
 * tuned session and dropped after restoring. Methods are synchronized (Handler thread and
 * broadcast receivers both call in). Nothing is written before onBootCompleted().
 */
public final class PerfStateMachine {
    public static final String DEFAULT_KEY = "*";

    private final Sink sink;
    private final Supplier<Snapshot> snapshotSupplier;
    private final Supplier<Limits> limitsSupplier;
    private final Predicate<String> isInstalled;

    private Map<String, Profile> profiles = new LinkedHashMap<>();
    private boolean enabled = true;
    private boolean booted;
    private boolean screenOff;   // while true the base (Default or snapshot) is in effect
    private String lastPkg;
    private Snapshot snapshot;      // non-null while a tuned session is active
    private Profile lastApplied;    // validated target currently in effect
    private int seq;

    public PerfStateMachine(Sink sink, Supplier<Snapshot> snapshotSupplier,
                            Supplier<Limits> limitsSupplier, Predicate<String> isInstalled) {
        this.sink = sink;
        this.snapshotSupplier = snapshotSupplier;
        this.limitsSupplier = limitsSupplier;
        this.isInstalled = isInstalled;
    }

    public synchronized void setEnabled(boolean on) {
        if (enabled == on) return;
        enabled = on;
        if (!on) {
            restore();
        } else {
            evaluate();
        }
    }

    public synchronized void setProfiles(Map<String, Profile> p) {
        Map<String, Profile> m = new LinkedHashMap<>();
        if (p != null) {
            for (Map.Entry<String, Profile> e : p.entrySet()) {
                Profile v = e.getValue();
                if (e.getKey() == null || v == null || v.isEmpty()) continue;
                if (!DEFAULT_KEY.equals(e.getKey()) && !isInstalled.test(e.getKey())) continue;
                m.put(e.getKey(), v);
            }
        }
        profiles = m;
        evaluate();
    }

    /** Stored profiles, without packages that are no longer installed. */
    public synchronized Map<String, Profile> profiles() {
        Map<String, Profile> m = new LinkedHashMap<>();
        for (Map.Entry<String, Profile> e : profiles.entrySet()) {
            if (DEFAULT_KEY.equals(e.getKey()) || isInstalled.test(e.getKey())) {
                m.put(e.getKey(), e.getValue());
            }
        }
        return m;
    }

    public synchronized void onForeground(String pkg) {
        lastPkg = pkg;
        screenOff = false;
        evaluate();
    }

    public synchronized void onScreenOff() {
        screenOff = true;
        evaluate();
    }

    public synchronized void onBootCompleted() {
        if (booted) return;
        booted = true;
        evaluate();
    }

    // ---- internals (caller holds the lock) ----
    // State (snapshot / lastApplied) is committed only after sink.write succeeds, so a
    // throwing Sink or Supplier leaves it consistent and a retry writes again. Exceptions
    // propagate to the caller. The apply counter always advances, even for a failed write.

    private void evaluate() {
        if (!booted || !enabled) return;
        Limits limits = limitsSupplier.get();
        // screen off -> base: Default when usable, else the raw snapshot
        Profile target = screenOff ? defaultTarget(limits) : effective(lastPkg, limits);
        if (target.isEmpty()) {
            restore();
            return;
        }
        if (snapshot != null && target.equals(lastApplied)) return;
        Snapshot snap = snapshot != null ? snapshot : snapshotSupplier.get();
        sink.write(PropPayload.build(target, snap, limits, ++seq));
        snapshot = snap;
        lastApplied = target;
    }

    private Profile defaultTarget(Limits limits) {
        Profile def = profiles.get(DEFAULT_KEY);
        return def == null ? Profile.empty() : Validator.validate(def, limits);
    }

    private Profile effective(String pkg, Limits limits) {
        Profile app = pkg == null ? null : profiles.get(pkg);
        Profile v = app == null ? Profile.empty() : Validator.validate(app, limits);
        return v.isEmpty() ? defaultTarget(limits) : v;
    }

    private void restore() {
        if (snapshot == null) return;
        sink.write(PropPayload.build(Profile.empty(), snapshot, limitsSupplier.get(), ++seq));
        snapshot = null;
        lastApplied = null;
    }
}
