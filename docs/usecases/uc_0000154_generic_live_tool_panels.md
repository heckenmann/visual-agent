# UC-0000154: Show structured live tool state in Conversation

## Goal
Show honest intermediate execution state in the existing tool row without creating a new
message for each update or sending presentation refreshes back to the model.

## Actors
- User watching a conversation.
- Server tool runtime and tools implementing the optional live capability.

## Preconditions
- The tool is enabled and authorized through the existing registry.
- The Conversation subscribes to the application activity port.
- The desktop runtime provides the real coroutine main dispatcher; test-only dispatcher
  replacement is not sufficient for activity callbacks in a launched application.

## Flow
1. The registry discovers `LiveVisualAgentTool` by interface, not by tool ID.
2. The runtime creates an invocation-local reporter with a generated execution UUID.
3. The tool publishes semantic status, measured progress, a deadline, bounded log text,
   table values, or managed artifact references.
4. The first snapshot is delivered immediately. Subsequent snapshots are coalesced to at
   most ten publications per second per execution; this does not delay the tool result.
5. The Conversation updates the matching tool row without querying the database.
   Status, deadline and progress are replaceable fields, not log messages. A countdown or
   download refresh updates its existing value and never appends a log entry. Actual log
   producers supply the ordered retained log entries in each complete snapshot.
6. Completion, cancellation, or timeout seals the latest snapshot. Late producer updates
   are ignored. The final snapshot is stored with the existing tool-call metadata.
7. Restoring history restores the final snapshot. Clearing history invalidates live frames.
   Reopening the panel requests active execution snapshots once after subscribing; it does not
   poll history or wait for a second producer update. A completion racing this initial request
   takes precedence over its older running snapshot.
   Updates received while the initial fetch is pending are buffered by execution identity
   and revision, then merged when its start arrives. Disposal, reset and replacement
   subscriptions invalidate their own pending fetches without discarding a newer subscription.
8. A running live card offers an icon-only Cancel action. The server resolves the generated
   execution identity and validates its request identity before signalling cancellation.
   Finished or unknown execution identities cannot be used to invoke another tool.
9. Compact status text is limited to two visual lines, and the log preview to six visual
   lines, including soft wrapping, selected from the end so the newest output remains visible.
   An icon-only Expand live details action reveals the
   complete retained status/log snapshot in a bounded, selectable scroll area. It does
   not recover data previously discarded by runtime limits. Collapse restores the preview.
10. Text updates use a short word-level flip; numeric tokens change digit by digit while
    unchanged surrounding spans remain static. Initial content appears without a flip.
    Inserted/removed digits reserve their slot and enter/exit with the same transition.
    Each transition has only one replaceable pending target, so
    high-rate updates cannot create an animation backlog. Terminal values take priority.
    Reduced/disabled motion removes the rotation. Expanded logs remain unanimated.

## Current Producers And Limits
- Sleep reports a deadline; remaining time is computed from that deadline, not decremented.
- Terminal drains combined output while the process runs, retaining bounded presentation text.
- JavaScript forwards its existing bounded console diagnostics during sandbox execution.
- Workspace downloads forward measured byte counts from the existing transfer control and retain
  a stable registered artifact reference. Speed and ETA are derived from measured bytes/time.
  Pausing or resuming through the existing Files controls updates the same live phase; an
  already in-flight progress callback cannot overwrite a paused state. Cancelled transfers
  retain a cancellation outcome rather than being presented as generic failures.
- Tools without the capability retain their existing compact static presentation.
- Snapshot text and collections are bounded; common credential assignments, URL credentials,
  and terminal escape sequences are removed from live presentation snapshots. This is not a
  guarantee that arbitrary user-generated output cannot contain sensitive information.
- Placeholder browser/search tools must not display fabricated progress.
- Image artifact previews are opened explicitly through the existing bounded server image resolver,
  using registered file IDs so later renames do not break the reference. No arbitrary client URL is loaded.
- Existing Todo and managed-download streams remain authoritative for their own execution state.

## Design Rationale
The implementation reuses Reactor scheduling and existing Material 3 primitives. Klibs/GitHub
research found KoalaPlot as a maintained MIT Compose chart option, but adding a chart dependency
is unnecessary for progress, countdown, logs and tables. Measured numeric series use an
accessible, bounded numeric table rather than introducing a chart engine. Dedicated line charts
are not implemented. Download pause/resume continues to use the existing Files controls, not
duplicate control ownership in Conversation. Generic cancellation and explicit managed-image
preview are supported directly in live cards.

For text animation, Klibs/GitHub research also found RollingNumbers, a character/number-oriented
GPL-3.0 library. It is not added: the required multiline word alignment, retained-log expansion
and request-scoped update coalescing are covered by existing Compose Foundation inline text
content and graphics-layer APIs. Native paragraph layout owns wrapping, shaping and ellipsis;
replacement slots use the incoming word's target width and remain mounted after the flip,
so completing an animation does not reflow unchanged surrounding text. Diff work
runs on Dispatchers.Default and animation-frame values are read in graphics layers, not used
to repeatedly remeasure the paragraph. Excessively large token sequences fall back to static
native text rather than introducing unbounded diff/layout work.

- https://klibs.io/project/delacrixmorgan/rollingnumbers
- https://developer.android.com/reference/kotlin/androidx/compose/foundation/text/InlineTextContent
- https://developer.android.com/develop/ui/compose/graphics/draw/modifiers

## Tool Calls

- `sleep` retains its existing function name and arguments.
- `terminal` retains its existing function name and arguments.
- `javascript_execute` retains its existing function name and arguments.
- `workspace_download` retains its existing function name and arguments.
- Intermediate presentation state is emitted by the runtime, not returned as additional model turns.
- No separate model-callable live-panel management tool is added.

## Code Entry Points

- `modules/model-core/src/main/kotlin/de/heckenmann/visualagent/model/ToolLiveState.kt`
- `modules/tools/src/main/kotlin/de/heckenmann/visualagent/agent/tools/LiveVisualAgentTool.kt`
- `modules/tools/src/main/kotlin/de/heckenmann/visualagent/agent/tools/ToolLiveSession.kt`
- `modules/tools/src/main/kotlin/de/heckenmann/visualagent/agent/tools/ReactiveToolExecution.kt`
- `application/src/main/kotlin/de/heckenmann/visualagent/server/SpringActivityPort.kt`
- `modules/ui/src/main/kotlin/de/heckenmann/visualagent/ui/conversation/live/ToolLivePanel.kt`

## Related Issues
- #495; follow-up to #147.
