# UC-0000033: Modify Canvas Via Tool

## Goal

Allow enabled sub-agents to inspect and modify the canvas through explicit tool calls.

## Primary Actor

Enabled sub-agent.

## Preconditions

- The canvas tool is enabled for the sub-agent.
- The canvas service can resolve the active canvas panel.

## Main Flow

1. The model calls the canvas tool.
2. The tool validates the requested action and inputs.
3. The canvas service runs model operations on the JavaFX thread.
4. The canvas updates with added, cleared, or inspected figures.

## Result

Agents can create or modify visual content while the main agent remains limited to orchestration.

## Tool Calls

- `canvas` actions: `get`, `clear`, `drawText`, `drawRect`, `drawLine`, `drawCircle`, `insertImage`, `captureImage`.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.CanvasTool`
- `de.heckenmann.visualagent.ui.panels.canvas.CanvasService`
- `de.heckenmann.visualagent.ui.panels.canvas.CanvasOperations`

## Acceptance Criteria

- The main agent does not receive direct canvas tools.
- Workspace image insertion rejects paths outside the managed workspace.
- Tool responses summarize canvas state or mutation results.
