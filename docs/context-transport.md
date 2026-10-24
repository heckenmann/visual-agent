# Context Transport Decision

Status: accepted for stateless context assembly. Research checked October 24, 2026.
Tracking: issue #484; UC-0000106, UC-0000094, UC-0000152.

## Ownership and scope

The server database owns the complete conversation audit history. The desktop sends
the new user submission and entry identities, not a second copy of that history.
Server-requested client data remains separately authorized and request-scoped.
The server builds a fresh, bounded model context for each request. No provider-side
conversation state, response chain, persistent Codex thread, or second memory store
is introduced by this decision.

## Why preserve native replay

A Responses assistant turn can contain ordered native message parts, including
opaque encrypted reasoning, rather than only visible text. Persisting those parts
is insufficient if the dedicated main-agent context loader drops them on reload.
The loader must carry `providerReplay` through history projection and model-aware
budgeting to the matching Direct provider. The existing Spring AI message-part codec
restores the parts; application code must not parse or reconstruct encrypted reasoning.

Replay is scoped to the originating provider profile and model. Foreign replay is
discarded before budgeting and restoration. Editing visible text invalidates stored
replay through the database update path. Budgeted visible text must never be replaced
with a longer original answer during restoration. Existing historical execution
summaries remain compact reference data; active tool rounds retain their native
call/output correlation through the Spring AI tool execution boundary.

## Stable policy and changing reference data

Requests are assembled in this order:

1. Stable application policy and the exact provider-native tool guard/schemas.
2. Selected previous dialogue and historical execution references, chronologically.
3. Current durable-memory and runtime snapshots, immediately before the latest user turn.
4. The latest user turn, including any subsequent in-progress execution context.

Current snapshots are explicitly labelled as application reference data, not prior
assistant speech or instructions. They retain the low-priority `SUMMARY_SOURCE`
classification. A newer snapshot supersedes historical state without rewriting the
persisted conversation. Interrupted-request presence belongs to the runtime snapshot;
the recovery rule itself is stable policy. Raw interrupted-message text is not added
to that snapshot.

The existing budget policy is unchanged: preserve the latest user request, prioritize
up to ten recent final answers with their user messages, reserve minimal tool help,
and include full schemas and lower-priority references when they fit. Input, tool
schemas, native replay and output reservation are counted for every request. Selection
priority does not reorder the retained conversation. Missing current snapshots must
never be represented as complete inventories; the model can query enabled tools.

## Cache-friendly does not mean stateful

Prefix caching requires matching content, not a client-side session label. Keep stable
instructions first and changing snapshots late. Sort selected tool callbacks by their
provider function names so registry iteration order cannot invalidate an otherwise
matching prefix. Never widen an allowlist or advertise unsupported tools merely to
improve caching. Configuration changes, history edits and budget reductions can
legitimately change the prefix; correctness takes priority over cache reuse.

The full selected context is still transmitted on each request. Reusing HTTP/TLS
connections does not make the conversation stateful. Even where a provider offers
response chaining, prior context still consumes its model context window and may
be billed as input. No latency or cost saving is guaranteed by this change.

When a `MeterRegistry` is configured, completed Responses rounds record provider-reported input and cached-input tokens
in `visualagent.responses.input.tokens` and
`visualagent.responses.cached.input.tokens`. The ratio of counter increases indicates
observed cache reuse for rounds with both values available. Unknown or invalid usage
is not counted as a cache miss. Record once after successful round aggregation, not
for each cumulative streaming chunk. No model, profile, credential, prompt, history,
or tool-payload labels are attached.

## Provider and framework boundaries

| Route | Decision and reason |
| --- | --- |
| Ollama `/api/chat` | Resend selected role-bearing messages; `keep_alive` retains a model, not an application conversation. |
| OpenAI-compatible Chat Completions | Resend selected messages. Compatibility with Chat Completions does not establish support for Responses state. |
| Direct Responses with API key | Use the existing stateless Spring AI model. OpenAI supports stateful alternatives, but our resolved framework does not implement them. |
| Direct Responses with ChatGPT OAuth over HTTP | Resend the required input with `store:false` and streaming; the documented route excludes `previous_response_id` and `conversation`. |
| Codex CLI/app-server | Intentionally retain one short-lived process and ephemeral thread per request, inject bounded completed history, and close the process on termination. Do not resume or reuse a thread. |

The resolved integration is Spring AI 2.1.0-M1. Its published
`OpenAiResponsesChatModel` and `ResponsesRequestBuilder` send the complete Prompt,
map system policy to `instructions`, and force `store:false`. Reuse those supported
APIs and the existing `ProviderReplayCodec`; do not bypass them with raw extra-body
state parameters, a second inference client, or a homemade encrypted-reasoning format.

The earlier library assessment in [Direct Codex Provider Design](codex-direct-provider.md)
and the Klibs/GitHub candidates were reconsidered for context management. Another
SDK or chat-memory library cannot fix an application loader that omits its own stored
field, and a second memory store would duplicate database ownership and budgeting.
No new dependency is needed; existing Spring AI message parts, token budgeting and
Micrometer usage APIs supply the required mechanisms.

## Verification

- Reproduce missing replay and unstable prefix/schema ordering before changing production code.
- Save an assistant response, close/reopen an isolated database, and capture the next main-agent request.
- Capture real SDK HTTP requests on loopback for both Direct authentication modes;
  verify roles, encrypted replay, stable instructions, repeated full history and `store:false`.
- Preserve existing profile/model isolation, edited-text invalidation, output-budget,
  native tool-correlation and Codex ephemeral-thread regression tests.
- Check changed runtime data leaves stable policy/history intact and context assembly
  does not mutate the stored audit history.
- Test reported cache counters deterministically; no sleeps, account access or timing-based success criteria.

## Sources

- [OpenAI conversation state](https://developers.openai.com/api/docs/guides/conversation-state)
- [OpenAI Responses message/item mapping](https://developers.openai.com/api/docs/guides/migrate-to-responses)
- [OpenAI prompt caching](https://developers.openai.com/api/docs/guides/prompt-caching)
- [ChatGPT OAuth preview limits](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations)
- [Spring AI Responses statelessness and lossless replay](https://docs.spring.io/spring-ai/reference/2.1/api/chat/openai-responses-chat.html)
- [Spring AI 2.1.0-M1 source](https://github.com/spring-projects/spring-ai/tree/v2.1.0-M1/models/spring-ai-openai/src/main/java/org/springframework/ai/openai/responses)
- [Spring AI chat memory versus complete history](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)
- [Ollama chat request contract](https://docs.ollama.com/api/chat)
- [Codex app-server thread lifecycle](https://learn.chatgpt.com/docs/app-server)
- [Klibs OpenAI Kotlin candidate](https://klibs.io/project/Aallam/openai-kotlin)
