# UC 0000157: Retain Third-Party Notices

## Goal

Collect original license and notice files from hash-verified packaged JARs while
keeping missing evidence and legal review status explicit.

## Main Flow

The existing `generateLicenseReport` task provides metadata and embedded text via
Gradle License Report's built-in all-license JSON renderer. Its broad dependency
scope includes build/test artifacts and does not imply that every entry is shipped.

1. Generate the desktop packaged-artifact inventory from the resolved distribution.
2. Build `./gradlew :release-tools:installDist` and run
   `bash scripts/release-tools.sh collect-notices` with `--package-root`, `--inventory`,
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
12. A version tag push builds separate review-only and recipient archives in the
    same workflow as the packages and smoke tests. The draft job checks and attaches
    only the two recipient archives together with all ten packages after every job
    passes. A maintainer publishes the completed draft through GitHub's UI. Archive
    presence does not establish legal clearance; see the [release guide](../releases.md).

## Failure Flow

Changed archives, escaping paths, duplicate inventory paths or notice entries,
linked notices, empty or oversized notice files, and nonempty output directories
fail collection. A failed collection does not produce a completed manifest.
Changed supplemental texts, mismatching artifact bytes, duplicate identities,
unsafe paths, invalid source URLs, self-approving registries, and an incomplete
or unexpected native-platform evidence set also fail.

## Limits

This evidence collector is not a license parser or legal approval service. It uses
Apache Commons Compress for ZIP metadata and entry streaming, not a replacement for the existing Gradle License Report
plugin. POM metadata, absent notices, dual-license selections, native code, fonts,
and source obligations require separate review and supplemental materials. Even
components with retained notices remain unreviewed. It does not alter release
approval or complete issue #410. Packaging the initial curated materials does not
mean every dependency/native/source obligation is covered. The About panel provides
access to documents included in the current application build; completeness
of the notice bundle, legal review, and corresponding sources remain open.

## Tooling Design

Issue #546 isolates the collector and bundle builder in the Gradle-built
`:release-tools` CLI. Compiled `.kt` sources share tested verification logic;
`.gradle.kts` files configure its build. This follows the documented
[Kotlin console application](https://kotlinlang.org/docs/jvm-get-started.html) and
[Gradle Application plugin](https://docs.gradle.org/current/userguide/application_plugin.html)
model. A separate scripting host or compiler installation adds no needed capability.

The migration plan and comparison evidence are recorded in
[issue #546](https://github.com/heckenmann/visual-agent/issues/546); lasting commands
and boundaries are documented in the [module architecture](../gradle-module-architecture.md#release-tooling).
The tooling reuses
Apache-2.0 [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)
for JSON trees and the catalog's Jackson Core family for strict duplicate-key
validation. Apache-2.0 [Commons Compress](https://github.com/apache/commons-compress)
provides ZIP symlink metadata and deterministic stored archives; the JDK ZIP API
alone does not expose all required Unix metadata. Apache-2.0
[Commons CSV](https://github.com/apache/commons-csv) reads Gradle's quoted inventory
records without a custom CSV parser. These maintained JVM libraries use
catalog-managed versions and remain outside application runtime dependencies.
NIO and `MessageDigest` cover filesystem confinement and SHA-256 hashing.

## Tool Calls

- None.

## Code Entry Points

- `scripts/release-tools.sh` (`collect-notices`)
- `scripts/release-tools.sh` (`build-bundles`)
- `modules/release-tools/src/test/kotlin/de/heckenmann/visualagent/release/NoticeCollectorContractTest.kt`
- `modules/release-tools/src/test/kotlin/de/heckenmann/visualagent/release/bundles/`
- `modules/desktop/build.gradle.kts`: `generateDistributionArtifactInventory`
- `modules/desktop/build.gradle.kts`: `prepareDistributionLegalResources`
- `modules/desktop/build.gradle.kts`: `prepareLibrarySourceMaterials`
- `modules/release-tools/src/test/kotlin/de/heckenmann/visualagent/release/packaging/`
- `application/build.gradle.kts`: `bootJar` and `verifyExecutableJar`
- `modules/protocol/src/main/kotlin/de/heckenmann/visualagent/protocol/OpenSourceLicensePort.kt`
- `application/src/main/kotlin/de/heckenmann/visualagent/server/BundledOpenSourceLicensePort.kt`
- `modules/ui/src/main/kotlin/de/heckenmann/visualagent/ui/info/OpenSourceLicensesPanel.kt`
- `modules/ui/src/main/kotlin/de/heckenmann/visualagent/ui/info/AboutPanel.kt`
- `buildSrc/src/main/kotlin/de/heckenmann/visualagent/gradle/BundledLegalMaterialsVerification.kt`
