package org.ingres.parts.logic;

/**
 * Keputusan pengisian baterai: batas cas, mode optimal 80% firmware, dan bypass.
 *
 * <p>Node yang dikendalikan (lewat init): qcom-battery/input_suspend (memutus input charger),
 * qcom-battery/night_charging (firmware menahan di 80%), battery/constant_charge_current
 * (arus cas; 0 = bypass, HP memakai daya charger tanpa mengisi baterai).
 */
public final class ChargePolicy {
    public static final String MODE_NORMAL = "normal";
    public static final String MODE_OPTIMAL80 = "optimal80";
    public static final String MODE_LIMIT = "limit";

    public static final int MIN_LIMIT = 50;
    public static final int MAX_LIMIT = 95;
    public static final int DEFAULT_LIMIT = 80;
    /** Cas dilanjutkan lagi setelah turun sebanyak ini di bawah batas. */
    public static final int RESUME_GAP = 5;

    private ChargePolicy() {}

    public static String normalizeMode(String mode) {
        if (MODE_OPTIMAL80.equals(mode) || MODE_LIMIT.equals(mode)) return mode;
        return MODE_NORMAL;
    }

    public static int clampLimit(int limit) {
        if (limit < MIN_LIMIT) return MIN_LIMIT;
        if (limit > MAX_LIMIT) return MAX_LIMIT;
        return limit;
    }

    /** Hasil keputusan. */
    public static final class State {
        public final boolean suspendInput;
        public final boolean nightCharging;
        public final boolean bypass;

        State(boolean suspendInput, boolean nightCharging, boolean bypass) {
            this.suspendInput = suspendInput;
            this.nightCharging = nightCharging;
            this.bypass = bypass;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof State)) return false;
            State s = (State) o;
            return s.suspendInput == suspendInput && s.nightCharging == nightCharging && s.bypass == bypass;
        }

        @Override
        public int hashCode() {
            return (suspendInput ? 1 : 0) | (nightCharging ? 2 : 0) | (bypass ? 4 : 0);
        }

        @Override
        public String toString() {
            return "State{suspend=" + suspendInput + ", night=" + nightCharging + ", bypass=" + bypass + "}";
        }
    }

    /**
     * @param mode MODE_*
     * @param limit batas persen untuk MODE_LIMIT
     * @param level persen baterai sekarang, negatif = belum diketahui
     * @param plugged charger tersambung
     * @param currentlySuspended input_suspend yang sedang berlaku (hasil keputusan sebelumnya)
     * @param bypassWanted bypass diminta (sakelar menyala dan, bila dipilih, sedang main game)
     */
    public static State decide(
            String mode,
            int limit,
            int level,
            boolean plugged,
            boolean currentlySuspended,
            boolean bypassWanted) {
        mode = normalizeMode(mode);
        boolean night = MODE_OPTIMAL80.equals(mode);
        boolean bypass = bypassWanted && plugged;
        boolean suspend;
        if (bypass || !MODE_LIMIT.equals(mode)) {
            // Bypass butuh input charger menyala; mode lain tidak memakai input_suspend.
            suspend = false;
        } else if (level < 0) {
            suspend = currentlySuspended;
        } else {
            int l = clampLimit(limit);
            if (level >= l) {
                suspend = true;
            } else if (level <= l - RESUME_GAP) {
                suspend = false;
            } else {
                suspend = currentlySuspended;
            }
        }
        return new State(suspend, night, bypass);
    }
}
