# Interpolate a conversation prompt

## Goal
Refine a draft with recent conversation context before sending it, optionally using a dedicated model.

## Actors
- Desktop user and the configured language-model provider.

## Preconditions
- A nonblank draft and a usable provider/model are available.
- Automatic interpolation is disabled by default.

## Main Flow
1. Optionally choose an interpolation provider/model in conversation settings. An empty assignment inherits the active selection. The main-agent selection stays independent.
2. Click **Refine prompt** to rewrite without sending. Alternatively enable automatic interpolation to rewrite once before the existing send/queue action.
3. Read the latest eight conversation records by default (configurable from five to ten). Include only user/assistant text that fits the model context budget; omit tools, audit records, attachments and assistant reasoning.
4. Send a separate tool-free request without persisting the auxiliary draft or response. Preserve intent, language and explicit constraints.
5. Show the themed Magic wand and animated sparkles while the request runs. Keep cancellation and editing available.
6. Replace the draft with the result for manual review, or forward it to the existing send/queue action in automatic mode. An unchanged manually refined draft is reused; queue draining does not interpolate again.

## Alternative Flows
- Duplicate clicks are ignored. Editing, clearing, cancellation or composition disposal invalidates pending results; late responses cannot replace newer text.
- Failure, a 30-second timeout, empty output or output above 16,384 characters preserves the draft and shows safe retry guidance. Disable automatic interpolation to send the original.
- Invalid, disabled or unavailable explicit assignments fail rather than silently switching models.
- Oversized drafts are rejected before inference. Older context is omitted rather than truncating the draft.

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
