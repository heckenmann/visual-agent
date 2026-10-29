# UC 0000155: Verify Release License Clearance

## Goal

Prevent technically passing builds from publishing unreviewed binary distributions.

## Preconditions

- Package and smoke validation has completed successfully for a release event.
- A trusted review covers every package, its legal bundle, and corresponding sources.

## Main Flow

1. The publisher loads the publication policy from the trusted default branch.
2. It downloads the ten verified application assets plus the legal/source archives.
3. It requires a recorded redistribution review for the exact tag and source commit.
4. It verifies the review evidence hash and every release file's SHA-256.
5. Only the complete, unchanged, reviewed file set may be attached to the release.

## Failure Flow

Missing reviews, evidence, legal/source archives, extra files, symlinks, or changed
hashes stop publication. Manual smoke runs never publish. No release is approved
by default while #410 remains unfinished. This integrity gate does not substitute
for artifact-level license and corresponding-source review.

## Tool Calls

- None.

## Code Entry Points

- `.github/workflows/release.yml`
- `scripts/verify-release-licenses.py`
- `docs/licensing/release-approvals.json`
