# UC-0000025: Search Workspace Files

## Goal

Search workspace file metadata and bounded extracted or textual content from both the files panel and tool calls.

## Primary Actor

Desktop user and enabled sub-agent.

## Preconditions

- Workspace files exist.
- Search query is non-blank.

## Main Flow

1. The user or model provides a search query.
2. The files panel applies a local visible-list filter for quick metadata narrowing, or the model invokes the workspace file search tool for persisted search results.
3. Workspace file metadata is searched case-insensitively.
4. Text files and cached/extractable PDF text are searched within configured limits when using the service/tool search path.
5. Matching records are returned with compact snippets.

## Result

Users and agents can find relevant workspace files without loading all file contents into global context.

## Tool Calls

- `workspace:file` with action `search` searches managed workspace metadata and bounded content.

## Code Entry Points

- `de.heckenmann.visualagent.workspace.WorkspaceFileService.searchFiles`
- `de.heckenmann.visualagent.agent.tools.WorkspaceFileTool`
- `de.heckenmann.visualagent.ui.compose.VisualAgentComposeApplication`

## Acceptance Criteria

- Search does not read unbounded file content.
- Results identify match type and file metadata.
- Empty queries are rejected.
- The files panel filter is non-mutating and can be cleared without changing workspace metadata.
