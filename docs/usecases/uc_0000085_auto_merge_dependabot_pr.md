# UC-0000085: Auto-Merge Dependabot Pull Requests

## Goal

Automatically merge Dependabot dependency-update pull requests once CI passes, reducing maintainer toil for low-risk version bumps.

## Primary Actor

Dependabot bot account (`dependabot[bot]`).

## Preconditions

- Dependabot is configured in `.github/dependabot.yml` (Gradle and GitHub Actions ecosystems).
- The `Tests` workflow runs on `pull_request` events.
- The `dependabot-automerge` job is present in `.github/workflows/test.yml`.

## Main Flow

1. Dependabot opens a pull request targeting `master`.
2. The `Tests` workflow triggers on the `pull_request` event.
3. The `test` job runs `./gradlew --no-daemon test` under `xvfb-run`.
4. If the `test` job succeeds, the `dependabot-automerge` job runs (only for `dependabot[bot]` author).
5. The job fetches Dependabot metadata to determine the update type and package ecosystem.
6. The job checks that only dependency files (`**/build.gradle*`, `**/gradle.properties`, `**/libs.versions.toml`, `.github/workflows/*.yml`) are touched.
7. The job checks that the PR does not have the `dependabot: no-auto-merge` label.
8. If all conditions are met, `gh pr merge --auto --squash --delete-branch` is called.
9. GitHub merges the PR once CI is green and any required approvals are satisfied.
10. The source branch is deleted on merge.

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

- `.github/workflows/test.yml` — `dependabot-automerge` job
- `.github/dependabot.yml` — Dependabot configuration (unchanged)
- `AGENTS.md` — documented auto-merge policy

## Acceptance Criteria

- Dependabot patch and minor bumps for Gradle dependencies are auto-merged when CI is green.
- Major bumps, GitHub Actions bumps, and PRs touching non-dependency files are **not** auto-merged.
- PRs with the `dependabot: no-auto-merge` label are **not** auto-merged.
- The `Tests` workflow runs on `push`, `pull_request`, and `workflow_dispatch`.
- The `Tests` workflow is added as a required status check on `master`.
