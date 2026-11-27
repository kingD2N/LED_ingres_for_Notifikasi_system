/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.ingres.parts.EntryPoints
import org.ingres.parts.PartsApplication
import org.ingres.parts.PartsController
import org.ingres.parts.R
import org.ingres.parts.SpeakerCleaner
import org.ingres.parts.SysNodes
import org.ingres.parts.logic.ChargePolicy
import org.ingres.parts.logic.ThermalProfile

/** Halaman Ingres Parts, dibuka dari halaman utama Setelan. */
class PartsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as? PartsApplication
        val ready = app != null && app.isReady
        setContent {
            PartsTheme {
                Surface(Modifier.fillMaxSize()) {
                    if (!ready || app == null) NotReadyScreen() else PartsNav(app.parts, app.speaker)
                }
            }
        }
    }
}

@Composable
private fun PartsNav(controller: PartsController, speaker: SpeakerCleaner) {
    var picker by rememberSaveable { mutableStateOf(false) }
    var games by remember { mutableStateOf(controller.prefs.games) }
    if (picker) {
        BackHandler { picker = false }
        GamePickerScreen(
            selected = games,
            onToggle = { pkg, on ->
                val next = if (on) games + pkg else games - pkg
                controller.prefs.games = next
                games = controller.prefs.games
                controller.applyAll()
            },
            onBack = { picker = false },
        )
    } else {
        MainScreen(controller, speaker, games.size, onOpenPicker = { picker = true })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    controller: PartsController,
    speaker: SpeakerCleaner,
    gameCount: Int,
    onOpenPicker: () -> Unit,
) {
    val prefs = controller.prefs
    val context = LocalContext.current

    var chargeMode by remember { mutableStateOf(prefs.chargeMode) }
    var chargeLimit by remember { mutableIntStateOf(prefs.chargeLimit) }
    var bypass by remember { mutableStateOf(prefs.bypassEnabled) }
    var bypassGameOnly by remember { mutableStateOf(prefs.bypassGameOnly) }
    var thermalBase by remember { mutableStateOf(prefs.thermalBase) }
    var thermalGame by remember { mutableStateOf(prefs.thermalGame) }
    var touch by remember { mutableStateOf(prefs.gameTouch) }
    var overlay by remember { mutableStateOf(prefs.gameOverlay) }
    var preview by remember { mutableStateOf(false) }
    var flat by remember { mutableStateOf(prefs.flatMode) }
    var cleaning by remember { mutableStateOf(speaker.isRunning) }

    // Status baterai diperbarui tiap 2 detik selama halaman terbuka.
    var statusTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            statusTick++
        }
    }
    DisposableEffect(speaker) {
        speaker.onStateChanged = { cleaning = it }
        onDispose {
            speaker.onStateChanged = null
            // Tutup pratinjau; overlay mode game yang sedang aktif tetap tampil.
            controller.previewOverlay(false)
        }
    }

    fun applied() = controller.applyAll()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Section(stringResource(R.string.section_battery)) {
                    BatteryStatus(controller, statusTick)
                    RadioRow(stringResource(R.string.charge_normal), stringResource(R.string.charge_normal_desc),
                        chargeMode == ChargePolicy.MODE_NORMAL) {
                        chargeMode = ChargePolicy.MODE_NORMAL; prefs.chargeMode = chargeMode; applied()
                    }
                    RadioRow(stringResource(R.string.charge_optimal), stringResource(R.string.charge_optimal_desc),
                        chargeMode == ChargePolicy.MODE_OPTIMAL80) {
                        chargeMode = ChargePolicy.MODE_OPTIMAL80; prefs.chargeMode = chargeMode; applied()
                    }
                    RadioRow(
                        stringResource(R.string.charge_limit),
                        stringResource(R.string.charge_limit_desc, chargeLimit, chargeLimit - ChargePolicy.RESUME_GAP),
                        chargeMode == ChargePolicy.MODE_LIMIT,
                    ) {
                        chargeMode = ChargePolicy.MODE_LIMIT; prefs.chargeMode = chargeMode; applied()
                    }
                    if (chargeMode == ChargePolicy.MODE_LIMIT) {
                        Slider(
                            value = chargeLimit.toFloat(),
                            onValueChange = { chargeLimit = (Math.round(it / 5f) * 5) },
                            onValueChangeFinished = { prefs.chargeLimit = chargeLimit; applied() },
                            valueRange = ChargePolicy.MIN_LIMIT.toFloat()..ChargePolicy.MAX_LIMIT.toFloat(),
                            steps = (ChargePolicy.MAX_LIMIT - ChargePolicy.MIN_LIMIT) / 5 - 1,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    SwitchRow(stringResource(R.string.bypass_title), stringResource(R.string.bypass_desc), bypass) {
                        bypass = it; prefs.bypassEnabled = it; applied()
                    }
                    if (bypass) {
                        SwitchRow(stringResource(R.string.bypass_game_only), null, bypassGameOnly) {
                            bypassGameOnly = it; prefs.bypassGameOnly = it; applied()
                        }
                    }
                }
            }

            item {
                Section(stringResource(R.string.section_thermal)) {
                    for (p in ThermalProfile.values()) {
                        RadioRow(thermalTitle(p), thermalDesc(p), thermalBase == p.key) {
                            thermalBase = p.key; prefs.thermalBase = p.key; applied()
                        }
                    }
                }
            }

            item {
                Section(stringResource(R.string.section_game)) {
                    ClickRow(stringResource(R.string.game_list), stringResource(R.string.game_list_count, gameCount), onOpenPicker)
                    Text(
                        stringResource(R.string.game_thermal),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                    )
                    RadioRow(stringResource(R.string.game_thermal_follow), null, thermalGame == ThermalProfile.KEY_FOLLOW) {
                        thermalGame = ThermalProfile.KEY_FOLLOW; prefs.thermalGame = thermalGame; applied()
                    }
                    for (p in ThermalProfile.values()) {
                        RadioRow(thermalTitle(p), null, thermalGame == p.key) {
                            thermalGame = p.key; prefs.thermalGame = p.key; applied()
                        }
                    }
                    SwitchRow(stringResource(R.string.game_touch), stringResource(R.string.game_touch_desc), touch) {
                        touch = it; prefs.gameTouch = it; applied()
                    }
                    // Driver Goodix ingres: bump_sample_rate 1 = 480 Hz, 0 = 240 Hz. Bila setelan LineageOS
                    // "High touch polling rate" aktif, di luar game tetap 480 Hz.
                    val highTouchEverywhere = remember(statusTick) { SysNodes.lineageHighTouchPolling(context) == "1" }
                    if (touch && highTouchEverywhere) {
                        Text(
                            stringResource(R.string.game_touch_warn),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    SwitchRow(stringResource(R.string.game_overlay), stringResource(R.string.game_overlay_desc), overlay) {
                        overlay = it; prefs.gameOverlay = it; applied()
                    }
                    OutlinedButton(
                        onClick = { preview = !preview; controller.previewOverlay(preview) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Text(stringResource(if (preview) R.string.overlay_hide else R.string.overlay_preview))
                    }
                }
            }

            item {
                Section(stringResource(R.string.section_display)) {
                    SwitchRow(stringResource(R.string.flat_title), stringResource(R.string.flat_desc), flat) {
                        flat = it; prefs.flatMode = it; applied()
                    }
                }
            }

            item {
                Section(stringResource(R.string.section_speaker)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(stringResource(R.string.clean_title), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.clean_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { if (cleaning) speaker.stop() else speaker.start() }) {
                            Text(stringResource(if (cleaning) R.string.clean_stop else R.string.clean_start))
                        }
                    }
                }
            }

            item {
                Section(stringResource(R.string.section_more)) {
                    for (feature in EntryPoints.FEATURES) {
                        val intent = remember(feature.pkg) { EntryPoints.intentFor(context, feature) }
                        if (intent != null) {
                            ClickRow(stringResource(feature.title), null) {
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Gagal membuka ${feature.pkg}", e)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryStatus(controller: PartsController, @Suppress("UNUSED_PARAMETER") tick: Int) {
    // tick hanya memicu gambar ulang supaya status terbaru dibaca.
    val level = controller.batteryLevel
    if (level < 0) return
    val c = controller.charge
    val state =
        when {
            c.bypass -> R.string.status_bypass
            c.suspendInput -> R.string.status_held
            controller.plugged -> R.string.status_charging
            else -> R.string.status_unplugged
        }
    Text(
        stringResource(R.string.battery_status, level, stringResource(state)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun thermalTitle(p: ThermalProfile): String =
    stringResource(
        when (p) {
            ThermalProfile.HEMAT -> R.string.thermal_hemat
            ThermalProfile.HARIAN -> R.string.thermal_harian
            ThermalProfile.SEIMBANG -> R.string.thermal_seimbang
            ThermalProfile.GAMING -> R.string.thermal_gaming
            ThermalProfile.PERFORMA -> R.string.thermal_performa
        },
    )

@Composable
private fun thermalDesc(p: ThermalProfile): String =
    stringResource(
        when (p) {
            ThermalProfile.HEMAT -> R.string.thermal_hemat_desc
            ThermalProfile.HARIAN -> R.string.thermal_harian_desc
            ThermalProfile.SEIMBANG -> R.string.thermal_seimbang_desc
            ThermalProfile.GAMING -> R.string.thermal_gaming_desc
            ThermalProfile.PERFORMA -> R.string.thermal_performa_desc
        },
    )

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            content()
        }
    }
}

@Composable
private fun RadioRow(title: String, desc: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        TitleDesc(title, desc, Modifier.weight(1f))
    }
}

@Composable
private fun SwitchRow(title: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, onValueChange = onChange, role = Role.Switch)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TitleDesc(title, desc, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ClickRow(title: String, desc: String?, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        TitleDesc(title, desc, Modifier)
    }
}

@Composable
private fun TitleDesc(title: String, desc: String?, modifier: Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (desc != null) {
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NotReadyScreen() {
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), Alignment.Center) {
        Text(stringResource(R.string.not_ready), style = MaterialTheme.typography.bodyLarge)
    }
}

private const val TAG = "IngresParts"

// Skema hijau statis (latar ikon #2E7D32).
private val lightScheme =
    lightColorScheme(
        primary = Color(0xFF2E7D32),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFB7F1B0),
        onPrimaryContainer = Color(0xFF002204),
        surfaceVariant = Color(0xFFDEE5D8),
        onSurfaceVariant = Color(0xFF424940),
    )

private val darkScheme =
    darkColorScheme(
        primary = Color(0xFF7EDB7B),
        onPrimary = Color(0xFF00390A),
        primaryContainer = Color(0xFF005313),
        onPrimaryContainer = Color(0xFF99F894),
        surfaceVariant = Color(0xFF2A3128),
        onSurfaceVariant = Color(0xFFC2C9BD),
    )

@Composable
private fun PartsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkScheme else lightScheme, content = content)
}
