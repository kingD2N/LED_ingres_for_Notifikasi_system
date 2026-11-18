/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.perf.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.ingres.perf.PerfApplication

/**
 * Boot selesai (sebelum dan sesudah unlock): jadwalkan profil Default (ditunda, hanya satu
 * panggilan tertunda). Idempoten.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED -> {
                try {
                    (context?.applicationContext as? PerfApplication)?.onBootCompleted()
                } catch (e: Exception) {
                    Log.e(TAG, "onBootCompleted gagal", e)
                }
            }
        }
    }

    private companion object {
        const val TAG = "IngresPerfBoot"
    }
}
