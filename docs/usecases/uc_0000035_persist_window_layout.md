# UC-0000035: Persist Workspace Panel Layout

## Goal

Save workspace panel visibility and layout state when the application exits and restore it on startup.

## Primary Actor

Desktop user.

## Preconditions

- Workspace layout persistence is available.
- Workspace panels have stable IDs.

## Main Flow

1. The user opens or closes workspace panels.
2. Visibility state and the current calculated split slots are captured.
3. Layout state is persisted.
4. On startup, stored workspace layout is loaded.
5. Workspace panels are restored to their previous visibility state and placed into valid split slots.

## Result

The user's preferred panel set survives restart.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.workspace.layout.WorkspaceLayoutPersistence`
- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`

## Acceptance Criteria

- Restored panels use persisted IDs.
- Missing panels or invalid stored bounds do not prevent startup.
- Restored panels are always placed inside the visible split workspace.
