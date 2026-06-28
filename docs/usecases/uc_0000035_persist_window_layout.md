# UC-0000035: Persist Workspace Panel Layout

## Goal

Save workspace panel visibility, order, preferred sizes, and layout state when the application exits and restore it on startup.

## Primary Actor

Desktop user.

## Preconditions

- Workspace layout persistence is available.
- Workspace panels have stable IDs.

## Main Flow

1. The user opens, hides, reorders, or resizes workspace panels.
2. Visibility state, user-defined order, preferred panel sizes, and the current calculated workspace slots are captured.
3. Layout state is persisted through the workspace layout service.
4. When the application exits, the latest workspace state has been saved.
5. On startup, stored workspace layout is loaded.
6. Workspace panels are restored to their previous visibility state, user-defined order, and preferred sizes.
7. Restored panels are placed into valid semantic workspace slots.

## Result

The user's preferred panel set, panel order, and panel sizing survive restart.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.workspace.layout.WorkspaceLayoutPersistence`
- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`

## Acceptance Criteria

- Restored panels use persisted IDs.
- Restored panels preserve user-defined order.
- Restored panels preserve user- or model-defined preferred sizes.
- Panels hidden before shutdown stay hidden after restart.
- Missing panels or invalid stored bounds do not prevent startup.
- Restored panels are always placed inside the visible semantic workspace.
