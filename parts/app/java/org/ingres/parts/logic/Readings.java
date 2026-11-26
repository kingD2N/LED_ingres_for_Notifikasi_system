package org.ingres.parts.logic;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pengurai bacaan sensor untuk overlay. */
public final class Readings {
    private static final Pattern FPS = Pattern.compile("fps:\\s*([0-9]+(?:\\.[0-9]+)?)");

    private Readings() {}

    /**
     * Isi sde-crtc-0/measured_fps, mis. "fps: 59.4 duration:1000000 frame_count:60".
     * Mengembalikan NaN bila tidak bisa dibaca.
     */
    public static float parseFps(String raw) {
        if (raw == null) return Float.NaN;
        Matcher m = FPS.matcher(raw);
        if (!m.find()) return Float.NaN;
        try {
            return Float.parseFloat(m.group(1));
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }

    /**
     * Suhu CPU dari daftar sensor thermal HAL: sensor bernama cpu_therm bila ada (paling
     * mewakili suhu SoC), selain itu rata-rata sensor bertipe CPU (sensor per inti lebih
     * panas 15-20 C dari suhu SoC, jadi bukan nilai maksimum). NaN bila tidak ada.
     */
    public static float pickCpuTemp(String[] names, int[] types, float[] values, int cpuType) {
        if (names == null || types == null || values == null) return Float.NaN;
        int n = Math.min(names.length, Math.min(types.length, values.length));
        for (int i = 0; i < n; i++) {
            if ("cpu_therm".equalsIgnoreCase(names[i]) && isSane(values[i])) return values[i];
        }
        float sum = 0f;
        int count = 0;
        for (int i = 0; i < n; i++) {
            if (types[i] == cpuType && isSane(values[i])) {
                sum += values[i];
                count++;
            }
        }
        return count == 0 ? Float.NaN : sum / count;
    }

    private static boolean isSane(float c) {
        return !Float.isNaN(c) && c > -40f && c < 150f;
    }

    /** "59.4" -> "59", NaN -> "-". */
    public static String formatFps(float fps) {
        return Float.isNaN(fps) ? "-" : String.valueOf(Math.round(fps));
    }

    /** 41.6 -> "42°C", NaN -> "-". */
    public static String formatTemp(float c) {
        return Float.isNaN(c) ? "-" : Math.round(c) + "°C";
    }
}
