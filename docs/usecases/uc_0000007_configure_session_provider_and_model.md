# UC-0000007: Configure Session Provider And Model

## Goal

Let the user select the active provider, model, and related model settings for the current session.

## Primary Actor

Desktop user.

## Preconditions

- Provider catalog data is available.
- The settings panel is visible.

## Main Flow

1. The user opens the settings panel.
2. The UI shows enabled provider connections as a visible list. Selecting a connection immediately makes it active for the main agent.
3. The UI then shows only models recognized as selectable for that active connection, without another provider selection control.
4. The user filters the visible model list and selects one model. The selection immediately becomes the main-agent model without changing endpoint or credential configuration.
5. The user may refresh models, inspect model details, or select a provider/model.
6. The settings are persisted.
7. Later model requests resolve against the selected provider/model.

## Result

Main-agent requests use the user-selected provider and model unless an agent-specific override applies.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.SettingsPanel`
- `de.heckenmann.visualagent.ui.compose.ComposeSettingsPanelSupport`
- `de.heckenmann.visualagent.config.AppConfig`
- `de.heckenmann.visualagent.agent.provider.ProviderCatalogService`
- `de.heckenmann.visualagent.agent.ConfiguredLLMProvider`

## Acceptance Criteria

- Provider selection uses enabled provider profiles rather than free-form text.
- Endpoint and credential configuration is available only through the separate provider-profile editor.
- API-key entry is available in the provider-profile editor and is masked by default.
- Model selection displays only catalog models that are selectable for the active provider.
- The model section has a clear empty state when the active provider has no recognized selectable models.
- Provider/model changes persist across restart.
- Standard providers mirror settings to their legacy `AppConfig` fields.
- Custom provider profiles remain catalog-backed.
- Saving the main-agent selection does not mutate the selected provider profile.
- Credentials are not exposed to model context or logs.
