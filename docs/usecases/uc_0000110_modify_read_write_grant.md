# UC-0000110: Modify a read-write directory grant

## Goal

Allow a model to perform a narrowly defined text mutation beneath an explicitly read-write server grant.

## Primary Actors

- Main agent or configured sub-agent
- Application server

## Preconditions

- The user created a server grant and explicitly selected **Read and write**.
- `workspace:directory` is enabled for the requesting agent.

## Main Flow

1. The model calls `writeText` with an opaque grant ID, relative file path, and bounded UTF-8 content.
2. Immediately before mutation, the server reloads the persisted grant and verifies its read-write mode.
3. The server resolves the real parent and any existing target and rejects traversal or symlink escape.
4. The server creates or replaces only the authorized text file and returns its grant-relative path.
5. A mode change or revocation causes future mutations to fail closed.

## Result

The mutation is confined to the approved root and does not grant terminal, JavaScript, canvas, provider, or arbitrary JVM filesystem access.

## Tool Calls

- `workspace:directory` — `writeText` is available only for a currently authorized `READ_WRITE` server grant.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.WorkspaceDirectoryTool`
- `de.heckenmann.visualagent.workspace.DirectoryGrantService`

## Acceptance Criteria

- `READ_ONLY` returns `ACCESS_DENIED: directory is read-only`.
- Authorization is reloaded immediately before writing.
- Content and search/list responses are bounded.
- Existing managed-workspace, terminal, JavaScript, canvas, and provider boundaries do not route through directory grants.
