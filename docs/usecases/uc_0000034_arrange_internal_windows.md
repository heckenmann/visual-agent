# UC-0000034: Arrange Workspace Panels

## Goal

Let users arrange and resize application panels in a deterministic, designer-curated workspace inside the main application window.

## Primary Actor

Desktop user.

## Preconditions

- The Compose workspace is initialized.
- Workspace panel descriptors are available to the Compose shell.

## Main Flow

1. The user opens a panel from navigation.
2. The panel appears in the workspace.
3. The Compose shell recalculates visible panel slots deterministically.
4. The first visible panel in the user-defined order becomes the primary stage.
5. Supporting panels are placed in a right-side inspector stack.
6. Additional supporting panels are placed in a bottom deck.
7. The user can move a panel earlier or later through icon-only buttons in the panel header.
8. The user can drag the panel resize handle to update the preferred panel size used by the split layout.
9. The user can hide a panel from either the left rail or the panel header hide button.
10. Panel cards use consistent spacing, rounded chrome, subtle borders, and compact headers.
11. The Compose shell exposes the calculated slot bounds through the workspace layout service.

## Result

The user can keep multiple panels visible, ordered, and sized for the current task without overlap or invalid resize states.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`
- `de.heckenmann.visualagent.ui.compose.ComposeWorkspaceComponents`
- `de.heckenmann.visualagent.ui.compose.ComposeWorkspaceModels`

## Acceptance Criteria

- Workspace panels stay inside usable workspace bounds.
- Each registered panel can be opened through navigation.
- Each visible panel can be hidden from its own panel header.
- Panel header order controls update the user-defined panel order.
- Panel resize handles update the preferred panel size while respecting minimum and viewport limits.
- Visible panels do not overlap.
- The semantic layout accepts pointer-driven resize preferences without allowing free-floating overlap.
- Large panel contents remain scrollable without expensive desktop chrome effects being recalculated.
- Panel cards remain visually separable through spacing, borders, header contrast, and clear primary/secondary hierarchy.
