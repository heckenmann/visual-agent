# UC-0000102: Delete a Workspace Directory

## Goal

Allow the main agent to remove a managed workspace directory safely, with recursive deletion only when explicitly requested.

## Preconditions

- The workspace directory is managed by the application server.
- The workspace root itself is never a valid deletion target.

## Main Flow

1. The agent calls `workspace:file` with `action=deleteDirectory` and a workspace-relative `path`.
2. The server deletes an empty directory when `recursive` is omitted or `false`.
3. When `recursive=true` is supplied, the server deletes all descendants and their persisted file metadata.
4. The filesystem operation and metadata removals run inside one database transaction boundary.
5. The server records the completed mutation as passive conversation context without triggering a new agent request.

## Alternative Flows

- A non-empty directory without `recursive=true` is rejected and remains unchanged.
- A path outside the managed workspace, a missing directory, or the workspace root is rejected.

## Tool Calls

- `workspace:file` with `{"action":"deleteDirectory","path":"projects/demo"}` removes an empty directory.
- `workspace:file` with `{"action":"deleteDirectory","path":"projects/demo","recursive":true}` removes the directory contents and metadata.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.WorkspaceFileTool`
- `de.heckenmann.visualagent.workspace.WorkspaceFileService.deleteDirectory`
- `de.heckenmann.visualagent.server.SpringWorkspaceFilePort`

## Acceptance Criteria

- Recursive deletion is never implicit.
- Nested managed files and their metadata are removed together.
- The workspace root cannot be deleted.
- Failed non-recursive deletion leaves the directory and all contents intact.
