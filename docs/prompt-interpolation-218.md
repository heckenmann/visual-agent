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

## Verification and delivery
Public planning comments are recorded on #218. The use case is `uc_0000149_interpolate_prompt.md`. Publish the tested implementation on `codex/issue-218-prompt-interpolation`, link #91/#133, close #218 through the PR, and do not merge automatically.
