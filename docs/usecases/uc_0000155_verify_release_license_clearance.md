# UC 0000155: Attach and Publish Verified Release Assets

## Goal

Automatically attach the exact packages and recipient-facing legal/source archives from a successful versioned candidate to its draft release, then publish that draft only after a maintainer manually dispatches the publisher.

## Preconditions

- A successful versioned package-and-smoke candidate exists for a valid SemVer tag.
- The tag points to the candidate commit and that commit is contained in the trusted default branch.

## Main Flow

1. When the tagged package-and-smoke run succeeds, the release workflow selects its versioned candidate artifact and verifies its tag, source commit, and trusted-branch ancestry.
2. It creates a draft release for the exact tag if one does not exist; a matching published release is rejected.
3. It downloads the ten application artifacts plus the legal/source archives and verifies the exact expected asset set, rejects empty files, and checks that both recipient archives are readable and not marked review-only.
4. It attaches the candidate assets to the draft and leaves it unpublished.
   Draft lookup uses the GitHub CLI's pending-tag support. Newly created drafts
   retain a SemVer prerelease suffix as GitHub's prerelease flag; build metadata
   alone does not mark a release as a prerelease.
5. A maintainer manually dispatches the publisher from the trusted default branch. The publisher re-verifies the matching draft and candidate, attaches the verified assets, then publishes the draft.

## Failure Flow

Missing legal/source archives, extra files, symlinks, invalid archives, mismatched
or expired candidates, tag/commit mismatches, or a published release stop
attachment/publication. Manual smoke runs never attach assets. Automatic
attachment creates or updates a draft only; publication remains a manual workflow
dispatch. These checks establish asset presence and build provenance only; they do
not determine whether every license condition has been satisfied. GitHub users
with repository write access can still publish releases directly.

## Tool Calls

- None.

## Code Entry Points

- `.github/workflows/release.yml`
- `scripts/verify-release-assets.py`
