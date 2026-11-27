/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts

import android.os.SystemProperties
import android.util.Log
import java.io.File

/**
 * Akses ke sistem. Menulis node sysfs tidak dilakukan app (SELinux): app mengisi
 * properti sys.ingres.parts.*, init.ingres_parts.rc yang menulis node-nya.
 * Membaca node dilakukan langsung (aturannya di sepolicy parts-vendor).
 */
object SysNodes {
    private const val TAG = "IngresParts"

    const val PROP_INPUT_SUSPEND = "sys.ingres.parts.input_suspend"
    const val PROP_NIGHT_CHARGING = "sys.ingres.parts.night_charging"
    const val PROP_FCC = "sys.ingres.parts.fcc"
    const val PROP_SCONFIG = "sys.ingres.parts.sconfig"
    const val PROP_TOUCH_BOOST = "sys.ingres.parts.touch_boost"
    const val PROP_FLAT = "sys.ingres.parts.flat"
    /** Pemicu tulis ulang mode Flat (init hanya terpicu bila nilai properti berubah). */
    const val PROP_FLAT_APPLY = "sys.ingres.parts.flat_apply"

    const val NODE_FPS = "/sys/class/drm/sde-crtc-0/measured_fps"

    /**
     * Nilai FCC yang melepas batas arus cas: qti_battery_charger mengirim nilai negatif ke ADSP
     * sebagai UINT_MAX (tanpa batas dari kernel), ADSP kembali ke step/JEITA/thermal-nya sendiri.
     */
    const val FCC_RELEASE = "-1"

    /** true bila properti berhasil diisi. */
    fun setProp(key: String, value: String): Boolean =
        try {
            SystemProperties.set(key, value)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengisi $key=$value", e)
            false
        }

    /** Isi node (dipangkas), null bila tidak bisa dibaca. */
    fun read(path: String): String? =
        try {
            File(path).readText().trim()
        } catch (e: Exception) {
            null
        }
}
