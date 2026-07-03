# UC-0000035: Persist Workspace Panel Layout

## Goal

Save the main application window size, workspace panel visibility, order, preferred sizes, and layout state when the application exits and restore them on startup.

## Primary Actor

Desktop user.

## Preconditions

- Workspace layout persistence is available.
- Workspace panels have stable IDs.

## Main Flow

1. The user opens, hides, or reorders workspace panels, or resizes the main application window.
2. The main window size, panel visibility, user-defined order, and preferred panel widths are captured.
3. Layout state is persisted through the workspace layout service.
4. When the application exits, the latest workspace state and window size have been saved.
5. On startup, the stored main window size and workspace layout are loaded.
6. The main window opens with the previously saved size.
7. Workspace panels are restored to their previous visibility state, user-defined order, and preferred widths.
8. Restored panels are placed side by side in the horizontal workspace row.

## Result

The user's preferred main window size, panel set, panel order, and persisted panel sizing survive restart.

## Tool Calls

- `workspace:layout get` returns persisted panel order, visibility, and preferred width plus the saved main window size.
- `workspace:layout set` persists model-requested changes to panel order, visibility, and preferred width.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`
- `de.heckenmann.visualagent.ui.compose.ComposeSplitWorkspace`
- `de.heckenmann.visualagent.ui.compose.ComposeWorkspaceModels`
- `de.heckenmann.visualagent.workspace.layout.WorkspaceLayoutService`
- `de.heckenmann.visualagent.workspace.layout.WorkspaceLayoutPersistence`
- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`

## Acceptance Criteria

- The main window restores its previously saved width and height.
- Restored panels use persisted IDs.
- Restored panels preserve user-defined order.
- Restored panels preserve user- or model-defined preferred sizes.
- Panels hidden before shutdown stay hidden after restart.
- Missing panels or invalid stored bounds do not prevent startup.
- Restored panels are always placed inside the visible horizontal workspace row.
