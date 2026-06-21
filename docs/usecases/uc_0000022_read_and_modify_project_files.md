# UC-0000022: Read And Modify Project Files

## Goal

Allow enabled agents to inspect and edit project files through file tools without using unmanaged external paths.

## Primary Actor

Enabled agent.

## Preconditions

- Relevant `file:*` tools are enabled.
- Requested paths are inside the allowed workspace.

## Main Flow

1. The model calls a file read, list, glob, grep, write, or edit tool.
2. Tool support validates and resolves paths.
3. The requested operation is performed.
4. The result is returned as text or structured metadata.

## Result

Agents can work on source files while path handling remains centralized.

## Tool Calls

- `file:read`: read a workspace file.
- `file:list`: list files.
- `file:glob`: find files by glob.
- `file:grep`: search file contents.
- `file:write`: write a file.
- `file:edit`: replace text in a file.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.FileReadTool`
- `de.heckenmann.visualagent.agent.tools.FileListTool`
- `de.heckenmann.visualagent.agent.tools.FileGlobTool`
- `de.heckenmann.visualagent.agent.tools.FileGrepTool`
- `de.heckenmann.visualagent.agent.tools.FileWriteTool`
- `de.heckenmann.visualagent.agent.tools.FileEditTool`

## Acceptance Criteria

- Path traversal outside the workspace is rejected.
- Read/write/edit outputs are bounded enough for model consumption.
