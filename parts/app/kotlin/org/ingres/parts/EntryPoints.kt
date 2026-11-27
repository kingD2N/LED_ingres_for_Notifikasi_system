/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/**
 * Fitur ingres lain yang dikumpulkan di menu Ingres Parts.
 *
 * Tiap aplikasi memisahkan activity aslinya (tanpa intent-filter, dibuka dari sini) dari
 * pintu masuk lamanya (ikon peluncur / entri Setelan) yang berupa activity-alias. Selama
 * Ingres Parts terpasang, alias itu dimatikan supaya fitur tidak muncul dobel.
 */
object EntryPoints {
    class Feature(val pkg: String, val activity: String, val title: Int)

    val FEATURES =
        listOf(
            Feature("org.lineageos.leds", "org.lineageos.leds.MainActivity", R.string.link_leds),
            Feature("org.ingres.perf", "org.ingres.perf.ui.PerfActivity", R.string.link_perf),
            Feature("org.lunaris.dolby", "org.lunaris.dolby.ui.DolbyActivity", R.string.link_dolby),
        )

    private val DUPLICATES =
        listOf(
            // Ikon "LEDs" di layar aplikasi
            ComponentName("org.lineageos.leds", "org.lineageos.leds.LauncherEntry"),
            // Setelan > Sistem > CPU/GPU per Aplikasi
            ComponentName("org.ingres.perf", "org.ingres.perf.SettingsEntry"),
            // Setelan > Suara > Dolby Atmos dan ikon Dolby di layar aplikasi
            ComponentName("org.lunaris.dolby", "org.lunaris.dolby.ui.SettingsEntry"),
            ComponentName("org.lunaris.dolby", "org.lunaris.dolby.ui.LauncherEntry"),
        )

    /** Matikan pintu masuk ganda. Alias yang tidak ada (aplikasi versi lama/tidak terpasang) dilewati. */
    fun hideDuplicates(ctx: Context) {
        val pm = ctx.packageManager
        for (cn in DUPLICATES) {
            try {
                pm.getActivityInfo(cn, PackageManager.ComponentInfoFlags.of((ANY_BOOT or PackageManager.MATCH_DISABLED_COMPONENTS).toLong()))
                if (pm.getComponentEnabledSetting(cn) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) continue
                pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                Log.i(TAG, "Disembunyikan (sudah ada di Ingres Parts): ${cn.flattenToShortString()}")
            } catch (e: PackageManager.NameNotFoundException) {
                // Aplikasi atau alias tidak ada di ROM ini.
            } catch (e: Exception) {
                Log.w(TAG, "Gagal menyembunyikan ${cn.flattenToShortString()}", e)
            }
        }
    }

    /** Intent ke activity asli fitur, atau null bila aplikasinya tidak terpasang. */
    fun intentFor(ctx: Context, f: Feature): Intent? {
        val cn = ComponentName(f.pkg, f.activity)
        return try {
            ctx.packageManager.getActivityInfo(cn, PackageManager.ComponentInfoFlags.of(ANY_BOOT.toLong()))
            Intent().setComponent(cn)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    // Juga temukan aplikasi yang belum siap direct boot (LED, Dolby) saat HP masih terkunci.
    private const val ANY_BOOT = PackageManager.MATCH_DIRECT_BOOT_AWARE or PackageManager.MATCH_DIRECT_BOOT_UNAWARE

    private const val TAG = "IngresParts"
}
