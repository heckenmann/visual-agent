# UC-0000082: Select UI Theme Mode

## Goal

Allow the user to switch the desktop UI between light, dark, and system appearance modes with immediate effect and persistence.

## Primary Actor

Desktop user.

## Preconditions

- The application is running with the Compose Multiplatform UI.
- Settings panel is open.

## Main Flow

1. The user opens the Settings workspace panel.
2. The user selects `Light`, `Dark`, or `System` from the **Theme** dropdown under the Appearance section.
3. The application immediately recomposes the main window using the matching Material3 color scheme.
4. The selected mode is persisted in `AppConfig.uiThemeMode` (preference key `ui.theme.mode`).
5. On the next launch, the saved mode is restored.

## Result

The UI renders with the chosen light or dark Material3 color scheme, or follows the OS setting when System is selected.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.config.ThemeMode`
- `de.heckenmann.visualagent.config.AppConfig`
- `de.heckenmann.visualagent.ui.compose.ComposeWorkspaceTheme`
- `de.heckenmann.visualagent.ui.compose.ComposeSettingsExecutionAndAppearanceSection`
- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`

## Acceptance Criteria

- A Theme dropdown is visible in Settings with options `System`, `Light`, and `Dark`.
- Changing the mode applies immediately without restart.
- The mode is persisted and restored on startup.
- `ThemeMode.SYSTEM` falls back to dark when OS detection fails.
- No hardcoded `Color(0x...)` literals remain in `ui/compose/*.kt` outside the theme definition file.
