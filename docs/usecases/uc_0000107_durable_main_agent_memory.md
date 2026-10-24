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

1. The user opens **Settings > Agent**.
2. The memory editor loads the current content, revision, character count and
   configured limit asynchronously, independently of other settings drafts.
3. The user may edit the full document and optionally adjust its character limit.
4. The dialog validates the draft using Unicode code points and displays the live
   count. It does not silently truncate content.
5. The memory's own Save action replaces the document only when its revision still
   matches. Changing the memory size limit is a separate Agent-settings save.
   After that save, the editor immediately uses the saved limit for its character
   counter and Save-button validation without discarding the document draft.
6. If another editor changed the document, the editor reports a conflict and retains
   the user's draft. Reload discards that draft explicitly and reads the current document.
7. Before every main-agent request, the server reads the latest document and adds
   exactly one labelled, optional reference snapshot before the latest user turn.
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
- `de.heckenmann.visualagent.ui.settings.agent.MainAgentMemoryEditor`

## Acceptance Criteria

- The memory is one database-backed document scoped to the main agent.
- It survives visible-history deletion, restart, and provider/model changes.
- Content has a persisted revision and timestamp; writes use compare-and-set.
- The document is bounded by a configurable Unicode code-point limit and oversized
  writes fail without truncation.
- Each assembled main-agent request includes exactly one fresh labelled memory
  reference before the latest user turn; provider budgeting can omit this optional data.
- The model can read and edit memory directly through the regular enabled
  `memory` tool without creating or delegating a todo.
- The UI shows a revision-aware editable draft, live character count, and a clear
  conflict outcome.
- Neither diagnostics nor exports log or expose memory content implicitly.
