# UC-0000072: Show Progress Animation During Requests

## Goal

Show a calm, always-on activity indicator in the workspace header whenever the
agent backend is waiting for an LLM, a tool, or a sub-agent. The indicator
disappears the instant all in-flight work finishes so the user is never shown a
stale spinner.

## Primary Actor

Desktop user running the main agent or operating a sub-agent.

## Preconditions

- The Compose desktop shell is running with `ToolEventBus` and `AgentManager`
  Spring beans available.
- The activity indicator is mounted in the workspace header.

## Main Flow

1. The user sends a message, runs a sub-agent, or triggers a model/details
   refresh in the settings panel.
2. The originating panel marks the request as in-flight in the shared
   `InFlightState` (stream id, sub-agent id, or settings loading flag).
3. The header indicator animates three pulsing dots whose pulse period shortens
   with the number of concurrent in-flight activities.
4. While the LLM streams, the `ToolEventBus` emits `STARTED` and `FINISHED`
   events for every tool call. Each `STARTED` adds the tool id to the pending
   set; each `FINISHED` removes it.
5. When the originating coroutine completes (success or failure), the panel
   clears its in-flight marker.
6. The header indicator disappears the moment `InFlightState.totalActive` is
   zero.

## Result

The user always sees feedback that the system is working without reading status
text. The indicator never occupies layout space when nothing is in flight.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.InFlightState`
- `de.heckenmann.visualagent.ui.compose.InFlightIndicator`
- `de.heckenmann.visualagent.ui.compose.rememberInFlightState`
- `de.heckenmann.visualagent.ui.compose.ComposeWorkspaceHeader`
- `de.heckenmann.visualagent.ui.compose.ComposePanelServices`
- `de.heckenmann.visualagent.ui.compose.ConversationPanel`
- `de.heckenmann.visualagent.ui.compose.SubAgentsPanel`
- `de.heckenmann.visualagent.ui.compose.SettingsPanel`
- `de.heckenmann.visualagent.agent.tools.ToolEventBus`

## Acceptance Criteria

- A pulse indicator is visible in the header from the moment a chat request
  starts until the assistant message finishes streaming.
- The same indicator pulses while a sub-agent job is queued or running and
  disappears when the job ends.
- A settings model/details refresh that exceeds ~200 ms shows the indicator
  for the duration of the call.
- Tool calls that take longer than ~200 ms add an additional dot to the
  indicator while they execute.
- The indicator does not consume layout space when nothing is in flight.
- The pulse period decreases as the number of in-flight activities grows so a
  heavy burst is more visually salient.
