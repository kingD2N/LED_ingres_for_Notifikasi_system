/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.perf

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.ingres.perf.logic.Profile
import org.ingres.perf.logic.ProfileCodec

/**
 * Profil permanen di penyimpanan device-protected (bisa dibaca sebelum unlock pertama,
 * jadi profil Default berlaku sejak LOCKED_BOOT_COMPLETED). Semua tulis pakai commit()
 * (atomik: SharedPreferences menulis berkas sementara lalu mengganti).
 */
class ProfileStore(ctx: Context) {
    private val prefs: SharedPreferences =
        ctx.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): Map<String, Profile> =
        try {
            ProfileCodec.decodeAll(prefs.getString(KEY_PROFILES, null))
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membaca profil", e)
            emptyMap()
        }

    fun save(profiles: Map<String, Profile>): Boolean {
        val ok = prefs.edit().putString(KEY_PROFILES, ProfileCodec.encodeAll(profiles)).commit()
        if (!ok) Log.e(TAG, "Gagal menyimpan profil")
        return ok
    }

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) {
            if (!prefs.edit().putBoolean(KEY_ENABLED, value).commit()) {
                Log.e(TAG, "Gagal menyimpan sakelar utama")
            }
        }

    /**
     * Snapshot nilai dasar (ProfileCodec) selama sesi tersetel, supaya proses yang mulai
     * ulang tidak mengambil nilai yang sedang disetel sebagai nilai dasar.
     */
    fun saveSnapshot(encoded: String): Boolean {
        val ok = prefs.edit().putString(KEY_SNAPSHOT, encoded).commit()
        if (!ok) Log.e(TAG, "Gagal menyimpan snapshot")
        return ok
    }

    fun loadSnapshot(): String? =
        try {
            prefs.getString(KEY_SNAPSHOT, null)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membaca snapshot", e)
            null
        }

    fun clearSnapshot() {
        // Dipanggil sesudah tiap peristiwa: tulis ke disk hanya bila memang ada.
        if (!prefs.contains(KEY_SNAPSHOT)) return
        if (!prefs.edit().remove(KEY_SNAPSHOT).commit()) Log.e(TAG, "Gagal menghapus snapshot")
    }

    /** Menghapus semua profil (dan snapshot) dan mengembalikan sakelar utama ke aktif. */
    fun resetAll(): Boolean {
        val ok = prefs.edit().clear().commit()
        if (!ok) Log.e(TAG, "Gagal reset")
        return ok
    }

    private companion object {
        const val TAG = "IngresPerfStore"
        const val PREFS = "perf"
        const val KEY_PROFILES = "profiles"
        const val KEY_ENABLED = "enabled"
        const val KEY_SNAPSHOT = "snapshot"
    }
}
