# UC-0000037: Manage Application Settings

## Goal

Let the user update application-level settings such as theme, font size, provider defaults, tool toggles, and runtime preferences.

## Primary Actor

Desktop user.

## Preconditions

- Settings UI is available.
- Preference persistence is available.

## Main Flow

1. The user opens application settings.
2. The UI displays configurable settings.
3. The user changes a setting.
4. The setting is persisted.
5. UI or runtime services apply the change immediately where supported.

## Result

User preferences survive restart and affect the application consistently.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`
- `de.heckenmann.visualagent.config.AppConfig`
- `de.heckenmann.visualagent.config.AppConfigPersistenceBinder`
- `de.heckenmann.visualagent.knowledge.PreferenceStore`

## Acceptance Criteria

- Supported settings persist in SQLite.
- Sensitive provider keys are not exposed in exports, logs, or model context.
