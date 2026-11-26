package org.ingres.parts.logic;

/** Nada sinus untuk membersihkan speaker (getaran membran mendorong air dan debu keluar). */
public final class Tone {
    /** Frekuensi rendah yang menggetarkan membran speaker paling kuat. */
    public static final int FREQUENCY_HZ = 165;
    public static final int SAMPLE_RATE = 48000;
    public static final int DURATION_SECONDS = 30;

    private Tone() {}

    /**
     * Satu detik nada sinus PCM 16-bit mono, dengan jumlah siklus utuh supaya bisa
     * diulang tanpa bunyi klik di sambungannya.
     */
    public static short[] oneSecond(int sampleRate, int frequencyHz, float amplitude) {
        if (sampleRate <= 0 || frequencyHz <= 0) throw new IllegalArgumentException("parameter tidak valid");
        float a = Math.max(0f, Math.min(1f, amplitude));
        short[] out = new short[sampleRate];
        for (int i = 0; i < sampleRate; i++) {
            double v = Math.sin(2.0 * Math.PI * frequencyHz * i / sampleRate);
            out[i] = (short) Math.round(v * a * Short.MAX_VALUE);
        }
        return out;
    }
}
