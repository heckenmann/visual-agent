# UC-0000062: Import Export Configuration

## Goal

Let the user export non-secret application configuration to a file and import configuration from a selected file.

## Primary Actor

Desktop user.

## Preconditions

- Application settings panel is visible.
- File chooser dialogs can be opened.

## Main Flow

1. The user chooses import or export.
2. The file chooser selects a source or destination.
3. Configuration is read from or written to the selected file.
4. UI controls synchronize with imported configuration.
5. Errors are shown in a dialog.

## Result

Configuration can be backed up or restored without exposing secrets.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`
- `de.heckenmann.visualagent.config.AppConfig.exportTo`
- `de.heckenmann.visualagent.config.AppConfig.importFrom`

## Acceptance Criteria

- Import updates visible settings controls.
- Export avoids sensitive key leakage according to config policy.
- File errors are shown to the user.
