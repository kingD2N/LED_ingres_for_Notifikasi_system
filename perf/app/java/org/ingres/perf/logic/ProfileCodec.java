package org.ingres.perf.logic;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Text codec. Profile: "c0min=1;c0max=2;c0gov=walt;...;gmin=1;gmax=2" (only set fields).
 * Map: one "package=profile" line each; key "*" is the Default profile.
 * Decoding never throws; garbage yields empty / skipped lines.
 */
public final class ProfileCodec {
    public static final String DEFAULT_KEY = "*";

    public ProfileCodec() {}

    public static String encode(Profile p) {
        if (p == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Profile.CLUSTERS; i++) {
            if (p.minKhz(i) > 0) add(sb, "c" + i + "min=" + p.minKhz(i));
            if (p.maxKhz(i) > 0) add(sb, "c" + i + "max=" + p.maxKhz(i));
            if (p.governor(i) != null) add(sb, "c" + i + "gov=" + p.governor(i));
        }
        if (p.gpuMinHz() > 0) add(sb, "gmin=" + p.gpuMinHz());
        if (p.gpuMaxHz() > 0) add(sb, "gmax=" + p.gpuMaxHz());
        return sb.toString();
    }

    private static void add(StringBuilder sb, String s) {
        if (sb.length() > 0) sb.append(';');
        sb.append(s);
    }

    public static Profile decode(String s) {
        if (s == null) return Profile.empty();
        try {
            int[] min = new int[Profile.CLUSTERS];
            int[] max = new int[Profile.CLUSTERS];
            String[] gov = new String[Profile.CLUSTERS];
            long gmin = 0, gmax = 0;
            for (String part : s.trim().split(";")) {
                int eq = part.indexOf('=');
                if (eq <= 0) continue;
                String k = part.substring(0, eq).trim();
                String v = part.substring(eq + 1).trim();
                if (v.isEmpty()) continue;
                if (k.equals("gmin")) gmin = parsePos(v);
                else if (k.equals("gmax")) gmax = parsePos(v);
                else if (k.length() >= 4 && k.charAt(0) == 'c'
                        && k.charAt(1) >= '0' && k.charAt(1) < '0' + Profile.CLUSTERS) {
                    int c = k.charAt(1) - '0';
                    String f = k.substring(2);
                    if (f.equals("min")) min[c] = (int) Math.min(parsePos(v), Integer.MAX_VALUE);
                    else if (f.equals("max")) max[c] = (int) Math.min(parsePos(v), Integer.MAX_VALUE);
                    else if (f.equals("gov") && isGovName(v)) gov[c] = v;
                }
            }
            Profile.Builder b = new Profile.Builder();
            for (int c = 0; c < Profile.CLUSTERS; c++) b.cluster(c, min[c], max[c], gov[c]);
            return b.gpu(gmin, gmax).build();
        } catch (RuntimeException e) {
            return Profile.empty();
        }
    }

    /** Positive long, or 0 if not a positive number. */
    private static long parsePos(String v) {
        try {
            long n = Long.parseLong(v);
            return n > 0 ? n : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean isGovName(String v) {
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            if (!(ch >= 'a' && ch <= 'z') && !(ch >= 'A' && ch <= 'Z')
                    && !(ch >= '0' && ch <= '9') && ch != '_' && ch != '-' && ch != '.') return false;
        }
        return !v.isEmpty();
    }

    private static boolean isKey(String k) {
        if (k.isEmpty()) return false;
        for (int i = 0; i < k.length(); i++) {
            char ch = k.charAt(i);
            if (ch <= ' ' || ch == '=' || ch == ';') return false;
        }
        return true;
    }

    public static String encodeAll(Map<String, Profile> m) {
        StringBuilder sb = new StringBuilder();
        if (m == null) return "";
        for (Map.Entry<String, Profile> e : m.entrySet()) {
            String k = e.getKey();
            Profile p = e.getValue();
            if (k == null || !isKey(k) || p == null || p.isEmpty()) continue;
            sb.append(k).append('=').append(encode(p)).append('\n');
        }
        return sb.toString();
    }

    public static Map<String, Profile> decodeAll(String s) {
        Map<String, Profile> out = new LinkedHashMap<>();
        if (s == null) return out;
        for (String line : s.split("\\r?\\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String k = line.substring(0, eq).trim();
            if (!isKey(k)) continue;
            Profile p = decode(line.substring(eq + 1));
            if (p.isEmpty()) continue;
            out.put(k, p);
        }
        return out;
    }
}
