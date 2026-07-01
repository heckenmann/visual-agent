# UC-0000065: View Model Details

## Goal

Let the user inspect provider model details such as family, size, format, and modified timestamp when available.

## Primary Actor

Desktop user.

## Preconditions

- A provider and model are selected.
- The provider supports model details or returns a graceful fallback.

## Main Flow

1. The user selects a model.
2. The settings panel requests details from the provider.
3. Provider details are formatted for the settings panel.
4. Errors are converted to user-facing messages.

## Result

The user can make informed model-selection decisions.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.SettingsPanel`
- `de.heckenmann.visualagent.agent.LLMProvider.getModelDetails`

## Acceptance Criteria

- Missing optional details render as `unknown`.
- Provider errors are shown in user-facing form.
- The UI remains responsive while details load.
