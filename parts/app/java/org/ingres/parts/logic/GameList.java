package org.ingres.parts.logic;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/** Daftar paket game, disimpan sebagai satu string dipisah koma. */
public final class GameList {
    private GameList() {}

    public static Set<String> decode(String raw) {
        Set<String> out = new TreeSet<>();
        if (raw == null) return out;
        for (String part : raw.split(",")) {
            String p = part.trim();
            if (isValidPackage(p)) out.add(p);
        }
        return out;
    }

    public static String encode(Collection<String> pkgs) {
        StringBuilder sb = new StringBuilder();
        for (String p : new TreeSet<>(pkgs)) {
            if (!isValidPackage(p)) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(p);
        }
        return sb.toString();
    }

    /** Nama paket Android: huruf, angka, garis bawah, dipisah titik, minimal dua bagian. */
    public static boolean isValidPackage(String p) {
        if (p == null || p.isEmpty() || p.length() > 255) return false;
        String[] parts = p.split("\\.", -1);
        if (parts.length < 2) return false;
        for (String s : parts) {
            if (s.isEmpty()) return false;
            char c0 = s.charAt(0);
            if (!Character.isLetter(c0)) return false;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (!(Character.isLetterOrDigit(c) || c == '_')) return false;
            }
        }
        return true;
    }
}
