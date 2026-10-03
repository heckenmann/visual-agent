# Workspace Resize and Conversation Reset Investigation

Issues: #424 and #425. Status: implementation and verification in progress.

## Environment

- Measurement date: 2026-10-03.
- macOS 27.0.1, Apple M4, ARM64, Temurin JDK 25.0.3.
- Display: 5120 x 2880 physical pixels, 2560 x 1440 logical pixels,
  2x scale, 120 Hz. The frame interval is approximately 8.33 ms.
- Disposable test data only; existing user databases are not modified.

## Confirmed Causes

### Workspace resize and reorder

The workspace called database-backed layout persistence from a UI-dispatched
effect, and synchronously read the provider catalog in composition. Nested
`BoxWithConstraints` invalidates its composition as native constraints change,
so header provider/model getters and the warning model getter could repeat
database reads while the window was being resized.

Layout publication now runs sequentially on `Dispatchers.IO`. Geometry-only
changes do not rewrite the persisted panel configuration. Header informational
badges have been removed at the user's request; capability warnings remain and
load catalog metadata off-thread only after a selection/catalog change.

Panel height also used an interpolated animation whose target changed with
every native height update. Height now follows the measured viewport directly;
intentional panel-width and placement animations are retained.

The catalog regression test passes with the corrected loader and fails at an
explicit UI-thread assertion with the original loader. It checks UI progress
while I/O is blocked, one set of reads across ten recompositions, and refresh
after a source revision. The height test proves the new height is applied in
one Compose test frame without waiting for interpolation. The layout test
checks off-thread I/O and final ordered writes while persistence is blocked.
An additional disposal test removes the workspace while its first write is
blocked and verifies that its latest observed panel order is flushed after
that write. The desktop now shares that coordinator with the workspace and
awaits its final write before requesting application exit. A desktop lifecycle
test verifies flush/shutdown ordering, off-caller-thread protocol work, and
duplicate-close protection. Native restoration after restart remains to be
verified; the unit test does not establish that end-to-end result.

The file-backed H2 `WorkspaceLayoutRestartTest` closes and reopens the server
persistence context and verifies panel order, hidden state, preferred widths,
main-window size, and position through the real layout port. It passes without
elapsed-time assertions. Native desktop restoration still requires a smoke test.

Spring and connection cleanup also runs on `Dispatchers.IO`. The process entry
point waits for cleanup only after the Compose application has returned, with
Compose's immediate process exit disabled. The lifecycle test blocks cleanup
with an explicit completion signal and verifies that scheduling returns to the
caller and cleanup finishes before process completion. No success assertion
depends on elapsed time.

These are functional regression proofs, not measured native frame timings.
Continuous mouse-driven resize and midpoint-crossing drag traces remain open;
the automated native drag attempts did not change the panel order. The first
partial fix did not resolve the reporter's severe mouse-resize lag, which led
to finding the additional catalog queries. Do not claim complete performance
acceptance based on window maximization or final-size assertions alone.

### Conversation reset

The previous operation returned only after generating a provider welcome
message, so the cleared history was not exposed until provider work finished.
The server now acknowledges persisted deletion before welcome generation; the
UI refreshes the cleared history and reports that the welcome is being prepared.
Deletion/cancellation run off-thread, duplicate confirmed resets are guarded,
and failures do not falsely report deletion. Database request invalidation
prevents late request results from recreating cleared messages.

Conversation writes share one persistence path. Reset and message persistence
also serialize updates to the server-owned presentation projection, preventing
a committed message from being appended to that projection after reset. This
coordination runs in server/background work, not Compose composition.
Interrupted-request recovery also registers a request identity before provider
work. Its delayed connection-failure response is rejected after reset. An
event-driven regression test fails when that response loses its request
identity; it waits for provider subscription and job completion, not elapsed
time.

The late-producer audit found another confirmed gap: canvas captures used the
short persistence overload without the owning request ID. A regression test
failed with an expected `capture-request` but actual `null`. Sub-agent jobs now
register before scheduling and forward the same identity through native provider
tool context and canvas persistence. Todo streaming and non-streaming fallbacks
also retain the identity. Automatic terminal reviews register a fresh identity
before queuing, guard both notification and response writes, and finish their
activity even when reset invalidates the result. Event-driven H2 reset tests have
passed for delayed captures and terminal review responses. The provider identity
test covers chat, non-streaming todos, streaming todos, and streaming fallback.

## Native Smoke-Test Limitation

An earlier isolated desktop process remained alive. Repeated thread dumps showed
a worker inside native library loading through Netty QUIC and Reactor Netty
HTTP initialization. The UI event thread is idle, and accessibility observation
times out. Screenshot capture does work and shows the Conversation/Todos
workspace with informational header badges removed. A screenshot alone does
not establish responsive rendering or completion of the blocked worker. This is
not evidence of a database operation on the UI thread, nor proof that native
resize is smooth. Do not restart solely because observation timed out; confirm
process state and investigate the native startup independently.

After a clean exit and a fresh build, the local server selection reached the
main Conversation/Todos workspace. Accessibility controls respond, including
the native window zoom action. The screen-control service currently captures
the window as a small perspective preview, so mouse coordinates and continuous
resize measurements are not reliable. Native performance acceptance remains
open rather than being inferred from the passing automated tests.

## Quality Gate

The latest `./gradlew ktlintCheck check test --no-daemon` run completed
successfully, including coverage, file/package size, architecture boundaries,
and use-case documentation checks (3 min 14 s, including the additional canvas,
sub-agent identity, and terminal-review reset regressions). This run was executed
through the VS Code MCP task connection. The preceding focused reset,
sub-agent, and orchestration test run also passed (30 s).
A preceding run failed in two TLS tests during a disk-space shortage; one
reported `No space left on device`. Both affected classes passed on targeted
retry, followed by the successful complete gate. No TLS test was removed or
changed to accommodate the environmental failure.
The layout disposal regression is included
in the passing UI test suite. No native frame-performance claim follows from
this result.

## Manual-Session Sampling

A JFR profile of the isolated desktop process was started while the user
manually tested the window. The process exited cleanly after 23 seconds and
the recording was saved outside the repository. It contains native
`CPlatformWindow.deliverMoveResizeEvent` samples and Compose measurement,
composition, text, and scroll work on the UI event thread. No sampled UI stack
matched the R2DBC stores, provider catalog, or workspace persistence classes;
sampling cannot prove that no short I/O operation occurred.

The scroll-to-bottom helper appears in the resize-period UI samples. Source
review shows `ConversationResizeScrollEffect` invokes it for each viewport
change; the helper requested positioning and forced scrolling again across two
additional frames. Resize now uses one `requestScrollToItem` for the next list
measurement, without immediate `scrollToItem` calls. A regression test checks
one request per observed size change and no positioning while browsing history;
it fails against the previous implementation. This proves removal of redundant
scroll requests, not a measured frame-time improvement. The user's perceived
smoothness result and before/after frame timings are still required. The API's
scheduled-measurement semantics are documented in the official
[LazyListState reference](https://developer.android.com/reference/kotlin/androidx/compose/foundation/lazy/LazyListState).

## Bulk Reset Measurements

`ConversationBulkResetTest` uses isolated file-backed H2 databases. Each assistant
turn has four parent-linked tool results of approximately 3.9 KB each. It checks
that history is empty and that a separate session, a preference, and a skill are
unchanged. Fixture creation and assertions are outside the measured deletion.

| Records | Existing SQL bulk delete | Transactional reset with invalidation |
| --- | --- | --- |
| 50 | 105.54 ms | 98.02 ms |
| 10,000 | 360.08 ms | 354.39 ms |

These are single diagnostic samples from one run, not statistically significant
speedup claims. They show that deletion itself is not a multi-second provider
wait and that the lifecycle protection did not introduce a large observed
deletion penalty. Test success never depends on elapsed time. Reproduce using:

```bash
./gradlew :application:test --tests '*ConversationBulkResetTest' --rerun-tasks --no-daemon
```

The measured local reset stage is below one second for these fixtures. A local
one-second acknowledgement budget is an investigation target, not a test
assertion or guarantee for all storage devices. Welcome generation is reported
separately and has provider-dependent latency. Remote transport/UI-refresh
latency and end-to-end large-history desktop timing still require measurement.

Remote timing has an existing product dependency, not merely a missing test
endpoint: `ComposeStartupHost` rejects `DesktopServerEndpoint.RemoteTls` with
"Remote application transport is not available in this desktop build" before
creating an application connection. This branch must not claim a successful
remote clear smoke test. The early-reset acknowledgement is verified through
the currently implemented local `SpringConversationPort`; a future remote
transport must preserve that acknowledgement independently of welcome generation.

### Local Protocol Integration Measurement

`ConversationResetIntegrationTest` exercises the real `AgentManager`,
`SpringConversationPort`, Flyway-migrated file-backed H2, and the history-read
path with 50 and 10,000 assistant/tool rows. The provider connection publisher
remains blocked until after acknowledgement and the empty-history assertions.
The operation is still incomplete at that point. Releasing the provider with
an unavailable result then persists exactly one fallback welcome, returning a
warning rather than undoing the reset.

| Records | Reset acknowledgement | Empty history read and integrity checks complete |
| --- | --- | --- |
| 50 | 168.01 ms | 340.94 ms |
| 10,000 | 329.88 ms | 368.85 ms |

These single samples include protocol dispatch and persisted reset, but not
native rendering. The second measurement also includes the direct database
integrity assertion after `port.latest()`. No assertion depends on duration;
the functional regression uses publisher subscription and explicit release.
The focused run passed in 27 seconds. Reproduce with
`./gradlew :application:test --tests '*ConversationResetIntegrationTest' --no-daemon`.

## Remaining Verification

### Workspace Composition Recreation Regression

Following the report that hidden panels reappear on restart, a Compose
regression reproduced a persistence lifecycle defect: disposing the workspace
effect permanently finished the coordinator owned by the still-live main
window. Recreated workspace effects then silently skipped subsequent writes,
including visibility, order, and preferred widths. Disposal now performs an
ordered non-cancellable flush only; final application shutdown still finishes
the coordinator and prevents writes after server cleanup.

The recreation regression failed before this change. Native verification of
the reporter's exact sequence remains necessary; the earlier positive visual
feedback must not be treated as proof of restart correctness.

### Background Producer Scope

Source review distinguishes request-owned assistant/tool output from independent
application events. `WorkspaceDownloadService.publishStatus()` emits download
lifecycle events identified by download ID. `WorkspaceDownloadNotificationService`
then appends a workspace notification, including completion of a managed file.
Clearing conversation history does not cancel the download or delete its file;
a subsequent download event is therefore not a replayed assistant response.
Do not discard these events through a global message filter. Likewise,
`AgentManagerLifecycleOps.persistTodoChange()` records explicit todo mutations,
distinct from the worker's request-owned start and completion messages.

The request-lifecycle regression tests cover late assistant, tool, recovery,
canvas capture, todo review, and worker writes. This distinction does not prove
that every future background producer is safe: new request-owned producers must
carry the registered request identity and participate in reset invalidation.

### Reset Cleanup Regression

`AutonomousTodoResetCleanupTest` exposed an additional reset edge case: rejecting
the cancellation notification prevented release of the worker. The notification
now releases the agent in `finally`, preserving the original cancellation rather
than treating the rejected write as successful. The regression failed before the
fix and passed afterward together with `AutonomousCoordinatorLifecycleTest`.
Assertions depend on callback execution, not elapsed time.

The producer audit subsequently found that the coordinator's initial todo
notification lacked the worker request identity. Requests are now registered
before claiming a todo, and the initial notification and worker share that
identity. `AutonomousTodoRequestIdentityTest` failed before this correction.
A second event-driven regression showed that a request-local invalidation
terminated the persistent pickup coroutine. Pickup now checks its actual
coroutine cancellation state before propagating cancellation, allowing an
invalidated operation to finish without disabling future pickup. The second
regression also failed before the fix, and the full orchestration test selection
passed afterward in 23 seconds. The complete quality gate must be rerun after
these additional production changes.

After this change, `./gradlew ktlintCheck check test --no-daemon` completed
successfully in 3 minutes 8 seconds (32 executed tasks, 127 up-to-date tasks),
including coverage verification. This is a quality-gate result, not native UI
performance evidence.

### Outstanding Checks

The user confirmed that continuous resizing and panel displacement no longer
stutter, but reported a brief visible delay before the application background
catches up with the resized native window. This remaining symptom matches the
upstream Metal live-resize problem; it is not evidence of a remaining database
call on the UI thread.

JetBrains documents an opt-in synchronous Metal live-resize fix in
[Compose 1.13.0-alpha01](https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.13.0-alpha01),
implemented by [Skiko PR #1226](https://github.com/JetBrains/skiko/pull/1226).
The project currently uses stable Compose 1.12.1 and Skiko 0.150.1. Inspection
of the resolved Skiko source JAR found no `metalSynchronousLiveResize` property
or corresponding implementation. Therefore no ineffective property was added,
no renderer was forcibly changed, and no toolkit workaround was introduced.
The user explicitly chose to retain stable Compose 1.12.1 rather than test the
alpha release. Keep the residual background delay documented until a compatible
stable release provides the upstream fix. The symptom's exact frame delay and a
corrected native runtime still require verification.

The full quality gate after the local reset integration test and unused scroll
helper removal passed in 3 minutes 26 seconds (35 executed, 124 up-to-date tasks).

The latest full quality gate, including the todo request-identity and persistent
pickup cancellation regressions, passed in 3 minutes 16 seconds (33 executed,
126 up-to-date tasks). Coverage verification also passed.

The rebuilt native application was launched through `:desktop:run` with an
isolated database under `/tmp`. Selecting the local server reached setup and,
after skipping optional provider setup, the Conversation and Todos workspace.
Native window zoom and closing/reopening the Conversation panel completed
without an observed error. This is a functional smoke check only. A coordinate
drag toggled the panel rather than producing a verified midpoint reorder, so it
does not establish reorder performance or replace the outstanding frame trace.

The native shutdown/restart check also passed for visibility persistence: after
closing the Todos panel, the application Close button terminated the process
cleanly. A second Gradle launch using the same isolated database restored the
Conversation panel and kept Todos hidden, without repeating onboarding. This
verifies that visibility survives process restart; it does not verify reordered
panel positions or a continuous resize frame budget.

The user subsequently reported that the current result looks good. This is
positive visual feedback, not an explicit confirmation of a populated-history
reset or reordered-panel restart. Keep those specific checks separate.

An additional event-driven `ConversationClearActionTest` verifies that
cancellation releases the reset mutex and restores the controls without
refreshing history, clearing visible todos, or reporting cancellation as a
database failure. The complete test class passed after module formatting.
The subsequent full `./gradlew ktlintCheck check test --no-daemon` gate passed
in 33 seconds (29 executed, 130 up-to-date tasks), including coverage
verification. Unchanged module tests were up-to-date; this was not a forced
rerun of every test suite.

- Record before/after native frame intervals for continuous resize and panel
  displacement with lightweight and populated panels; compare against the
  tested display's frame interval and inspect missed frames rather than averages.
- Verify final reorder persistence and panel state after restart.
- Measure reset acknowledgement and visible refresh separately from welcome
  generation in representative local and remote configurations.
- Review late-write protection for every background producer and complete the
  full quality gate after the final changes.
