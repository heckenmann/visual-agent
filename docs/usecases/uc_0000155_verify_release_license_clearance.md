# UC 0000155: Verify Release License Clearance

## Goal

Prevent technically passing builds from publishing unreviewed binary distributions.

## Preconditions

- A GitHub draft release exists for a valid SemVer tag.
- The tag points to a commit contained in the trusted default branch.
- A successful versioned package-and-smoke candidate exists for the exact tagged commit.
- A trusted review covers every package, its legal bundle, and corresponding sources.

## Main Flow

1. The maintainer manually dispatches the publisher from the trusted default
   branch for the draft release tag.
2. The publisher loads the publication policy from the trusted default branch and verifies the matching release is still a draft.
3. It selects the successful candidate run for the exact tag commit and verifies its metadata.
4. It downloads the ten application artifacts plus the legal/source archives.
5. It requires a recorded redistribution review for the exact tag and source commit.
6. It verifies the review evidence hash and every release file's SHA-256.
7. Only the complete, unchanged, reviewed file set may be attached; the publisher then changes the release from draft to published.

## Failure Flow

Missing reviews, evidence, legal/source archives, extra files, symlinks, mismatched
or expired candidates, a non-draft release, or changed hashes stop publication and
leave the draft unpublished. Manual smoke runs never publish. No release is
approved by default while #410 remains unfinished. This integrity gate does not
substitute for artifact-level license and corresponding-source review.
It governs the documented publisher workflow only; GitHub users with repository
write access can still publish releases directly, so repository access and
maintainer procedure must prevent bypasses.

## Tool Calls

- None.

## Code Entry Points

- `.github/workflows/release.yml`
- `scripts/verify-release-licenses.py`
- `docs/licensing/release-approvals.json`
