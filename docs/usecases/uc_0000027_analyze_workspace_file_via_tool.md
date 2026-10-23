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

- `workspace_file` actions: `list`, `info`, `hash`, `readText`, `extractPdfText`, `renderPdfPage`, `imageInfo`, `imageBytes`, `analyzeImage`, `analyzeFile`.
- `renderPdfPage` writes a generated PNG preview into the managed workspace and returns its metadata.
- `analyzeFile`: `{"action":"analyzeFile","id":"<managed-file-id>","prompt":"Summarize this file"}`.
  Sends up to 8 MiB of native file content to the calling agent's selected provider/model.
  The tool returns the analysis, not base64 input bytes. Native file support is currently
  implemented by Codex (direct), independently for OAuth and API-key mode; unsupported
  adapters fail explicitly without fallback. Audio/video are not supported.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.WorkspaceFileTool`
- `de.heckenmann.visualagent.workspace.WorkspaceFileService`
- `de.heckenmann.visualagent.agent.LLMProvider.vision`
- `de.heckenmann.visualagent.agent.LLMProvider.fileReactive`

## Acceptance Criteria

- Raw file contents are not injected into every model request.
- Hash action supports SHA-256.
- PDF page previews are stored as immutable generated workspace files with size and SHA-256 metadata.
- Unsupported vision/model combinations return clear failures.
- Codex CLI vision requests use the app-server image input protocol and preserve the detected image MIME type.
- Image bytes and provider credentials are not written to logs or conversation history.
- Native file analysis is explicit, bounded, managed-path validated, and preserves the
  calling sub-agent's selected provider/model. Missing agents and path escapes fail closed.
- Before native file/image inference, the direct provider counts the attachment with
  the selected model and credential route through the official input-token API. Its
  token cost is reserved across prompt fitting and continuation rounds, independently
  of the 8-MiB transport limit. Mandatory user text is never truncated to make it fit.
- Counting transfers the explicitly selected attachment to the provider; a fitting file
  is transferred again for inference. Unsupported, failed, or invalid counts prevent
  inference with an explicit error. OAuth counting entitlement is not assumed from login.
- API-key output respects the calculated remaining budget even without an explicit
  output setting; OAuth requests continue to omit unsupported output-limit fields.
