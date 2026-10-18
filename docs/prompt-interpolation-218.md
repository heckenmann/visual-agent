# Prompt interpolation implementation plan (#218)

## Design and order
1. Extend the protocol with a cancellable interpolation operation and persisted settings: automatic mode (default false), context window (default eight, range five–ten), dedicated provider ID and model ID (both empty means active selection).
2. Implement a Spring-managed Reactor-native server service. Resolve and pin one provider catalog snapshot, validate explicit assignments, fetch recent history reactively, then assemble text context off the UI thread within token limits. Preserve the complete draft, exclude tool/audit records and assistant reasoning, disable tools, and avoid conversation persistence.
3. Bound input/output to 16,384 characters and requests to 30 seconds. Propagate cancellation by completing the `takeUntilOther` notifier; translate the resulting empty completion to cancellation. Adapt Reactor to suspension only at the protocol boundary.
4. Add a per-composition controller for manual and automatic actions. Guard duplicate requests, track draft revisions, invalidate on edits/clear/disposal, preserve drafts on errors, and reuse unchanged manually prepared results before automatic send. Keep the existing queue gateway as the only sending path.
5. Add an accessible wand action and themed Compose Magic animation to both composer placements. Show animation only during inference, with cancellation and safe feedback.
6. Add staged conversation settings for automatic mode, context count and independent provider/model selection. Persist/export/import the settings without changing the main-agent assignment.
7. Test service context and budgets, provider routing, cancellation/timeout/errors, protocol propagation, settings round trips, controller races and both rendered composers. Verify changing pixels with the Compose test clock, run module formatters and the complete repository quality gate, then commit logical server/UI/documentation changes and open a focused PR.

## Model assignment compatibility
Issue #91 is the open capability-specific model assignment project. Issue #133 already supplies the implemented provider catalog and router. Interpolation uses a distinct stored provider/model pair through those existing components; it introduces no parallel catalog or artificial capability type. Explicit unavailable assignments fail instead of falling back. This change does not close #91.

## Library decision
Research of klibs.io, the official Compose animation examples and Spring AI prompt templating supports reusing existing dependencies. Compose animation-core supplies the Magic animation. Existing Spring AI provider adapters and JTokkit supply inference and token budgeting. PromptTemplate performs variable substitution rather than semantic rewriting. No additional loading-animation library or model-routing dependency is needed.

## Research-Based Rewrite Policy
There is no universal perfect prompt. For interpolation, optimize clarity and faithful contextual enrichment rather than prompt length or a mandatory template. The same instruction applies to every provider; no local pronoun replacement, model-name heuristics or extra dependency is needed.

- Use concise, specific language and preserve the user's details, examples and placeholders, following OpenAI's [prompt-generation guide](https://developers.openai.com/api/docs/guides/prompt-generation). Do not copy its general system-prompt template wholesale: interpolation must not invent roles, formats or requirements for the user's task.
- Include only relevant conversation context, following the [prompt-engineering guide](https://developers.openai.com/api/docs/guides/prompt-engineering). Our application policy resolves references only when unambiguous, keeps the latest draft authoritative and does not promote unaccepted assistant suggestions into requirements.
- Keep the rewrite instruction direct and do not inject chain-of-thought demands, consistent with [reasoning best practices](https://developers.openai.com/api/docs/guides/reasoning-best-practices). Preserve uncertainty when evidence is missing instead of manufacturing precision.

The system instruction and context assembly have deterministic contract tests. Semantic rewrite quality still depends on the configured model and requires evaluation of actual outputs; passing contract tests is not evidence of perfect reference resolution.

Interpolation input is a JSON object containing `draft` and `recentConversation`. Prior messages retain role labels inside that data, but are not injected as live assistant/user dialogue. The instruction explicitly requires resolving unambiguous references within the draft before consulting history; an already understandable sentence is not a reason to skip expansion. Token budgeting covers the serialized JSON, including escaping and structure.

### Live Rewrite Check (2026-10-17)
An authenticated Codex app-server check with `gpt-6-luna`, the production rewrite instruction, separate JSON input fields and no dynamic tools returned:

- Draft: "Wenn ein Haus fünf Fenster hat, welche Farbe haben diese dann?" Without history, the result was "Wenn ein Haus fünf Fenster hat, welche Farbe haben die Fenster?"
- Draft: "Er soll während des Downloads deaktiviert sein." With a previous user message naming the Download button in the Files panel, the result was "Der Download-Button im Files-Panel soll während des Downloads deaktiviert sein."

This is live model evidence, not a deterministic guarantee for all models or future responses. The automated component tests separately verify input boundaries, reference-resolution instructions, token budgets. Native UI input could not be exercised in this check because desktop automation exposed only a perspective window preview; the rewrite check used the app-server protocol directly.

## Verification and delivery
Public planning comments are recorded on #218. The use case is `uc_0000149_interpolate_prompt.md`. Publish the tested implementation on `codex/issue-218-prompt-interpolation`, link #91/#133, close #218 through the PR, and do not merge automatically.
