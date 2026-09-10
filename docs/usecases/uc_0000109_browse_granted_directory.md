# UC-0000109: Browse a granted directory

## Goal

Let an enabled model inspect only explicitly granted directory contents without receiving native host paths.

## Primary Actors

- Main agent or configured sub-agent
- Application server

## Preconditions

- A direct user action created an available directory grant.
- `workspace:directory` is enabled for the requesting agent.

## Main Flow

1. The model lists grants and receives opaque IDs, names, origins, modes, and availability only.
2. The model supplies one grant ID and a relative path to list entries or read bounded UTF-8 text.
3. The filesystem owner reloads the grant, rejects absolute paths, parent traversal, control characters, and symlink escapes, and canonicalizes the target.
4. Search visits a bounded number of regular files and returns bounded relative-path matches.
5. Missing, revoked, unreadable, disconnected-client, and unavailable grants fail closed.

## Result

The model can query only the authorized grant-relative namespace and never learns the persisted server root automatically.

## Tool Calls

- `workspace:directory` — `listGrants`, `list`, `readText`, and `search` use opaque grant IDs and relative paths.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.WorkspaceDirectoryTool`
- `de.heckenmann.visualagent.agent.tools.DirectoryToolPortAdapter`
- `de.heckenmann.visualagent.workspace.DirectoryGrantService`
