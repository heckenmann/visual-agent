# UC-0000033: Modify Canvas Via Tool

## Goal

Allow enabled sub-agents to inspect and modify the canvas through explicit tool calls.

## Primary Actor

Enabled sub-agent.

## Preconditions

- The canvas tool is enabled for the sub-agent.
- The toolkit-neutral canvas service is available.

## Main Flow

1. The model calls the canvas tool.
2. The tool validates the requested action and inputs.
3. The canvas service applies the requested operation to the current canvas state.
4. For document actions, the service reads or writes managed `.canvas` workspace files.
5. The tool returns the updated, inspected, saved, or loaded canvas state.

## Result

Agents can create, select, move, resize, delete, save, open, or capture visual content while the main agent remains limited to orchestration.

## Tool Calls

- `canvas` actions: `get`, `clear`, `drawText`, `drawRect`, `drawLine`, `drawCircle`, `insertImage`, `select`, `selectAt`, `moveFigure`, `resizeFigure`, `deleteFigure`, `saveDocument`, `openDocument`, `captureImage`.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.canvas.CanvasTool`
- `de.heckenmann.visualagent.canvas.InMemoryCanvasService`
- `de.heckenmann.visualagent.canvas.CanvasOperations`

## Acceptance Criteria

- The main agent does not receive direct canvas tools.
- Workspace image insertion rejects paths outside the managed workspace.
- Figure selection, movement, resizing, and deletion return updated canvas snapshots.
- Save and open actions use managed workspace canvas files.
- Tool responses summarize canvas state or mutation results.
