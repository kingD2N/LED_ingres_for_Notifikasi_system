package org.ingres.parts.logic;

import java.util.Arrays;
import java.util.Set;

/**
 * Tes logika murni Ingres Parts (tanpa Android). Jalankan:
 *   javac -d out $(find app/java app/test -name "*.java")
 *   java -cp out org.ingres.parts.logic.HostTests
 */
public class HostTests {
    private static int pass = 0;

    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError("GAGAL: " + name);
        pass++;
    }

    public static void main(String[] args) {
        charge();
        thermal();
        readings();
        games();
        tone();
        System.out.println("ALL PASS (" + pass + ")");
    }

    private static void charge() {
        String L = ChargePolicy.MODE_LIMIT;
        // Batas 80: berhenti di 80, lanjut lagi di 75.
        check(ChargePolicy.decide(L, 80, 80, true, false, false).suspendInput, "limit: berhenti tepat di batas");
        check(ChargePolicy.decide(L, 80, 85, true, false, false).suspendInput, "limit: di atas batas berhenti");
        check(!ChargePolicy.decide(L, 80, 79, true, false, false).suspendInput, "limit: di bawah batas tetap cas");
        check(ChargePolicy.decide(L, 80, 78, true, true, false).suspendInput, "limit: histeresis tahan suspend");
        check(!ChargePolicy.decide(L, 80, 75, true, true, false).suspendInput, "limit: lanjut cas di batas-5");
        check(!ChargePolicy.decide(L, 80, 60, false, true, false).suspendInput, "limit: turun jauh -> lepas suspend");
        check(ChargePolicy.decide(L, 80, -1, true, true, false).suspendInput, "limit: level tak diketahui -> tetap");
        // Batas dijepit ke 50..95.
        check(ChargePolicy.decide(L, 10, 50, true, false, false).suspendInput, "limit: dijepit ke 50");
        check(ChargePolicy.decide(L, 100, 95, true, false, false).suspendInput, "limit: dijepit ke 95, berhenti di 95");
        check(!ChargePolicy.decide(L, 100, 94, true, false, false).suspendInput, "limit: dijepit ke 95, 94 tetap cas");
        check(ChargePolicy.clampLimit(100) == 95 && ChargePolicy.clampLimit(10) == 50, "clampLimit");
        // Mode lain tidak pernah suspend.
        check(!ChargePolicy.decide(ChargePolicy.MODE_NORMAL, 80, 100, true, true, false).suspendInput, "normal: tanpa suspend");
        ChargePolicy.State opt = ChargePolicy.decide(ChargePolicy.MODE_OPTIMAL80, 80, 100, true, false, false);
        check(opt.nightCharging && !opt.suspendInput, "optimal80: night_charging");
        check(!ChargePolicy.decide(L, 80, 90, true, false, false).nightCharging, "limit: night mati");
        check(ChargePolicy.normalizeMode("ngawur").equals(ChargePolicy.MODE_NORMAL), "mode tak dikenal -> normal");
        check(ChargePolicy.normalizeMode(null).equals(ChargePolicy.MODE_NORMAL), "mode null -> normal");
        // Bypass hanya saat charger tersambung, dan mengalahkan suspend.
        ChargePolicy.State by = ChargePolicy.decide(L, 80, 90, true, true, true);
        check(by.bypass && !by.suspendInput, "bypass: input tetap menyala");
        check(!ChargePolicy.decide(L, 80, 90, false, false, true).bypass, "bypass: butuh charger");
        check(ChargePolicy.decide(L, 80, 90, true, false, false).equals(ChargePolicy.decide(L, 80, 90, true, false, false)), "State.equals");
    }

    private static void thermal() {
        check(ThermalProfile.fromKey(null) == ThermalProfile.SEIMBANG, "thermal: null -> seimbang");
        check(ThermalProfile.fromKey("xx") == ThermalProfile.SEIMBANG, "thermal: tak dikenal -> seimbang");
        check(ThermalProfile.SEIMBANG.sconfig == ThermalProfile.ROM_DEFAULT_SCONFIG, "thermal: seimbang = bawaan ROM");
        check(ThermalProfile.HEMAT.sconfig == 11 && ThermalProfile.HARIAN.sconfig == 0, "thermal: class0/normal");
        check(ThermalProfile.GAMING.sconfig == 13 && ThermalProfile.PERFORMA.sconfig == 10, "thermal: tgame/nolimits");
        check(ThermalProfile.isKnownSconfig(20) && !ThermalProfile.isKnownSconfig(99), "thermal: sconfig dikenal");
        check(ThermalProfile.effective("hemat", false, "gaming") == ThermalProfile.HEMAT, "thermal: tanpa game pakai utama");
        check(ThermalProfile.effective("hemat", true, "gaming") == ThermalProfile.GAMING, "thermal: game pakai profil game");
        check(ThermalProfile.effective("harian", true, ThermalProfile.KEY_FOLLOW) == ThermalProfile.HARIAN, "thermal: game ikut utama");
        check(ThermalProfile.fromKeyOrNull(ThermalProfile.KEY_FOLLOW) == null, "thermal: ikut bukan profil");
        Set<String> keys = new java.util.HashSet<>();
        for (ThermalProfile p : ThermalProfile.values()) keys.add(p.key);
        check(keys.size() == ThermalProfile.values().length, "thermal: kunci unik");
    }

    private static void readings() {
        check(Readings.parseFps("fps: 59.4 duration:1000000 frame_count:60") == 59.4f, "fps: urai");
        check(Readings.parseFps("fps: 120 duration:1") == 120f, "fps: bulat");
        check(Float.isNaN(Readings.parseFps("fps information not available")), "fps: tidak tersedia");
        check(Float.isNaN(Readings.parseFps(null)), "fps: null");
        check(Readings.formatFps(59.6f).equals("60") && Readings.formatFps(Float.NaN).equals("-"), "fps: format");
        int CPU = 0;
        float t = Readings.pickCpuTemp(new String[] {"cpu-0-0", "cpu_therm", "battery"}, new int[] {CPU, 99, 2},
                new float[] {70f, 42f, 33f}, CPU);
        check(t == 42f, "suhu: pakai cpu_therm");
        t = Readings.pickCpuTemp(new String[] {"cpu-0-0", "cpu-1-0", "battery"}, new int[] {CPU, CPU, 2},
                new float[] {40f, 50f, 33f}, CPU);
        check(t == 45f, "suhu: rata-rata CPU");
        check(Float.isNaN(Readings.pickCpuTemp(new String[] {"x"}, new int[] {5}, new float[] {30f}, CPU)), "suhu: tanpa CPU");
        check(Float.isNaN(Readings.pickCpuTemp(new String[] {"cpu_therm"}, new int[] {CPU}, new float[] {Float.NaN}, CPU)), "suhu: NaN");
        check(Readings.formatTemp(41.6f).equals("42°C") && Readings.formatTemp(Float.NaN).equals("-"), "suhu: format");
    }

    private static void games() {
        Set<String> s = GameList.decode("com.mobile.legends, com.tencent.ig,,bad,1x.y,com.a.b");
        check(s.equals(new java.util.TreeSet<>(Arrays.asList("com.a.b", "com.mobile.legends", "com.tencent.ig"))), "game: urai");
        check(GameList.encode(s).equals("com.a.b,com.mobile.legends,com.tencent.ig"), "game: simpan");
        check(GameList.decode(null).isEmpty() && GameList.decode("").isEmpty(), "game: kosong");
        check(!GameList.isValidPackage("a.b,c") && !GameList.isValidPackage("a..b") && GameList.isValidPackage("a_1.b2"), "game: validasi paket");
    }

    private static void tone() {
        short[] s = Tone.oneSecond(Tone.SAMPLE_RATE, Tone.FREQUENCY_HZ, 1f);
        check(s.length == Tone.SAMPLE_RATE, "nada: satu detik");
        check(s[0] == 0, "nada: mulai dari nol");
        // Siklus utuh: sampel sesudah ujung kembali ke awal gelombang (tanpa klik).
        double next = Math.sin(2.0 * Math.PI * Tone.FREQUENCY_HZ * Tone.SAMPLE_RATE / Tone.SAMPLE_RATE);
        check(Math.abs(next) < 1e-9, "nada: siklus utuh");
        short max = 0;
        for (short v : s) max = (short) Math.max(max, v);
        check(max > 32000, "nada: amplitudo penuh");
        check(Tone.oneSecond(1000, 10, 2f)[25] == Short.MAX_VALUE, "nada: amplitudo dijepit");
        boolean threw = false;
        try {
            Tone.oneSecond(0, 10, 1f);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check(threw, "nada: parameter salah ditolak");
    }
}
