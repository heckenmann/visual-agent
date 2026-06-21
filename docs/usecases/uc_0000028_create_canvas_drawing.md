# UC-0000028: Create Canvas Drawing

## Goal

Let the user create and edit structured canvas drawings with selectable figures, freehand paths, text, shapes, and images.

## Primary Actor

Desktop user.

## Preconditions

- Canvas panel is visible.
- JHotDraw drawing components are initialized.

## Main Flow

1. The user selects a canvas tool.
2. The user draws, inserts, selects, moves, resizes, or deletes figures.
3. The canvas model stores structured figures rather than raster-only pixels.
4. UI controls reflect current selection and zoom state.

## Result

The canvas remains editable during resize and after object insertion.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.panels.canvas.CanvasPanel`
- `de.heckenmann.visualagent.ui.panels.canvas.CanvasToolbar`
- `de.heckenmann.visualagent.ui.panels.canvas.CanvasFigureFactory`
- `de.heckenmann.visualagent.ui.panels.canvas.CanvasSelectionDeletionController`

## Acceptance Criteria

- Inserted images can be selected, moved, and resized.
- Delete key, context actions, and delete toolbar button remove selected figures.
- Toolbar buttons are icon-only with tooltips.
