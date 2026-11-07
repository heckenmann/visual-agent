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

This gate is the initial safety boundary, not completion of #410. Bundle generation,
artifact-level license/source review, and package-content checks remain open.
AboutLibraries was assessed on klibs.io (15.3.0, Apache-2.0, Compose 1.12 support);
the existing maintained Gradle License Report 3.1.4 remains the metadata inventory
tool. Neither scanner alone covers all native redistribution obligations.

## Retained Runtime Inputs

`./gradlew :desktop:prepareRuntimeLegalMaterials` collects original source-JDK legal
and vendor readme/notice files through a cacheable build task. The native distribution
includes them under the common app-resources `legal/java-runtime` directory, using
[Compose's supported app-resources mechanism](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html).
The project's MIT license is separate under `legal/visual-agent`.

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

CI currently uses Zulu Community Availability JDK 27, not Subscriber Availability.
The MIT project remains independent of the runtime. GPLv2 plus Classpath Exception
does not waive runtime notices or corresponding-source obligations. See the
[exact Zulu 27 terms](https://docs.azul.com/core/tpls/october-2026/zulu27_jdk_tpl.html)
and [CA/SA distinction](https://support.azul.com/hc/en-us/articles/360051512551-What-s-the-difference-between-CA-and-SA-bundles-of-Azul-Zulu-Builds-of-OpenJDK).
Changing to Temurin would not remove those obligations.

## Original Third-Party Notices

After `:desktop:generateDistributionArtifactInventory`, collect the original texts:

```bash
python3 scripts/collect-third-party-notices.py \
  --package-root modules/desktop/build/compose/binaries/main/app \
  --inventory modules/desktop/build/reports/dependency-license/packaged-artifacts.csv \
  --output build/reports/dependency-license/third-party-notices \
  --supplements docs/licensing/third-party/sources.json
```

Use a new or empty output directory. The collector verifies actual packaged JAR
hashes, retains original bytes, and records a hash-addressed manifest. The platform
inventory workflow uploads this directory for review. Components without detected
notices remain listed; every component remains `unreviewed`, even if texts exist.
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

The optional supplemental registry binds additional original texts to exact Maven
coordinates and original resolved-artifact hashes. Packaged JARs are still checked
independently against the generated inventory: macOS packaging can transform their
bytes, so a locally packaged hash is not a portable upstream identity. Initial
evidence covers H2 2.5.252 (proposed MPL-2.0), Logback 1.6.6 (proposed EPL-2.0),
and JNA 5.19.1 (proposed Apache-2.0),
following the documented audit choices. Texts come from pinned upstream commits;
the full EPL text comes from the Eclipse Foundation and is hash-pinned. Git disables
line-ending conversion for this directory to retain original bytes on Windows too.
These are proposed application-library choices, not clearance of embedded native
code. The registry keeps explicit pending work and cannot approve redistribution.
H2/MPL and Logback/EPL source availability and binary-distribution statements remain
to be included; JNA's embedded libffi/native payload needs its own notice review.

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
