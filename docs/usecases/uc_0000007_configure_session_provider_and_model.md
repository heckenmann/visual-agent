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
2. The UI shows editable provider and model fields.
3. The user enters the desired provider/model values or updates related provider settings.
4. The settings are persisted.
5. Later model requests resolve against the selected provider/model.

## Result

Main-agent requests use the user-selected provider and model unless an agent-specific override applies.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.SettingsPanel`
- `de.heckenmann.visualagent.config.AppConfig`
- `de.heckenmann.visualagent.agent.ConfiguredLLMProvider`

## Acceptance Criteria

- Empty provider/model values fall back to safe defaults.
- Provider/model changes persist across restart.
- Credentials are not exposed to model context or logs.
