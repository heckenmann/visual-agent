# UC-0000085: Auto-Merge Dependabot Pull Requests

## Goal

Automatically merge Dependabot dependency-update pull requests once CI passes, reducing maintainer toil for low-risk version bumps.

## Primary Actor

Dependabot bot account (`dependabot[bot]`).

## Preconditions

- Dependabot is configured in `.github/dependabot.yml` (Gradle and GitHub Actions ecosystems).
- The `Tests` workflow runs on `pull_request` events.
- The `dependabot-automerge` workflow is present in `.github/workflows/dependabot-automerge.yml`.

## Main Flow

1. Dependabot opens a pull request targeting `master`.
2. The `Tests` workflow triggers on the `pull_request` event and runs `./gradlew --no-daemon test`.
3. The `dependabot-automerge` workflow triggers on the same `pull_request` event (only for `dependabot[bot]` author).
4. The workflow fetches Dependabot metadata to determine the update type and package ecosystem.
5. The workflow checks that only dependency files (`**/build.gradle*`, `**/gradle.properties`, `**/libs.versions.toml`, `.github/workflows/*.yml`) are touched.
6. The workflow checks that the PR does not have the `dependabot: no-auto-merge` label.
7. If all conditions are met, `gh pr merge --auto --squash --delete-branch` is called.
8. GitHub waits until all required status checks (including `Gradle tests`) are green, then merges the PR.
9. The source branch is deleted on merge.

## Exclusion Rules

Auto-merge is skipped when any of the following is true:

- The update is a major version bump (`version-update:semver-major`).
- The package ecosystem is `github_actions` (GitHub Actions version bumps).
- The PR touches files outside the allowed dependency-file set.
- The PR has the `dependabot: no-auto-merge` label.

## Result

Low-risk Dependabot PRs are merged automatically without manual intervention. The maintainer retains control via the exclusion label and the manual-only rules for major bumps and action upgrades.

## Tool Calls

- None.

## Code Entry Points

- `.github/workflows/dependabot-automerge.yml` — auto-merge workflow
- `.github/workflows/test.yml` — test workflow (runs on `pull_request`)
- `.github/dependabot.yml` — Dependabot configuration (unchanged)
- `AGENTS.md` — documented auto-merge policy

## Acceptance Criteria

- Dependabot patch and minor bumps for Gradle dependencies are auto-merged when CI is green.
- Major bumps, GitHub Actions bumps, and PRs touching non-dependency files are **not** auto-merged.
- PRs with the `dependabot: no-auto-merge` label are **not** auto-merged.
- The `Tests` workflow runs on `push`, `pull_request`, and `workflow_dispatch`.
- The `Tests` workflow is added as a required status check on `master`.
