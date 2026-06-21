# UC-0000026: Sync Workspace Filesystem Metadata

## Goal

Reconcile managed workspace files on disk with database metadata after external changes or previous inconsistencies.

## Primary Actor

Desktop user and enabled sub-agent.

## Preconditions

- The managed workspace directory exists or can be created.
- The workspace file store is available.

## Main Flow

1. The user clicks sync or a model calls the sync action.
2. The service scans regular files under the workspace root.
3. Missing DB records are added.
4. Changed files update size, MIME type, hash, and timestamp.
5. Records for missing files are removed.
6. A reconciliation report is returned.

## Result

Filesystem and database metadata return to a consistent state.

## Tool Calls

- `workspace:file` with action `sync` reconciles managed files and database metadata.

## Code Entry Points

- `de.heckenmann.visualagent.workspace.WorkspaceFileService.syncMetadataWithFilesystem`
- `de.heckenmann.visualagent.agent.tools.WorkspaceFileTool`
- `de.heckenmann.visualagent.ui.panels.FilesPanel`

## Acceptance Criteria

- Report includes added, updated, removed, and total counts.
- Files outside the managed workspace are ignored.
