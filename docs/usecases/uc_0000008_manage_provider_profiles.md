# UC-0000008: Manage Provider Profiles

## Goal

Support multiple provider profiles with model lists, credentials, generation defaults, and provider-specific options.

## Primary Actor

Desktop user.

## Preconditions

- The provider catalog store is available.
- The settings/session UI can open provider profile editing.

## Main Flow

1. The user creates, edits, disables, or deletes a provider profile from the Compose settings panel.
2. The profile editor stores provider ID, display name, adapter type, endpoint, API key, enabled state, and provider options independently from main-agent model selection.
3. The panel validates required fields and persists the profile through the catalog.
4. The catalog permits multiple profiles using the same adapter, so separate endpoints and credentials can be configured independently.
5. If the active profile is disabled or deleted, the catalog selects another enabled profile; it never leaves the session without an enabled provider.
6. Model resolution uses provider, model, agent, and variant settings in deterministic order.

## Result

Different agents and sessions can use different providers and model parameters.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.agent.provider.ProviderProfile`
- `de.heckenmann.visualagent.agent.provider.ProviderCatalogService`
- `de.heckenmann.visualagent.ui.compose.SettingsPanel`
- `de.heckenmann.visualagent.ui.compose.ComposeSettingsPanelSupport`
- `de.heckenmann.visualagent.ui.compose.ActionIconButton`

## Acceptance Criteria

- Profiles survive restart.
- Provider ID, name, and Base URL are required.
- Provider ID accepts only letters, digits, `.`, `_`, and `-`.
- Deleting the active provider selects another enabled provider.
- Disabling the active provider selects another enabled provider.
- Multiple profiles may use the same adapter type.
- Changing the main-agent model does not overwrite provider endpoints or credentials.
- At least one provider profile remains enabled.
- Option merging is deterministic.
- Raw API keys are not included in tool output, model context, exported config, or logs.
