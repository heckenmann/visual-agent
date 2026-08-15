# Refresh UI panels automatically when the agent modifies data via tool calls

## Description

When the main agent or a sub-agent modifies data through a tool call, the UI panels that display that data must refresh automatically to reflect the change. This ensures the user always sees the current state without manual refresh actions.

## Tool Calls

- `ToolEventRefreshEffect` subscribes to protocol-owned `ActivityPort` FINISHED events and debounces refresh calls.
- Files panel refreshes on `file:write`, `file:edit`, `workspace:file` FINISHED.
- Todos panel refreshes through `TodoPort` change and progress listeners.
- Sub-agents panel refreshes on `agent:create`, `agent:update`, `agent:delete`, and `agent:list` FINISHED.
- Canvas panel refreshes on `canvas` FINISHED.
- Settings panel refreshes through `SettingsPort` and `ProviderPort` change listeners.
- Conversation panel refreshes history on any `ActivityPort` FINISHED event (tool-call entries are persisted for all tools).
- Only successful tool calls trigger data refresh in data panels; failed calls do not (except conversation history).
- Refreshes are debounced at 150ms to avoid excessive recomposition during rapid tool-call bursts.

## Code Entry Points

- `modules/ui/src/main/kotlin/de/heckenmann/visualagent/ui/status/ComposeToolEventRefresh.kt` — reusable `ToolEventRefreshEffect` composable.
- `modules/ui/src/main/kotlin/de/heckenmann/visualagent/ui/files/ComposeFilesPanel.kt` — activity-driven refresh for file tools.
- `modules/ui/src/main/kotlin/de/heckenmann/visualagent/ui/todo/ComposeTodoPanel.kt` — `TodoPort` change and progress listeners.
- `modules/ui/src/main/kotlin/de/heckenmann/visualagent/ui/application/ComposeManagementPanels.kt` — activity-driven refresh for sub-agent tools.
- `modules/ui/src/main/kotlin/de/heckenmann/visualagent/ui/canvas/ComposeCanvasPanel.kt` — activity-driven refresh for canvas tools.
- `modules/ui/src/main/kotlin/de/heckenmann/visualagent/ui/settings/ComposeProtocolSettingsPanel.kt` — settings and provider change listeners.
- `modules/ui/src/main/kotlin/de/heckenmann/visualagent/ui/conversation/ComposeConversationPanel.kt` — activity and todo listeners for history refresh.
- `application/src/main/kotlin/de/heckenmann/visualagent/server/SpringActivityPort.kt` — server adapter that publishes tool activity through `ActivityPort`.
