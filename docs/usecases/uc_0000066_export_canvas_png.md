# UC-0000066: Capture Canvas PNG

## Goal

Let the user capture the current editable canvas as a managed PNG workspace file.

## Primary Actor

Desktop user.

## Preconditions

- Canvas panel is visible.
- Workspace file persistence is available.

## Main Flow

1. The user clicks the canvas capture action.
2. The current toolkit-neutral canvas state is rendered to PNG bytes.
3. The PNG is saved as a managed workspace file.
4. The files panel can list the generated PNG after refresh.

## Result

The current canvas can be inspected and reused as a PNG image in the managed workspace.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.CanvasPanel`
- `de.heckenmann.visualagent.canvas.InMemoryCanvasService`
- `de.heckenmann.visualagent.canvas.CanvasPngRenderer`
- `de.heckenmann.visualagent.image.RgbaPngEncoder`
- `de.heckenmann.visualagent.workspace.WorkspaceFileService`

## Acceptance Criteria

- The capture action writes a managed PNG workspace file.
- The written file is valid PNG bytes.
- The capture is rendered from toolkit-neutral canvas data instead of UI chrome or selection handles.
