# UC-0000035: Persist Workspace Panel Layout

## Goal

Save the main application window size, workspace panel visibility, order, preferred sizes, and layout state when the application exits and restore them on startup.

## Primary Actor

Desktop user.

## Preconditions

- Workspace layout persistence is available.
- Workspace panels have stable IDs.

## Main Flow

1. The user opens, hides, or reorders workspace panels from the rail or the workspace row, or resizes the main application window.
2. The main window size, panel visibility, user-defined order, and preferred panel widths are captured.
3. Layout state is persisted through the workspace layout service on a background
   dispatcher. Updates are processed sequentially; geometry-only updates do not
   rewrite panel preferences, and intermediate updates may be coalesced while a
   previous operation is still running.
4. When the application exits, the desktop awaits the shared layout coordinator's
   final background write before closing the server. It saves the main window size
   and position off the UI thread and ignores duplicate close requests.
5. On startup, the stored main window size and workspace layout are loaded while the independent splash window remains visible.
6. After server readiness, the splash is disposed and the main window opens with the previously saved size.
7. Workspace panels are restored to their previous visibility state, user-defined order, and preferred widths.
8. Restored panels are placed side by side in the horizontal workspace row.

## Result

The user's preferred main window size, panel set, panel order, and persisted panel sizing survive restart.

## Tool Calls

- `workspace:layout get` returns persisted panel order, visibility, and preferred width plus the saved main window size.
- `workspace:layout set` persists model-requested changes to panel order, visibility, and preferred width.

## Code Entry Points

- `de.heckenmann.visualagent.desktop.ComposeStartupHost`
- `de.heckenmann.visualagent.ui.workspace.ComposeSplitWorkspace`
- `de.heckenmann.visualagent.ui.workspace.ComposeWorkspaceModels`
- `de.heckenmann.visualagent.workspace.layout.WorkspaceLayoutService`
- `de.heckenmann.visualagent.workspace.layout.WorkspaceLayoutPersistence`
- `de.heckenmann.visualagent.ui.application.VisualAgentComposeApp`
- `de.heckenmann.visualagent.ui.application.WorkspaceLayoutEffects`
- `de.heckenmann.visualagent.ui.application.WorkspaceLayoutPersistenceCoordinator`

## Acceptance Criteria

- The main window restores its previously saved width and height.
- Splash geometry is never used for main-window restoration.
- Restored panels use persisted IDs.
- Restored panels preserve user-defined order.
- Restored panels preserve user- or model-defined preferred sizes.
- Panels hidden before shutdown stay hidden after restart.
- Missing panels or invalid stored bounds do not prevent startup.
- Restored panels are always placed inside the visible horizontal workspace row.
- A persisted main-window position outside the current screen is clamped before the main window is presented.
- Layout protocol calls and persistence never block composition or the UI thread.
- Slow persistence preserves update order and eventually saves the newest panel state.
- Application exit flushes pending panel changes before server resources close;
  finished coordinators never issue later writes against the closed server.
- Temporary workspace composition disposal flushes pending state without
  permanently finishing the window-owned coordinator. Recreated compositions
  continue persisting visibility, order, and preferred widths.
