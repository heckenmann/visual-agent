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
2. The profile stores provider ID, display name, adapter type, endpoint, API key, enabled state, model entries, defaults, filters, and provider options.
3. The panel validates required fields and persists the profile through the catalog.
4. Model resolution uses provider, model, agent, and variant settings in deterministic order.

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
- Option merging is deterministic.
- Raw API keys are not included in tool output, model context, exported config, or logs.
