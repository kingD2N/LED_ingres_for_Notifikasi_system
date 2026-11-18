/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.perf.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ingres.perf.PerfApplication
import org.ingres.perf.PerfController
import org.ingres.perf.R
import org.ingres.perf.logic.Profile
import org.ingres.perf.logic.ProfileCodec

/**
 * Halaman "CPU/GPU per Aplikasi", dibuka dari Setelan > Sistem (EXTRA_SETTINGS).
 * Berjalan di proses persistent yang sama dengan layanan; semua perubahan lewat
 * [PerfController] di thread utama.
 */
class PerfActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as? PerfApplication
        val controller: PerfController? = if (app != null && app.isReady) app.perf else null

        setContent {
            PerfTheme {
                Surface(Modifier.fillMaxSize()) {
                    if (controller == null) NotReadyScreen() else PerfNav(controller)
                }
            }
        }
    }
}

/** Navigasi sederhana di dalam activity: daftar <-> detail satu kunci profil. */
@Composable
private fun PerfNav(controller: PerfController) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(controller.isEnabled()) }
    var profiles by remember { mutableStateOf(controller.profiles()) }
    val limits = remember { controller.limits() }
    // Kunci profil yang sedang dibuka: null = daftar, "*" = Default, selain itu nama paket.
    var selected by rememberSaveable { mutableStateOf<String?>(null) }

    val iconPx = with(LocalDensity.current) { APP_ICON_DP.roundToPx() }
    val apps by produceState<List<AppEntry>?>(initialValue = null) {
        val pm = context.packageManager
        // Paket berprofil yang tidak punya activity peluncur tetap ditampilkan.
        val extra = controller.profiles().keys.toList()
        value = withContext(Dispatchers.Default) { loadApps(pm, extra, iconPx) }
    }

    fun refresh(ok: Boolean) {
        profiles = controller.profiles()
        if (!ok) Toast.makeText(context, R.string.save_failed, Toast.LENGTH_SHORT).show()
    }

    val key = selected
    if (key == null) {
        AppListScreen(
            enabled = enabled,
            onEnabledChange = {
                controller.setEnabled(it)
                enabled = controller.isEnabled()
            },
            profiles = profiles,
            apps = apps,
            onOpen = { selected = it },
            onResetAll = {
                val ok = controller.resetAll()
                enabled = controller.isEnabled()
                refresh(ok)
            },
        )
    } else {
        BackHandler { selected = null }
        val isDefault = key == ProfileCodec.DEFAULT_KEY
        val title =
            if (isDefault) stringResource(R.string.default_title)
            else apps?.firstOrNull { it.pkg == key }?.label ?: key
        ProfileDetailScreen(
            title = title,
            subtitle = if (isDefault) stringResource(R.string.default_summary) else key,
            profile = profiles[key] ?: Profile.empty(),
            limits = limits,
            onChange = { p -> refresh(controller.saveProfile(key, p)) },
            onRemove = { refresh(controller.removeProfile(key)) },
            onBack = { selected = null },
        )
    }
}

@Composable
private fun NotReadyScreen() {
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), Alignment.Center) {
        Text(stringResource(R.string.not_ready), style = MaterialTheme.typography.bodyLarge)
    }
}

// Skema statis seperti app LEDs (tanpa warna dinamis), diturunkan dari latar ikon #524558.
private val lightScheme =
    lightColorScheme(
        primary = Color(0xFF6B5574),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFF4D9FC),
        onPrimaryContainer = Color(0xFF52405B),
        secondary = Color(0xFF675A6B),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFEFDDF1),
        onSecondaryContainer = Color(0xFF4E4352),
        background = Color(0xFFFFF7FC),
        onBackground = Color(0xFF1F1A20),
        surface = Color(0xFFFFF7FC),
        onSurface = Color(0xFF1F1A20),
        surfaceVariant = Color(0xFFEBDFEA),
        onSurfaceVariant = Color(0xFF4C444D),
    )

private val darkScheme =
    darkColorScheme(
        primary = Color(0xFFD8BDE0),
        onPrimary = Color(0xFF3B2944),
        primaryContainer = Color(0xFF52405B),
        onPrimaryContainer = Color(0xFFF4D9FC),
        secondary = Color(0xFFD2C1D5),
        onSecondary = Color(0xFF382C3B),
        secondaryContainer = Color(0xFF4E4352),
        onSecondaryContainer = Color(0xFFEFDDF1),
        background = Color(0xFF171218),
        onBackground = Color(0xFFEAE0E8),
        surface = Color(0xFF171218),
        onSurface = Color(0xFFEAE0E8),
        surfaceVariant = Color(0xFF4C444D),
        onSurfaceVariant = Color(0xFFCFC3CE),
    )

@Composable
private fun PerfTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkScheme else lightScheme,
        content = content,
    )
}
