# SubAgents (Phase 2)

This document describes the SubAgents persistence and UI features added in Phase 2.

## Overview

- SubAgents now persist to the knowledge DB table `sub_agents` (id, name, role, status, currentTask, parentAgentId, config, created_at, updated_at).
- AgentConfig model added with templates: `researcher`, `coder`, `documenter`, `reviewer`, `tester`.
- AgentManager loads agents from DB at startup and will seed default agents if the DB is empty.

## UI (SubAgentsPanel)

- New SubAgent card view with quick actions: Configure, Run, Logs, Delete.
- Add Agent button opens an Agent Details dialog (name, role, template).
- Edit persists changes via AgentManager.updateAgent (MainWindow wires UI → backend).
- Delete shows a confirmation dialog and calls AgentManager.deleteAgent when confirmed.
- Logs opens a readonly dialog showing the agent's chatHistory collected during autonomous runs.

## Live status updates

- AgentManager notifies listeners via a callback (AgentManager.setAgentCallback) when agents are created, change status (BUSY/IDLE), or emit messages. MainWindow subscribes and updates the SubAgentsPanel cards.

## Autonomous processing

- UX tasks can be seeded via `agentManager.seedUxTodos()`.
- Start autonomous processing with `agentManager.startAutonomousProcessing(seed = true)`; the manager will assign pending todos to idle agents and run them in the background.

## Files to review

- agent/AgentManager.kt — load/save agents, assignment, notifications
- agent/SubAgent.kt — domain model + performTodo
- agent/AgentConfig.kt — templates and tuning params
- ui/panels/SubAgentsPanel.kt — card list, Add/Edit/Delete wiring
- ui/panels/SubAgentCardView.kt — card controller and callbacks
- ui/panels/AgentDetailsDialog.kt — create/edit dialog
- ui/panels/AgentLogsDialog.kt — logs viewer

## How to run and test

1. Build & test: `./gradlew build` (tests pass)
2. Run the app: `./gradlew run` (starts JavaFX UI)
3. To enable agents to work autonomously: call `agentManager.startAutonomousProcessing()` from the MainWindow (or via a debug hook).

## Notes & next steps

- Consider converting notification payloads into structured events (rather than raw strings) for more robust UI handling.
- Add UI integration tests for create/delete/logs flows.
- Polish visuals: icons, toasts, and agent log export.
