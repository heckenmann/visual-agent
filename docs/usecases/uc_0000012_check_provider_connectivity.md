# UC-0000012: Check Provider Connectivity

## Goal

Show whether the selected model provider is reachable and usable before the user starts a task.

## Primary Actor

Desktop user.

## Preconditions

- A provider profile is selected.

## Main Flow

1. The UI or backend requests a connectivity check.
2. The configured provider delegates to the active provider adapter.
3. The adapter performs a lightweight provider-specific check.
4. The result is returned to UI status surfaces.

## Result

The user can distinguish configuration/network problems from model-response problems.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.agent.LLMProvider.checkConnection`
- `de.heckenmann.visualagent.agent.ConfiguredLLMProvider`
- `de.heckenmann.visualagent.ui.panels.ChatRuntimeStatusController`

## Acceptance Criteria

- Connectivity status is not duplicated in conflicting UI locations.
- Failures do not expose credentials.
