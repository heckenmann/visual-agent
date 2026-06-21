# UC-0000035: Persist Window Layout

## Goal

Save internal window arrangement when the application exits and restore it on startup.

## Primary Actor

Desktop user.

## Preconditions

- Workspace layout persistence is available.
- Internal windows have stable IDs.

## Main Flow

1. The user arranges internal windows.
2. Bounds and visibility state are captured.
3. Layout state is persisted.
4. On startup, stored window layout is loaded.
5. Internal windows are restored to their previous positions and sizes.

## Result

The user's preferred workspace layout survives restart.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.WorkspaceLayoutPersistence`
- `de.heckenmann.visualagent.ui.WorkspaceWindowManager`
- `de.heckenmann.visualagent.ui.MainWindowWorkspace`

## Acceptance Criteria

- Restored windows use persisted IDs and bounds.
- Missing panels or invalid bounds do not prevent startup.
