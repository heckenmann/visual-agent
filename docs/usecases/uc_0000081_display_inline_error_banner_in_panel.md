# UC-0000081: Display Inline Error Banner in Panel

## Goal

Show a compact, structured error banner inline inside a workspace panel when an operation fails, without blocking the rest of the UI.

## Primary Actor

Desktop user.

## Preconditions

- A panel (conversation, files, canvas, management) performed an operation that failed.
- The panel holds a `UserFacingError` produced by `ErrorMessageMapper`.

## Main Flow

1. The panel maps the exception to a `UserFacingError`.
2. The panel renders `ErrorBanner(userError = ...)` above or below the relevant content.
3. The banner shows an error icon, the error summary, and the detail text.
4. If the error is retryable, a retry action button appears.
5. If a copy-details callback is provided, a copy button appears.
6. The user can read the message and optionally retry or copy details.

## Result

The user gets contextual, non-blocking feedback about failures within the panel.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.ErrorBanner`
- `de.heckenmann.visualagent.ui.compose.ComposeErrorBanner`
- `de.heckenmann.visualagent.error.ErrorMessageMapper`

## Acceptance Criteria

- An `ErrorBanner` composable exists and renders summary, detail, icon, and category color.
- Retryable errors show a retry action.
- A copy action appears when a callback is provided.
- The banner can be used in any workspace panel without blocking interaction.
- `./gradlew ktlintCheck check test` passes.
- `jacocoTestCoverageVerification` (≥ 0.80 LINE) continues to pass.
