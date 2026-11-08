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
   `--license-report`, `--project-license`, and a new or empty `--output` directory.
3. Verify every listed archive against its packaged SHA-256 before reading it.
4. Retain detected license, notice, copyright, disclaimer, and author files
   verbatim, separated by archive hash and original entry path.
5. Locate exactly one packaged `resources/legal/java-runtime` directory. Copy
   every file byte-for-byte, including Azul's vendor offer information, into
   `runtime-legal/`; record package paths, sizes, and hashes in the manifest.
   Missing, duplicate, linked, escaping, or oversized runtime material fails
   collection.
6. Match each packaged Maven coordinate to the exact module/version in Gradle's
   license report. Record all reported alternatives and URLs, the report hash,
   artifact identity, original paths, sizes, and content hashes in `manifest.json`.
   Missing declarations remain unreviewed; a missing exact metadata match fails.
   First-party project artifacts are recorded separately and bound to the project
   `LICENSE` hash. Components without detected notices remain explicitly listed.
7. When `--supplements` names a registry, verify its original upstream texts and
   exact component/version/source-archive identities. Continue checking the
   packaged bytes separately against the inventory, since packaging may transform
   JARs. Retain these under a separate
   `supplements/` directory without hiding absent embedded notices or granting
   clearance. Record the proposed license and remaining review work.
8. Native packaging combines the existing runtime materials with the curated
   third-party directory through Gradle `Sync` and Compose's app-resources input
   before signing. Both executable JARs include the project license and curated
   directory under `META-INF/visual-agent/legal/`. The desktop **About** panel's
   open-source license section reads these same packaged documents and
   lets the user filter, select, and copy their text from the application. The
   user opens About from the workspace rail or command palette; the eight
   existing numeric panel shortcuts remain assigned to their current panels.
9. Package verification compares every included curated file with its original
   bytes. Verification is read-only and never patches signed bundles.
   Review-bundle assembly also requires the project-license hash recorded during
   package collection to match the `LICENSE` included in that same legal bundle.
10. `:desktop:prepareLibrarySourceMaterials` collects exact selected runtime source
   variants for H2 and Logback using Gradle's built-in artifact view, together with
    curated legal inputs and an explicit scope statement. Source resolution errors
    and missing required archives fail collection. This partial evidence directory
    is not published as a completed corresponding-source bundle. The release review
    job stages the checksum-pinned supplemental Azul Java source package once via
    `:desktop:prepareReleaseSourceMaterials`; platform inventory jobs do not
    duplicate that large archive.
11. Review-bundle assembly requires notice evidence for every supported native
    package (`linux-deb`, `linux-rpm`, `linux-appimage`, `macos-arm64`,
    `macos-x64`, and `windows`). Missing or unexpected platform evidence fails
    the assembly rather than producing a misleading partial archive.
12. A tagged manual package-and-smoke run may build a SemVer release candidate
    before a GitHub release exists. It uploads separate review-only archives and
    hash-bound recipient-candidate archives, plus the platform packages, as
    workflow artifacts; neither is published as a release. Candidate archive
    generation omits only the review marker and does not update or grant the
    source registry's approval state. The release publisher requires a successful
    candidate for the exact release tag and source commit, then applies the
    trusted clearance gate before attaching those exact candidate artifacts.

## Failure Flow

Changed archives, escaping paths, duplicate inventory paths or notice entries,
linked notices, empty or oversized notice files, and nonempty output directories
fail collection. A failed collection does not produce a completed manifest.
Changed supplemental texts, mismatching artifact bytes, duplicate identities,
unsafe paths, invalid source URLs, self-approving registries, and an incomplete
or unexpected native-platform evidence set also fail.

## Limits

This evidence collector is not a license parser or legal approval service. It uses
the standard ZIP reader, not a replacement for the existing Gradle License Report
plugin. POM metadata, absent notices, dual-license selections, native code, fonts,
and source obligations require separate review and supplemental materials. Even
components with retained notices remain unreviewed. It does not alter release
approval or complete issue #410. Packaging the initial curated materials does not
mean every dependency/native/source obligation is covered. The About panel provides
access to documents included in the current application build; completeness
of the notice bundle, legal review, and corresponding sources remain open.

## Tool Calls

- None.

## Code Entry Points

- `scripts/collect-third-party-notices.py`
- `scripts/build_release_material_bundles.py`
- `scripts/test_release_notices.py`
- `scripts/test_release_material_bundles.py`
- `modules/desktop/build.gradle.kts`: `generateDistributionArtifactInventory`
- `modules/desktop/build.gradle.kts`: `prepareDistributionLegalResources`
- `modules/desktop/build.gradle.kts`: `prepareLibrarySourceMaterials`
- `scripts/test_release_library_sources.py`
- `application/build.gradle.kts`: `bootJar` and `verifyExecutableJar`
- `modules/protocol/src/main/kotlin/de/heckenmann/visualagent/protocol/OpenSourceLicensePort.kt`
- `application/src/main/kotlin/de/heckenmann/visualagent/server/BundledOpenSourceLicensePort.kt`
- `modules/ui/src/main/kotlin/de/heckenmann/visualagent/ui/info/OpenSourceLicensesPanel.kt`
- `modules/ui/src/main/kotlin/de/heckenmann/visualagent/ui/info/AboutPanel.kt`
- `buildSrc/src/main/kotlin/de/heckenmann/visualagent/gradle/BundledLegalMaterialsVerification.kt`
