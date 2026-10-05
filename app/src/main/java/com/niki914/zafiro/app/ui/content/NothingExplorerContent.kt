package com.niki914.zafiro.app.ui.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.niki914.libterm.runtime.LibTerm
import com.niki914.libterm.runtime.TermResult
import com.niki914.uikit.infra.liquidScreenTopPadding
import kotlinx.coroutines.launch

private data class NothingSettingEntry(
    val namespace: String,
    val key: String,
    val value: String,
)

private data class NothingExplorerState(
    val loading: Boolean = true,
    val error: String? = null,
    val all: List<NothingSettingEntry> = emptyList(),
)

private data class NothingSettingChange(
    val namespace: String,
    val key: String,
    val before: String?,
    val after: String?,
)

@Composable
fun NothingExplorerContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(NothingExplorerState()) }
    var query by remember { mutableStateOf("") }
    var baseline by remember { mutableStateOf<List<NothingSettingEntry>?>(null) }

    fun refresh() {
        if (state.loading && state.all.isNotEmpty()) return
        state = state.copy(loading = true, error = null)
        scope.launch {
            state = loadNothingSettings(context)
        }
    }

    LaunchedEffect(context) {
        state = loadNothingSettings(context)
    }

    val trimmedQuery = query.trim()
    val changes = remember(state.all, baseline) {
        baseline?.let { diffSettings(it, state.all) }.orEmpty()
    }
    val changedKeys = remember(changes) {
        changes.map { "${it.namespace}:${it.key}" }.toSet()
    }
    val visible = remember(state.all, trimmedQuery) {
        if (trimmedQuery.isBlank()) {
            state.all.filter(::isNothingCandidate)
        } else {
            state.all.filter {
                it.namespace.contains(trimmedQuery, ignoreCase = true) ||
                    it.key.contains(trimmedQuery, ignoreCase = true) ||
                    it.value.contains(trimmedQuery, ignoreCase = true)
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = liquidScreenTopPadding() + 12.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "NOTHING EXPLORER",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "Read-only scan of Android settings exposed by Nothing OS. Search reveals any key across global, system and secure namespaces.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search key or value") },
                placeholder = { Text("glyph, aod, refresh, nothing…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { baseline = state.all },
                    enabled = !state.loading && state.all.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (baseline == null) "Capture baseline" else "Replace baseline")
                }
                if (baseline != null) {
                    Button(
                        onClick = { baseline = null },
                    ) {
                        Text("Clear")
                    }
                }
            }
        }

        if (baseline != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = if (changes.isEmpty()) "NO CHANGES" else "${changes.size} CHANGED SETTINGS",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Capture a baseline, change something in Nothing OS, then tap Refresh. Modified, added and removed settings are highlighted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (trimmedQuery.isBlank()) {
                        "${visible.size} Nothing/vendor candidates"
                    } else {
                        "${visible.size} matches · ${state.all.size} total settings"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 12.dp, end = 12.dp),
                )
                Button(
                    onClick = ::refresh,
                    enabled = !state.loading,
                ) {
                    Text(if (state.loading) "Scanning…" else "Refresh")
                }
            }
        }

        state.error?.let { error ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        if (!state.loading && state.error == null && visible.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (trimmedQuery.isBlank()) {
                            "No Nothing-specific candidates found. Try searching for a broader term."
                        } else {
                            "No settings matched “$trimmedQuery”."
                        },
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        items(
            items = visible,
            key = { "${it.namespace}:${it.key}" },
        ) { entry ->
            NothingSettingCard(
                entry = entry,
                changed = "${entry.namespace}:${entry.key}" in changedKeys,
                change = changes.firstOrNull {
                    it.namespace == entry.namespace && it.key == entry.key
                },
            )
        }
    }
}

@Composable
private fun NothingSettingCard(
    entry: NothingSettingEntry,
    changed: Boolean,
    change: NothingSettingChange?,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = entry.key,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = entry.value.ifBlank { "(empty)" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${entry.namespace.uppercase()} · ${classifyNothingSetting(entry)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (changed) {
                    Text(
                        text = "CHANGED",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (change != null) {
                Text(
                    text = "${change.before ?: "(missing)"} → ${change.after ?: "(removed)"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

private suspend fun loadNothingSettings(context: android.content.Context): NothingExplorerState {
    val term = LibTerm.openShizukuTerm(context)
    return try {
        val command = """
            printf '__GLOBAL__\n'
            settings list global
            printf '__SYSTEM__\n'
            settings list system
            printf '__SECURE__\n'
            settings list secure
        """.trimIndent()

        val output = when (val result = term.exec(command, timeoutMillis = 15_000L)) {
            is TermResult.Success -> result.value.stdout.toByteArray().decodeToString()
            is TermResult.Failure -> {
                return NothingExplorerState(
                    loading = false,
                    error = "Shizuku shell failed: ${result.failure}",
                )
            }
        }

        NothingExplorerState(
            loading = false,
            all = parseSettingsDump(output),
        )
    } catch (throwable: Throwable) {
        NothingExplorerState(
            loading = false,
            error = "Scan failed: ${throwable.message ?: throwable.javaClass.simpleName}",
        )
    } finally {
        term.close()
    }
}

private fun parseSettingsDump(output: String): List<NothingSettingEntry> {
    var namespace = ""
    val result = mutableListOf<NothingSettingEntry>()

    output.lineSequence().forEach { raw ->
        val line = raw.trim()
        when (line) {
            "__GLOBAL__" -> namespace = "global"
            "__SYSTEM__" -> namespace = "system"
            "__SECURE__" -> namespace = "secure"
            else -> {
                if (namespace.isBlank() || !line.contains('=')) return@forEach
                val key = line.substringBefore('=').trim()
                if (key.isBlank()) return@forEach
                result += NothingSettingEntry(
                    namespace = namespace,
                    key = key,
                    value = line.substringAfter('=', missingDelimiterValue = "").trim(),
                )
            }
        }
    }

    return result.sortedWith(
        compareBy<NothingSettingEntry> { it.namespace }
            .thenBy { it.key.lowercase() }
    )
}

private fun isNothingCandidate(entry: NothingSettingEntry): Boolean {
    val haystack = "${entry.key} ${entry.value}".lowercase()
    return NOTHING_KEYWORDS.any(haystack::contains)
}

private val NOTHING_KEYWORDS = listOf(
    "nothing",
    "glyph",
    "nt_",
    "nt.",
    "essential",
    "aod",
    "always_on",
    "flip_to",
)


private fun diffSettings(
    before: List<NothingSettingEntry>,
    after: List<NothingSettingEntry>,
): List<NothingSettingChange> {
    fun keyOf(entry: NothingSettingEntry) = "${entry.namespace}:${entry.key}"
    val beforeMap = before.associateBy(::keyOf)
    val afterMap = after.associateBy(::keyOf)

    return (beforeMap.keys + afterMap.keys)
        .asSequence()
        .distinct()
        .mapNotNull { id ->
            val old = beforeMap[id]
            val new = afterMap[id]
            if (old?.value == new?.value) return@mapNotNull null
            NothingSettingChange(
                namespace = new?.namespace ?: old!!.namespace,
                key = new?.key ?: old!!.key,
                before = old?.value,
                after = new?.value,
            )
        }
        .sortedWith(compareBy<NothingSettingChange> { it.namespace }.thenBy { it.key })
        .toList()
}

private fun classifyNothingSetting(entry: NothingSettingEntry): String {
    val text = "${entry.key} ${entry.value}".lowercase()
    return when {
        listOf("glyph", "led", "light").any(text::contains) -> "Glyph"
        listOf("aod", "always_on", "ambient", "lock_screen").any(text::contains) -> "Display"
        listOf("refresh", "fps", "hz", "display").any(text::contains) -> "Display"
        listOf("battery", "charge", "power").any(text::contains) -> "Power"
        listOf("essential", "space").any(text::contains) -> "Essential"
        listOf("gesture", "flip_to", "motion").any(text::contains) -> "Gestures"
        listOf("sound", "audio", "volume").any(text::contains) -> "Audio"
        else -> "Vendor"
    }
}
