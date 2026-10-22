# Direct Codex Provider Design

Tracking issue: #484. Status: implementation in progress; no completed provider is claimed.

## Framework Decision

Use Spring AI 2.1.0-M1 with its Spring Boot 4.2.0-M2 baseline. Both are prereleases;
the upgrade requires the full regression and package gates before publication.
Use the plain Ollama model module rather than its starter: Visual Agent owns its
profile-aware authenticated TLS clients and must not also auto-configure a second
`ollamaApi` bean from application properties.
The published `OpenAiResponsesChatModel` implements `ChatModel`, streams a Reactor
`Flux`, sends stateless requests, and does not execute tools itself. Reuse it rather
than implementing another Responses protocol client. Its transport uses the OpenAI
Java SDK through Spring AI; preserve the application's trusted-certificate customizer.
Use Spring WebClient and Spring Security OAuth2 Client for authorization operations,
not a second inference transport. Confirm protocol-specific registration and callback
requirements against the actual framework APIs before adding custom code.
Native package builds use the same Java 24 toolchain as tests, including their
runtime and packaging tools. Reactor Netty 1.4.0-M2 directly references QUIC during
TLS initialization: removing its HTTP/3 dependency causes a startup linkage error,
even when HTTP/3 is not configured. The native desktop smoke exposed a
library-loading stall; no HTTP/3 exclusion is retained as a workaround. OAuth,
discovery, and Ollama transports now use Spring's supported JDK HTTP connectors
with the same managed JSSE trust context. WebClient remains reactive and streaming;
the inference Responses model retains its Spring AI transport. A repeated native
smoke reached onboarding, verified both explicit authentication modes without a
real login, and closed the isolated instance. TLS handshakes have a separate local
contract test; no real account access or inference entitlement is claimed.

Spring AI, Spring Security, and Reactor use Apache-2.0 licensing. The Kotlin
`aallam/openai-kotlin` alternative adds another coroutine-oriented transport and has
an open Responses streaming request (#465); it is not selected. `Heapy/kotlm` is a
standalone proxy rather than an embedded provider and targets a different backend;
it is not selected. No proxy or private Codex endpoint is permitted.

### Library Assessment

Verified against repository metadata and published releases on October 21, 2026.
Catalog results are discovery aids, not proof that an OAuth flow is authorized.

| Candidate | License / maintenance evidence | Protocol and integration decision |
| --- | --- | --- |
| Spring AI 2.1.0-M1 + Spring Security | Apache-2.0; milestone published September 25, 2026 | Selected. JVM/Reactor integration already exists; native ordered Responses parts, image mapping, and incremental aggregation are reused. Spring Security supplies code exchange, refresh, PKCE, and JWT verification. Dynamic registration, profile isolation, and application tool dispatch remain application responsibilities. |
| `aallam/openai-kotlin` | MIT; 4.1.0 published February 7, 2026; repository not archived | Responses is documented, but streaming issue #465 remains open. Ktor/coroutine transports would add another integration stack; no verified complete replacement for the required public OAuth/streaming contract. Not selected. |
| `Heapy/kotlm` | Apache-2.0; last push October 3, 2026; no latest GitHub release endpoint | Standalone proxy, not an embedded library. Its README specifies a private backend, buffered streaming, and rejection of tools/non-text input. It fails endpoint, incremental-streaming, image, and tool requirements irrespective of maintenance activity. Not selected. |
| `tddworks/openai-kotlin` | Apache-2.0; 0.2.3 published December 18, 2025; repository not archived | Klibs describes Ktor-based, Flow-oriented multi-provider chat. A complete documented public Responses/OAuth integration was not established; another provider gateway is unnecessary here. Not selected. |
| `AndroidPoet/openai-kmp` | MIT; last push July 3, 2026; repository not archived | Additional KMP SDK candidate found through GitHub. No verified advantage over the existing Spring Responses integration or complete dynamic-registration contract was established. Not added; no claim of a full transitive/security audit. |

The selected stack does not require a platform-specific Codex binary or proxy.
JDK HTTP connectors use JSSE and avoid adding a native inference transport. Existing
Reactor Netty remains for the loopback callback server, configured to use portable
Java NIO event loops rather than native transports; existing framework native
dependencies and cross-platform packages still require inventory/CI verification.
No dependency from the rejected candidates is added, so their Ktor/native platform
engines do not enter the application's runtime. MIT and Apache-2.0 compatibility
does not remove notice obligations; regenerate the project's dependency inventory
for the final resolved framework versions before publication.

Sources: [Spring release](https://github.com/spring-projects/spring-ai/releases/tag/v2.1.0-M1),
[Aallam release](https://github.com/aallam/openai-kotlin/releases/tag/4.1.0),
[streaming gap](https://github.com/aallam/openai-kotlin/issues/465),
[kotlm README](https://github.com/Heapy/kotlm),
[tddworks release](https://github.com/tddworks/openai-kotlin/releases/tag/0.2.3),
[additional SDK](https://github.com/AndroidPoet/openai-kmp),
[Klibs Aallam entry](https://klibs.io/project/Aallam/openai-kotlin),
[Klibs tddworks entry](https://klibs.io/project/tddworks/openai-kotlin).

## Integration Boundaries

- Add a separate `CODEX_DIRECT` adapter through `ProfiledProviderAdapter` and the
  existing `ConfiguredLLMProvider` routing. Keep all existing adapter identities.
- Keep authentication mode explicit per profile: ChatGPT OAuth or Platform API key.
  Never infer billing from the presence of a credential or fall back across modes.
- Reuse existing request budgeting, callback permissions, tool correlation, and
  persisted assistant-turn ownership. Preserve ordered Responses items and opaque
  reasoning payloads across tool continuations and persisted-history replay.
- Both reactive chat and streaming must use the streaming transport for ChatGPT
  plan requests. Aggregate the stream for non-streaming callers, not vice versa.
- Do not execute network requests or credential reads in Compose composition.

## Authentication and Security Gate

The existing provider catalog stores API keys as plaintext by product decision;
it is not a protected OAuth credential store. OAuth tokens must not be added to
catalog options, exported settings, protocol DTOs, logs, or model context.
The implementation stores OAuth records under the server data root at
`credentials/codex-direct/`. Files require owner-only POSIX permissions or an
owner-only Windows ACL; unsupported or permissive storage is rejected. Tokens are
not encrypted at rest, so the operating-system account and filesystem remain the
security boundary. Profile ownership is checked on every record access. Atomic
replacement, generation checks, and session file locks protect refresh/sign-out
ordering across server processes.

The documented public-client flow uses `dynamic_agent_client` only for initial
registration, a persistent per-host ID, a one-attempt state/nonce/PKCE verifier,
and an HTTP callback on `127.0.0.1`. Exchange the code with the issued client ID.
Validate the signed ID token, issuer, audience, expiry, nonce, and returning account
identity before activation. Granted scopes, not successful identity login alone,
authorize plan inference. Replace rotated refresh/access credentials atomically;
serialize refresh per credential record and prevent sign-out races from restoring
revoked local credentials. Define the callback/token ownership for remote servers
explicitly; do not send tokens through ordinary conversation messages.

Local signed-JWT/JWKS contract tests cover a valid issuer identity and rejection of
an untrusted signature, wrong issuer, audience, nonce, authorized party, and expired
identity token. These tests use synthetic credentials and do not establish real
account eligibility or production authorization. Real-account smoke testing remains
optional and requires explicit user authorization.

## Conversation Replay

The final assistant turn stores its native Responses parts in a separate nullable
history field introduced by migration V7. Both synchronous and streaming application
paths retain this state. It is not added to presentation protocol DTOs. Replay is
scoped to the original provider profile and model; switching either must not send or
budget another session's opaque reasoning. Native continuation rounds retain their
ordered tool-call/result parts within the existing tool execution loop. Historic
audit tool rows are not reconstructed into guessed native call pairs.
Editing persisted answer text clears its replay payload atomically in the database.
Updating only metadata, or retaining identical text, does not discard valid replay.

## Safe Inference Failures

The direct provider classifies typed SDK, HTTP, and OAuth failures at its boundary.
Authentication, denied access, unavailable models, quota exhaustion, retryable rate
limits, invalid request options, timeouts, invalid streams, and transport failures
receive fixed actionable messages. Raw response bodies and credential-bearing causes
are not retained in these user-facing exceptions. Explicit cancellation remains
cancellation rather than being converted into a provider error. An error never
changes the profile's authentication mode or selects another billing route.

## Route Matrix

| Concern | ChatGPT OAuth | Platform API key | Existing Codex CLI |
| --- | --- | --- | --- |
| Inference | Public Responses endpoint | Public Responses endpoint | Existing app-server |
| Billing | Eligible ChatGPT plan | Platform API usage | Existing CLI behavior |
| Installed CLI | Not required | Not required | Required |
| Streaming | Required | Supported | Existing adapter |
| Storage | `store:false` | Spring AI stateless mode | Existing adapter |
| Options | Validate documented plan subset | Validate API/model support | CLI catalog/options |
| Models | Credential-scoped discovery; access not guaranteed | Project-scoped discovery; access not guaranteed | CLI catalog |
| Tools | Enabled local function/custom subset only | Supported explicit subset | Existing policy |

Do not enable hosted tools automatically. Do not equate a listed model with proven
inference entitlement. Preserve the last catalog when refresh fails. Unknown
capabilities remain unknown and must not be inferred from model-name substrings.

OAuth catalog parsing uses the documented `models` array, ordered `slug` identifiers,
`display_name`, and `visibility == "list"`. Platform API-key parsing uses `data[].id`.
Neither schema falls back to the other authentication route. A completed transport
alone is not completed inference: the tool loop requires the native `completed`
status before dispatching tools or finalizing replay, and reports incomplete or
interrupted streams as failures. The status is captured before Spring aggregation,
which does not preserve every arbitrary response-metadata field.

The public OAuth catalog contract documents identifiers, display names, visibility,
and ordering, not a complete capability or reasoning-level schema. If an OAuth model
entry additionally supplies a positive integer `context_window`, retain that explicit
value through catalog persistence and context budgeting. Missing, null, non-numeric,
or non-positive values remain unknown; never substitute a CLI catalog value or infer
limits from model names. Unreported capabilities remain unknown. User-configured
model options remain available. The configured conversation context remains an
independent upper bound, including when the provider does not report a model limit.

## Request Options and Resource Ownership

Both routes explicitly map reasoning effort/summary, verbosity, service tier, parallel
tool calls, tool choice, and strict schemas. Canonical camel-case names and documented
snake-case counterparts are accepted where applicable; conflicting aliases, malformed
booleans, unknown options, and required tool choice without enabled tools fail locally.
The API-key route additionally maps sampling and output limits. OAuth rejects sampling
and treats `maxTokens` only as an internal budget, never `max_output_tokens` on the wire.
Unsupported hosted/stateful options are rejected rather than silently ignored.

`ResponsesOutputLimit` adds a separate resource guard of 16,777,216 cumulative serialized
native-part characters per response round. It includes tool arguments and encrypted
reasoning, cancels excess output, and reports a non-retryable error; it never truncates
an answer and presents it as complete. This is a memory-safety boundary, not a substitute
for the existing model/context-window token budgeting. Each round/subscription has its
own counter; ordinary chunks are still forwarded immediately.

Sign-in cleanup uses Spring's asynchronous `SmartLifecycle` callback. Listeners close
before credential cleanup, unclaimed credentials are invalidated before lifecycle
completion, and a failure for one attempt does not prevent cleanup of the others.
Atomic claim/discard ownership ensures that a competing waiter cannot invalidate an
already accepted session. No blocking shutdown wait is added to the UI or provider.

## OAuth Tool Declarations

The October 21 official preview contract requires function/custom tools to use
namespaces or `additional_tools` input items. Spring AI 2.1.0-M1's automatic callback
conversion emits flat top-level functions. `DirectResponsesTransport` therefore
uses the documented developer-role `additional_tools` item through Spring AI's
provider-native `UnknownPart` extension. It derives schemas exclusively from the
request's allowed callbacks. No hosted tool search or custom HTTP/SSE runtime is added.

The adaptation is transport-local: the original prompt retains its callbacks for
budgeting, registry permissions, correlation, and continuation. Each outbound request
contains one declaration at the same initial position; continuation history is not
mutated or accumulated with repeated declarations. API-key requests are unchanged.
This avoids a namespace reconstruction workaround: the framework currently drops
the SDK function-call namespace field, whereas `additional_tools` preserves existing
leaf function names and their normal structured call/result continuation.

Tests inspect the real Spring AI-generated SDK request and execute an OAuth-adapted
two-round stream through the existing registry. They verify native declaration role,
schema/name preservation, absent flat top-level tools, correlated results, unchanged
permissions, and no synchronous inference or duplicate declarations.

## Native File Input

`workspace_file` action `analyzeFile` explicitly sends a managed workspace file to
the calling agent's configured provider/model through `LLMProvider.fileReactive`.
The request uses Spring AI `Media`, carries no enabled tools of its own, and returns
only the model's analysis. The managed path and current file size are validated
before reading; inline input is limited to 8 MiB. Filesystem and catalog work run
on bounded-elastic workers. Client-owned paths are not implicitly read or uploaded.

The selected direct provider and authentication route remain authoritative. Other
adapters fail explicitly unless they implement native file input; there is no
fallback to another provider, image conversion, text extraction, or billing route.
Audio/video, remote attachment URLs, empty inputs, and host paths in filenames are
rejected. Actual model acceptance remains provider-authoritative, not inferred from
the model name or a model-list entry.

Spring AI M1's file mapping appends the MIME subtype to filenames, turning
`notes.txt` into `notes.txt.plain`. A failing contract test demonstrated this.
`DirectFileTransport` uses official SDK input-file/message types through Spring AI's
native-part extension to preserve the original filename and bytes. This conversion
occurs only at the transport boundary, after budgeting; the execution prompt retains
its user role, attachments, and history. Text, PDF, and CSV request serialization is
tested for both authentication modes. Images continue to use native image inputs.
No independent file upload, HTTP, SSE, or model runtime is introduced.

## Delivery and Verification

1. Upgrade and verify existing providers before adding the adapter.
2. Add protected profile-scoped credential lifecycle and deterministic OAuth tests.
3. Add shared Responses prompt/model mapping and auth-specific option validation.
4. Integrate discovery, ordered history, native tools, images/files, usage, and errors.
5. Add explicit settings/onboarding controls through protocol ports and use cases.
6. Test incremental delivery before completion, cancellation, malformed/failing SSE,
   tool continuation, refresh rotation, account isolation, and regression behavior.
7. Run formatter, full quality gates, package verification, and authorized smoke tests.
   No timing-based success assertions or billable inference on provider selection.

Local HTTP/SSE integration tests use the real Spring AI model and SDK transports against
a synthetic loopback server. Controlled events prove incremental delivery before the
terminal event, token usage, malformed SSE classification, clean EOF rejection, midstream
plan errors, and cancellation closing the actual HTTP connection. A two-request tool test
also proves developer-role declarations, registry dispatch, native encrypted-reasoning
replay, and call/result correlation across the real transport. These are not real OpenAI
account or entitlement tests; those were not authorized.

## Sources

- https://spring.io/blog/2026/09/25/spring-ai-2-1-0-M1-available-now/
- https://docs.spring.io/spring-ai/reference/2.1-SNAPSHOT/api/chat/openai-responses-chat.html
- https://docs.spring.io/spring-security/reference/reactive/oauth2/client/authorization-grants.html
- https://docs.spring.io/spring-framework/reference/7.1/web/webflux-webclient/client-builder.html
- https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html
- https://developers.openai.com/siwc/token-sharing-open-source/sign-in
- https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference
- https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations
- https://developers.openai.com/api/docs/guides/tools-tool-search#add-tools-at-a-specific-point-in-the-input
- https://developers.openai.com/api/docs/guides/pdf-files
- https://github.com/aallam/openai-kotlin/issues/465
- https://github.com/Heapy/kotlm
