# UC-0000114: Complete first-run provider onboarding

## Goal

Guide a user through selecting and validating an LLM provider/model after the
desktop client has connected to a Visual Agent server.

## Primary Actors

- User
- Connected Visual Agent server

## Preconditions

- A Visual Agent server protocol connection is ready.
- The server-owned onboarding state is `NOT_STARTED`.

## Main Flow

1. The desktop reads onboarding state from the connected server.
2. The onboarding window explains that it configures the server's LLM provider, not the Visual Agent server connection.
3. The user selects an enabled provider profile and the server discovers structured model metadata using a staged, non-persistent draft.
4. The user may refresh model discovery without persisting the draft, then selects a model and reviews the safe provider summary.
5. The server validates the exact provider/model selection.
6. After successful validation the server atomically saves the provider selection and marks onboarding completed.
7. The desktop opens the workspace.

## Alternative Flow

1. The user chooses **Skip setup**.
2. The server stores `DISMISSED` without changing provider configuration.
3. The desktop opens the workspace.

## Manual Re-run

1. The user selects **Run onboarding again** in **Providers and models**.
2. The wizard loads a safe draft from the currently connected Visual Agent server.
3. The user may select **Cancel**, close the window, or press `Esc` to discard the draft without changing onboarding state or provider configuration.
4. A successful Finish repeats validation and atomically stores `COMPLETED` with the provider/model configuration.

## Result

The selected Visual Agent server owns onboarding state and provider configuration;
the desktop never reads existing credentials.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.protocol.OnboardingPort`
- `de.heckenmann.visualagent.server.SpringOnboardingPort`
- `de.heckenmann.visualagent.ui.onboarding.ComposeOnboardingWizard`

## Acceptance Criteria

- Onboarding begins only after the Visual Agent server connection is ready.
- Provider views expose credential presence but not credential values.
- Skip changes only the server-owned onboarding state.
- Manual cancellation does not change the server-owned onboarding state.
- Providers and models can open the manual onboarding flow for the current server.
- Provider/model validation occurs in the connected Visual Agent server environment.
- Model discovery preserves provider-supplied metadata when available and can be refreshed without saving configuration.
