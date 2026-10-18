# Interpolate a conversation prompt

## Goal
Turn a draft into a precise, concise, self-contained prompt by recovering relevant, clearly supported intent from recent conversation, optionally using a dedicated model. Resolve references rather than merely polishing grammar; never invent missing intent.

## Actors
- Desktop user and the configured language-model provider.

## Preconditions
- A nonblank draft and a usable provider/model are available.
- Automatic interpolation is disabled by default.

## Main Flow
1. Optionally choose an interpolation provider/model in conversation settings. An empty assignment inherits the active selection. The main-agent selection stays independent.
2. Click **Refine prompt** to rewrite without sending. Alternatively enable automatic interpolation to rewrite once before the existing send/queue action.
3. Read the latest eight conversation records by default (configurable from five to ten). Include only user/assistant text that fits the model context budget; omit tools, audit records, attachments and assistant reasoning.
4. Send a separate tool-free request without persisting the auxiliary draft or response. Encode the draft and eligible chronological conversation as distinct JSON fields in a single user message; conversation records are reference data, not live turns to continue. Include JSON escaping and structure in token budgeting. Preserve intent, language and explicit constraints. Resolve references within the draft itself first, then consult recent conversation; replace every unambiguous reference even when the original sentence is already understandable. Recover only relevant omitted details clearly established by the user or unambiguously referenced in the draft. The final draft overrides conflicting history; assistant suggestions are not user requirements without evidence of acceptance. Preserve code, identifiers, quoted text, examples, placeholders and requested output formats. Return only the rewritten prompt, without answering or executing it.
5. Show the themed Magic wand and animated sparkles while the request runs. Keep cancellation and editing available.
6. Replace the draft with the result for manual review, or forward it to the existing send/queue action in automatic mode. An unchanged manually refined draft is reused; queue draining does not interpolate again.

## Alternative Flows
- Duplicate clicks are ignored. Editing, clearing, cancellation or composition disposal invalidates pending results; late responses cannot replace newer text.
- Failure, a 30-second timeout, empty output or output above 16,384 characters preserves the draft and shows safe retry guidance. Disable automatic interpolation to send the original.
- Invalid, disabled or unavailable explicit assignments fail rather than silently switching models.
- Oversized drafts are rejected before inference. Older context is omitted rather than truncating the draft.
- If a reference has multiple plausible referents or its context is missing, preserve the unresolved wording rather than guessing. Do not introduce generic roles, reasoning instructions, output formats or unrelated history to make a prompt appear more elaborate.

## Rewrite Examples and Verification
- Given a conversation about the Download button, "It should be disabled during the download" can become "The Download button should be disabled during the download."
- A reference within the same sentence must also be resolved: "Wenn ein Haus fünf Fenster hat, welche Farbe haben diese dann?" becomes "Wenn ein Haus fünf Fenster hat, welche Farbe haben die Fenster dann?" Do not answer the question or invent the windows' color.
- Given two buttons without a unique reference, "Change its color" must not invent which button or which color was intended.
- "Use plain text instead" overrides an earlier JSON requirement. A proposed retry mechanism in an assistant message must not become mandatory without user acceptance.
- Deterministic tests verify the instruction contract, intact chronological context, final-draft precedence in the request, and token budgeting. They do not prove a model follows semantic instructions; evaluate actual rewrites against these examples with the configured provider/model.

## Tool Calls

- None.

## Code Entry Points
- `ConversationPort.interpolate`
- `PromptInterpolationService` and `PromptInterpolationContext`
- `PromptInterpolationController` and `rememberPromptInterpolationController`
- `PromptInterpolationMagic` and the conversation composers
- `promptInterpolationSettings` and `SpringSettingsPort`

## Related Issues
- #218: Optional prompt interpolation.
- #91: Per-capability model assignments; interpolation adds an independent purpose-specific selection without implementing the entire capability project.
- #133: Existing multi-provider catalog and routing.
