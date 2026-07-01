# UC-0000063: Reset Appearance Defaults

## Goal

Let the user reset theme and font size to default appearance values after confirmation.

## Primary Actor

Desktop user.

## Preconditions

- Application settings panel is visible.

## Main Flow

1. The user clicks reset defaults.
2. A confirmation dialog is shown.
3. If confirmed, theme and font size are restored to defaults.
4. Settings are persisted.
5. UI controls synchronize with the new values.

## Result

The user can recover from undesirable appearance settings.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`
- `de.heckenmann.visualagent.config.AppConfig`

## Acceptance Criteria

- Cancel does not modify settings.
- Confirm resets only the intended appearance values.
- Updated values are persisted.
