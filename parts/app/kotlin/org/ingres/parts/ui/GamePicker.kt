/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.parts.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ingres.parts.R

private data class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap?)

/** Daftar aplikasi berikon peluncur dengan kotak centang; yang dicentang = game. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamePickerScreen(selected: Set<String>, onToggle: (String, Boolean) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val iconPx = with(LocalDensity.current) { 40.dp.roundToPx() }
    var query by rememberSaveable { mutableStateOf("") }
    val apps by
        produceState<List<AppEntry>?>(initialValue = null) {
            value = withContext(Dispatchers.Default) { loadApps(context.packageManager, selected, iconPx) }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.picker_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text(stringResource(R.string.apps_loading)) }
            } else {
                val q = query.trim()
                // Game terpilih di atas, lalu urut nama.
                val shown =
                    list.filter { q.isEmpty() || it.label.contains(q, true) || it.pkg.contains(q, true) }
                        .sortedWith(compareBy<AppEntry>({ it.pkg !in selected }, { it.label.lowercase() }))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(shown, key = { it.pkg }) { app ->
                        val checked = app.pkg in selected
                        Row(
                            Modifier.fillMaxWidth()
                                .toggleable(value = checked, onValueChange = { onToggle(app.pkg, it) }, role = Role.Checkbox)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val icon = app.icon
                            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(40.dp))
                            else Spacer(Modifier.size(40.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    app.pkg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Checkbox(checked = checked, onCheckedChange = null)
                        }
                    }
                }
            }
        }
    }
}

/** Lambat (label + ikon): panggil di luar thread utama. */
private fun loadApps(pm: PackageManager, extra: Collection<String>, iconPx: Int): List<AppEntry> {
    val out = LinkedHashMap<String, AppEntry>()
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val infos =
        try {
            pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0L))
        } catch (e: Exception) {
            Log.e("IngresParts", "Gagal membaca daftar aplikasi", e)
            emptyList()
        }
    for (ri in infos) {
        val ai = ri.activityInfo ?: continue
        val pkg = ai.packageName ?: continue
        if (out.containsKey(pkg)) continue
        try {
            out[pkg] = AppEntry(pkg, ai.applicationInfo.loadLabel(pm).toString(), icon(ri.loadIcon(pm), iconPx))
        } catch (e: Exception) {
            Log.w("IngresParts", "Gagal memuat $pkg", e)
        }
    }
    // Game terpilih yang tidak punya ikon peluncur tetap ditampilkan supaya bisa dilepas.
    for (pkg in extra) {
        if (out.containsKey(pkg)) continue
        try {
            val ai = pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
            out[pkg] = AppEntry(pkg, ai.loadLabel(pm).toString(), icon(ai.loadIcon(pm), iconPx))
        } catch (e: Exception) {
            out[pkg] = AppEntry(pkg, pkg, null)
        }
    }
    return out.values.toList()
}

private fun icon(d: android.graphics.drawable.Drawable?, px: Int): ImageBitmap? =
    try {
        d?.toBitmap(px, px)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
