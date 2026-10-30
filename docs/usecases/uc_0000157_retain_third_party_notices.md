# UC 0000157: Retain Third-Party Notices

## Goal

Collect original license and notice files from hash-verified packaged JARs while
keeping missing evidence and legal review status explicit.

## Main Flow

The existing `generateLicenseReport` task provides metadata and embedded text via
Gradle License Report's built-in all-license JSON renderer. Its broad dependency
scope includes build/test artifacts and does not imply that every entry is shipped.

1. Generate the desktop packaged-artifact inventory from the resolved distribution.
2. Run `scripts/collect-third-party-notices.py` with `--package-root`, `--inventory`,
   and a new or empty `--output` directory.
3. Verify every listed archive against its packaged SHA-256 before reading it.
4. Retain detected license, notice, copyright, disclaimer, and author files
   verbatim, separated by archive hash and original entry path.
5. Record component identities, original paths, sizes, and content hashes in
   `manifest.json`. Components without detected notices remain explicitly listed.
6. When `--supplements` names a registry, verify its original upstream texts and
   exact component/version/source-archive identities. Continue checking the
   packaged bytes separately against the inventory, since packaging may transform
   JARs. Retain these under a separate
   `supplements/` directory without hiding absent embedded notices or granting
   clearance. Record the proposed license and remaining review work.
7. Native packaging combines the existing runtime materials with the curated
   third-party directory through Gradle `Sync` and Compose's app-resources input
   before signing. Both executable JARs include the project license and curated
   directory under `META-INF/visual-agent/legal/`.
8. Package verification compares every included curated file with its original
   bytes. Verification is read-only and never patches signed bundles.

## Failure Flow

Changed archives, escaping paths, duplicate inventory paths or notice entries,
linked notices, empty or oversized notice files, and nonempty output directories
fail collection. A failed collection does not produce a completed manifest.
Changed supplemental texts, mismatching artifact bytes, duplicate identities,
unsafe paths, invalid source URLs, and self-approving registries also fail.

## Limits

This evidence collector is not a license parser or legal approval service. It uses
the standard ZIP reader, not a replacement for the existing Gradle License Report
plugin. POM metadata, absent notices, dual-license selections, native code, fonts,
and source obligations require separate review and supplemental materials. Even
components with retained notices remain unreviewed. It does not alter release
approval or complete issue #410. Packaging the initial curated materials does not
mean every dependency/native/source obligation is covered: the full notice bundle,
legal review, corresponding sources, and user-facing notice access remain open.

## Tool Calls

- None.

## Code Entry Points

- `scripts/collect-third-party-notices.py`
- `scripts/test_release_notices.py`
- `modules/desktop/build.gradle.kts`: `generateDistributionArtifactInventory`
- `modules/desktop/build.gradle.kts`: `prepareDistributionLegalResources`
- `application/build.gradle.kts`: `bootJar` and `verifyExecutableJar`
- `buildSrc/src/main/kotlin/de/heckenmann/visualagent/gradle/BundledLegalMaterialsVerification.kt`
