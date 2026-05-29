# Changelog

All notable changes to this project are documented in this file.

## Unreleased

### Added
- SubAgents persistence: new `sub_agents` table in KnowledgeDb and CRUD helpers.
- AgentConfig model with templates: researcher, coder, documenter, reviewer, tester.
- AgentManager improvements: load/save agents from DB, create/update/delete API, assignment and autonomous processing hooks.
- SubAgents UI (Phase 2): SubAgent card view (agent-card.fxml), SubAgentCardView, AgentDetailsDialog, AgentLogsDialog.
- UI flows: Add/Edit/Delete agents, Run agent (assigns todo), Logs viewer, live status updates via AgentManager callback.
- Docs: docs/subagents.md, README.md and AGENTS.md updated to reflect Phase 2 work.

### Changed
- SubAgentsPanel refactored to use card views and expose callbacks for backend wiring.
- MainWindow wired SubAgentsPanel → AgentManager (create/update/delete/run + live updates).
- Tests: retained passing state; build/test verified after changes.

### Notes
- Build and tests verified: `./gradlew build` completed successfully.
- Remaining work: Todo persistence wiring, drag-and-drop task assignment, UI polish (icons/toasts), structured event payloads, logs export and filtering.

---

Generated on 2026-05-29.
