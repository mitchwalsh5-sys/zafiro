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

@Composable
fun NothingExplorerContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(NothingExplorerState()) }
    var query by remember { mutableStateOf("") }

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
            NothingSettingCard(entry)
        }
    }
}

@Composable
private fun NothingSettingCard(entry: NothingSettingEntry) {
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
            Text(
                text = entry.namespace.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
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
