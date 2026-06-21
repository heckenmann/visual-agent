# UC-0000067: Query Use Case Catalog

## Goal

Let enabled agents answer product-function questions from the maintained Visual Agent use-case catalog.

## Primary Actor

Enabled agent.

## Preconditions

- Use-case documents exist under `docs/usecases/`.
- The build has packaged the use-case documents as runtime resources.
- The `usecases` tool is enabled for the requesting agent.

## Main Flow

1. The user asks about an implemented Visual Agent function.
2. The model calls the `usecases` tool with `list`, `show`, or `search`.
3. The tool loads the packaged use-case index and matching Markdown documents.
4. The model answers from the returned use-case content.

## Result

The agent can answer functionality questions from maintained product documentation without injecting all use cases into every prompt.

## Tool Calls

- `usecases` actions: `list`, `show`, `search`.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.UseCaseTool`
- `de.heckenmann.visualagent.agent.config.AgentToolConfigService`
- `build.gradle.kts`

## Acceptance Criteria

- The use-case catalog is included in the build output.
- The tool can list, show, and search use cases.
- Invalid file names and path traversal attempts are rejected.
- Main-agent orchestration restrictions remain unchanged.
