# UC-0000076: Scroll Workspace Panels with Arrow Buttons

## Goal

Let the user scroll the horizontal workspace row with clickable arrow buttons when the total panel width exceeds the visible viewport.

## Primary Actor

Desktop user.

## Preconditions

- Two or more panels are visible.
- The combined width of the visible panels is wider than the workspace viewport.

## Main Flow

1. The workspace detects that the panel row is wider than the viewport and renders a left arrow on the left edge and a right arrow on the right edge of the workspace.
2. The user clicks the right arrow.
3. The workspace row scrolls horizontally by a fixed step toward the right, clamped to the maximum scroll value.
4. The user clicks the left arrow.
5. The workspace row scrolls horizontally by the same step toward the left, clamped to the minimum scroll value.
6. The user presses and holds the right arrow.
7. The workspace row scrolls continuously toward the right until the arrow is released or the maximum scroll value is reached.
8. The user presses and holds the left arrow.
9. The workspace row scrolls continuously toward the left until the arrow is released or the minimum scroll value is reached.
10. The user releases the arrow button.
11. Continuous scrolling stops immediately.
12. The user continues interacting with the workspace while scroll animations are in progress.

## Result

Users can reach panels that are outside the current viewport quickly via click steps or by pressing and holding an arrow for continuous scrolling.

## Tool Calls

- None.

## Code Entry Points

- `de.heckenmann.visualagent.ui.compose.ComposeSplitWorkspace`
- `de.heckenmann.visualagent.ui.compose.ScrollArrow`
- `de.heckenmann.visualagent.ui.compose.scrollArrowHandler`
- `de.heckenmann.visualagent.ui.compose.ApplicationLifecycle`

## Acceptance Criteria

- Left and right arrows appear only when the workspace row is wider than the viewport.
- Clicking an arrow scrolls the row by a fixed step in the requested direction.
- Pressing and holding an arrow scrolls the row continuously in that direction.
- Releasing the arrow stops continuous scrolling immediately.
- Scroll target is clamped to the available scroll range.
- The arrow handler uses a shallow lambda structure to avoid Compose compiler-generated deep inner classes.
- Pointer events during application shutdown are ignored and do not launch new coroutines.
- `./gradlew ktlintCheck check test` passes.
- `jacocoTestCoverageVerification` (≥ 0.80 LINE) continues to pass.
