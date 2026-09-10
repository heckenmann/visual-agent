# UC-0000108: Grant directory access

## Goal

Let a user explicitly approve an additional server filesystem directory with a persistent, least-privilege access mode.

## Primary Actors

- User
- Application server

## Preconditions

- The server can resolve and read the proposed absolute directory.

## Main Flow

1. The user opens **Directory access** and chooses the server origin.
2. The user enters an absolute server path and resolves it before saving.
3. The server rejects missing, relative, unreadable, non-directory, and duplicate canonical roots.
4. The user supplies a display name and chooses **Read only** or **Read and write**.
5. The server persists an opaque grant ID and canonical root in SQLite.
6. The panel shows its origin, access mode, canonical server location, and current availability.
7. The user may explicitly save a new name or mode and may revoke access through a confirmation modal.

## Result

Only the persisted canonical root is available to grant-aware operations; revocation affects all future authorization checks immediately.

## Tool Calls

- None. Grant creation, changes, and revocation are direct-user protocol actions and are never exposed to a model.

## Code Entry Points

- `de.heckenmann.visualagent.workspace.DirectoryGrantService`
- `de.heckenmann.visualagent.server.SpringDirectoryAccessPort`
- `de.heckenmann.visualagent.ui.directories.DirectoryAccessPanel`

## Design Decision

No new library is used. Java NIO supplies canonical path, symlink, containment, and bounded stream primitives, while the existing Spring Data/Flyway stack supplies transactional persistence. Adding a general filesystem library would broaden the dependency and attack surfaces without improving the required owner-side authorization checks.
