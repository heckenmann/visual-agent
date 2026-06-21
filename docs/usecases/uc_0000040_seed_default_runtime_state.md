# UC-0000040: Seed Default Runtime State

## Goal

Create useful default runtime data when the database does not yet contain required seed records.

## Primary Actor

Application runtime.

## Preconditions

- Database is empty or missing expected records.
- Seeding logic can write to persistence.

## Main Flow

1. The application initializes persistence-backed services.
2. Runtime services inspect stored agents, todos, or preferences.
3. Missing required defaults are created.
4. The UI and agent manager load the resulting persisted state.

## Result

The application remains usable on first run or after the user deletes all optional runtime records.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.agent.AgentManagerLifecycleOps`
- `de.heckenmann.visualagent.orchestration.UxSeedTasks`
- `de.heckenmann.visualagent.knowledge.PersistenceStores`

## Acceptance Criteria

- First-run startup creates enough state for the UI to operate.
- Deleted optional data does not crash startup.
