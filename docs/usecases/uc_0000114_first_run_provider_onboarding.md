# UC-0000114: Complete first-run provider onboarding

## Goal

Explain Visual Agent's core concepts, configure an LLM provider/model, and optionally
let the configured main model create a first sub-agent after the desktop client connects.

## Primary Actors

- User
- Connected Visual Agent server

## Preconditions

- A Visual Agent server protocol connection is ready.
- The server-owned onboarding state is `NOT_STARTED`.

## Main Flow

1. The desktop reads onboarding state from the connected server.
2. One welcome page briefly explains the Main Agent, specialized Sub-agents, and durable Todos.
3. If the server already has enabled provider profiles, the user selects one. If the catalog is empty, the wizard offers **Add provider** and keeps the new profile in a staged, non-persistent draft while the server discovers structured model metadata.
4. The user may refresh model discovery without persisting the draft, filters by model name or ID if needed, then selects a model and reviews the safe provider summary.
5. The server validates the exact provider/model selection.
6. After successful validation the server saves and activates the provider selection.
7. The wizard displays all existing persisted sub-agents from this server and a prefilled example request for a Researcher.
8. The user may edit the request and ask the configured Main Agent to create a sub-agent through its normal `agent:list` and `agent:create` flow.
9. The user finishes onboarding, or skips only the optional sub-agent step; the server then marks onboarding completed.
10. The desktop opens the workspace.

## Alternative Flow

1. The user chooses **Skip setup**.
2. The server stores `DISMISSED` without changing provider configuration.
3. The desktop opens the workspace.

## Manual Re-run

1. The user selects **Run onboarding again** in **Settings > Providers**.
2. The wizard loads a safe draft from the currently connected Visual Agent server.
3. The user may select **Cancel**, close the window, or press `Esc` to discard the draft without changing onboarding state or provider configuration.
4. Saving the provider advances to the optional sub-agent step; finishing or skipping that step stores `COMPLETED`.

## Result

The selected Visual Agent server owns onboarding state, provider configuration, conversation request,
and persisted sub-agents; the desktop never reads existing credentials.

## Tool Calls

- `agent:list`: the Main Agent inspects existing sub-agents before deciding whether another is needed.
- `agent:create`: the Main Agent creates the requested sub-agent when no suitable existing agent exists.

## Code Entry Points

- `de.heckenmann.visualagent.protocol.OnboardingPort`
- `de.heckenmann.visualagent.server.SpringOnboardingPort`
- `de.heckenmann.visualagent.server.OnboardingAgentService`
- `de.heckenmann.visualagent.ui.onboarding.ComposeOnboardingWizard`
- `de.heckenmann.visualagent.ui.onboarding.OnboardingAgentStep`

## Acceptance Criteria

- Onboarding begins only after the Visual Agent server connection is ready.
- Onboarding shares the splash screen's initial 880 x 600 dp dimensions and
  decorative background resources. The connected server's theme selection
  determines the light/dark variant; System follows the OS appearance.
- The fixed 880 x 600 dp setup window keeps step instructions, validation feedback,
  and navigation visible. Provider, model, and existing sub-agent lists scroll
  independently when needed. The provider editor replaces the profile list while open.
- Reopening onboarding refreshes theme settings from the server asynchronously.
- Missing or malformed background assets do not interrupt the wizard; theme
  colors provide the fallback and all foreground/contrast treatments.
- Provider views expose credential presence but not credential values.
- A fresh server database has no provider profiles; showing or skipping onboarding does not create any.
- Adding a profile, discovering models, and validating a draft do not persist it. Finishing a valid draft saves only that profile; discarding it leaves the catalog empty across restart.
- ChatGPT sign-in for a draft uses a temporary server registration for model discovery and validation. The registration is stored with the provider only when setup saves the profile, and expires after 15 minutes if unused.
- Existing installations keep their saved legacy provider configuration when it is migrated into the provider catalog.
- Skip changes only the server-owned onboarding state.
- Manual cancellation does not change the server-owned onboarding state.
- Settings > Providers can open the manual onboarding flow for the current server.
- Provider/model validation occurs in the connected Visual Agent server environment.
- Model discovery preserves provider-supplied metadata when available and can be refreshed without saving configuration.
- Existing sub-agents are shown before the optional creation request.
- The Researcher example is editable and is sent through the normal Main Agent conversation path.
- Sub-agent setup can be skipped without undoing the configured provider and model.
- No onboarding code creates a sub-agent directly; model-owned creation remains an explicit `agent:create` action.

## Dependency Decision

No new library is used. A klibs.io search was unavailable from the development environment, and a GitHub search found no maintained
Compose Multiplatform onboarding-wizard library matching this flow. The implementation therefore extends the existing Compose wizard,
protocol boundary, and Main Agent tool flow without introducing another dependency or security boundary.
The temporary OAuth registration reuses the protected credential store's existing lifecycle and locking. A general-purpose cache such as Caffeine would only supply expiration and would still need the profile-bound promotion and rollback logic.
