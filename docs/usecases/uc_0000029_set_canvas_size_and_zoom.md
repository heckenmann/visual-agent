# UC-0000029: Set Canvas Size And Zoom

## Goal

Let the user control the fixed editable canvas surface size and viewport zoom independently from the application window size.

## Primary Actor

Desktop user.

## Preconditions

- Canvas panel is visible.

## Main Flow

1. The user opens canvas size controls or zoom controls.
2. The canvas surface dimensions or zoom value are updated.
3. The drawing view keeps existing figure coordinates stable.
4. Preferences persist size and related canvas settings.

## Result

Window resizing does not move drawings into inaccessible space or blur the structured canvas.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.panels.canvas.CanvasPanel`
- `de.heckenmann.visualagent.ui.panels.canvas.CanvasToolbar`
- `de.heckenmann.visualagent.ui.panels.canvas.CanvasSurfaceSizeController`
- `de.heckenmann.visualagent.ui.panels.canvas.CanvasEditorDefaults`

## Acceptance Criteria

- Increasing window height does not cause canvas jitter.
- The editable canvas surface starts at the viewport origin and is not centered inside a larger blank area.
- Canvas size is user-controlled and survives restart.
- Zoom reset is available through an icon-only toolbar button with tooltip.
