/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.perf

import java.io.File
import org.ingres.perf.logic.NodeReader

/**
 * Membaca node sysfs (di-trim). Gagal apa pun (tidak ada, ditolak SELinux seperti node
 * GPU) menghasilkan null, sehingga Limits/Snapshot menganggapnya kosong, tidak crash.
 */
class SysfsNodeReader : NodeReader {
    override fun read(path: String?): String? {
        if (path.isNullOrEmpty()) return null
        return try {
            File(path).readText().trim()
        } catch (e: Exception) {
            null
        }
    }
}
