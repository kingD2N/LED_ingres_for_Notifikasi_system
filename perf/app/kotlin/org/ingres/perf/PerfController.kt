/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.perf

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemProperties
import android.util.Log
import java.util.function.Predicate
import java.util.function.Supplier
import org.ingres.perf.logic.Limits
import org.ingres.perf.logic.PerfStateMachine
import org.ingres.perf.logic.Profile
import org.ingres.perf.logic.ProfileCodec
import org.ingres.perf.logic.PropPayload
import org.ingres.perf.logic.Snapshot

/**
 * Perekat ProfileStore <-> PerfStateMachine <-> PropSink, dan API untuk UI.
 *
 * Thread: semua metode dipanggil dari thread utama saja (UI, receiver, Debouncer lewat
 * Handler utama, ForegroundWatcher). PerfStateMachine sendiri synchronized.
 * Kegagalan menulis (Sink/Supplier melempar) dicatat lalu diabaikan: state machine tetap
 * konsisten dan peristiwa berikutnya mencoba lagi. Proses persistent tidak boleh crash.
 *
 * Snapshot nilai dasar disimpan di ProfileStore selama sesi tersetel. Bila proses mulai
 * ulang di boot yang sama (sys.ingres.perf.apply sudah terisi), snapshot tersimpan dipakai
 * untuk sesi berikutnya, bukan membaca ulang nilai yang sedang disetel.
 */
class PerfController(context: Context) {
    private val appContext: Context = context.applicationContext ?: context
    private val store = ProfileStore(appContext)
    private val reader = SysfsNodeReader()

    @Volatile private var cachedLimits: Limits? = null

    /**
     * Snapshot tersimpan dari proses sebelumnya di boot ini; dipakai oleh pengambilan
     * snapshot berikutnya dan dibuang setelah state machine memegang snapshot.
     */
    private var persistedSnapshot: String? = initPersistedSnapshot()

    private val machine =
        PerfStateMachine(
            PropSink(),
            Supplier { takeSnapshot() },
            Supplier { limits() },
            Predicate { pkg -> isInstalled(pkg) },
        )

    /** Memuat sakelar dan profil tersimpan. Tidak menulis apa pun sebelum boot selesai. */
    fun load() {
        guarded("load") {
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
        guarded("setEnabled") { machine.setEnabled(on) }
        store.enabled = on
    }

    /** "Reset semua": hapus semua profil, kembalikan nilai dasar, sakelar aktif lagi. */
    fun resetAll(): Boolean {
        guarded("resetAll") { machine.setProfiles(emptyMap()) }
        guarded("resetAll") { machine.setEnabled(true) }
        // store.resetAll() menghapus semua preferensi, termasuk snapshot.
        return store.resetAll()
    }

    // ---- peristiwa ----

    fun onForeground(pkg: String) = guarded("onForeground") { machine.onForeground(pkg) }

    fun onScreenOff() = guarded("onScreenOff") { machine.onScreenOff() }

    fun onBootCompleted() = guarded("onBootCompleted") { machine.onBootCompleted() }

    // ---- internal ----

    private fun edit(change: (MutableMap<String, Profile>) -> Unit): Boolean {
        return try {
            val m = LinkedHashMap<String, Profile>(machine.profiles())
            change(m)
            guarded("setProfiles") { machine.setProfiles(m) }
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

    /** Boot baru: snapshot lama tidak berlaku (sysfs kembali ke awal), jadi dihapus. */
    private fun initPersistedSnapshot(): String? =
        try {
            val saved = store.loadSnapshot()
            if (SystemProperties.get(PropPayload.APPLY).isNotEmpty() && !saved.isNullOrEmpty()) {
                saved
            } else {
                store.clearSnapshot()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memuat snapshot tersimpan", e)
            null
        }

    /**
     * Supplier snapshot state machine. Melempar bila node yang diiklankan kernel tidak
     * terbaca: state machine tidak menyetel apa pun dan peristiwa berikutnya mencoba lagi.
     */
    private fun takeSnapshot(): Snapshot {
        val lim = limits()
        val saved = persistedSnapshot
        if (saved != null) {
            val s = Snapshot.of(ProfileCodec.decode(saved))
            if (s.isUsableFor(lim)) {
                // Pastikan tetap ada di store (mis. sesudah "Reset semua" menghapus semuanya).
                store.saveSnapshot(saved)
                return s
            }
            Log.w(TAG, "Snapshot tersimpan tidak lengkap, dibaca ulang")
            persistedSnapshot = null
        }
        val s = Snapshot.read(reader)
        check(s.isUsableFor(lim)) { "snapshot tidak terbaca" }
        store.saveSnapshot(ProfileCodec.encode(s.values()))
        return s
    }

    /**
     * guard() lalu menyelaraskan snapshot tersimpan dengan state machine: dipegang state
     * machine -> salinan dari proses lama tidak dipakai lagi; tidak ada sesi tersetel -> hapus
     * dari store, kecuali snapshot proses lama yang belum terpakai (masih nilai dasar asli).
     */
    private inline fun guarded(what: String, block: () -> Unit) {
        guard(what, block)
        guard("syncSnapshot") {
            if (machine.hasSnapshot()) {
                persistedSnapshot = null
            } else if (persistedSnapshot == null) {
                store.clearSnapshot()
            }
        }
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
