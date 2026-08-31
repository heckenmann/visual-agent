# UC-0000071: Confirm Destructive Action In Internal Modal

## Goal

Require users to confirm destructive UI actions in an internal Compose modal instead of a native operating-system dialog.

## Primary Actor

Desktop user.

## Preconditions

- The Compose workspace is visible.
- A destructive action is available from a panel or toolbar.

## Main Flow

1. The user activates a destructive action such as clearing conversation history, deleting a todo, deleting a sub-agent, deleting a workspace file, clearing the canvas, or deleting the selected canvas figure from the toolbar.
2. The application shows an internal modal above all workspace panels.
3. The dimmed workspace remains visible but cannot be interacted with until the modal is resolved.
4. The dialog slides upward into view.
5. The user confirms or cancels through clearly labelled standard buttons.
6. On confirmation, the original action runs and the dialog scales down out of view.
7. On cancellation, no destructive change is applied and the dialog scales down out of view.

## Result

Destructive UI actions are deliberate and stay inside the Visual Agent workspace interaction model.

## Tool Calls

- None. Model tool calls remain explicit API actions and do not display UI modals.

## Code Entry Points

- `de.heckenmann.visualagent.ui.modal.composeModalHost`
- `de.heckenmann.visualagent.ui.modal.ComposeConfirmationModal`
- `de.heckenmann.visualagent.ui.modal.ComposeModalRequester`
- `de.heckenmann.visualagent.ui.modal.modalPrimaryButton`
- `de.heckenmann.visualagent.ui.modal.modalSecondaryButton`
- `de.heckenmann.visualagent.ui.application.VisualAgentComposeApp`

## Acceptance Criteria

- Confirmation UI is rendered inside the main Compose window, not as a native dialog.
- The modal is visually above all workspace panels.
- Workspace interaction is blocked while the modal is visible.
- Confirm and cancel actions use visible, descriptive text labels.
- Cancelling a destructive action does not mutate persisted state.
- Every internal modal uses the common title bar, independently scrollable body, and fixed,
  right-aligned action footer so that actions remain available for long content.
- A modal grows with its content until it reaches 80% of the window height; additional content
  scrolls without moving its footer actions.
- All dialog variants use the same upward entry and scale-down exit transition.
