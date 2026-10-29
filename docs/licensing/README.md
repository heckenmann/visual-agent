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

CI currently uses Zulu Community Availability JDK 27, not Subscriber Availability.
The MIT project remains independent of the runtime. GPLv2 plus Classpath Exception
does not waive runtime notices or corresponding-source obligations. See the
[exact Zulu 27 terms](https://docs.azul.com/core/tpls/october-2026/zulu27_jdk_tpl.html)
and [CA/SA distinction](https://support.azul.com/hc/en-us/articles/360051512551-What-s-the-difference-between-CA-and-SA-bundles-of-Azul-Zulu-Builds-of-OpenJDK).
Changing to Temurin would not remove those obligations.
