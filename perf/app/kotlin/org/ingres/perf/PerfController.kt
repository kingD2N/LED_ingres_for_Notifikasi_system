/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.perf

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.util.function.Predicate
import java.util.function.Supplier
import org.ingres.perf.logic.Limits
import org.ingres.perf.logic.PerfStateMachine
import org.ingres.perf.logic.Profile
import org.ingres.perf.logic.Snapshot

/**
 * Perekat ProfileStore <-> PerfStateMachine <-> PropSink, dan API untuk UI.
 *
 * Thread: semua metode dipanggil dari thread utama saja (UI, receiver, Debouncer lewat
 * Handler utama, ForegroundWatcher). PerfStateMachine sendiri synchronized.
 * Kegagalan menulis (Sink/Supplier melempar) dicatat lalu diabaikan: state machine tetap
 * konsisten dan peristiwa berikutnya mencoba lagi. Proses persistent tidak boleh crash.
 */
class PerfController(context: Context) {
    private val appContext: Context = context.applicationContext ?: context
    private val store = ProfileStore(appContext)
    private val reader = SysfsNodeReader()

    @Volatile private var cachedLimits: Limits? = null

    private val machine =
        PerfStateMachine(
            PropSink(),
            Supplier { Snapshot.read(reader) },
            Supplier { limits() },
            Predicate { pkg -> isInstalled(pkg) },
        )

    /** Memuat sakelar dan profil tersimpan. Tidak menulis apa pun sebelum boot selesai. */
    fun load() {
        guard("load") {
            machine.setEnabled(store.enabled)
            machine.setProfiles(store.load())
        }
    }

    // ---- API untuk UI ----

    /** Daftar frekuensi/governor dari kernel. Daftar kosong = tidak terbaca (mis. GPU). */
    fun limits(): Limits {
        cachedLimits?.let { return it }
        val l = Limits.read(reader)
        // Daftar frekuensi statis; simpan hanya bila CPU terbaca supaya gagal sesaat dicoba lagi.
        if (l.cpuFreqsKhz(0).isNotEmpty()) cachedLimits = l
        return l
    }

    /** Profil tersimpan (paket yang sudah dihapus tidak ikut). Kunci "*" = Default. */
    fun profiles(): Map<String, Profile> = machine.profiles()

    /** Menyimpan profil paket (atau "*"). Profil kosong = hapus. */
    fun saveProfile(pkg: String, profile: Profile): Boolean = edit { m ->
        if (profile.isEmpty) m.remove(pkg) else m.put(pkg, profile)
    }

    fun removeProfile(pkg: String): Boolean = edit { it.remove(pkg) }

    fun isEnabled(): Boolean = store.enabled

    fun setEnabled(on: Boolean) {
        guard("setEnabled") { machine.setEnabled(on) }
        store.enabled = on
    }

    /** "Reset semua": hapus semua profil, kembalikan nilai dasar, sakelar aktif lagi. */
    fun resetAll(): Boolean {
        guard("resetAll") { machine.setProfiles(emptyMap()) }
        guard("resetAll") { machine.setEnabled(true) }
        return store.resetAll()
    }

    // ---- peristiwa ----

    fun onForeground(pkg: String) = guard("onForeground") { machine.onForeground(pkg) }

    fun onScreenOff() = guard("onScreenOff") { machine.onScreenOff() }

    fun onBootCompleted() = guard("onBootCompleted") { machine.onBootCompleted() }

    // ---- internal ----

    private fun edit(change: (MutableMap<String, Profile>) -> Unit): Boolean {
        return try {
            val m = LinkedHashMap<String, Profile>(machine.profiles())
            change(m)
            guard("setProfiles") { machine.setProfiles(m) }
            // Simpan versi state machine: paket yang sudah dihapus ikut dibersihkan.
            store.save(machine.profiles())
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengubah profil", e)
            false
        }
    }

    private fun isInstalled(pkg: String): Boolean =
        try {
            // Sebelum unlock pertama, tanpa flag ini aplikasi non-direct-boot dianggap tidak ada.
            appContext.packageManager.getPackageInfo(
                pkg,
                PackageManager.MATCH_DIRECT_BOOT_AWARE or PackageManager.MATCH_DIRECT_BOOT_UNAWARE,
            )
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            // Ragu: jangan buang profil pengguna.
            Log.e(TAG, "Gagal memeriksa paket $pkg", e)
            true
        }

    private inline fun guard(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "$what gagal", e)
        }
    }

    private companion object {
        const val TAG = "IngresPerf"
    }
}
