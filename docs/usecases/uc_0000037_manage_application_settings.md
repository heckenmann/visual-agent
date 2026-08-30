# UC-0000037: Manage Application Settings

## Goal

Let the user update application-level appearance preferences.

## Primary Actor

Desktop user.

## Preconditions

- Settings UI is available.
- Preference persistence is available.

## Main Flow

1. The user opens application settings.
2. The UI displays appearance settings.
3. The user changes one or more settings in a local draft.
4. The user selects **Save changes** to persist the draft, or **Reset changes** to discard it and reload the database state.
5. UI or runtime services apply saved settings where supported.

## Result

User preferences survive restart and affect the application consistently.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.application.VisualAgentComposeApp`
- `de.heckenmann.visualagent.ui.settings.settingsPanel`
- `de.heckenmann.visualagent.ui.components.ActionIconButton`
- `de.heckenmann.visualagent.config.AppConfig`
- `de.heckenmann.visualagent.knowledge.PreferenceStore`

## Acceptance Criteria

- Supported settings persist in SQLite.
- Appearance edits do not apply until **Save changes** is selected.
- **Reset changes** reloads the persisted database state and never resets provider credentials or configuration.
- Settings panels keep Reset and Save in a fixed, right-aligned footer while settings content scrolls independently.
- Every editable appearance setting includes an information icon explaining its effect.
- UI scale can be restored to automatic operating-system scaling.
- Sensitive provider keys are not exposed in exports, logs, or model context.
