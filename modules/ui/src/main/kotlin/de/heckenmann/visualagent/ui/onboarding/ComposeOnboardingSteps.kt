package de.heckenmann.visualagent.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.CredentialUpdate
import de.heckenmann.visualagent.protocol.OnboardingProviderDraft
import de.heckenmann.visualagent.protocol.OnboardingProviderProfile
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.ui.components.PanelDropdownField
import de.heckenmann.visualagent.ui.components.PanelSelectOption
import java.util.UUID

/** Renders the welcome copy for the server-owned onboarding flow. */
@Composable
internal fun OnboardingWelcome() {
    Text("Welcome", style = MaterialTheme.typography.titleLarge)
    Text("Choose a provider and model for this server. You can change them later in Providers and models.")
    Text("How Visual Agent works", style = MaterialTheme.typography.titleMedium)
    Text("Main Agent — understands your request, plans the work, and coordinates available tools and helpers.")
    Text("Sub-agents — specialized helpers created for roles such as research, coding, or analysis.")
    Text("Todos — durable work items the Main Agent can assign to a Sub-agent and track until completion.")
}

/** Renders selection and staged editing of one LLM provider profile. */
@Composable
internal fun ColumnScope.OnboardingProviderStep(
    profiles: List<OnboardingProviderProfile>,
    selected: OnboardingProviderProfile?,
    customDraft: OnboardingProviderDraft?,
    loading: Boolean,
    onSelect: (OnboardingProviderProfile) -> Unit,
    onDraftChange: (OnboardingProviderDraft) -> Unit,
    onCancelDraft: () -> Unit,
    authorizationControls: @Composable (OnboardingProviderDraft) -> Unit = {},
) {
    Text("LLM provider", style = MaterialTheme.typography.titleLarge)
    if (customDraft != null) {
        OnboardingProviderEditor(customDraft, onDraftChange)
        authorizationControls(customDraft)
        OutlinedButton(onClick = onCancelDraft) { Text("Back to providers") }
        return
    }
    when {
        loading -> Text("Loading providers from the Visual Agent server…")
        profiles.isEmpty() -> Text("No provider profile is configured on this server yet.")
        else ->
            OnboardingChoices(profiles, selected, { it.name }, onSelect, Modifier.weight(1f, fill = false))
    }
    OutlinedButton(
        onClick = {
            onDraftChange(
                OnboardingProviderDraft(
                    id = UUID.randomUUID().toString(),
                    name = "Ollama",
                    adapter = ProviderAdapter.OLLAMA,
                    baseUrl = ProviderAdapter.OLLAMA.defaultEndpoint(),
                ),
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Add provider")
    }
    selected?.toDraft()?.let { authorizationControls(it) }
}

@Composable
private fun OnboardingProviderEditor(
    draft: OnboardingProviderDraft,
    onChange: (OnboardingProviderDraft) -> Unit,
) {
    Text("New provider", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = draft.name,
        onValueChange = { name -> onChange(draft.copy(name = name)) },
        label = { Text("Provider name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    PanelDropdownField(
        label = "Provider type",
        selectedValue = draft.adapter.name,
        options = ProviderAdapter.entries.map { PanelSelectOption(it.name, it.label()) },
        onSelected = { onChange(draft.withAdapter(ProviderAdapter.valueOf(it))) },
        modifier = Modifier.fillMaxWidth(),
    )
    if (draft.adapter == ProviderAdapter.CODEX_CLI) {
        Text("Codex CLI is checked on the connected Visual Agent server and does not use an HTTP endpoint.")
    } else {
        if (draft.adapter == ProviderAdapter.CODEX_DIRECT) {
            de.heckenmann.visualagent.ui.settings.DirectProviderAuthenticationFields(draft.directAuthMode) { mode ->
                onChange(draft.copy(directAuthMode = mode))
            }
        }
        OnboardingHttpProviderFields(draft, onChange)
    }
}

@Composable
private fun OnboardingHttpProviderFields(
    draft: OnboardingProviderDraft,
    onChange: (OnboardingProviderDraft) -> Unit,
) {
    OutlinedTextField(
        value = draft.baseUrl,
        onValueChange = { baseUrl -> onChange(draft.copy(baseUrl = baseUrl)) },
        label = { Text("Provider endpoint") },
        supportingText = { Text("Resolved from the connected Visual Agent server.") },
        singleLine = true,
        readOnly = draft.adapter == ProviderAdapter.CODEX_DIRECT,
        modifier = Modifier.fillMaxWidth(),
    )
    if (draft.adapter == ProviderAdapter.CODEX_DIRECT &&
        draft.directAuthMode == de.heckenmann.visualagent.protocol.DirectProviderAuthMode.CHATGPT_OAUTH
    ) {
        return
    }
    val credential = (draft.credential as? CredentialUpdate.Replace)?.value.orEmpty()
    OutlinedTextField(
        value = credential,
        onValueChange = { value -> onChange(draft.copy(credential = CredentialUpdate.Replace(value))) },
        label = { Text("API key (optional)") },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Renders discovery results and the selected provider model. */
@Composable
internal fun ColumnScope.OnboardingModelStep(
    draft: OnboardingProviderDraft?,
    models: List<ProviderModel>,
    selectedModel: ProviderModel?,
    loading: Boolean,
    error: String?,
    onSelect: (ProviderModel) -> Unit,
    onRefresh: () -> Unit,
) {
    var query by remember(draft?.id) { mutableStateOf("") }
    val filter = query.trim()
    val visibleModels =
        remember(models, filter) {
            models.filter { it.name.contains(filter, ignoreCase = true) || it.id.contains(filter, ignoreCase = true) }
        }
    Text("Model", style = MaterialTheme.typography.titleLarge)
    Text(draft?.let { "Select a model for ${it.name}." } ?: "Select a provider first.")
    if (loading) {
        Text("Discovering models…")
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search models") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Text("Refresh models", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
    when {
        visibleModels.isNotEmpty() ->
            OnboardingChoices(visibleModels, selectedModel, { it.name }, onSelect, Modifier.weight(1f, fill = false))
        !loading && models.isNotEmpty() -> Text("No models match your search.")
        !loading && error == null -> Text("No models were found. Refresh after checking the provider.")
    }
    if (selectedModel != null && selectedModel !in visibleModels) {
        Text("Selected model: ${selectedModel.name}", style = MaterialTheme.typography.bodyMedium)
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

/** Renders a review limited to safe provider and model data. */
@Composable
internal fun OnboardingReviewStep(
    draft: OnboardingProviderDraft?,
    credentialConfigured: Boolean,
    model: ProviderModel?,
    error: String?,
) {
    Text("Review", style = MaterialTheme.typography.titleLarge)
    Text(
        draft?.let { selectedDraft ->
            "Provider: ${selectedDraft.name}\n" +
                "Endpoint: ${selectedDraft.baseUrl.ifBlank { "Not applicable" }}\n" +
                "Credential configured: $credentialConfigured\n" +
                "Model: ${model?.name.orEmpty()}"
        }
            ?: "No provider is selected.",
    )
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

/** Converts a safe persisted provider view into a write-only staged draft. */
internal fun OnboardingProviderProfile.toDraft(): OnboardingProviderDraft =
    OnboardingProviderDraft(
        id,
        name,
        adapter,
        baseUrl,
        CredentialUpdate.Unchanged,
        enabled,
        defaultModel,
        directAuthMode,
        credentialReference,
    )

/** Updates a staged provider adapter with the matching default endpoint. */
internal fun OnboardingProviderDraft.withAdapter(adapter: ProviderAdapter): OnboardingProviderDraft =
    copy(
        adapter = adapter,
        baseUrl = adapter.defaultEndpoint(),
        directAuthMode = de.heckenmann.visualagent.protocol.DirectProviderAuthMode.API_KEY,
        credentialReference = null,
    )

/** Returns the documented default endpoint for an HTTP provider adapter. */
internal fun ProviderAdapter.defaultEndpoint(): String =
    when (this) {
        ProviderAdapter.OLLAMA -> "http://localhost:11434"
        ProviderAdapter.OPENAI_COMPATIBLE -> "https://api.openai.com"
        ProviderAdapter.CODEX_DIRECT -> "https://api.openai.com"
        ProviderAdapter.CODEX_CLI -> ""
    }

private fun ProviderAdapter.label(): String =
    when (this) {
        ProviderAdapter.OLLAMA -> "Ollama"
        ProviderAdapter.OPENAI_COMPATIBLE -> "OpenAI-compatible"
        ProviderAdapter.CODEX_CLI -> "Codex CLI"
        ProviderAdapter.CODEX_DIRECT -> "Codex (direct)"
    }
