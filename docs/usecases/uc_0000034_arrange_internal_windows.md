# UC-0000034: Arrange Workspace Panels

## Goal

Let users arrange application panels in a single, horizontally scrollable workspace row inside the main application window.

## Primary Actor

Desktop user.

## Preconditions

- The Compose workspace is initialized.
- Workspace panel descriptors are available to the Compose shell.

## Main Flow

1. The user opens a panel from the left navigation rail.
2. The panel appears as a card in the horizontal workspace row.
3. The Compose shell lays out visible panels in the user-defined order, each panel using its own stored preferred width.
4. The first visible panel in the user-defined order becomes the primary stage.
5. Supporting panels are placed to the right of the primary stage.
6. The user drags a panel by its header drag handle to reorder the row.
7. The user drags the resizer between two panels to change the width of the left panel; all panels to the right shift right, and the row becomes scrollable if needed. The resizer area shows a horizontal resize cursor.
8. The user long-presses or right-clicks a rail button and chooses "Set width…" to adjust a panel's preferred width with a slider.
9. The user can move a panel earlier or later through vertical drag gestures on the rail button.
10. The user can hide a panel from either the left rail or the panel header close button.
11. When the combined panel widths exceed the viewport, the user can scroll horizontally with a horizontal mouse wheel, the on-screen scroll arrows, or the horizontal scrollbar.
12. Panel cards use consistent spacing, rounded chrome, subtle borders, and compact headers.
13. The Compose shell exposes the calculated slot bounds through the workspace layout service.

## Result

The user can keep multiple panels visible and ordered for the current task without overlap, with each panel keeping its own width across reordering.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`
- `de.heckenmann.visualagent.ui.compose.ComposeWorkspaceComponents`
- `de.heckenmann.visualagent.ui.compose.ComposeWorkspaceModels`
- `de.heckenmann.visualagent.ui.compose.ComposeWorkspaceNavigation`
- `de.heckenmann.visualagent.ui.compose.PanelDragHandle`
- `sh.calvin.reorderable.ReorderableRow`

## Acceptance Criteria

- Workspace panels stay inside usable workspace bounds.
- Each registered panel can be opened through navigation.
- Each visible panel can be hidden from its own panel header or the rail.
- Dragging a panel header reorders the user-defined panel order.
- Panel widths are attached to the panel identity, not to its position; reordering does not change panel widths.
- Dragging a resizer changes only the left panel's width and shifts all panels to the right instead of shrinking a neighbour; the resizer cursor shows horizontal resize arrows.
- Panel width changes can be made through a slider reachable from the rail button context menu.
- Horizontal scrolling works through horizontal mouse wheels, on-screen scroll arrows, and the horizontal scrollbar when the row overflows.
- Visible panels do not overlap.
- Large panel contents remain scrollable without expensive desktop chrome effects being recalculated.
- Panel cards remain visually separable through spacing, borders, header contrast, and clear primary/secondary hierarchy.
