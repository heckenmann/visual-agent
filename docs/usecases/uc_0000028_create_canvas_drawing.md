# UC-0000028: Create Canvas Drawing

## Goal

Let the user create and edit structured canvas drawings with selectable figures, freehand paths, text, shapes, and images.

## Primary Actor

Desktop user.

## Preconditions

- Canvas panel is visible.
- Toolkit-neutral canvas service and Compose canvas surface are initialized.

## Main Flow

1. The user selects a canvas tool.
2. The user draws, inserts, selects, moves, resizes, or deletes figures.
3. The canvas model stores structured figures rather than raster-only pixels.
4. UI controls reflect current selection and zoom state.
5. Toolbar clear/delete actions show an internal confirmation modal before mutating the canvas.
6. The user can press `Delete` or `Backspace` while the canvas has focus to remove the selected figure.

## Result

The canvas remains editable during resize and after object insertion.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.CanvasPanel`
- `de.heckenmann.visualagent.ui.compose.CanvasSurface`
- `de.heckenmann.visualagent.ui.compose.ComposeModalHost`
- `de.heckenmann.visualagent.canvas.CanvasOperations`
- `de.heckenmann.visualagent.canvas.InMemoryCanvasService`

## Acceptance Criteria

- Figures can be selected, moved, and resized from the Compose canvas surface.
- The delete toolbar button is enabled only when a figure is selected and removes the selected figure after internal modal confirmation.
- The clear toolbar button clears all figures only after internal modal confirmation.
- `Delete` and `Backspace` remove the selected figure when the Compose canvas surface has focus.
- Toolbar buttons are icon-only with tooltips.
