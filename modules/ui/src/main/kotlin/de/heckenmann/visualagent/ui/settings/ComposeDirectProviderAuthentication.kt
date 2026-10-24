package de.heckenmann.visualagent.ui.settings

import androidx.compose.runtime.Composable
import de.heckenmann.visualagent.protocol.DirectProviderAuthMode
import de.heckenmann.visualagent.ui.components.PanelDropdownField
import de.heckenmann.visualagent.ui.components.PanelInfoBox
import de.heckenmann.visualagent.ui.components.PanelSelectOption

/** Makes the direct provider's credential and billing path explicit rather than inferred. */
@Composable
internal fun DirectProviderAuthenticationFields(
    selected: DirectProviderAuthMode,
    onSelected: (DirectProviderAuthMode) -> Unit,
) {
    PanelDropdownField(
        label = "Authentication and billing",
        selectedValue = selected.name,
        options =
            listOf(
                PanelSelectOption(DirectProviderAuthMode.API_KEY.name, "OpenAI API key — Platform billing"),
                PanelSelectOption(DirectProviderAuthMode.CHATGPT_OAUTH.name, "ChatGPT OAuth — eligible plan usage"),
            ),
        onSelected = { onSelected(DirectProviderAuthMode.valueOf(it)) },
    )
    PanelInfoBox(
        if (selected == DirectProviderAuthMode.API_KEY) {
            "Requests use separately billed OpenAI Platform API usage, not your ChatGPT subscription. No CLI or ChatGPT login is required."
        } else {
            "Requires explicit ChatGPT authorization and an eligible plan. API keys are not used as a fallback. No Codex CLI is required."
        },
    )
}
