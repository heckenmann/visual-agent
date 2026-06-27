# UC-0000027: Analyze Workspace File Via Tool

## Goal

Allow an enabled sub-agent to list, inspect, hash, read, extract text from, render, or analyze imported workspace files by explicit tool call.

## Primary Actor

Enabled sub-agent.

## Preconditions

- `workspace:file` is enabled for the agent.
- The target file exists in managed workspace metadata.

## Main Flow

1. The model calls `workspace:file` with an action.
2. The tool resolves the file by ID or relative path.
3. The service validates the managed path.
4. The requested action returns bounded content, metadata, hash, image information, generated PDF page previews, or vision analysis.

## Result

Agents can work with imported files while content access remains explicit and auditable.

## Tool Calls

- `workspace:file` actions: `list`, `info`, `hash`, `readText`, `extractPdfText`, `renderPdfPage`, `imageInfo`, `imageBytes`, `analyzeImage`.
- `renderPdfPage` writes a generated PNG preview into the managed workspace and returns its metadata.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.WorkspaceFileTool`
- `de.heckenmann.visualagent.workspace.WorkspaceFileService`
- `de.heckenmann.visualagent.agent.LLMProvider.vision`

## Acceptance Criteria

- Raw file contents are not injected into every model request.
- Hash action supports SHA-256.
- PDF page previews are stored as immutable generated workspace files with size and SHA-256 metadata.
- Unsupported vision/model combinations return clear failures.
