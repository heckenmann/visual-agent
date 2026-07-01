# UC-0000036: Arrange Workspace Layout Via Tool

## Goal

Allow enabled sub-agents to inspect and update workspace panel visibility, order, and preferred sizes.

## Primary Actor

Enabled sub-agent.

## Preconditions

- `workspace:layout` tool is enabled for the sub-agent.
- The main window has registered its layout service.

## Main Flow

1. The model calls the workspace layout tool with `get`.
2. The tool requests the current layout report.
3. The service returns stage, desktop, and workspace panel slot state.
4. The model uses the reported slots to reason about visible panels and available screen space.
5. When an enabled model needs a layout change, it calls `workspace:layout` with `set` and a bounded window patch.
6. The service persists the requested state and notifies the live Compose workspace.
7. The Compose workspace restores the patched visibility, order, and preferred sizes into the semantic split layout.

## Result

The agent can reason about and adjust the visible workspace layout without relying on fragile free-floating window coordinates.

## Tool Calls

- `workspace:layout` action `get`: inspects screens, main-window bounds, desktop bounds, and visible workspace panel slots.
- `workspace:layout` action `set`: updates panel visibility, z-order, and preferred bounds such as width and height.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.WorkspaceLayoutTool`
- `de.heckenmann.visualagent.workspace.layout.WorkspaceLayoutService`
- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`

## Acceptance Criteria

- The tool reports current workspace and panel slot positions.
- Hidden panels are reported as hidden.
- The tool can persist model-requested panel size changes.
- Live Compose workspaces are notified when the tool applies updated window states.
- Invalid requests return clear tool failures.
