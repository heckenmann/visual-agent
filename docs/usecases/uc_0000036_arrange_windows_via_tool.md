# UC-0000036: Inspect Workspace Layout Via Tool

## Goal

Allow enabled sub-agents to inspect main window dimensions, internal workspace dimensions, and visible workspace panel slots.

## Primary Actor

Enabled sub-agent.

## Preconditions

- `workspace:layout` tool is enabled for the sub-agent.
- The main window has registered its layout service.

## Main Flow

1. The model calls the workspace layout tool.
2. The tool requests the current layout report.
3. The service returns stage, desktop, and workspace panel slot state.
4. The model uses the reported slots to reason about visible panels and available screen space.

## Result

The agent can reason about the visible workspace layout without relying on fragile free-floating window coordinates.

## Tool Calls

- `workspace:layout` actions inspect screens, main-window bounds, desktop bounds, and visible workspace panel slots.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.WorkspaceLayoutTool`
- `de.heckenmann.visualagent.workspace.layout.WorkspaceLayoutService`
- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`

## Acceptance Criteria

- The tool reports current workspace and panel slot positions.
- Hidden panels are reported as hidden.
- Invalid requests return clear tool failures.
