/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.perf.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import org.ingres.perf.R
import org.ingres.perf.logic.Profile
import org.ingres.perf.logic.ProfileCodec

/** Satu aplikasi di daftar. Ikon sudah dirender ke bitmap di luar thread utama. */
data class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap?)

private const val TAG = "IngresPerf"

/**
 * Aplikasi berikon peluncur (ACTION_MAIN/CATEGORY_LAUNCHER), satu entri per paket, ditambah
 * paket [extra] yang sudah punya profil tapi tidak muncul di peluncur. Lambat (label + ikon):
 * panggil di luar thread utama.
 */
fun loadApps(pm: PackageManager, extra: Collection<String>, iconPx: Int): List<AppEntry> {
    val out = LinkedHashMap<String, AppEntry>()
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val infos =
        try {
            pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0L))
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membaca daftar aplikasi", e)
            emptyList()
        }
    for (ri in infos) {
        val ai = ri.activityInfo ?: continue
        val pkg = ai.packageName ?: continue
        if (out.containsKey(pkg)) continue
        try {
            out[pkg] = entry(pkg, ai.applicationInfo.loadLabel(pm), ri.loadIcon(pm), iconPx)
        } catch (e: Exception) {
            Log.w(TAG, "Gagal memuat $pkg", e)
        }
    }
    for (pkg in extra) {
        if (pkg == ProfileCodec.DEFAULT_KEY || out.containsKey(pkg)) continue
        try {
            val ai = pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
            out[pkg] = entry(pkg, ai.loadLabel(pm), ai.loadIcon(pm), iconPx)
        } catch (e: Exception) {
            // Sudah dihapus: tidak ditampilkan.
        }
    }
    return out.values.toList()
}

private fun entry(pkg: String, label: CharSequence?, icon: Drawable?, iconPx: Int): AppEntry {
    val bitmap =
        try {
            icon?.toBitmap(iconPx, iconPx)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    val name = label?.toString()?.takeIf { it.isNotBlank() } ?: pkg
    return AppEntry(pkg, name, bitmap)
}

/**
 * Halaman utama: sakelar utama, entri Default, pencarian, daftar aplikasi.
 * Aplikasi berprofil diurutkan paling atas dan menampilkan ringkasan profilnya.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    profiles: Map<String, Profile>,
    apps: List<AppEntry>?,
    onOpen: (String) -> Unit,
    onResetAll: () -> Unit,
) {
    val res = LocalContext.current.resources
    var query by rememberSaveable { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }

    val shown =
        remember(apps, profiles, query) {
            val q = query.trim()
            apps.orEmpty()
                .filter {
                    q.isEmpty() ||
                        it.label.contains(q, ignoreCase = true) ||
                        it.pkg.contains(q, ignoreCase = true)
                }
                .sortedWith(
                    compareBy<AppEntry>({ !profiles.containsKey(it.pkg) }, { it.label.lowercase() })
                )
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.perf_settings_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    TextButton(onClick = { confirmReset = true }) {
                        Text(stringResource(R.string.reset_all))
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item(key = "_master") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.master_switch_title)) },
                    supportingContent = { Text(stringResource(R.string.master_switch_summary)) },
                    trailingContent = { Switch(checked = enabled, onCheckedChange = onEnabledChange) },
                    modifier = Modifier.clickable { onEnabledChange(!enabled) },
                )
            }
            item(key = "_default") {
                val summary = profiles[ProfileCodec.DEFAULT_KEY]?.let { profileSummary(res, it) }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.default_title)) },
                    supportingContent = {
                        Text(
                            summary ?: stringResource(R.string.default_summary),
                            color =
                                if (summary != null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingContent = {
                        Box(Modifier.size(APP_ICON_DP), Alignment.Center) {
                            Icon(Icons.Filled.Settings, contentDescription = null)
                        }
                    },
                    modifier = Modifier.clickable { onOpen(ProfileCodec.DEFAULT_KEY) },
                )
            }
            item(key = "_search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon =
                        if (query.isNotEmpty()) {
                            {
                                IconButton(onClick = { query = "" }) {
                                    Icon(
                                        Icons.Filled.Clear,
                                        contentDescription = stringResource(R.string.clear_search),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                )
            }
            when {
                apps == null -> item(key = "_loading") { MessageRow(stringResource(R.string.apps_loading)) }
                shown.isEmpty() -> item(key = "_empty") { MessageRow(stringResource(R.string.apps_empty)) }
                else ->
                    items(shown, key = { it.pkg }) { app ->
                        AppRow(app, profiles[app.pkg]?.let { profileSummary(res, it) }) {
                            onOpen(app.pkg)
                        }
                    }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        onResetAll()
                    }
                ) {
                    Text(stringResource(R.string.reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) }
            },
            title = { Text(stringResource(R.string.reset_all_confirm_title)) },
            text = { Text(stringResource(R.string.reset_all_confirm_message)) },
        )
    }
}

@Composable
private fun AppRow(app: AppEntry, summary: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent =
            if (summary != null) {
                { Text(summary, color = MaterialTheme.colorScheme.primary) }
            } else {
                null
            },
        leadingContent = {
            val icon = app.icon
            if (icon != null) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(APP_ICON_DP))
            } else {
                Spacer(Modifier.size(APP_ICON_DP))
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun MessageRow(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Ukuran ikon aplikasi di daftar; juga ukuran bitmap yang dirender loadApps. */
internal val APP_ICON_DP = 40.dp
