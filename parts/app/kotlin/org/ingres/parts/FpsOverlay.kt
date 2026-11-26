/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Handler
import android.os.IThermalService
import android.os.Looper
import android.os.ServiceManager
import android.os.Temperature
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import org.ingres.parts.logic.Readings

/**
 * Overlay kecil di pojok kiri atas: FPS layar, suhu CPU, dan suhu baterai, diperbarui
 * tiap detik. FPS dari sde-crtc-0/measured_fps (frame yang benar-benar dikirim ke panel),
 * suhu CPU dari thermal HAL (izin DEVICE_POWER), suhu baterai dari BatteryManager.
 */
class FpsOverlay(private val ctx: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var view: TextView? = null
    private val thermal: IThermalService? =
        try {
            // Nama layanan sama dengan Context.THERMAL_SERVICE (konstanta tersembunyi).
            IThermalService.Stub.asInterface(ServiceManager.getService("thermalservice"))
        } catch (e: Exception) {
            Log.w(TAG, "Layanan thermal tidak tersedia", e)
            null
        }

    private val tick =
        object : Runnable {
            override fun run() {
                view?.text = currentText()
                handler.postDelayed(this, INTERVAL_MS)
            }
        }

    fun show() {
        if (view != null) return
        val tv =
            TextView(ctx).apply {
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.MONOSPACE
                val pad = dp(6)
                setPadding(pad, dp(3), pad, dp(3))
                background =
                    GradientDrawable().apply {
                        cornerRadius = dp(8).toFloat()
                        setColor(0x99000000.toInt())
                    }
                text = currentText()
            }
        val lp =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = dp(12)
                y = dp(36)
                title = "IngresPartsOverlay"
            }
        try {
            wm.addView(tv, lp)
            view = tv
            handler.postDelayed(tick, INTERVAL_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menampilkan overlay", e)
        }
    }

    fun hide() {
        val v = view ?: return
        handler.removeCallbacks(tick)
        try {
            wm.removeViewImmediate(v)
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menutup overlay", e)
        }
        view = null
    }

    private fun currentText(): String {
        val fps = Readings.parseFps(SysNodes.read(SysNodes.NODE_FPS))
        return "FPS ${Readings.formatFps(fps)}  CPU ${Readings.formatTemp(cpuTemp())}  BAT ${Readings.formatTemp(batteryTemp())}"
    }

    private fun cpuTemp(): Float {
        val temps: Array<Temperature> =
            try {
                thermal?.currentTemperatures ?: return Float.NaN
            } catch (e: Exception) {
                return Float.NaN
            }
        return Readings.pickCpuTemp(
            Array(temps.size) { temps[it].name ?: "" },
            IntArray(temps.size) { temps[it].type },
            FloatArray(temps.size) { temps[it].value },
            Temperature.TYPE_CPU,
        )
    }

    private fun batteryTemp(): Float {
        val i: Intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return Float.NaN
        val t = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        return if (t == Int.MIN_VALUE) Float.NaN else t / 10f
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    private companion object {
        const val TAG = "IngresPartsOverlay"
        const val INTERVAL_MS = 1000L
    }
}
