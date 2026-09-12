# UC-0000060: Query Authorized Workspace Roots

## Goal

Let enabled agents discover authorized workspace roots without exposing native filesystem paths.

## Primary Actor

Enabled agent.

## Preconditions

- The `workspace:file` tool is enabled.

## Main Flow

1. The model calls `workspace:file` action `listRoots`.
2. The server returns the managed workspace and currently available grants as opaque IDs.
3. The model uses a returned ID with root-relative paths for later file operations.

## Result

Agents can form correct authorized file operations without learning host paths.

## Tool Calls

- `workspace:file` action `listRoots`.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.WorkspaceFileTool`
- `de.heckenmann.visualagent.workspace.DirectoryGrantService`

## Acceptance Criteria

- Returned IDs are opaque and do not contain a path or directory origin details beyond the safe display metadata.
- Native workspace, configuration, and data paths are never exposed to the model.
