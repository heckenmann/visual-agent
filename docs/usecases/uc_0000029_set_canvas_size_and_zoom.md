# UC-0000029: Maintain Canvas Viewport And Figure Sizing

## Goal

Keep the editable canvas coordinate surface stable while the user resizes the canvas panel or individual figures.

## Primary Actor

Desktop user.

## Preconditions

- Canvas panel is visible.

## Main Flow

1. The user opens the canvas panel.
2. The user pans or zooms the InfiniteCanvas-backed surface, resizes the workspace panel, drags a figure node, or drags a selected figure resize handle.
3. The Compose canvas surface keeps existing figure coordinates stable while InfiniteCanvas handles viewport pan/zoom and node dragging.
4. Figure move and resize operations are written back through the canvas service.
5. Saved canvas documents preserve figure dimensions, zoom metadata, and grid metadata from the toolkit-neutral canvas model.

## Result

Workspace panel resizing does not move drawings into inaccessible space or blur the structured canvas.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.CanvasPanel`
- `de.heckenmann.visualagent.ui.compose.ComposeSplitWorkspace`
- `de.heckenmann.visualagent.canvas.InMemoryCanvasService`
- `de.heckenmann.visualagent.canvas.CanvasOperations`
- `io.github.xingray.compose.infinitecanvas.InfiniteCanvas`

## Acceptance Criteria

- Resizing the workspace panel does not cause canvas jitter.
- The editable canvas surface supports pan and zoom through the InfiniteCanvas viewport controls.
- Figures can be moved by dragging their canvas nodes.
- Selected figures can be resized from the Compose canvas surface.
- Saved canvas documents preserve zoom and grid metadata from the canvas model.
