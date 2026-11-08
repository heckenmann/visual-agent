# Release redistribution clearance

Issue #410 is in progress. **No release currently has a completed redistribution
review.** `release-approvals.json` is intentionally empty. Missing approval, legal
material, or corresponding sources blocks publication; it is not a CI failure to
work around by adding a generic license allowlist or a fabricated approval.

## Approval Boundary

`scripts/verify-release-licenses.py` binds a review to an exact source commit and
all twelve release files: ten application packages/JARs, `visual-agent-legal.zip`,
and `visual-agent-corresponding-sources.zip`. Every file must match its reviewed
SHA-256. Extra files, missing files, symlinks, and changed payloads are rejected.
The approval and its hashed evidence document come from the trusted default
branch, not the package-producing workflow's artifacts. A technically green
build cannot self-approve redistribution.

The checker verifies evidence integrity and scope, **not legal correctness**.
A competent review must establish the following before recording an approval:

- Map every actual packaged dependency, nested native/font resource, runtime,
  copied tool, and project asset to its exact license and provenance.
- Retain full applicable license texts, upstream notices, disclaimers, selected
  dual-license options, and modification notices in the packages and legal bundle.
- Supply complete corresponding source and build/relink material where required,
  including the exact JDK runtime and statically linked AppImage components.
  A Maven POM, a `src.zip` containing only Java classes, or a generic upstream
  homepage does not establish complete corresponding-source delivery.
- Inspect every platform and format; a macOS app-image inventory alone does not
  prove the Windows installer, Linux packages, or executable JARs are cleared.
- Identify the reviewer, date, source commit, exact file hashes, evidence document,
  and its SHA-256. Never manufacture a legal opinion or approval attribution.

This gate is the initial safety boundary, not completion of #410. CI now creates
review-only legal and source archives. The legal evidence archive includes
byte-verified license/notice files extracted from runtime JARs on Linux x64,
macOS ARM64/x64, and Windows x64, with package hashes and unreviewed status
manifests. It does not cover every platform-specific native payload or make the
archive recipient-ready. Complete artifact-level license/source review and final
package-content checks remain open.

The release publisher is manually dispatched from the trusted default branch for
an existing GitHub draft release. It accepts only a successful versioned package
candidate for the draft tag's exact commit, attaches the candidate artifacts only
after exact-hash clearance, and publishes the draft last. A missing or failed
review leaves the release unpublished. A plain `published` event cannot provide
this gate because it occurs after the release has become public ([GitHub release
events](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#release)). This
protects the documented publisher workflow; GitHub still allows repository
collaborators and users with write access to manage releases ([GitHub release
permissions](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases)).
Repository permissions and the release procedure must therefore ensure that
maintainers do not bypass this workflow. Do not describe the workflow alone as a
platform-enforced publication restriction.
AboutLibraries was assessed on klibs.io (15.3.0, Apache-2.0, Compose 1.12 support);
the existing maintained Gradle License Report 3.1.5 remains the metadata inventory
tool. Neither scanner alone covers all native redistribution obligations.

## Retained Runtime Inputs

`./gradlew :desktop:prepareRuntimeLegalMaterials` collects original source-JDK legal
and vendor readme/notice files through a cacheable build task. The native distribution
includes them under the common app-resources `legal/java-runtime` directory, using
[Compose's supported app-resources mechanism](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html).
The project's MIT license is separate under `legal/visual-agent`.
Package notice collection also copies the complete `legal/java-runtime` tree
from each native package into its platform-specific review evidence, with a
per-file hash manifest. This lets the combined legal review archive retain the
same Azul offer information and JDK module notices; it does not grant clearance.

Native package verification also writes `runtime-provenance.properties` outside
the signed application bundle, recording source and packaged runtime/tool hashes
separately. [JDK 27 macOS jpackage removes and replaces executable signatures](https://github.com/openjdk/jdk27u/blob/master/src/jdk.jpackage/macosx/classes/jdk/jpackage/internal/AppImageSigner.java),
and jlink transforms the modules image. These supported stages must not be confused
with byte-identical copies. Source inputs and retained notices are still verified
against their hashes; no signed bundle is patched after packaging. Final release
approval continues to bind the complete actual package bytes independently.

`source-jdk.properties` includes only public version/vendor/platform keys, not host
paths or arbitrary JDK properties. `source-inputs.sha256` identifies original JDK
tools/modules and retained files; those original executables are not copied into
the legal-material directory. File links outside the source JDK and directory links
are rejected. This fixes provenance retention, not corresponding-source delivery
or application/native dependency notice completeness.

The `Release workflow contracts` CI job runs the complete `test_release_*.py`
suite independently of `Gradle tests`. The runtime legal-material fixtures execute
real Gradle builds; serializing them after the application suite exhausted the
existing 15-minute job budget despite successful assertions. Both suites retain
their tests and limits, and the contract job uses the Gradle cache read-only.

## Zulu Runtime

See [the source-delivery worklist](source-delivery.md) for verified exact-version
Maven source inputs and the remaining bundle/recipient-notice implementation.
Available upstream sources are not evidence that our release already delivers them.

CI pins Zulu Community Availability JDK `27.0.1.0.3+1`, not Subscriber
Availability. The setup-java version selector is exact, and the Azul metadata API
currently returns that build for the Linux x64, macOS ARM64/x64, and Windows x64
package formats used by the workflow. Each packaged runtime still records its own
provenance and must be checked against the selected JDK before release. Azul's
[October 2026 release notes](https://docs.azul.com/core/release-notes) map Zulu
27.30.109 CA to JDK 27.0.1.0.3; the corresponding [Zulu 27.30 licensing
information](https://docs.azul.com/core/tpls/october-2026/zulu27_jdk_tpl.html)
contains the runtime's license and source-availability terms. Each exact Zulu JDK
archive also includes a root `readme.txt` with a three-year, cost-limited source
offer and a package-specific request code. The existing runtime-material task
copies this file byte-for-byte into native packages; the exact archive and readme
hashes for all four release platforms are recorded in
[`source-delivery.md`](source-delivery.md). Since this project is noncommercial,
GPLv2 section 3(c) may provide a pass-through route, but a qualified reviewer
must confirm the offer satisfies section 3(b), covers the relevant exact runtime
components, and remains available with every final package. No source request was
sent. The MIT project remains independent of the runtime. GPLv2 plus Classpath
Exception does not waive runtime notices or corresponding-source obligations.
See the [CA/SA distinction](https://support.azul.com/hc/en-us/articles/360051512551-What-s-the-difference-between-CA-and-SA-bundles-of-Azul-Zulu-Builds-of-OpenJDK).
Changing to Temurin would not remove those obligations and would require a
platform-matrix change because JDK 27 is not available for Intel macOS.

## Original Third-Party Notices

After `:desktop:generateDistributionArtifactInventory`, collect the original texts:

```bash
python3 scripts/collect-third-party-notices.py \
  --package-root modules/desktop/build/compose/binaries/main/app \
  --inventory modules/desktop/build/reports/dependency-license/packaged-artifacts.csv \
  --license-report build/reports/dependency-license/licenses-all.json \
  --project-license LICENSE \
  --output build/reports/dependency-license/third-party-notices \
  --supplements docs/licensing/third-party/sources.json
```

Use a new or empty output directory. The collector verifies actual packaged JAR
hashes, retains original bytes, and records a hash-addressed manifest. The platform
inventory workflow uploads this directory for review. Components without detected
notices remain listed; every component remains `unreviewed`, even if texts exist.
Each packaged Maven component is joined to the report by exact module and version;
all reported license alternatives and URLs are retained in the manifest. Missing
license declarations remain explicit and unreviewed, while an absent exact report
match fails collection. The report hash binds the manifest to the metadata snapshot.
First-party project JARs are recorded separately and bound to the repository's
top-level `LICENSE` hash instead of being mislabeled as third-party dependencies.
Review-bundle generation rejects evidence collected against a different project
license revision than the `LICENSE` file included in the bundle.
This is evidence collection, not a replacement license scanner or clearance gate.

The maintained Gradle License Report plugin remains the metadata inventory tool;
its built-in `ExtendedJsonReportRenderer("licenses-all.json", false)` exports all
declared alternatives and available embedded texts. This avoids inventing another
metadata parser or silently selecting the first declared license. The report covers
the existing broad build/test/runtime inventory, not just the packaged runtime.
The plugin decodes/re-encodes extracted text, so this JSON is not byte-exact archive
evidence. Original package files and supplemental texts are retained separately.
The report is not a complete reviewed redistribution bundle. The ZIP collector
uses the standard library to retain
actual artifact contents. Upstream/version-specific supplemental notices, manual
dual-license decisions, native/font review, installer/JAR bundle inclusion, and
source delivery remain separate work in #410. Never fill those gaps by assuming
that one generic license text covers all resolved dependencies.

### GraalJS Runtime Terms And Packaging

The selected desktop runtime graph currently includes Oracle's non-community
GraalJS isolate artifact (for example,
`org.graalvm.js:js-isolate-darwin-aarch64:25.4.4.1.1`) alongside GraalJS and
Truffle components whose metadata reports UPL and ICU licensing. The upstream
[GraalJS licensing notes](https://github.com/oracle/graaljs#license) distinguish
community UPL artifacts from non-community GFTC artifacts and identify isolate
artifacts as GFTC-only. Oracle's [GFTC terms](https://www.oracle.com/downloads/licenses/graal-free-license.html)
allow redistribution without fees but require redistribution of the unmodified
Program and prohibit removing proprietary-rights markings. The exact received
artifact and its use in the beta must be checked against the complete terms; a
noncommercial project description alone is not a clearance decision.

The curated package materials now include
`third-party/graaljs-25.4.4.1.1/GFTC-TERMS-REFERENCE.md`, which points recipients
to Oracle's complete terms and summarizes the relevant review conditions. It is
not the agreement text and does not satisfy release clearance by itself. The
supplement registry binds this reference to the exact Linux x64, macOS ARM64,
and Windows x64 isolate JAR hashes; macOS x64 has no isolate JAR in the audited
package inventory.

The local macOS inventory records distinct original-artifact and packaged-JAR
hashes for the isolate artifact. The transformation is now identified: Compose
Desktop Gradle Plugin 1.12.2's `MacJarSignFileCopyingProcessor` re-signs every
embedded `.dylib` when it copies a JAR. Its macOS ARM64 no-certificate signer
removes the existing signature and adds an ad-hoc runtime signature. In the
inspected GraalJS library, the Mach-O UUID is unchanged and both source and
packaged signatures validate; the code-signature data shrank from 1,256,544 to
314,400 bytes. This explains the changed binary hash and size, but is not a legal
determination that the GFTC's unmodified-program condition permits this
redistribution transformation. Confirm that point for the exact artifact and
delivery, and retain required notices. The upstream UPL community artifacts
were evaluated as an alternative, but direct `SandboxPolicy.UNTRUSTED` startup
of the macOS ARM64 community artifact failed on the required JDK 27 with both
GraalJS 25.4.4.1.1 and 25.5.5.0.3 due to
`JavaLangAccess.addEnableNativeAccess(Module)` linkage incompatibility. The
GraalVM 25.5 release is based on JDK 25 and does not document JDK 27 support.
The application factory's constrained fallback had hidden this failure from
service-level tests. No dependency change was retained, and no redistribution
approval is recorded.

The optional supplemental registry binds additional original texts to exact Maven
coordinates and original resolved-artifact hashes. Packaged JARs are still checked
independently against the generated inventory: macOS packaging can transform their
bytes, so a locally packaged hash is not a portable upstream identity. The inventory
maps transformed JARs back only through a unique exact resolved-artifact filename;
the task fails closed if any packaged JAR remains ambiguous or unmatched. It records
the original Gradle artifact hash separately from the packaged-file hash. The
project's own desktop JAR and bundled JDK's `jrt-fs.jar` are recorded as such rather
than misclassified as third-party modules. Initial
evidence covers H2 2.5.252 (proposed MPL-2.0), Logback 1.6.6 (proposed EPL-2.0),
and JNA 5.19.1 (proposed Apache-2.0),
following the documented audit choices. Texts come from pinned upstream commits;
the full EPL text comes from the Eclipse Foundation and is hash-pinned. Git disables
line-ending conversion for this directory to retain original bytes on Windows too.
These are proposed application-library choices, not clearance of embedded native
code. The registry keeps explicit pending work and cannot approve redistribution.
H2/MPL and Logback/EPL source availability and binary-distribution statements remain
to be included; JNA's embedded libffi/native payload needs its own notice review.
The pinned JNA source tree contains libffi as a subtree. Its original runtime
license is now retained separately as `jna-5.19.1/libffi-LICENSE`; JNA's Apache-2.0
option does not replace this notice. The upstream `native/libffi/LICENSE-BUILDTOOLS`
separately describes tooling/test code, so do not infer that those tools are
embedded in the runtime, or that the runtime license alone covers a complete
source-tree distribution. Exact native binary/build mapping and source-specific
attribution remain unreviewed. Retaining this text does not grant clearance.

Skiko AWT 0.150.2 now has its original Apache license and Android-derived-code
NOTICE retained from pinned upstream commit `caed3841b8725429bb9b9805ff162a40eb7bce47`.
This covers additional wrapper evidence, not blanket native-runtime clearance.
That source pins Skia `m150-a3f509bf52` and ANGLE `ec4d8f8e4d`; applicable native
third-party notices and packaging transformations remain separate review items.
The pinned Skiko `build.gradle.kts` declares static Skia libraries including
`skia`, `svg`, `skparagraph`, `skshaper`, ICU, HarfBuzz, PNG/JPEG/WebP, zlib,
expat, and Skottie; it additionally declares `piex`/`dng_sdk` for macOS and
`d3d12allocator` for Windows. Direct symbol inspection of the Skiko 0.150.2
Linux x64 and macOS runtime binaries found DNG SDK, Piex, and Wuffs symbols,
while the stripped Windows binary leaves DNG SDK/Piex presence unknown. See the
registry for artifact hashes, exact upstream revisions, and remaining work.
Build declarations do not substitute for binary inspection or establish
source-delivery obligations.

## Packaging Curated Materials

Native images include the retained upstream directory at
`app-resources/legal/third-party/` (the app-resources location is platform-specific).
Gradle's built-in `Sync` merges these inputs with runtime legal materials before
Compose/jpackage signs the application. No new scanner or packaging dependency is
added, and no signed output is patched. The separate project MIT license remains
under `legal/visual-agent/LICENSE`.

Both standalone-server and desktop executable JARs include the curated directory
at `META-INF/visual-agent/legal/third-party/` and the project license at
`META-INF/visual-agent/legal/visual-agent/LICENSE`. These JARs do not contain a JDK,
so runtime legal material is confined to native packages that actually ship one.

`verifyPackages` verifies byte equality for every curated input and the project
license in the real native image and both executable JARs. Missing, changed,
duplicate JAR entries, or linked/oversized files fail verification. This checks
retention of the current input set, not its legal completeness or validity.
The generated evidence report and full remaining dependency/native/source review
are not replaced by these initial curated files. Publication remains blocked.
