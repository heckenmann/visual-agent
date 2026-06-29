# UC-0000037: Manage Application Settings

## Goal

Let the user update application-level settings such as provider defaults, runtime preferences, theme, and font size.

## Primary Actor

Desktop user.

## Preconditions

- Settings UI is available.
- Preference persistence is available.

## Main Flow

1. The user opens application settings.
2. The UI displays provider, execution, response behavior, model instruction, and appearance settings.
3. The user changes a setting and saves the panel.
4. The setting is persisted.
5. UI or runtime services apply the change immediately where supported.

## Result

User preferences survive restart and affect the application consistently.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`
- `de.heckenmann.visualagent.ui.compose.SettingsPanel`
- `de.heckenmann.visualagent.config.AppConfig`
- `de.heckenmann.visualagent.knowledge.PreferenceStore`

## Acceptance Criteria

- Supported settings persist in SQLite.
- Runtime numeric settings are constrained to supported ranges.
- Sensitive provider keys are not exposed in exports, logs, or model context.
