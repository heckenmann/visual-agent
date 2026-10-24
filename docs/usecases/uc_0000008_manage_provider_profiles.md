# UC-0000008: Manage Provider Profiles

## Goal

Support multiple provider profiles with model lists, credentials, generation defaults, and provider-specific options.

## Primary Actor

Desktop user.

## Preconditions

- The provider catalog store is available.
- Settings > Providers is available.

## Main Flow

1. The user opens the same provider-connection dialog for creating or editing a profile. The dialog uses mode-specific titles and primary actions while keeping the connection fields and validation consistent.
2. The profile editor generates an opaque stable provider ID for new profiles and stores it without exposing it as a user-editable setting. The user manages the display name, adapter type, endpoint, API key, enabled state, and provider options independently from main-agent model selection.
3. The panel validates required fields and stages the profile under Settings > Providers.
4. The user may refresh models with the staged endpoint and credentials; discovered models remain local to the draft until saved.
5. The user explicitly saves the profile draft or resets it to reload the persisted catalog.
   Saving definitions does not submit an active model selection. A stale catalog
   baseline produces a conflict rather than overwriting another client's edits.
   Direct Codex browser sign-in and sign-out are explicit, immediately persisted
   authentication actions, independent of this staged save. Completed sign-in is retained
   when the dialog closes or the application restarts. See UC-0000152.
6. The catalog permits multiple profiles using the same adapter, so separate endpoints and credentials can be configured independently.
7. Before removing or disabling the active profile, the user selects another provider/model
   in Conversation. Merely browsing provider details never switches the active model.
8. Model resolution uses provider, model, agent, and variant settings in deterministic order.
9. The optional onboarding wizard receives only safe profile information; existing credentials are represented as configured or not configured and are write-only when changed.

## Result

Different agents and sessions can use different providers and model parameters.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.agent.provider.ProviderProfile`
- `de.heckenmann.visualagent.agent.provider.ProviderCatalogService`
- `de.heckenmann.visualagent.ui.settings.providers.ProviderManagementPanel`
- `de.heckenmann.visualagent.ui.settings.ComposeProtocolSettingsSupport`
- `de.heckenmann.visualagent.ui.components.ActionIconButton`

## Acceptance Criteria

- Profiles survive restart.
- Profile edits do not affect the active provider until the global overlay is explicitly saved.
- Model refresh uses the staged provider profile and does not persist a catalog update until the overlay is saved.
- New profiles receive a generated stable provider ID; existing profile IDs never change during editing.
- Name and Base URL are required unless the selected adapter owns a fixed endpoint.
- Removing/disabling the active provider from this UI requires an explicit Conversation selection change first.
- Multiple profiles may use the same adapter type.
- Changing the main-agent model does not overwrite provider endpoints or credentials.
- At least one provider profile remains enabled.
- Option merging is deterministic.
- `structuredOutput.native=true` explicitly declares JSON-schema response support for an endpoint/model. Set it only after verifying support; `false` forces prompt-only output. Unknown OpenAI-compatible and Ollama endpoints remain prompt-only. Codex schema requests use its documented native protocol by default. All review results are validated server-side (UC-0000057).
- Raw API keys are not included in tool output, model context, exported config, or logs.
- Re-running onboarding preserves an existing credential unless the user explicitly replaces or clears it.

- Concurrent catalog mutations use database compare-and-set and rebase on the latest persisted state, with at most 16 attempts before an actionable retry error.
- Discovery/capability updates preserve concurrent provider edits and active selection. Explicit profile or complete-catalog replacements intentionally replace their declared scope; the last successful replacement wins within that scope.
- Runtime selection and refresh hints are published only after a successful commit; failed or rolled-back writes emit no change.
