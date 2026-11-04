/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package vendor.lineage.leds;

interface ILeds {
    /** Status lampu (dipakai sistem): mati. */
    const int STATE_OFF = 0;
    /** Baterai penuh saat di-charge. */
    const int STATE_FULL = 1;
    /** Sedang di-charge. */
    const int STATE_CHARGING = 2;
    /** Baterai lemah. */
    const int STATE_LOW_BATTERY = 3;
    /** Ada notifikasi dan layar mati. */
    const int STATE_NOTIFICATION = 4;

    boolean isEnabled();
    void setEnabled(boolean enabled);

    /** Index ke getAvailableEffects(). */
    int getCurrentEffect();
    void setCurrentEffect(int effect);
    List<String> getAvailableEffects();

    int getFrequency();
    void setFrequency(int frequency);

    /** 9 warna RGB (0xRRGGBB). */
    int[] getAllColors();
    void setColor(int index, int color);

    /** Terapkan efek; useCustomColors = pakai warna & frekuensi sendiri. */
    void flushConfig(boolean useCustomColors);

    /** Lampu status (notifikasi, charging, baterai penuh/lemah). */
    boolean isStatusLightEnabled();
    void setStatusLightEnabled(boolean enabled);
    void setSystemState(int state);
}
