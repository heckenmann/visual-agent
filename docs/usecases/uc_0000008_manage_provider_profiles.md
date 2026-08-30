# UC-0000008: Manage Provider Profiles

## Goal

Support multiple provider profiles with model lists, credentials, generation defaults, and provider-specific options.

## Primary Actor

Desktop user.

## Preconditions

- The provider catalog store is available.
- The settings/session UI can open provider profile editing.

## Main Flow

1. The user opens the same provider-connection dialog for creating or editing a profile. The dialog uses mode-specific titles and primary actions while keeping the connection fields and validation consistent.
2. The profile editor generates an opaque stable provider ID for new profiles and stores it without exposing it as a user-editable setting. The user manages the display name, adapter type, endpoint, API key, enabled state, and provider options independently from main-agent model selection.
3. The panel validates required fields and stages the profile in the global provider settings overlay.
4. The user may refresh models with the staged endpoint and credentials; discovered models remain local to the draft until saved.
5. The user explicitly saves the overlay to persist the complete catalog and active model selection together, or resets it to reload the persisted catalog.
6. The catalog permits multiple profiles using the same adapter, so separate endpoints and credentials can be configured independently.
7. If the active profile is disabled or deleted, the catalog selects another enabled profile; it never leaves the session without an enabled provider.
8. Model resolution uses provider, model, agent, and variant settings in deterministic order.

## Result

Different agents and sessions can use different providers and model parameters.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.agent.provider.ProviderProfile`
- `de.heckenmann.visualagent.agent.provider.ProviderCatalogService`
- `de.heckenmann.visualagent.ui.settings.providerSettingsOverlay`
- `de.heckenmann.visualagent.ui.settings.ComposeProtocolSettingsSupport`
- `de.heckenmann.visualagent.ui.components.ActionIconButton`

## Acceptance Criteria

- Profiles survive restart.
- Profile edits do not affect the active provider until the global overlay is explicitly saved.
- Model refresh uses the staged provider profile and does not persist a catalog update until the overlay is saved.
- New profiles receive a generated stable provider ID; existing profile IDs never change during editing.
- Name and Base URL are required unless the selected adapter owns a fixed endpoint.
- Deleting the active provider selects another enabled provider.
- Disabling the active provider selects another enabled provider.
- Multiple profiles may use the same adapter type.
- Changing the main-agent model does not overwrite provider endpoints or credentials.
- At least one provider profile remains enabled.
- Option merging is deterministic.
- Raw API keys are not included in tool output, model context, exported config, or logs.
