/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 *
 * Lampu status untuk LED aw22xxx: notifikasi saat layar mati, charging,
 * baterai penuh dan baterai lemah (logika dari LED_ingres_for_Notifikasi_system).
 * Akses notification listener diberikan otomatis lewat overlay
 * config_defaultListenerAccessPackages.
 */

package org.lineageos.leds.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import org.lineageos.leds.Application
import vendor.lineage.leds.ILeds

class StatusLightService : NotificationListenerService() {
    private var screenOff = false
    private var plugged = false
    private var batteryStatus = BatteryManager.BATTERY_STATUS_UNKNOWN
    private var batteryLevel = 100
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> screenOff = true
                Intent.ACTION_SCREEN_ON -> screenOff = false
                Intent.ACTION_BATTERY_CHANGED -> readBattery(intent)
            }
            update()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()

        screenOff = !getSystemService(PowerManager::class.java).isInteractive
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        registerReceiver(receiver, filter)?.let { readBattery(it) }
        receiverRegistered = true
        update()
    }

    override fun onListenerDisconnected() {
        if (receiverRegistered) {
            unregisterReceiver(receiver)
            receiverRegistered = false
        }
        setState(ILeds.STATE_OFF)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = update()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = update()

    private fun readBattery(intent: Intent) {
        plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        batteryStatus = intent.getIntExtra(
            BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN
        )
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level >= 0 && scale > 0) {
            batteryLevel = level * 100 / scale
        }
    }

    private fun hasNotifications(): Boolean = try {
        activeNotifications?.any { it.isClearable && !it.isOngoing } == true
    } catch (e: SecurityException) {
        false
    }

    private fun update() {
        // Prioritas sama dengan skrip LED: penuh > charging > baterai lemah > notifikasi
        val state = when {
            plugged && (batteryStatus == BatteryManager.BATTERY_STATUS_FULL ||
                    batteryLevel >= 100) -> ILeds.STATE_FULL
            plugged && batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING ->
                ILeds.STATE_CHARGING
            !plugged && batteryLevel <= LOW_BATTERY_LEVEL -> ILeds.STATE_LOW_BATTERY
            screenOff && hasNotifications() -> ILeds.STATE_NOTIFICATION
            else -> ILeds.STATE_OFF
        }
        setState(state)
    }

    private fun setState(state: Int) {
        try {
            Application.INSTANCE.ledsService.setSystemState(state)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengatur lampu status", e)
        }
    }

    companion object {
        private const val TAG = "LedsStatusLight"
        private const val LOW_BATTERY_LEVEL = 15
    }
}
