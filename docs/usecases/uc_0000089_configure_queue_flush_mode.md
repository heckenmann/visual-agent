# UC-0000089: Configure Queue Flush Mode

## Goal

Allow the user to configure how queued messages are delivered to the main agent: one-by-one (separate requests) or all-at-once (combined into a single request).

## Primary Actor

Desktop user.

## Preconditions

- The application is running.
- The settings panel is accessible.

## Main Flow

1. The user opens the Settings panel.
2. In the Execution section, the user selects a flush mode from the "Queue flush" dropdown.
3. Options: "One by one" (each queued message sent as a separate request) or "All at once" (all queued messages combined into one request).
4. The user saves settings.
5. The flush mode is persisted and applied immediately.

## Result

The queue delivery behavior matches the user's preference.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.config.AppConfigBean.queueFlushMode`
- `de.heckenmann.visualagent.config.AppConfig.queueFlushMode`
- `de.heckenmann.visualagent.ui.compose.SettingsExecutionAndAppearanceSection`
- `de.heckenmann.visualagent.ui.compose.SettingsPanel`

## Acceptance Criteria

- The "Queue flush" dropdown is visible in the Execution section of Settings.
- Selecting "One by one" sets `queueFlushMode` to `ONE_BY_ONE`.
- Selecting "All at once" sets `queueFlushMode` to `ALL_AT_ONCE`.
- The setting is persisted across application restarts.
- The setting is included in configuration export/import.
