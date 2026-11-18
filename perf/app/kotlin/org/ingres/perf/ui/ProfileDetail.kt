/*
 * SPDX-FileCopyrightText: 2026 kingD2N
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ingres.perf.ui

import android.content.res.Resources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.ingres.perf.R
import org.ingres.perf.logic.Limits
import org.ingres.perf.logic.Profile

private val CLUSTER_TITLES =
    intArrayOf(R.string.cluster_little, R.string.cluster_big, R.string.cluster_prime)
private val CLUSTER_SHORT =
    intArrayOf(R.string.cluster_little_short, R.string.cluster_big_short, R.string.cluster_prime_short)

// ---- format ----

/** CPU: kHz -> "307 MHz" / "1,80 GHz" (pemisah desimal mengikuti locale). */
fun cpuFreqText(res: Resources, khz: Int): String =
    if (khz >= 1_000_000) res.getString(R.string.freq_ghz, "%.2f".format(khz / 1_000_000.0))
    else res.getString(R.string.freq_mhz, (khz / 1000).toString())

/** GPU: Hz -> "818 MHz". */
fun gpuFreqText(res: Resources, hz: Long): String =
    res.getString(R.string.freq_mhz, (hz / 1_000_000L).toString())

private fun rangeText(res: Resources, min: String?, max: String?): String? =
    when {
        min != null && max != null -> res.getString(R.string.range_min_max, min, max)
        min != null -> res.getString(R.string.range_min_only, min)
        max != null -> res.getString(R.string.range_max_only, max)
        else -> null
    }

/** Ringkasan satu baris, mis. "Prime ≤ 2,40 GHz, performance · GPU ≤ 818 MHz". Null bila kosong. */
fun profileSummary(res: Resources, p: Profile): String? {
    if (p.isEmpty) return null
    val parts = ArrayList<String>()
    for (c in 0 until Profile.CLUSTERS) {
        val min = p.minKhz(c).takeIf { it > 0 }?.let { cpuFreqText(res, it) }
        val max = p.maxKhz(c).takeIf { it > 0 }?.let { cpuFreqText(res, it) }
        val bits = listOfNotNull(rangeText(res, min, max), p.governor(c))
        if (bits.isNotEmpty()) {
            parts += res.getString(R.string.summary_item, res.getString(CLUSTER_SHORT[c]), bits.joinToString(", "))
        }
    }
    val gmin = p.gpuMinHz().takeIf { it > 0 }?.let { gpuFreqText(res, it) }
    val gmax = p.gpuMaxHz().takeIf { it > 0 }?.let { gpuFreqText(res, it) }
    rangeText(res, gmin, gmax)?.let {
        parts += res.getString(R.string.summary_item, res.getString(R.string.gpu_title), it)
    }
    return parts.joinToString(" · ")
}

/** Salinan [this] dengan perubahan dari [change]. */
private fun Profile.edit(change: (Profile.Builder) -> Unit): Profile {
    val b = Profile.Builder()
    for (c in 0 until Profile.CLUSTERS) b.cluster(c, minKhz(c), maxKhz(c), governor(c))
    b.gpu(gpuMinHz(), gpuMaxHz())
    change(b)
    return b.build()
}

// ---- halaman detail ----

/**
 * Detail satu profil. Pilihan hanya dari daftar kernel ([Limits]); bagian tanpa daftar
 * disembunyikan. Min yang melebihi max (dan sebaliknya) tidak bisa dipilih. Tiap pilihan
 * langsung disimpan lewat [onChange]; "Tidak diatur" = 0/null = mengikuti sistem.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDetailScreen(
    title: String,
    subtitle: String?,
    profile: Profile,
    limits: Limits,
    onChange: (Profile) -> Unit,
    onRemove: () -> Unit,
    onBack: () -> Unit,
) {
    val res = LocalContext.current.resources
    val cpuFreqs = remember(limits) { List(Profile.CLUSTERS) { limits.cpuFreqsKhz(it).toList() } }
    val cpuGovs = remember(limits) { List(Profile.CLUSTERS) { limits.cpuGovernors(it).toList() } }
    val gpuFreqs = remember(limits) { limits.gpuFreqsHz().toList() }
    val anyLimits = gpuFreqs.isNotEmpty() || cpuFreqs.any { it.isNotEmpty() } || cpuGovs.any { it.isNotEmpty() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Hint(stringResource(if (anyLimits) R.string.not_set_hint else R.string.limits_unavailable))

            for (c in 0 until Profile.CLUSTERS) {
                val freqs = cpuFreqs[c]
                val govs = cpuGovs[c]
                if (freqs.isNotEmpty() || govs.isNotEmpty()) {
                    val min = profile.minKhz(c)
                    val max = profile.maxKhz(c)
                    val gov: String? = profile.governor(c)
                    SectionTitle(stringResource(CLUSTER_TITLES[c]))
                    if (freqs.isNotEmpty()) {
                        ChoiceRow(
                            title = stringResource(R.string.min_freq),
                            current = min.takeIf { it > 0 },
                            options = freqs,
                            optionText = { cpuFreqText(res, it) },
                            optionEnabled = { max == 0 || it <= max },
                            onSelect = { v -> onChange(profile.edit { it.cluster(c, v ?: 0, max, gov) }) },
                        )
                        ChoiceRow(
                            title = stringResource(R.string.max_freq),
                            current = max.takeIf { it > 0 },
                            options = freqs,
                            optionText = { cpuFreqText(res, it) },
                            optionEnabled = { min == 0 || it >= min },
                            onSelect = { v -> onChange(profile.edit { it.cluster(c, min, v ?: 0, gov) }) },
                        )
                    }
                    if (govs.isNotEmpty()) {
                        ChoiceRow(
                            title = stringResource(R.string.governor),
                            current = gov,
                            options = govs,
                            optionText = { it },
                            onSelect = { g -> onChange(profile.edit { it.cluster(c, min, max, g) }) },
                        )
                    }
                }
            }

            // GPU: hanya min/max (governor GPU tidak di versi pertama).
            if (gpuFreqs.isNotEmpty()) {
                val gmin = profile.gpuMinHz()
                val gmax = profile.gpuMaxHz()
                SectionTitle(stringResource(R.string.gpu_title))
                ChoiceRow(
                    title = stringResource(R.string.min_freq),
                    current = gmin.takeIf { it > 0 },
                    options = gpuFreqs,
                    optionText = { gpuFreqText(res, it) },
                    optionEnabled = { gmax == 0L || it <= gmax },
                    onSelect = { v -> onChange(profile.edit { it.gpu(v ?: 0L, gmax) }) },
                )
                ChoiceRow(
                    title = stringResource(R.string.max_freq),
                    current = gmax.takeIf { it > 0 },
                    options = gpuFreqs,
                    optionText = { gpuFreqText(res, it) },
                    optionEnabled = { gmin == 0L || it >= gmin },
                    onSelect = { v -> onChange(profile.edit { it.gpu(gmin, v ?: 0L) }) },
                )
            }

            OutlinedButton(
                onClick = onRemove,
                enabled = !profile.isEmpty,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text(stringResource(R.string.remove_profile))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Baris dropdown: "Tidak diatur" (null) lalu [options]. Opsi dengan [optionEnabled] false
 * tampil tapi tidak bisa dipilih (dipakai untuk min <= max).
 */
@Composable
private fun <T : Any> ChoiceRow(
    title: String,
    current: T?,
    options: List<T>,
    optionText: (T) -> String,
    onSelect: (T?) -> Unit,
    optionEnabled: (T) -> Boolean = { true },
) {
    var expanded by remember { mutableStateOf(false) }
    val notSet = stringResource(R.string.not_set)
    Box {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(if (current == null) notSet else optionText(current)) },
            trailingContent = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.clickable { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(notSet) },
                onClick = {
                    expanded = false
                    if (current != null) onSelect(null)
                },
                trailingIcon =
                    if (current == null) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
            )
            for (o in options) {
                DropdownMenuItem(
                    text = { Text(optionText(o)) },
                    onClick = {
                        expanded = false
                        if (o != current) onSelect(o)
                    },
                    enabled = optionEnabled(o),
                    trailingIcon =
                        if (o == current) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else {
                            null
                        },
                )
            }
        }
    }
}
