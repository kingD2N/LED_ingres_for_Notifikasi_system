package org.ingres.parts.logic;

/**
 * Profil thermal mi_thermald, dipilih lewat /sys/class/thermal/thermal_message/sconfig.
 *
 * <p>Angka sconfig diambil dari thermal-map.conf vendor ingres (didekripsi):
 * 0 = thermal-normal, 10 = thermal-nolimits, 11 = thermal-class0, 13 = thermal-tgame,
 * 20 = thermal-mgame. ROM menulis 20 saat boot (init.xiaomi_sm8450.rc).
 */
public enum ThermalProfile {
    /** class0: CPU prime dibatasi 2,05 GHz sejak suhu rendah, GPU ditahan. Paling dingin. */
    HEMAT("hemat", 11),
    /** normal: profil harian MIUI. */
    HARIAN("harian", 0),
    /** mgame: bawaan ROM ingres. */
    SEIMBANG("seimbang", 20),
    /** tgame: CPU prime penuh sampai suhu lebih tinggi dari mgame. */
    GAMING("gaming", 13),
    /** nolimits: hampir tanpa batas sampai 51 C, lalu dipotong keras. Paling panas. */
    PERFORMA("performa", 10);

    /** Nilai yang ditulis ROM saat boot. */
    public static final int ROM_DEFAULT_SCONFIG = 20;
    /** Kunci profil game: ikuti profil utama. */
    public static final String KEY_FOLLOW = "ikut";

    public final String key;
    public final int sconfig;

    ThermalProfile(String key, int sconfig) {
        this.key = key;
        this.sconfig = sconfig;
    }

    /** Kunci tidak dikenal atau null = SEIMBANG (bawaan ROM). */
    public static ThermalProfile fromKey(String key) {
        if (key != null) {
            for (ThermalProfile p : values()) {
                if (p.key.equals(key)) return p;
            }
        }
        return SEIMBANG;
    }

    /** null bila kunci bukan profil (mis. KEY_FOLLOW). */
    public static ThermalProfile fromKeyOrNull(String key) {
        if (key == null) return null;
        for (ThermalProfile p : values()) {
            if (p.key.equals(key)) return p;
        }
        return null;
    }

    public static boolean isKnownSconfig(int value) {
        for (ThermalProfile p : values()) {
            if (p.sconfig == value) return true;
        }
        return false;
    }

    /**
     * Profil yang berlaku: profil game saat game di depan (kecuali "ikuti profil utama"),
     * selain itu profil utama.
     */
    public static ThermalProfile effective(String baseKey, boolean gameActive, String gameKey) {
        if (gameActive) {
            ThermalProfile g = fromKeyOrNull(gameKey);
            if (g != null) return g;
        }
        return fromKey(baseKey);
    }
}
