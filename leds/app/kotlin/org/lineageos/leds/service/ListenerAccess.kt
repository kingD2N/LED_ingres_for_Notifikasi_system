/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 *
 * Memberi akses notification listener ke StatusLightService.
 * config_defaultListenerAccessPackages hanya berlaku di data baru, jadi di
 * flash tanpa format lampu notifikasi tidak menyala. App ini bertanda tangan
 * platform (MANAGE_NOTIFICATION_LISTENERS = signature), jadi bisa memberi
 * dirinya sendiri. userSet = false: pilihan pengguna tidak ditimpa.
 */

package org.lineageos.leds.service

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.util.Log

object ListenerAccess {
    private const val TAG = "LedsListenerAccess"

    fun ensure(context: Context) {
        try {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val listener = ComponentName(context, StatusLightService::class.java)
            if (!nm.isNotificationListenerAccessGranted(listener)) {
                nm.setNotificationListenerAccessGranted(listener, true, false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memberi akses notifikasi", e)
        }
    }
}
