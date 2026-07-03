# UC-0000028: Create Canvas Drawing

## Goal

Let the user create and edit structured canvas drawings with selectable figures, freehand paths, text, shapes, and images.

## Primary Actor

Desktop user.

## Preconditions

- Canvas panel is visible.
- Toolkit-neutral canvas service and Compose canvas surface are initialized.

## Main Flow

1. The user selects a canvas tool from the drawing toolbar.
2. The toolbar shows separate Select and Pen buttons; the active mode is highlighted so the user always knows which tool is selected.
3. The user draws, inserts, selects, moves, resizes, or deletes figures.
4. The canvas model stores structured figures rather than raster-only pixels.
5. UI controls reflect current selection and zoom state.
6. Selected figures are shown with a thin, unobtrusive border instead of a large bright background so the figure and surrounding content remain visible.
7. The toolbar clear action shows an internal confirmation modal before removing all figures.
8. The toolbar delete action and the `Delete`/`Backspace` keys remove all selected figures immediately without showing a confirmation modal.

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
- Multiple figures can be selected at the same time; each selected figure is highlighted by a thin colored border.
- Selected figure backgrounds stay transparent or use their natural color.
- The delete toolbar button is enabled whenever at least one figure is selected and removes all selected figures immediately without confirmation.
- The clear toolbar button clears all figures only after internal modal confirmation.
- `Delete` and `Backspace` remove all selected figures when the Compose canvas surface has focus.
- Toolbar buttons are icon-only with tooltips; the active Select/Pen mode button is highlighted.
