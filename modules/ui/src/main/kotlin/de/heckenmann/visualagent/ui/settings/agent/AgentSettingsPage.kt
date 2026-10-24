package de.heckenmann.visualagent.ui.settings.agent

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.heckenmann.visualagent.protocol.MainAgentMemoryPort
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.SettingsSection
import de.heckenmann.visualagent.ui.components.PanelSection
import de.heckenmann.visualagent.ui.settings.conversationNumberSetting
import de.heckenmann.visualagent.ui.settings.conversationSettingField
import de.heckenmann.visualagent.ui.settings.forms.ScopedSettingsForm
import de.heckenmann.visualagent.ui.settings.navigation.SettingsPageDraft

/** Groups server-owned instructions, durable memory and execution limits outside Conversation. */
@Composable
internal fun AgentSettingsPage(
    settings: SettingsPort,
    memory: MainAgentMemoryPort,
    state: SettingsPageDraft,
    memoryDraft: MainAgentMemoryDraft = remember(memory) { MainAgentMemoryDraft() },
    onSaved: () -> Unit,
) {
    ScopedSettingsForm(settings, SettingsSection.AGENT, state, onSaved) { draft, change ->
        PanelSection("Agent instructions") {
            conversationSettingField("Model instruction", "Persistent guidance for the main agent, such as language and tone.") {
                OutlinedTextField(
                    value = draft.userModelInstruction,
                    onValueChange = { change(draft.copy(userModelInstruction = it)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Model instruction" },
                )
            }
        }
        MainAgentMemoryEditor(memory, memoryDraft, configuredLimit = state.persisted.value.maxMainAgentMemoryChars)
        PanelSection("Execution") {
            conversationNumberSetting("Parallel agents", draft.maxParallelSubAgents, "Maximum simultaneous sub-agent jobs.", 1..20, true) {
                change(draft.copy(maxParallelSubAgents = it))
            }
            conversationNumberSetting(
                "Default tool timeout (seconds)",
                draft.timeoutSeconds,
                "Default duration allowed for a tool call.",
                5..600,
            ) {
                change(draft.copy(timeoutSeconds = it))
            }
            conversationNumberSetting("Startup history", draft.loadLimit, "Conversation messages loaded on startup.", 1..1000) {
                change(draft.copy(loadLimit = it))
            }
            conversationNumberSetting(
                "Main-agent memory limit",
                draft.maxMainAgentMemoryChars,
                "Save a smaller memory document before lowering this limit.",
                1000..100000,
            ) {
                change(draft.copy(maxMainAgentMemoryChars = it))
            }
        }
    }
}
