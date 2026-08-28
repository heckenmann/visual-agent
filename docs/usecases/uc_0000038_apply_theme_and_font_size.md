# UC-0000038: Apply Theme And Font Size

## Goal

Apply user-selected visual theme mode, font sizing, and UI scaling to the Compose Multiplatform UI.

## Primary Actor

Desktop user.

## Preconditions

- Compose Material3 theme tokens are available.
- Settings can be read from configuration or preferences.

## Main Flow

1. The user selects a theme mode (`System`, `Light`, or `Dark`), adjusts font size, or selects automatic or manual UI scaling.
2. The application resolves the selected Material3 color scheme, font-size settings, and UI-scale setting.
3. The main window applies the visual change immediately.
4. The preference is persisted for future launches.

## Result

The UI reflects user-selected visual preferences using Material3 light/dark color schemes.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.config.ThemeMode`
- `de.heckenmann.visualagent.ui.workspace.ComposeWorkspaceTheme`
- `de.heckenmann.visualagent.ui.settings.SettingsPanel`
- `de.heckenmann.visualagent.ui.application.VisualAgentComposeApp`

## Acceptance Criteria

- Theme mode changes do not require manual source edits.
- Theme mode selection is constrained to supported [ThemeMode] values.
- Font size and UI scale changes are persisted.
- Font size selection is constrained to the supported numeric range.
- Automatic UI scaling preserves the operating-system and monitor density; manual scale is applied as an application-wide relative factor.
- Font size changes are applied through maintained Compose settings instead of stylesheet classes.
