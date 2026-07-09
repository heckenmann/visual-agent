# UC-0000080: Display Structured Error in Internal Modal

## Goal

Present a structured, user-friendly error in an internal modal when a user-invoked operation fails with a user-facing error.

## Primary Actor

Desktop user.

## Preconditions

- The user triggered an action that can fail (e.g. provider model load, file import, canvas export).
- The operation failed with a mapped `UserFacingError`.

## Main Flow

1. The UI builds a `UserFacingError` from the failing exception via `ErrorMessageMapper`.
2. The panel calls `modalRequester.requestError(ComposeErrorModal(...))`.
3. The modal host dims the workspace and renders the error modal.
4. The modal shows the error summary as the title, the detail as the body, and an error icon colored by `ErrorCategory`.
5. If the error is retryable, a retry button is shown.
6. If a copy-details callback is provided, a copy button is shown.
7. The user clicks Close or one of the action buttons.
8. The modal is dismissed.

## Result

The user sees a concise, actionable explanation of what went wrong instead of a raw exception message.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.ComposeErrorModal`
- `de.heckenmann.visualagent.ui.compose.ComposeModalHost`
- `de.heckenmann.visualagent.ui.compose.ComposeModalRequester.requestError`
- `de.heckenmann.visualagent.error.ErrorMessageMapper`

## Acceptance Criteria

- A `ComposeErrorModal` variant exists in the `ComposeModal` sealed type.
- The modal renders summary, detail, error icon, and category-specific color.
- Retryable errors show a retry button.
- A copy-details button appears when a callback is provided.
- The modal is dismissed by Close, Retry, or Copy.
- `./gradlew ktlintCheck check test` passes.
- `jacocoTestCoverageVerification` (≥ 0.80 LINE) continues to pass.
