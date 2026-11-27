/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts

import android.content.Context
import android.net.Uri
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

    const val NODE_FCC = "/sys/class/power_supply/battery/constant_charge_current"
    const val NODE_TOUCH_BOOST = "/sys/devices/virtual/touch/touch_dev/bump_sample_rate"
    const val NODE_FPS = "/sys/class/drm/sde-crtc-0/measured_fps"

    /** Arus cas bawaan ingres (HyperCharge 120W) bila nilai asli tidak bisa dibaca. */
    const val FCC_FALLBACK_UA = 5_850_000

    /** true bila properti berhasil diisi. */
    fun setProp(key: String, value: String): Boolean =
        try {
            SystemProperties.set(key, value)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengisi $key=$value", e)
            false
        }

    /**
     * Setelan LineageOS "High touch polling rate" (Setelan > Layar): "1"/"0", atau null bila ROM
     * tidak punya. Node bump_sample_rate tidak bisa dibaca (EINVAL), jadi setelah game nilainya
     * dikembalikan menurut setelan ini.
     */
    fun lineageHighTouchPolling(ctx: Context): String? =
        try {
            ctx.contentResolver
                .call(Uri.parse("content://lineagesettings"), "GET_system", "high_touch_polling_rate_enable", null)
                ?.getString("value")
                ?.takeIf { it == "0" || it == "1" }
        } catch (e: Exception) {
            null
        }

    /** Isi node (dipangkas), null bila tidak bisa dibaca. */
    fun read(path: String): String? =
        try {
            File(path).readText().trim()
        } catch (e: Exception) {
            null
        }
}
