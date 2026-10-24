package de.heckenmann.visualagent.ui.settings.agent

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import de.heckenmann.visualagent.protocol.MainAgentMemoryPort
import de.heckenmann.visualagent.protocol.MainAgentMemoryUpdate
import de.heckenmann.visualagent.ui.components.ActionIconButton
import de.heckenmann.visualagent.ui.components.toUiErrorMessage
import de.heckenmann.visualagent.ui.settings.initialMainAgentMemorySnapshot
import de.heckenmann.visualagent.ui.settings.mainAgentMemorySection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Retains a versioned memory draft while global Settings changes destination. */
internal class MainAgentMemoryDraft {
    val snapshot = mutableStateOf(initialMainAgentMemorySnapshot())
    val content = mutableStateOf("")
    val loaded = mutableStateOf(false)
    val busy = mutableStateOf(false)
    val status = mutableStateOf("")
}

/** Edits the versioned memory resource independently of the surrounding settings draft. */
@Composable
internal fun MainAgentMemoryEditor(
    port: MainAgentMemoryPort,
    state: MainAgentMemoryDraft = remember(port) { MainAgentMemoryDraft() },
) {
    val scope = rememberCoroutineScope()
    var snapshot by state.snapshot
    var content by state.content
    var loaded by state.loaded
    var busy by state.busy
    var status by state.status

    /** Loads the revisioned memory after initial entry or explicit reload. */
    fun load() {
        scope.launch {
            busy = true
            try {
                snapshot = withContext(Dispatchers.IO) { port.snapshot() }
                content = snapshot.content
                loaded = true
                status = ""
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                status = error.toUiErrorMessage()
            } finally {
                busy = false
            }
        }
    }
    LaunchedEffect(port) { if (!loaded || content == snapshot.content) load() }
    mainAgentMemorySection(content, snapshot, snapshot.limit) { content = it }
    Row {
        ActionIconButton(Icons.Filled.Refresh, "Reload main-agent memory", enabled = !busy, onClick = ::load)
        ActionIconButton(
            Icons.Filled.Save,
            "Save main-agent memory",
            enabled = loaded && !busy && content != snapshot.content && content.codePointCount(0, content.length) <= snapshot.limit,
            onClick = {
                val submitted = content
                val revision = snapshot.revision
                busy = true
                scope.launch {
                    try {
                        when (val result = withContext(Dispatchers.IO) { port.replace(submitted, revision) }) {
                            is MainAgentMemoryUpdate.Saved -> {
                                snapshot = result.snapshot
                                status = "Saved main-agent memory"
                            }
                            is MainAgentMemoryUpdate.Conflict -> {
                                snapshot = result.snapshot
                                status = "Memory changed elsewhere. Your draft is retained; review it before saving again."
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        status = error.toUiErrorMessage()
                    } finally {
                        busy = false
                    }
                }
            },
        )
    }
    if (status.isNotBlank()) Text(status)
}
