/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.ingres.parts.logic.Tone

/**
 * Membersihkan speaker: nada 165 Hz selama 30 detik lewat speaker HP (bukan headset),
 * dengan volume media dinaikkan sementara lalu dikembalikan.
 */
class SpeakerCleaner(private val ctx: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private var track: AudioTrack? = null
    private var savedVolume = -1
    var onStateChanged: ((Boolean) -> Unit)? = null

    val isRunning: Boolean
        get() = track != null

    private val stopRunnable = Runnable { stop() }

    fun start() {
        if (track != null) return
        try {
            val pcm = Tone.oneSecond(Tone.SAMPLE_RATE, Tone.FREQUENCY_HZ, 0.9f)
            val t =
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(Tone.SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
            t.write(pcm, 0, pcm.size)
            t.setLoopPoints(0, pcm.size, -1)
            speaker()?.let { t.setPreferredDevice(it) }
            savedVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
            t.play()
            track = t
            handler.postDelayed(stopRunnable, Tone.DURATION_SECONDS * 1000L)
            onStateChanged?.invoke(true)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memutar nada pembersih", e)
            stop()
        }
    }

    fun stop() {
        handler.removeCallbacks(stopRunnable)
        track?.let {
            try {
                it.stop()
            } catch (e: Exception) {
                // sudah berhenti
            }
            it.release()
        }
        track = null
        if (savedVolume >= 0) {
            try {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0)
            } catch (e: Exception) {
                Log.w(TAG, "Gagal mengembalikan volume", e)
            }
            savedVolume = -1
        }
        onStateChanged?.invoke(false)
    }

    private fun speaker(): AudioDeviceInfo? =
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        }

    private companion object {
        const val TAG = "IngresPartsSpeaker"
    }
}
