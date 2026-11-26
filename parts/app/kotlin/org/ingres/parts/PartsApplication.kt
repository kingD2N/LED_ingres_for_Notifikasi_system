/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemProperties
import android.util.Log

/**
 * Proses persistent Ingres Parts. Merangkai status baterai, aplikasi di depan (mode game),
 * layar, dan boot ke [PartsController]. Semua panggilan ke controller di thread utama.
 */
class PartsApplication : Application() {
    /** Belum ada bila onCreate gagal; cek [isReady]. */
    lateinit var parts: PartsController
        private set
    lateinit var speaker: SpeakerCleaner
        private set

    val isReady: Boolean
        get() = ::parts.isInitialized

    private val mainHandler = Handler(Looper.getMainLooper())
    private val bootToken = Any()

    private val batteryReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!isReady) return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                parts.onBattery(pct, plugged)
            }
        }

    override fun onCreate() {
        super.onCreate()
        try {
            setup()
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menyiapkan Ingres Parts", e)
        }
    }

    private fun setup() {
        val controller = PartsController(this)
        parts = controller
        speaker = SpeakerCleaner(this)
        controller.start()

        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)

        val watcher =
            ForegroundWatcher(
                this,
                onTop = { pkg -> controller.onForeground(pkg) },
                onScreenOff = { controller.onScreenOff() },
                onScreenOn = { controller.onScreenOn() },
            )
        try {
            watcher.start()
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memulai pengawas aplikasi", e)
        }

        // Proses dimulai ulang sesudah boot: broadcast boot tidak datang lagi.
        if (SystemProperties.get(PROP_BOOT_COMPLETED) == "1") scheduleBootCompleted()
    }

    /** Dipanggil BootCompletedReceiver (thread utama). Idempoten. */
    fun onBootCompleted() {
        if (isReady) scheduleBootCompleted()
    }

    /**
     * Ditunda supaya tulisan boot ROM (sconfig 20 di init.xiaomi_sm8450.rc), modul KSU, dan
     * Kernel Manager selesai dulu, lalu profil pengguna ditulis sesudahnya.
     */
    private fun scheduleBootCompleted() {
        mainHandler.removeCallbacksAndMessages(bootToken)
        mainHandler.postDelayed({ parts.onBootCompleted() }, bootToken, BOOT_SETTLE_MS)
    }

    private companion object {
        const val TAG = "IngresParts"
        const val BOOT_SETTLE_MS = 20_000L
        const val PROP_BOOT_COMPLETED = "sys.boot_completed"
    }
}
