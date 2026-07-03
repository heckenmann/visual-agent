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
5. Selected figures are shown with a thin, unobtrusive border instead of a large bright background so the figure and surrounding content remain visible.
6. The underlying canvas library hard-codes a white background behind every node; shapes that should reveal the canvas use the canvas background color as the closest achievable approximation to transparency.
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
- `de.heckenmann.visualagent.ui.compose.ActionIconButton`
- `de.heckenmann.visualagent.canvas.CanvasOperations`
- `de.heckenmann.visualagent.canvas.InMemoryCanvasService`

## Acceptance Criteria

- Figures can be selected, moved, and resized from the Compose canvas surface.
- Selected figures are highlighted by a thin colored border.
- Shapes that should reveal the canvas (stroke, line, image placeholder) use the canvas background color to mask the library's hard-coded white node background.
- The delete toolbar button is enabled only when a figure is selected and removes the selected figure after internal modal confirmation.
- The clear toolbar button clears all figures only after internal modal confirmation.
- `Delete` and `Backspace` remove the selected figure when the Compose canvas surface has focus.
- Toolbar buttons are icon-only with tooltips.
