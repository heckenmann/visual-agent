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
2. The UI shows the active provider as a dropdown sourced from enabled provider profiles.
3. The UI shows the provider Base URL and a masked API-key field for the active profile.
4. The UI shows known selectable models as a searchable dropdown and keeps a custom model ID fallback for models not yet in the catalog.
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
- Base URL entry is available for the selected provider.
- API-key entry is available for the selected provider and is masked by default.
- Model selection uses catalog models when available and allows an explicit custom model ID.
- Empty model values fall back to safe defaults.
- Provider/model changes persist across restart.
- Standard providers mirror settings to their legacy `AppConfig` fields.
- Custom provider profiles remain catalog-backed.
- Credentials are not exposed to model context or logs.
