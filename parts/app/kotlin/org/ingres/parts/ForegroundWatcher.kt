/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts

import android.app.ActivityTaskManager
import android.app.TaskStackListener
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log

/**
 * Mengawasi aplikasi di depan (task root yang fokus, sama seperti ThermalService di
 * sm8450-common) dan layar. Semua callback dipanggil di thread utama: listener task
 * datang di thread binder lalu di-post ke Handler utama.
 *
 * - Task berubah: onTop(paket) bila paket berbeda dari sebelumnya; diabaikan saat layar mati.
 * - SCREEN_OFF: onScreenOff().
 * - SCREEN_ON / USER_PRESENT: kueri ulang task teratas dan selalu panggil onTop, supaya
 *   mode game dinilai ulang setelah layar menyala.
 */
class ForegroundWatcher(
    private val ctx: Context,
    private val onTop: (String) -> Unit,
    private val onScreenOff: () -> Unit,
    private val onScreenOn: () -> Unit = {},
) {
    private val handler = Handler(Looper.getMainLooper())

    // Hanya disentuh di thread utama.
    private var lastPkg: String? = null
    private var screenOn = true
    private var started = false

    private val taskListener =
        object : TaskStackListener() {
            override fun onTaskStackChanged() {
                handler.post { emitTop(force = false) }
            }
        }

    private val screenReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        screenOn = false
                        try {
                            onScreenOff()
                        } catch (e: Exception) {
                            Log.e(TAG, "onScreenOff gagal", e)
                        }
                    }
                    Intent.ACTION_SCREEN_ON,
                    Intent.ACTION_USER_PRESENT -> {
                        screenOn = true
                        try {
                            onScreenOn()
                        } catch (e: Exception) {
                            Log.e(TAG, "onScreenOn gagal", e)
                        }
                        emitTop(force = true)
                    }
                }
            }
        }

    /** Dipanggil di thread utama. */
    fun start() {
        if (started) return
        started = true
        screenOn = ctx.getSystemService(PowerManager::class.java)?.isInteractive ?: true
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
        ctx.registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        try {
            ActivityTaskManager.getService().registerTaskStackListener(taskListener)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mendaftar TaskStackListener", e)
        }
        emitTop(force = true)
    }

    /** Dipanggil di thread utama. */
    fun stop() {
        if (!started) return
        started = false
        try {
            ActivityTaskManager.getService().unregisterTaskStackListener(taskListener)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal melepas TaskStackListener", e)
        }
        try {
            ctx.unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal melepas receiver layar", e)
        }
    }

    private fun emitTop(force: Boolean) {
        if (!started || !screenOn) return
        val pkg = topPackage() ?: return
        if (!force && pkg == lastPkg) return
        lastPkg = pkg
        try {
            onTop(pkg)
        } catch (e: Exception) {
            Log.e(TAG, "onTop gagal", e)
        }
    }

    private fun topPackage(): String? =
        try {
            ActivityTaskManager.getService().focusedRootTaskInfo?.topActivity?.packageName
                ?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membaca task teratas", e)
            null
        }

    private companion object {
        const val TAG = "IngresPartsWatcher"
    }
}
