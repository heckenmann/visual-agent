# UC-0000066: Export Canvas PNG

## Goal

Let the user export the current editable canvas view to a PNG file.

## Primary Actor

Desktop user.

## Preconditions

- Canvas panel is visible.
- The user can choose a destination file.

## Main Flow

1. The user clicks the canvas export action.
2. A PNG save dialog is shown.
3. Selection handles are cleared.
4. The canvas node is snapshotted using the current Compose Multiplatform window render scale.
5. Snapshot bytes are encoded as PNG and written to disk.

## Result

The current canvas can be shared outside the managed workspace as a PNG image.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`
- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`
- `de.heckenmann.visualagent.image.PngEncoder`
- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`

## Acceptance Criteria

- Cancelling the file dialog writes no file.
- The export excludes selection handles.
- The written file is valid PNG bytes.
- On high-DPI displays, exported PNG dimensions use the Compose Multiplatform render scale instead of forcing a logical 1x snapshot.
