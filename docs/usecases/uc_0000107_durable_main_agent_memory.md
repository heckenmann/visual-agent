# UC-0000107: Maintain durable main-agent memory

## Goal

Let the main agent and the user maintain one concise, durable reference document
without coupling it to the visible conversation history, provider, or model.

## Primary Actors

- User
- Main agent orchestration

## Preconditions

- The application database has initialized the durable `main` memory document.
- The configured memory limit is within the supported safe range.

## Main Flow

1. The user opens **Providers and models** from the conversation header.
2. The dialog loads the current memory content, revision, character count, and
   configured limit together with the other staged conversation settings.
3. The user may edit the full document and optionally adjust its character limit.
4. The dialog validates the draft using Unicode code points and displays the live
   count. It does not silently truncate content.
5. On save, the server persists the settings and replaces the memory only when the
   revision still matches.
6. If another editor changed the document, the dialog reloads the current version
   and reports the conflict instead of overwriting it.
7. Before every main-agent request, the server reads the latest document and adds
   exactly one labelled system section before the conversation history.
8. The main model may call the enabled `memory` tool directly in any turn to show
   or replace the same document with an expected revision. It does not need to
   create, update, or delegate a todo to use memory. Sub-agents do not receive
   this tool by default.

## Result

Durable project facts remain available after a clean conversation, restart, or
provider/model change while conversation history remains separate and auditable.

## Tool Calls

- `memory` — Main agent only and callable directly without a todo. `show`
  returns the content, revision, size, and limit. `edit` replaces the entire
  document with optimistic revision checking.

## Code Entry Points

- `de.heckenmann.visualagent.knowledge.MainAgentLongTermMemoryStore`
- `de.heckenmann.visualagent.agent.conversation.AgentManagerContextOps`
- `de.heckenmann.visualagent.agent.tools.MainAgentMemoryTool`
- `de.heckenmann.visualagent.server.SpringMainAgentMemoryPort`
- `de.heckenmann.visualagent.ui.settings.providerSettingsOverlay`

## Acceptance Criteria

- The memory is one database-backed document scoped to the main agent.
- It survives visible-history deletion, restart, and provider/model changes.
- Content has a persisted revision and timestamp; writes use compare-and-set.
- The document is bounded by a configurable Unicode code-point limit and oversized
  writes fail without truncation.
- Every main-agent request receives exactly one fresh labelled memory section before
  history, including when the document is empty.
- The model can read and edit memory directly through the regular enabled
  `memory` tool without creating or delegating a todo.
- The UI shows a revision-aware editable draft, live character count, and a clear
  conflict outcome.
- Neither diagnostics nor exports log or expose memory content implicitly.
