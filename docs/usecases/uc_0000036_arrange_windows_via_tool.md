# UC-0000036: Arrange Windows Via Tool

## Goal

Allow enabled sub-agents to inspect screens, main window dimensions, internal workspace dimensions, and arrange internal windows.

## Primary Actor

Enabled sub-agent.

## Preconditions

- `workspace:layout` tool is enabled for the sub-agent.
- The main window has registered its layout service.

## Main Flow

1. The model calls the workspace layout tool.
2. The tool requests the current layout report or a move/resize operation.
3. The service returns screen, stage, desktop, and internal window state.
4. For mutations, requested bounds are applied to the target internal window.

## Result

The agent can reason about and organize the visible workspace layout.

## Tool Calls

- `workspace:layout` actions inspect screens/window bounds and move or resize internal windows.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.WorkspaceLayoutTool`
- `de.heckenmann.visualagent.ui.WorkspaceLayoutService`
- `de.heckenmann.visualagent.ui.WorkspaceWindowManager`

## Acceptance Criteria

- The tool reports available screens and current window positions.
- Invalid window IDs or bounds return clear tool failures.
