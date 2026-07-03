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
2. The main window size, visibility state, user-defined order, preferred panel sizes from existing layout state, and the current calculated workspace slots are captured.
3. Layout state is persisted through the workspace layout service.
4. When the application exits, the latest workspace state and window size have been saved.
5. On startup, the stored main window size and workspace layout are loaded.
6. The main window opens with the previously saved size.
7. Workspace panels are restored to their previous visibility state, user-defined order, and preferred sizes.
8. Restored panels are placed into valid semantic workspace slots.

## Result

The user's preferred main window size, panel set, panel order, and persisted panel sizing survive restart.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.workspace.layout.WorkspaceLayoutPersistence`
- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`

## Acceptance Criteria

- The main window restores its previously saved width and height.
- Restored panels use persisted IDs.
- Restored panels preserve user-defined order.
- Restored panels preserve user- or model-defined preferred sizes.
- Panels hidden before shutdown stay hidden after restart.
- Missing panels or invalid stored bounds do not prevent startup.
- Restored panels are always placed inside the visible semantic workspace.
