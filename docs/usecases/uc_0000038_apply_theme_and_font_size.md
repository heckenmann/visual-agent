# UC-0000038: Apply Theme And Font Size

## Goal

Apply user-selected visual theme and font sizing to the JavaFX UI.

## Primary Actor

Desktop user.

## Preconditions

- Theme stylesheets are available.
- Settings can be read from configuration or preferences.

## Main Flow

1. The user selects a theme or font size.
2. The application resolves the related theme stylesheet or supported root font-size CSS class.
3. The main window applies the visual change.
4. The preference is persisted for future launches.

## Result

The UI reflects user-selected visual preferences.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.config.AppThemeStylesheets`
- `de.heckenmann.visualagent.ui.MainWindow`
- `de.heckenmann.visualagent.ui.panels.ApplicationSettingsPanel`

## Acceptance Criteria

- Theme changes do not require manual source edits.
- Font size changes are persisted.
- Font size changes are applied through maintained CSS classes, not inline root styles.
