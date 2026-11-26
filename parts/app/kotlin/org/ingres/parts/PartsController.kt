/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts

import android.content.Context
import android.util.Log
import org.ingres.parts.logic.ChargePolicy
import org.ingres.parts.logic.ThermalProfile

/**
 * Otak Ingres Parts. Semua method dipanggil di thread utama.
 *
 * Masukan: status baterai, aplikasi di depan, layar, boot, dan setelan dari UI.
 * Keluaran: properti sys.ingres.parts.* (ditulis ke sysfs oleh init) dan overlay FPS.
 * Properti hanya ditulis bila nilainya berubah dari yang terakhir ditulis.
 */
class PartsController(private val ctx: Context) {
    val prefs = PartsPrefs(ctx)
    private val overlay = FpsOverlay(ctx)

    // Masukan
    var batteryLevel = -1
        private set
    var plugged = false
        private set
    var gameActive = false
        private set
    private var booted = false

    // Keluaran terakhir. init.ingres_parts.rc mengembalikan input_suspend dan
    // night_charging ke 0 setiap boot, jadi nilai awalnya "0".
    private val written =
        mutableMapOf<String, String>(
            SysNodes.PROP_INPUT_SUSPEND to "0",
            SysNodes.PROP_NIGHT_CHARGING to "0",
        )
    var charge: ChargePolicy.State = ChargePolicy.decide(ChargePolicy.MODE_NORMAL, 0, -1, false, false, false)
        private set

    private var flatNonce = 0
    private var savedTouch: String? = null
    private var touchBoosted = false

    /** Dipanggil sekali saat proses mulai. */
    fun start() {
        // Bypass yang belum dikembalikan (app atau HP mati saat bypass): pulihkan arus cas dulu.
        val pending = prefs.pendingFccRestore
        if (pending > 0) {
            Log.w(TAG, "Memulihkan arus cas $pending setelah bypass terputus")
            if (write(SysNodes.PROP_FCC, pending.toString(), force = true)) prefs.pendingFccRestore = 0
        }
        applyAll()
    }

    fun onBootCompleted() {
        booted = true
        applyAll()
        // HAL layar bisa menyetel ulang mode warna saat boot; tulis ulang Flat sekali lagi.
        if (prefs.flatMode) pushFlat("1")
    }

    fun onBattery(level: Int, isPlugged: Boolean) {
        if (level == batteryLevel && isPlugged == plugged) return
        batteryLevel = level
        plugged = isPlugged
        applyCharge()
    }

    fun onForeground(pkg: String) {
        setGameActive(pkg in prefs.games)
    }

    fun onScreenOff() {
        setGameActive(false)
    }

    fun onScreenOn() {
        // Panel bisa melupakan mode Flat saat layar mati; tulis ulang.
        if (prefs.flatMode) pushFlat("1")
    }

    private fun setGameActive(active: Boolean) {
        if (active == gameActive) return
        gameActive = active
        Log.d(TAG, "Mode game: $active")
        applyAll()
    }

    /** Panggil setelah setelan berubah dari UI. */
    fun applyAll() {
        applyCharge()
        applyThermal()
        applyTouch()
        applyOverlay()
        applyFlat()
    }

    private fun applyCharge() {
        val bypassWanted = prefs.bypassEnabled && (!prefs.bypassGameOnly || gameActive)
        val next =
            ChargePolicy.decide(
                prefs.chargeMode,
                prefs.chargeLimit,
                batteryLevel,
                plugged,
                charge.suspendInput,
                bypassWanted,
            )
        charge = next
        write(SysNodes.PROP_NIGHT_CHARGING, if (next.nightCharging) "1" else "0")
        applyBypass(next.bypass)
        write(SysNodes.PROP_INPUT_SUSPEND, if (next.suspendInput) "1" else "0")
    }

    private fun applyBypass(active: Boolean) {
        val pending = prefs.pendingFccRestore
        if (active && pending <= 0) {
            val original =
                SysNodes.read(SysNodes.NODE_FCC)?.toIntOrNull()?.takeIf { it > 0 } ?: SysNodes.FCC_FALLBACK_UA
            // Simpan dulu (commit) supaya arus cas bisa dipulihkan walau proses mati sesudahnya.
            prefs.pendingFccRestore = original
            write(SysNodes.PROP_FCC, "0", force = true)
        } else if (!active && pending > 0) {
            if (write(SysNodes.PROP_FCC, pending.toString(), force = true)) prefs.pendingFccRestore = 0
        }
    }

    private fun applyThermal() {
        if (!booted) return
        val p = ThermalProfile.effective(prefs.thermalBase, gameActive, prefs.thermalGame)
        val value = p.sconfig.toString()
        // Profil bawaan ROM (20) sudah ditulis ROM saat boot: jangan menulis apa pun sampai
        // pengguna memilih profil lain, supaya bawaan Ingres Parts = perilaku ROM.
        if (written[SysNodes.PROP_SCONFIG] == null && p.sconfig == ThermalProfile.ROM_DEFAULT_SCONFIG) return
        write(SysNodes.PROP_SCONFIG, value)
    }

    private fun applyTouch() {
        val want = gameActive && prefs.gameTouch
        if (want && !touchBoosted) {
            savedTouch = SysNodes.read(SysNodes.NODE_TOUCH_BOOST)?.takeIf { it == "0" || it == "1" }
            touchBoosted = write(SysNodes.PROP_TOUCH_BOOST, "1", force = true)
        } else if (!want && touchBoosted) {
            // Kembalikan nilai sebelum game (mis. "Laju polling sentuh tinggi" di Setelan).
            write(SysNodes.PROP_TOUCH_BOOST, savedTouch ?: "0", force = true)
            touchBoosted = false
            savedTouch = null
        }
    }

    private fun applyOverlay() {
        if (gameActive && prefs.gameOverlay) overlay.show() else overlay.hide()
    }

    private fun applyFlat() {
        val v = if (prefs.flatMode) "1" else "0"
        // Belum pernah diubah dan mati: biarkan panel apa adanya.
        if (written[SysNodes.PROP_FLAT] == null && v == "0") return
        if (written[SysNodes.PROP_FLAT] != v) pushFlat(v)
    }

    /** Isi nilai lalu naikkan nonce pemicu, supaya init menulis walau nilainya sama. */
    private fun pushFlat(v: String) {
        if (write(SysNodes.PROP_FLAT, v, force = true)) {
            flatNonce++
            SysNodes.setProp(SysNodes.PROP_FLAT_APPLY, "${android.os.SystemClock.elapsedRealtime()}.$flatNonce")
        }
    }

    /** Uji overlay dari UI tanpa harus membuka game. */
    fun previewOverlay(show: Boolean) {
        if (show) overlay.show() else if (!(gameActive && prefs.gameOverlay)) overlay.hide()
    }

    private fun write(key: String, value: String, force: Boolean = false): Boolean {
        if (!force && written[key] == value) return true
        val ok = SysNodes.setProp(key, value)
        if (ok) written[key] = value
        return ok
    }

    private companion object {
        const val TAG = "IngresParts"
    }
}
