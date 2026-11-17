# UC 0000155: Prepare a Verified Draft Release

## Goal

Prepare a draft with all twelve recipient files after a version tag push. The
maintainer publishes the completed draft through GitHub. See the [release guide](../releases.md).

## Preconditions

- The original push creates the exact `v`-prefixed SemVer tag without force or deletion, and its commit is contained in `master`.
- Every required job in the same release workflow run has succeeded.

## Main Flow

1. The draft job checks that the remote tag still resolves to the exact build commit.
2. It downloads the ten packages and two recipient archives from this same run.
3. It rejects missing, empty, extra, linked, corrupt or review-only recipient files.
4. It creates a draft or reuses the existing matching draft, setting its title to the exact tag. Prerelease flags must match the SemVer prerelease suffix; build metadata alone does not indicate a prerelease.
5. It uploads only the twelve verified files, replacing those names on retry while preserving notes and unrelated assets.
6. It reads all pages of uploaded asset metadata and compares the twelve files' states, sizes, and SHA-256 digests with the build. Missing, partial, duplicate or changed uploads fail the job.
7. The maintainer inspects the completed draft and clicks **Publish release** on GitHub. Automation never publishes it.

## Failure Flow

Failed required jobs prevent draft attachment. Tag movement, a commit outside
`master`, invalid assets, API failures, or an already published release stop the
operation. A partially completed upload remains a draft and can be retried in the
same workflow run while its artifacts remain available. Concurrency prevents two
workflow runs for the same tag from updating the draft simultaneously. Repository
writers must wait for workflow success before publishing; GitHub's UI does not
enforce this job dependency. These checks do not determine legal clearance.

## Tool Calls

- None.

## Code Entry Points

- `.github/workflows/release.yml`
- `scripts/verify-release-ref.sh`
- `scripts/attach-release-draft.sh`
- `scripts/release-tools.sh` (`verify-assets`, built by `:release-tools:installDist`)
