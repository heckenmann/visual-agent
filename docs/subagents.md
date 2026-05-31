# SubAgents

## Current State

Sub-agent management is persisted and integrated into runtime orchestration.

Implemented:

- DB-backed sub-agent loading at startup
- default-agent seeding if DB has no agents
- create/update/delete from UI through `AgentManager`
- status/task updates reflected in panel cards
- tool-policy loading through `AgentToolConfigService`

## Runtime Model

Main components:

- `AgentManager`: orchestration, assignment, lifecycle
- `SubAgent`: execution unit with role/config
- `SubAgentsPanel`: UI controls and status rendering

Agent statuses:

- `IDLE`
- `BUSY`

## Task Assignment

`AgentManager` assigns pending todos to idle agents and can run autonomous processing loops.  
Main-agent prompts include todo summaries and execution policy so model planning aligns with persisted state.

## Persistence

State is persisted via `KnowledgeDb` (`sub_agents`, related config tables).  
UI and runtime reload from DB after restart.

## Remaining Work

- Additional modularization of `AgentManager` (file currently exceeds LOC target)
- Expanded integration tests for concurrent multi-agent execution paths
