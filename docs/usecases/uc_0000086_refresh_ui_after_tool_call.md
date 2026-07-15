# Refresh UI panels automatically when the agent modifies data via tool calls

## Description

When the main agent or a sub-agent modifies data through a tool call, the UI panels that display that data must refresh automatically to reflect the change. This ensures the user always sees the current state without manual refresh actions.

## Tool Calls

- `ToolEventRefreshEffect` composable subscribes to `ToolEventBus` FINISHED events and debounces refresh calls.
- Files panel refreshes on `file:write`, `file:edit`, `workspace:file` FINISHED.
- Todos panel refreshes on `todos`, `agent:assign-todo`, `agent:assign-next-todo`, `agent:assign-all-todos` FINISHED.
- Sub-agents panel refreshes on `agent:create`, `agent:update`, `agent:delete`, `agent:start`, `agent:list` FINISHED.
- Canvas panel refreshes on `canvas` FINISHED.
- Settings panel refreshes on `ui` FINISHED.
- Conversation panel refreshes history on any FINISHED event (tool-call entries are persisted for all tools).
- Only successful tool calls trigger data refresh in data panels; failed calls do not (except conversation history).
- Refreshes are debounced at 150ms to avoid excessive recomposition during rapid tool-call bursts.

## Code Entry Points

- `src/main/kotlin/de/heckenmann/visualagent/ui/compose/ComposeToolEventRefresh.kt` — reusable `ToolEventRefreshEffect` composable.
- `src/main/kotlin/de/heckenmann/visualagent/ui/compose/ComposeFilesPanel.kt:61` — `ToolEventRefreshEffect` for file tools.
- `src/main/kotlin/de/heckenmann/visualagent/ui/compose/ComposeTodoPanel.kt:51` — `ToolEventRefreshEffect` for todo tools.
- `src/main/kotlin/de/heckenmann/visualagent/ui/compose/ComposeManagementPanels.kt:62` — `ToolEventRefreshEffect` for agent tools.
- `src/main/kotlin/de/heckenmann/visualagent/ui/compose/ComposeCanvasPanel.kt:56` — `ToolEventRefreshEffect` for canvas tool.
- `src/main/kotlin/de/heckenmann/visualagent/ui/compose/ComposeSettingsPanel.kt:80` — `ToolEventRefreshEffect` for `ui` tool.
- `src/main/kotlin/de/heckenmann/visualagent/ui/compose/ComposeConversationPanel.kt:78` — `DisposableEffect` on `ToolEventBus` for history refresh.
- `src/main/kotlin/de/heckenmann/visualagent/agent/tools/ToolEventBus.kt` — event source.
- `src/main/kotlin/de/heckenmann/visualagent/ui/compose/ComposeWorkspaceModels.kt:285` — `ComposePanelServices.toolEventBus`.
- `src/main/kotlin/de/heckenmann/visualagent/ui/compose/ComposeWorkspaceComponents.kt:245` — `WindowBody` wiring.
