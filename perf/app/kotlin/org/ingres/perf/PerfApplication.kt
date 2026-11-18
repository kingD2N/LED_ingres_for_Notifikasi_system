/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.perf

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemProperties
import android.util.Log
import java.util.function.Consumer
import org.ingres.perf.logic.Debouncer
import org.ingres.perf.logic.Scheduler

/**
 * Proses persistent IngresPerf. Merangkai:
 * ProfileStore -> PerfStateMachine -> PropSink (di PerfController),
 * ForegroundWatcher -> Debouncer(300 ms, Handler utama) -> onForeground,
 * LOCKED_BOOT_COMPLETED / BOOT_COMPLETED (BootCompletedReceiver) -> onBootCompleted,
 * ditunda BOOT_SETTLE_MS (satu panggilan tertunda saja).
 *
 * Semua panggilan ke state machine terjadi di thread utama.
 */
class PerfApplication : Application() {
    /** API untuk UI. Belum ada bila onCreate gagal; cek [isReady]. */
    lateinit var perf: PerfController
        private set

    val isReady: Boolean
        get() = ::perf.isInitialized

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scheduler = HandlerScheduler(mainHandler)
    private var watcher: ForegroundWatcher? = null
    private val bootToken = Any()

    override fun onCreate() {
        super.onCreate()
        try {
            setup()
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menyiapkan IngresPerf", e)
        }
    }

    private fun setup() {
        val controller = PerfController(this)
        perf = controller
        controller.load()

        val debouncer =
            Debouncer(scheduler, DEBOUNCE_MS, Consumer<String> { pkg -> controller.onForeground(pkg) })
        val w =
            ForegroundWatcher(
                this,
                onTop = { pkg -> debouncer.submit(pkg) },
                onScreenOff = {
                    // Buang pindah-aplikasi yang tertunda supaya tidak menimpa nilai dasar.
                    scheduler.cancel()
                    controller.onScreenOff()
                },
            )
        watcher = w
        try {
            w.start()
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memulai pengawas aplikasi", e)
        }

        // Proses dimulai ulang sesudah boot: broadcast boot tidak datang lagi.
        if (SystemProperties.get(PROP_BOOT_COMPLETED) == "1") {
            scheduleBootCompleted(controller)
        }
    }

    /** Dipanggil BootCompletedReceiver (thread utama). Idempoten. */
    fun onBootCompleted() {
        if (isReady) scheduleBootCompleted(perf)
    }

    /**
     * Menunda onBootCompleted supaya tulisan boot vendor/KSU/Kernel Manager selesai dulu.
     * Dijadwalkan ulang = jadwal lama dibatalkan, jadi hanya satu panggilan tertunda.
     */
    private fun scheduleBootCompleted(controller: PerfController) {
        mainHandler.removeCallbacksAndMessages(bootToken)
        mainHandler.postDelayed(
            Runnable { controller.onBootCompleted() },
            bootToken,
            BOOT_SETTLE_MS,
        )
    }

    /** Scheduler Debouncer di atas Handler utama; token membuat cancel() hanya membuang miliknya. */
    private class HandlerScheduler(private val handler: Handler) : Scheduler {
        private val token = Any()

        override fun post(r: Runnable, delayMs: Long) {
            handler.postDelayed(r, token, delayMs)
        }

        override fun cancel() {
            handler.removeCallbacksAndMessages(token)
        }
    }

    private companion object {
        const val TAG = "IngresPerf"
        const val DEBOUNCE_MS = 300L
        // Jeda sesudah boot selesai sebelum profil Default diterapkan: memberi waktu skrip
        // post-boot vendor, modul KSU, dan Kernel Manager menulis nilainya lebih dulu.
        const val BOOT_SETTLE_MS = 20_000L
        const val PROP_BOOT_COMPLETED = "sys.boot_completed"
    }
}
