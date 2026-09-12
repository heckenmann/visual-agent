# UC-0000022: Read And Modify Authorized Files

## Goal

Allow enabled agents to inspect and edit managed-workspace and explicitly granted files without unmanaged host paths.

## Primary Actor

Enabled agent.

## Preconditions

- `workspace:file` is enabled.
- The target is addressed by the managed workspace or an opaque granted root ID.

## Main Flow

1. The model calls `workspace:file` with a list, readText, glob, grep, writeText, edit, copy, or move action.
2. The filesystem-owning server or client capability validates the opaque root ID and relative path.
3. A copy or move identifies both source and target with separate root IDs and relative paths.
4. For a move, the target is copied and verified before the source is deleted.
5. The result is returned as text or structured metadata.

## Result

Agents can work on source files while path handling remains centralized.

## Tool Calls

- `workspace:file` actions `list`, `readText`, `glob`, `grep`, `writeText`, `edit`, `copy`, and `move`.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.WorkspaceFileTool`
- `de.heckenmann.visualagent.workspace.UnifiedFileService`
- `de.heckenmann.visualagent.workspace.DirectoryGrantService`

## Acceptance Criteria

- Path traversal outside the workspace is rejected.
- Read/write/edit outputs are bounded enough for model consumption.
- Copy supports distinct server-owned grant IDs; the source may be read-only and the target must be read-write.
- Move requires read-write access to both server-owned grants and deletes the source only after target verification.
- Transfers that include a client-owned root or cross the workspace/grant boundary fail explicitly until the file-exchange transport is available.
