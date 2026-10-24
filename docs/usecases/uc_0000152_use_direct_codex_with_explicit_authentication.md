# UC-0000152: Use Direct Codex with Explicit Authentication

## Goal

Use OpenAI's documented Responses endpoint without installing Codex CLI, choosing
explicitly between eligible ChatGPT plan authorization and Platform API-key billing.

## Actors

- User configuring a provider profile.
- Visual Agent server owning authentication, credentials, model discovery, and inference.
- Desktop client presenting safe settings and opening the system browser on request.

## Preconditions

- The user has a reachable OpenAI API endpoint and a suitable authorized account.
- ChatGPT plan eligibility and workspace permissions are not assumed from identity login.
- Existing Ollama, OpenAI-compatible, and Codex CLI profiles remain independently available.

## Main Flow

1. Create or edit a profile and explicitly select Codex (direct).
2. Select OpenAI API key (separate Platform billing) or ChatGPT OAuth (eligible plan usage).
3. For API-key mode, enter or update the masked key. No ChatGPT login or CLI is required.
4. For OAuth, select Continue with ChatGPT. The server creates a bounded, one-use
   state/nonce/PKCE transaction and loopback listener before the browser opens.
5. Complete consent. The server validates the callback and signed account identity,
   granted plan permission, and registration identity before storing credentials.
6. The UI receives only an opaque credential reference after the server persists the
   selected registration. No additional settings save is required to retain a completed
   login across dialog closure or restart. Editing or switching providers alone does not run inference.
   Reopening settings with a retained reference shows configured ChatGPT sign-in,
   an optional Sign in to ChatGPT again action, and sign-out rather than asking for
   initial sign-in again. Switching accounts requires explicit sign-out first;
   reauthorization retains the verified account identity. This indicates stored configuration, not verified live
   entitlement or token validity; merely displaying settings performs no login or API call.
7. Refresh models or explicitly test the selected model. Listing a model does not prove
   inference entitlement. A failed refresh must not clear the persisted catalog.
   An explicitly returned positive numeric `context_window` is retained. Missing or
   invalid model limits remain unknown; the configured conversation context still
   bounds the request, and omitted context or tool schemas still trigger a warning.
   Refreshing an existing model replaces its reported context/output limits without
   losing model options. An omitted limit preserves an already known value.
8. Chat and tool continuations use streaming Responses requests under existing context,
   tool permission, cancellation, and conversation ownership rules.
   Server-local Micrometer timers measure subscription-to-first-visible-text and
   total request duration, plus credential, transport, preparation, and attachment-count stages.
   Each native model stream and each registry tool round also has a separate timer.
   Empty provider events do not count as visible text. Completion, failure, and
   cancellation remain distinguishable without recording content or credentials;
   no network metrics exporter is enabled by this feature.
9. Cancel an outstanding sign-in using the cancel action, or sign out the selected account.
   Sign-out clears local credentials and the persisted provider reference immediately,
   and reports whether remote revocation was confirmed.

## Alternative and Error Flows

- Denied consent, invalid state, reused/expired transactions, invalid identity tokens,
  missing plan permission, or a changed returning identity cannot activate the account.
- A sign-in deadline invalidates unclaimed credentials even if its browser callback
  already completed. Expired or cancelled attempts cannot later be claimed by the UI.
- Server shutdown closes pending listeners and invalidates unclaimed registrations
  through Spring's asynchronous lifecycle. Only one waiter can claim a registration;
  rejected duplicate claims never sign out the successful claimant.
- Refresh uses only the selected registration and replaces rotated credentials atomically.
  Concurrent callers serialize session renewal; stale refresh cannot undo local sign-out.
- A completed login atomically updates only the provider's authentication route and reference,
  retaining concurrently edited model settings and the active conversation selection.
  For a new provider, successful authorization also retains its profile. A deleted provider
  or concurrently changed authentication route cannot be silently overwritten by a late callback.
  Stale settings drafts cannot erase a newer login or restore a signed-out registration.
- Converting an existing profile to direct OAuth also persists the validated direct
  endpoint, while preserving unrelated concurrent edits. Concurrent changes to the
  previous adapter's endpoint reject the pending conversion instead of overwriting it.
- Refresh notifications are best-effort after a successful catalog write. A notification
  failure is logged without credentials and cannot invalidate a committed registration
  or report an already-persisted sign-out as a failed database write.
- API-key failures never switch to ChatGPT OAuth, and OAuth failures never incur API billing.
- The callback is HTTP on 127.0.0.1 at `/auth/callback`. A browser running on another host
  requires an explicitly arranged loopback forwarding path; do not expose callback listeners
  publicly or change them to an undocumented redirect host.
- Unsupported route-specific options fail explicitly. ChatGPT requests omit unsupported
  output-limit and sampling fields and use stateless streaming.
- Unknown/misspelled options and conflicting aliases fail locally instead of being
  silently ignored. A separate native-output memory guard cancels excessive responses
  with an explicit failure; it never replaces the selected model's context budget.
- Native file/image requests first count the attachment with the selected model and
  credentials using the official SDK input-token operation. That cost remains reserved
  across all provider rounds. Counting is an explicit attachment transfer, not inference;
  a fitting attachment is subsequently sent again for generation. If counting is unavailable
  or exceeds the configured/model context limit, no inference is sent and no billing
  route changes. OAuth counting entitlement is not assumed from successful sign-in.

## Security

- API keys retain the existing product storage policy. OAuth tokens use separate protected,
  owner-only server storage and are not copied into provider options or exported settings.
- Authentication URLs can contain identity hints and must not be logged or included in
  model context. Access and refresh tokens never appear in URLs, UI results, or chat history.
- The provider accepts only the documented OpenAI API origin; credentials are not forwarded
  to arbitrary profile endpoints or redirects. Existing generic providers handle custom APIs.
- All network and credential filesystem operations run outside the UI thread.

## Tool Calls

- `model_selection` lists configured providers/models and refreshes the selected catalog;
  existing selection permissions and profile isolation apply.
- Account sign-in, consent, credential entry, and sign-out are explicit user actions, not
  model-callable authentication operations. They never expose tokens to an agent.
- Inference uses the request-scoped native tool schemas and existing registry callbacks.
- ChatGPT OAuth declares those functions in a developer-role `additional_tools` input
  item. Names, schemas, permissions, call IDs, and results remain authoritative in the
  existing registry; API-key mode retains normal Responses function declarations.
- `workspace_file` action `analyzeFile` sends a managed file of up to 8 MiB as native
  input to the calling agent's selected direct profile/model. It never uploads unrelated
  files or silently changes providers, authentication methods, or billing routes.

## Code Entry Points

- `modules/provider-codex-direct/`: OAuth protocol, protected credentials, callback lifecycle,
  direct model discovery, and `CodexDirectProvider`.
- `ReactiveResponsesToolLoop`: native streaming and correlated tool continuation.
- `DirectResponsesTransport`: route-specific native tool declarations without changing
  the execution loop's permission-bearing prompt.
- `DirectProviderAuthorization` / `ProviderPort`: server-owned authentication boundary.
- `SpringProviderPort` and `SpringSettingsPort`: safe results and persisted profile mapping.
- `DirectAuthorizationControls`, `DirectProviderAuthenticationFields`, and profile editor.

## Verification

- Deterministic callback correlation, denied permission, malformed identity token, and expiry tests.
- Protected storage, profile isolation, generation checks, and concurrent refresh tests.
- Incremental text delivery, streaming-only tool continuation, and cancellation tests.
- Real SDK/Spring AI loopback SSE tests cover usage, early deltas, terminal-event
  requirements, malformed data, midstream errors, HTTP cancellation, and native tool
  continuation with encrypted reasoning. They use no real account or API credentials.
- Actual framework request serialization and OAuth-adapted registry continuation tests.
- Native PDF/text/CSV filename and byte preservation in both routes, managed-path and
  payload limits, deferred execution, and sub-agent/provider selection tests.
- Settings mapping tests preserve explicit authentication mode and credential reference.
- Catalog regression tests retain reported context limits and reject invalid values
  without guessing limits, capabilities, or output budgets.
- Real-account testing requires explicit user authorization and disclosure of potential API cost.

## Implementation Status

Issue #484 remains in progress. Native packaging and an isolated onboarding smoke
have passed with the JDK HTTP connectors; both explicit authentication modes were
inspected without real sign-in. Local TLS tests enforce managed roots and hostname
verification. History persistence, budget scoping, answer editing, and non-streaming
normalization/retry paths have targeted replay regressions. Final acceptance still
requires the complete route/capability audit, a final
full quality/package rerun after the latest lifecycle changes, and PR CI. Real
account/API-key inference was not authorized and is not claimed as verified.
Headless Compose-v2 button tests cover simulated authorization success, cancellation,
local sign-out with unconfirmed remote revocation, and hidden OAuth actions in API-key
mode. Their protocol and browser collaborators are synthetic; they never log in.
