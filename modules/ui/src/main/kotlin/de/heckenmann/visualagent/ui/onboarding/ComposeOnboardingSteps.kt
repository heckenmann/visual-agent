package de.heckenmann.visualagent.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.CredentialUpdate
import de.heckenmann.visualagent.protocol.OnboardingProviderDraft
import de.heckenmann.visualagent.protocol.OnboardingProviderProfile
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderModel
import java.util.UUID

/** Renders the welcome copy for the server-owned onboarding flow. */
@Composable
internal fun OnboardingWelcome() {
    Text("Welcome", style = MaterialTheme.typography.titleLarge)
    Text("Provider settings remain on this Visual Agent server. You can change them later in Providers and models.")
}

/** Renders selection and staged editing of one LLM provider profile. */
@Composable
internal fun OnboardingProviderStep(
    profiles: List<OnboardingProviderProfile>,
    selected: OnboardingProviderProfile?,
    customDraft: OnboardingProviderDraft?,
    loading: Boolean,
    onSelect: (OnboardingProviderProfile) -> Unit,
    onDraftChange: (OnboardingProviderDraft) -> Unit,
) {
    Text("LLM provider", style = MaterialTheme.typography.titleLarge)
    when {
        loading -> Text("Loading providers from the Visual Agent server…")
        profiles.isEmpty() -> Text("No provider profile is configured on this server yet.")
        else ->
            profiles.forEach { profile ->
                OutlinedButton(onClick = { onSelect(profile) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (profile == selected) "✓ ${profile.name}" else profile.name)
                }
            }
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
    customDraft?.let { draft -> OnboardingProviderEditor(draft, onDraftChange) }
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
    Text("Provider type", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProviderAdapter.entries.forEach { adapter ->
            OutlinedButton(onClick = { onChange(draft.withAdapter(adapter)) }) {
                Text(if (adapter == draft.adapter) "✓ ${adapter.label()}" else adapter.label())
            }
        }
    }
    if (draft.adapter == ProviderAdapter.CODEX_CLI) {
        Text("Codex CLI is checked on the connected Visual Agent server and does not use an HTTP endpoint.")
    } else {
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
        modifier = Modifier.fillMaxWidth(),
    )
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
internal fun OnboardingModelStep(
    draft: OnboardingProviderDraft?,
    models: List<ProviderModel>,
    selectedModel: ProviderModel?,
    loading: Boolean,
    error: String?,
    onSelect: (ProviderModel) -> Unit,
    onRefresh: () -> Unit,
) {
    Text("Model", style = MaterialTheme.typography.titleLarge)
    Text(draft?.let { "Select a model for ${it.name}." } ?: "Select a provider first.")
    if (loading) {
        Text("Discovering models…")
    } else {
        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Text("Refresh models")
        }
    }
    models.forEach { model ->
        OutlinedButton(onClick = { onSelect(model) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (model == selectedModel) "✓ ${model.name}" else model.name)
        }
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
    OnboardingProviderDraft(id, name, adapter, baseUrl, CredentialUpdate.Unchanged, enabled, defaultModel)

/** Updates a staged provider adapter with the matching default endpoint. */
internal fun OnboardingProviderDraft.withAdapter(adapter: ProviderAdapter): OnboardingProviderDraft =
    copy(adapter = adapter, baseUrl = adapter.defaultEndpoint())

/** Returns the documented default endpoint for an HTTP provider adapter. */
internal fun ProviderAdapter.defaultEndpoint(): String =
    when (this) {
        ProviderAdapter.OLLAMA -> "http://localhost:11434"
        ProviderAdapter.OPENAI_COMPATIBLE -> "https://api.openai.com"
        ProviderAdapter.CODEX_CLI -> ""
    }

private fun ProviderAdapter.label(): String =
    when (this) {
        ProviderAdapter.OLLAMA -> "Ollama"
        ProviderAdapter.OPENAI_COMPATIBLE -> "OpenAI-compatible"
        ProviderAdapter.CODEX_CLI -> "Codex CLI"
    }
