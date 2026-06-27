# UC-0000034: Arrange Workspace Panels

## Goal

Let users arrange application panels in a deterministic split workspace inside the main application window.

## Primary Actor

Desktop user.

## Preconditions

- The Compose workspace is initialized.
- Workspace panel descriptors are available to the Compose shell.

## Main Flow

1. The user opens a panel from navigation.
2. The panel appears in the split workspace.
3. The Compose shell recalculates visible panel slots deterministically.
4. One visible panel fills the workspace.
5. Two visible panels are shown side by side.
6. Three visible panels use one large left slot and two stacked right slots.
7. Four or more visible panels are shown in a two-column grid.
8. The Compose shell exposes the calculated slot bounds through the workspace layout service.

## Result

The user can keep multiple panels visible without overlap, drag jitter, or invalid resize states.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`
- `de.heckenmann.visualagent.ui.compose.ComposeWorkspaceComponents`
- `de.heckenmann.visualagent.ui.compose.ComposeWorkspaceModels`

## Acceptance Criteria

- Workspace panels stay inside usable workspace bounds.
- Each registered panel can be opened through navigation.
- Visible panels do not overlap.
- The split layout avoids pointer-driven drag and resize work.
- Large panel contents remain scrollable without expensive desktop chrome effects being recalculated.
- Panel cards remain visually separable through borders and header contrast.
