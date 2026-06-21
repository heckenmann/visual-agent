# UC-0000060: Query Workspace Directory Tool

## Goal

Let enabled agents ask for the current workspace directory used by file and terminal tools.

## Primary Actor

Enabled agent.

## Preconditions

- The `pwd` tool is enabled.

## Main Flow

1. The model calls the `pwd` tool.
2. The tool resolves the workspace root.
3. The path is returned as text.

## Result

Agents can form correct relative file operations for subsequent tool calls.

## Tool Calls

- `pwd`: returns the workspace root used by file and terminal tools.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.PwdTool`
- `de.heckenmann.visualagent.agent.tools.ToolSupport`

## Acceptance Criteria

- The returned path matches the workspace root used by file tools.
- The tool does not expose secrets.
