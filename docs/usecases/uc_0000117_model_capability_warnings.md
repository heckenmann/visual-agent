# UC-0000117: Show active-model capability warnings

## Goal

Make limitations of the active provider model visible before the user submits a
request that requires more context capacity or tool calling.

## Primary Actor

Visual Agent user.

## Preconditions

- A provider and model selection is active.
- The provider catalog contains the active provider and model selection.

## Main Flow

1. The workspace reads the active model metadata from the provider catalog and
   refreshes its detail metadata through the provider boundary.
2. It calculates the effective context limit from the configured limit and the
   current provider-reported model limit, using the lower positive value.
3. When the effective limit is below 4096 tokens, it shows a context warning in
   the workspace header.
4. When the provider supplies a complete capability declaration that omits
   `tools`, it shows a tool-calling warning in the workspace header.
5. Both warning badges use the same fade-and-width transition when they appear or
   disappear after a provider, model, or settings change.
6. Hovering a warning explains the limitation and its practical effect.

## Result

Users can select a suitable model before starting a request that needs sustained
conversation context or agent tool calls. Unknown provider metadata is not
presented as an unsupported capability.

## Tool Calls

- None. The warnings use metadata obtained through the provider boundary; they
  do not expose a model tool-call.

## Code Entry Points

- `de.heckenmann.visualagent.ui.workspace.modelCapabilityWarnings`
- `de.heckenmann.visualagent.ui.workspace.ModelCapabilityWarningBadges`
- `de.heckenmann.visualagent.ui.workspace.ComposeWorkspaceHeader`

## Acceptance Criteria

- A model with an effective context limit below 4096 tokens shows a context badge.
- A model with a complete capability declaration that lacks `tools` shows a
  tooling badge.
- Models with unknown or partial capability metadata do not show a tooling
  warning.
- All model-capability badges share the same entrance and exit animation.
- The badges are rendered in the top-right header region and provide a tooltip.
