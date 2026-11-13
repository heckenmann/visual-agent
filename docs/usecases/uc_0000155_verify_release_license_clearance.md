# UC 0000155: Publish Verified Release Assets

## Goal

Publish the exact packages and recipient-facing legal/source archives from a successful versioned candidate.

## Preconditions

- A GitHub draft release exists for a valid SemVer tag.
- The tag points to a commit contained in the trusted default branch.
- A successful versioned package-and-smoke candidate exists for the exact tagged commit.

## Main Flow

1. The maintainer manually dispatches the publisher from the trusted default
   branch for the draft release tag.
2. The publisher verifies the matching release is still a draft.
3. It selects the successful candidate run for the exact tag commit and verifies its metadata.
4. It downloads the ten application artifacts plus the legal/source archives.
5. It verifies the exact expected asset set, rejects empty files, and checks that both recipient archives are readable and not marked review-only.
6. It attaches the candidate assets to the draft, then publishes the draft.

## Failure Flow

Missing legal/source archives, extra files, symlinks, invalid archives, mismatched
or expired candidates, or a non-draft release stop publication and leave the draft
unpublished. Manual smoke runs never publish. These checks establish asset presence
and build provenance only; they do not determine whether every license condition
has been satisfied. The maintainer's manual workflow dispatch is the publication
action. GitHub users with repository write access can still publish releases
directly.

## Tool Calls

- None.

## Code Entry Points

- `.github/workflows/release.yml`
- `scripts/verify-release-assets.py`
