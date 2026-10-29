# UC 0000157: Retain Third-Party Notices

## Goal

Collect original license and notice files from hash-verified packaged JARs while
keeping missing evidence and legal review status explicit.

## Main Flow

1. Generate the desktop packaged-artifact inventory from the resolved distribution.
2. Run `scripts/collect-third-party-notices.py` with `--package-root`, `--inventory`,
   and a new or empty `--output` directory.
3. Verify every listed archive against its packaged SHA-256 before reading it.
4. Retain detected license, notice, copyright, disclaimer, and author files
   verbatim, separated by archive hash and original entry path.
5. Record component identities, original paths, sizes, and content hashes in
   `manifest.json`. Components without detected notices remain explicitly listed.

## Failure Flow

Changed archives, escaping paths, duplicate inventory paths or notice entries,
linked notices, empty or oversized notice files, and nonempty output directories
fail collection. A failed collection does not produce a completed manifest.

## Limits

This evidence collector is not a license parser or legal approval service. It uses
the standard ZIP reader, not a replacement for the existing Gradle License Report
plugin. POM metadata, absent notices, dual-license selections, native code, fonts,
and source obligations require separate review and supplemental materials. Even
components with retained notices remain unreviewed. It does not alter release
approval, embed the bundle in installers, or complete issue #410.

## Tool Calls

- None.

## Code Entry Points

- `scripts/collect-third-party-notices.py`
- `scripts/test_release_notices.py`
- `modules/desktop/build.gradle.kts`: `generateDistributionArtifactInventory`
