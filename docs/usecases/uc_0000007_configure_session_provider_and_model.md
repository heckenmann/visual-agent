# UC-0000007: Configure Session Provider And Model

## Goal

Let the user select the active provider, model, and related model settings for the current session.

## Primary Actor

Desktop user.

## Preconditions

- Provider catalog data is available.
- The session panel is visible.

## Main Flow

1. The user opens session settings.
2. The UI lists configured providers and selectable models.
3. The user selects a provider/model or updates provider settings.
4. The settings are persisted.
5. Later model requests resolve against the selected provider/model.

## Result

Main-agent requests use the user-selected provider and model unless an agent-specific override applies.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.panels.SessionPanel`
- `de.heckenmann.visualagent.ui.panels.session.SessionModelController`
- `de.heckenmann.visualagent.agent.provider.ProviderCatalogService`
- `de.heckenmann.visualagent.agent.ConfiguredLLMProvider`

## Acceptance Criteria

- Disabled or unsupported models are not selected accidentally.
- Provider/model changes persist across restart.
- Credentials are not exposed to model context or logs.
