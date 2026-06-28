# UC-0000038: Apply Theme And Font Size

## Goal

Apply user-selected visual theme and font sizing to the Compose Multiplatform UI.

## Primary Actor

Desktop user.

## Preconditions

- Compose theme tokens are available.
- Settings can be read from configuration or preferences.

## Main Flow

1. The user selects a theme or font size.
2. The application resolves the supported Compose theme and font-size settings.
3. The main window applies the visual change.
4. The preference is persisted for future launches.

## Result

The UI reflects user-selected visual preferences.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.ComposeTheme`
- `de.heckenmann.visualagent.ui.compose.SettingsPanel`
- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`

## Acceptance Criteria

- Theme changes do not require manual source edits.
- Font size changes are persisted.
- Font size changes are applied through maintained Compose settings instead of stylesheet classes.
