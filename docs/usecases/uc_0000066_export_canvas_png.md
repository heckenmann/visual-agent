# UC-0000066: Capture Canvas PNG

## Goal

Let the user capture the current editable canvas as a managed PNG workspace file.

## Primary Actor

Desktop user.

## Preconditions

- Canvas panel is visible.
- Workspace file persistence is available.

## Main Flow

1. The user enters or accepts a PNG filename in the canvas panel.
2. The user clicks the canvas capture action.
3. The current toolkit-neutral canvas state is rendered to PNG bytes.
4. The PNG is saved as a managed workspace file.
5. The files panel can list the generated PNG after refresh.

## Result

The current canvas can be inspected and reused as a PNG image in the managed workspace.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.canvas.CanvasPanel`
- `de.heckenmann.visualagent.canvas.InMemoryCanvasService`
- `de.heckenmann.visualagent.canvas.CanvasPngRenderer`
- `de.heckenmann.visualagent.canvas.CanvasCaptureLimits`
- `de.heckenmann.visualagent.workspace.WorkspaceFileService`

## Acceptance Criteria

- The capture action writes a managed PNG workspace file.
- The written file is valid PNG bytes.
- The capture is rendered from toolkit-neutral canvas data instead of UI chrome or selection handles.
- The UI capture action is disabled until a non-blank PNG filename is available.
- Capture uses the same bounded Skia renderer as immutable conversation snapshots
  (UC-0000032): actual glyphs/images, authorized managed image bytes, aspect-preserving
  scaling, at most 4096 pixels per dimension and 4,194,304 pixels overall. Decoder
  dimensions are checked before bitmap allocation and decoding must succeed before
  drawing. Text wraps to the figure width with a two-line limit and ellipsis.
  Oversized, corrupt or incomplete input returns an actionable error and does not
  mutate the editable document.
