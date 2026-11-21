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
standard draft operation. If the only failed job is the published-release guard,
the maintainer may dispatch **Recover verified assets for published release** with
the source run ID. That Action accepts only a completed tag-push run where source,
quality, packaging, legal/source-material and smoke jobs all succeeded and the
draft attachment step failed for the published-state guard. It revalidates the tag,
downloads only that run's assets, appends missing verified files without replacing
mismatched public assets, and verifies the final remote files. It never changes the
release's published status, notes or unrelated assets. No local upload is used.

A partial upload can be retried through the same recovery Action while its source
artifacts remain available. The tag must still point to the verified commit in
`master`. These technical checks do not determine legal clearance; see open issue
#410.

## Tool Calls

- None.

## Code Entry Points

- `.github/workflows/release.yml`
- `.github/workflows/recover-published-release-assets.yml`
- `scripts/verify-release-ref.sh`
- `scripts/attach-release-draft.sh`
- `scripts/attach-published-release-assets.sh`
- `scripts/release-tools.sh` (`verify-assets`, built by `:release-tools:installDist`)
