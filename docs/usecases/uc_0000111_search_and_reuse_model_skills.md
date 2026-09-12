# UC-0000111: Search and reuse model skills

## Goal

Let the main agent and explicitly enabled sub-agents store stable, reusable
Markdown instructions and results in a searchable database-backed catalog.

## Primary Actors

- Main agent
- User
- Optional sub-agent with the `skills` tool enabled

## Preconditions

- The application database has applied the searchable-skills migration.
- The requesting agent has the `skills` tool enabled, or the user opens the Skills panel.

## Main Flow

1. The model searches the catalog before expensive or repetitive work when a reusable solution may exist.
2. Search matches indexed titles and Markdown content and returns bounded snippets, revision numbers, and read statistics.
3. The model reads a selected skill to receive the complete Markdown document. A successful model read increments the read count atomically; search and user-panel reads do not.
4. After substantial successful work, the model creates a self-contained skill, or updates an existing skill with its current revision.
5. The server rejects invalid identifiers, oversized content, duplicate content, stale revisions, and protected data without overwriting another skill.
6. The user opens the Skills panel to search, read rendered Markdown, create, edit, or delete skills. Concurrent edits are reported as conflicts and require reloading.
7. Deleting a skill removes its searchable content and records a minimal audit tombstone without retaining the body.

## Result

Reusable project knowledge survives conversation cleanup, restart, and provider changes without being injected into every prompt. The model opts into skills through explicit tool calls and can react to actionable JSON errors.

## Tool Calls

- `skills` — Main agent by default; sub-agents only when their configured tool set includes it. Actions are `search`, `get`, `create`, `update`, and `delete`.

## Code Entry Points

- `de.heckenmann.visualagent.knowledge.SkillStore`
- `de.heckenmann.visualagent.knowledge.JpaSkillStore`
- `de.heckenmann.visualagent.agent.tools.SkillsTool`
- `de.heckenmann.visualagent.server.SpringSkillPort`
- `de.heckenmann.visualagent.ui.skills.ComposeSkillsPanel`

## Acceptance Criteria

- Skill titles are bounded to 200 Unicode code points and Markdown bodies to 120,000 Unicode code points.
- SQLite FTS5 indexes title and body content, with bounded ranked results and a safe fallback for invalid FTS syntax.
- Every model read increments `readCount` and `lastReadAt` only after a complete, non-cancelled read.
- Creates and updates use SHA-256 duplicate detection and optimistic revision checks.
- Deletes are transactional, remove the FTS row, and retain only a body-free audit tombstone.
- The main prompt explicitly explains when to search, read, create, update, and avoid storing sensitive or transient data.
- Skill requests are handled directly through the database-backed `skills` tool. `SKILL.md` and other durable skill documents are never created in the workspace, by the main agent or by sub-agents; if the tool is unavailable, the request is reported as unavailable.
- The user panel shows catalog metadata, rendered Markdown, read statistics, editing, conflict handling, and deletion.
