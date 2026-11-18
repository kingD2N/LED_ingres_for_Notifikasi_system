/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.perf

import android.os.SystemClock
import android.os.SystemProperties
import org.ingres.perf.logic.PropPayload
import org.ingres.perf.logic.Sink

/**
 * Menulis payload ke properti sys.ingres.perf.*. Pemicu sys.ingres.perf.apply ditulis
 * paling akhir dan hanya bila semua properti lain berhasil: SystemProperties.set
 * melempar bila gagal, jadi pengecualian berhenti sebelum apply dan init tidak
 * menerapkan payload setengah jadi.
 */
class PropSink : Sink {
    override fun write(props: Map<String, String>?) {
        requireNotNull(props) { "payload kosong" }
        val apply = props[PropPayload.APPLY]
            ?: throw IllegalArgumentException("payload tanpa ${PropPayload.APPLY}")
        for ((key, value) in props) {
            if (key == PropPayload.APPLY) continue
            SystemProperties.set(key, value)
        }
        // Proses bisa mulai ulang dengan nomor urut dari awal; pastikan nilainya berubah.
        val current = SystemProperties.get(PropPayload.APPLY)
        val trigger = if (current == apply) "$apply.${SystemClock.elapsedRealtime()}" else apply
        SystemProperties.set(PropPayload.APPLY, trigger)
    }
}
