# UC-0000108: Grant directory access

## Goal

Let a user explicitly approve an additional server- or client-owned directory with a persistent, least-privilege access mode.

## Primary Actors

- User
- Desktop client
- Application server

## Preconditions

- The selected filesystem owner can resolve and read the proposed directory.

## Main Flow

1. The user opens **Directory access** and selects **This device** or **Application server**.
2. For **This device**, the native FileKit picker selects a client directory. For **Application server**, the dialog browses paged server-owned directories through short-lived opaque server selections.
3. The filesystem owner rejects missing, relative, unreadable, non-directory, duplicate, traversal, symlink-escaping, Visual-Agent-data, Visual-Agent-configuration, and ancestor roots.
4. The user supplies a display name and chooses **Read only** or **Read and write**.
5. The server persists an opaque grant ID, origin, mode, and owner client ID. A client root path never crosses the client/server boundary or enters SQLite.
6. The panel shows its origin, access mode, display name, and current availability without exposing a native root path.
7. The user may explicitly save a new name or mode and may revoke access through a confirmation modal.
8. When a client grant becomes unavailable after reconnect or restart, the card offers **Reconnect directory**. The user selects the client directory again; the same device registers a fresh non-persisted capability for the existing grant ID.

## Result

Only the persisted server root or the live client capability is available to grant-aware operations; revocation affects all future authorization checks immediately. The model cannot create, change, reconnect, or revoke a grant.

## Tool Calls

- None. Grant creation, changes, and revocation are direct-user protocol actions and are never exposed to a model.

## Code Entry Points

- `de.heckenmann.visualagent.workspace.DirectoryGrantService`
- `de.heckenmann.visualagent.server.SpringDirectoryGrantAdministrationPort`
- `de.heckenmann.visualagent.ui.directories.DirectoryAccessPanel`

## Design Decision

FileKit supplies the existing native client directory picker. Java NIO supplies server-side canonical path, symlink, containment, and bounded stream primitives, while the existing Spring Data/Flyway stack supplies transactional persistence. Adding a general filesystem library would broaden the dependency and attack surfaces without improving the required owner-side authorization checks.
