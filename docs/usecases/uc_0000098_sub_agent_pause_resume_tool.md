# UC-0000098: Control Sub-Agent Execution Through the Main Agent Tool

## Goal

Expose the same authoritative sub-agent pause/resume state to the main agent through a model-callable tool.

## Primary Actor

Main orchestration agent.

## Preconditions

- The main agent has its standard management tools enabled.

## Main Flow

1. The main agent calls `subagents:execution` with `status`, `pause`, or `resume` and an optional `agentId`.
2. Without `agentId`, the action changes the global gate; with `agentId`, it changes only that worker's individual gate.
3. The tool returns global state, individual state, effective state, pause reason, and paused agent IDs.
4. The UI and autonomous scheduler observe the same event and persisted state.

## Result

The main agent can safely control worker execution without gaining a pause control over itself or cancelling worker progress.

## Tool Calls

- `subagents:execution` with `{ "action": "status|pause|resume", "agentId": "optional" }`.

## Code Entry Points

- `de.heckenmann.visualagent.agent.tools.SubAgentsExecutionTool`
- `de.heckenmann.visualagent.agent.tools.AgentToolPortAdapter`
- `de.heckenmann.visualagent.agent.SubAgentJobScheduler`
- `de.heckenmann.visualagent.orchestration.AutonomousCoordinator`

## Acceptance Criteria

- The tool is available to the main agent and not included in default sub-agent tool sets.
- Global resume preserves individual pauses.
- Unknown agent IDs return a clear tool failure without changing state.
- State is restored from preferences before autonomous scheduling starts.
