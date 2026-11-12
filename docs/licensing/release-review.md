# Beta Redistribution Review

Refs #410 and UC 0000155. Review started on 2026-11-09 by Codex at the
maintainer's explicit request. This is an attributed, AI-assisted engineering
review of license conditions and distribution evidence, not an external legal
opinion or an OpenChain certification. Do not attribute this review to a human
or organization that has not performed it.

## Exact Candidate

- Source: `4163037e6b17d4d973f425726ab31208e017b881`.
- Version input: `v0.1.0`; no Git tag was created or moved.
- Package workflow: [42651138364](https://github.com/heckenmann/visual-agent/actions/runs/42651138364).
- This workflow builds candidates and does not publish a release.
- Final package and archive hashes have not yet been reviewed. The approval
  registry remains unchanged until that review succeeds.

The branch was rebased onto newer dependencies before this review. In
particular, GraalJS is now `25.5.5.0.3`, while the retained Graal supplements
identify `25.4.4.1.1`. The older artifact hashes cannot approve the new graph.
Spring Boot, Spring AI, and CMP Clipboard versions also changed; their actual
selected runtime dependencies must be checked in the new package inventories.

### Latest Non-Publishing Candidate Evidence (PR #533)

PR head `e4f0e32a1de6a6793d1958733a023b2e958d03ef` passed the complete CI
matrix: Gradle tests, package and launch, release-workflow contracts, CodeQL,
and four platform license inventories in workflow
[`43135760618`](https://github.com/heckenmann/visual-agent/actions/runs/43135760618)
and inventory workflow
[`43135760520`](https://github.com/heckenmann/visual-agent/actions/runs/43135760520).
This is a technical candidate run for the open PR, not a versioned release
candidate and not redistribution approval.

The fresh inventory manifests contain 263 components each for Linux x64,
Windows x64, and macOS ARM64, and 261 for macOS x64. Every component remains
`unreviewed`, and all four manifests set `redistributionApproved` to `false`.
The collector retained package notices for 136 components on every platform;
127 components on Linux, Windows, and macOS ARM64 and 125 on macOS x64 have no
notice detected in the packaged artifact. Of these, 66 per platform have no
curated supplement. The prior 68-item set included both Kotlinx IO 0.9.1
coordinates; the exact upstream NOTICE is now captured as a hash-bound
supplement in every platform inventory. The remaining 66 are concrete triage
items, not proof that each component has a missing legal notice; applicable
upstream license and NOTICE scope still require component-level review. The
other no-detected entries have supplements, but those records also remain
explicitly unreviewed.

The same manifests include 241-243 bundled-JDK legal files per platform; the
runtime legal section is also `unreviewed`. The Linux provenance report records
Zulu `27.0.1.0.3+1`, its exact source and packaged module lists, and hashes for
both lists. Package-generation and inventory success verify technical
packaging and provenance capture only. They do not resolve the outstanding
Apache NOTICE, GraalJS signing, Skia/ANGLE, AppImage source/relink, JDK-module,
and final recipient-archive reviews described below. No approval registry
entry or release tag was changed.

### Kotlinx IO 0.9.1 Upstream NOTICE Evidence

The exact upstream tag resolves to commit
`362bdc35e159bad6da1e08101c8321792d762281`, whose root `NOTICE` identifies
itself as the Apache License section 4(d) notice for kotlinx-io. The published
source JARs and packaged binaries contain no `NOTICE` entry for
`kotlinx-io-bytestring-jvm:0.9.1` or `kotlinx-io-core-jvm:0.9.1`. Its original
486-byte content is retained at
`docs/licensing/third-party/kotlinx-io-0.9.1/NOTICE` with SHA-256
`a126623d2c0c1ca21814828749243869ae2c44c7f6a56ef6074bb87f430b659e` and is
mapped to both resolved source-artifact hashes in `sources.json`. All four
platform inventories resolve those same source hashes; macOS package signing
changes the packaged JAR hashes, which the notice collector records separately
for each packaged artifact. All four candidate inventory manifests contain the
supplement under the corresponding actual package hashes.

This closes the prior evidence-collection gap for those two coordinates only.
The NOTICE attribution scope and inclusion in the final recipient-facing legal
archive remain unreviewed; redistribution approval remains false.

### Verified Graal Evidence Gap and Local Correction

The Linux notice manifest from inventory workflow `42649987764` contains 14
Graal `25.5.5.0.3` artifacts. Each has `no-detected-notice` and no matching
supplement. This is a concrete missing-material mapping, not a conclusion based
only on a POM license name. The corresponding Linux, macOS ARM64, and Windows
inventories provide the original JAR hashes; the macOS isolate's packaged hash
differs from its original hash, so its signing review remains necessary.

The local registry now also retains the 16 current-version coordinates across
those platforms, without deleting the historical records. The current official
Graal release's UPL text is byte-identical to the retained original; its SHA-256
is recorded in the new entry, avoiding a duplicate license file. The current
shadowed ICU source JAR has SHA-256
`485f54aaa23f47224ae0fe8c12c622314dfb46a0ca93f5c3d7e2683281c612d6`;
`VersionInfo.java` identifies ICU 78.2 and Unicode 17.0, matching the retained
original ICU license. A version-specific GFTC reference covers the three
isolate platform coordinates without asserting that macOS re-signing is allowed.

The language implementation separately retains its own original
`LICENSE_GRAALJS` and `THIRD_PARTY_LICENSE_GRAALJS.txt` from the exact upstream
release commit. These contain Oracle, Lucent, V8, Unicode, and other applicable
notices that a generic SDK UPL copy cannot replace. Its exact-version source
archive is now included through the existing Gradle runtime source-variant
collector, with recipient instructions and hash recorded in
`source-delivery.md`. This does not imply that the native isolate is UPL-licensed.

The catalog-version regression test and supplemental-material integrity tests
pass for these corrections. These local changes are not yet in the candidate
identified above. Therefore that candidate cannot be approved as if it already
contained them; final package verification must use the subsequent corrected
source commit and its own exact artifact hashes.

## Component Decisions

### ANGLE Runtime Dependency Scope

The pinned [JetBrains angle-pack recipe](https://github.com/JetBrains/angle-pack/blob/0a5e6ab42f7c4759b7f3ae18963327e3e574995/script/build.py)
builds only libEGL and libGLESv2, with Vulkan, GL, null backend, RapidJSON, and
component builds disabled. This does not justify collecting every test or
development dependency from ANGLE's DEPS file as a shipped component.

The pinned ANGLE BUILD.gn nevertheless includes compression and xxHash.
Its Abseil switch is `angle_has_build && is_clang`; final compiler settings
must therefore be checked rather than assuming Abseil is absent on Windows.
Original notices are now retained for bundled xxHash, DEPS-pinned Abseil
`2401283880a451e9ea10787920117fb67695abb4`, and zlib
`e8ff3f35551275330696606d7fef3a2a0f73a831`. The compression wrapper explicitly
refers to Chromium's BSD license, so that license is retained separately at
the DEPS-pinned Chromium revision, not replaced by zlib's license.

These permissive notices do not themselves require publishing Visual Agent
source or a bespoke relinking framework. This scoped assessment is not final
clearance: Khronos header attribution, source-file-specific notices, actual
linked dependencies, and both final DLL payloads still require verification.

### Cross-Platform Project License Integrity

The baseline candidate built all ten application artifacts successfully and all
package smoke tests passed. Its AppImage source recipe also built successfully
as a static x86_64 executable. However, legal/source bundle assembly failed:
the Windows manifest's project-license hash did not match the Linux bundle.

The repository license has SHA-256
`b71992bd03e1d5c31b6cef45c9a5a267b322f9327df7dfa8e2f116292b209bb3`.
Windows recorded
`bcc8db08af3ffd38991785caa46c840e2e2e3f0a569248d6e51a4350304a7422`,
which exactly matches a CRLF checkout of those same license contents. The
other three platform inventories match the repository bytes. The local fix
extends the existing byte-preservation Git attributes to the project license.
[Git documents `-text`](https://git-scm.com/docs/gitattributes) as disabling
checkout/check-in line-ending conversion. The integrity checker remains strict.

A regression test uses a real isolated Git index and Windows-style CRLF
settings. An ordinary control file converts to CRLF, while the project license
must preserve its original bytes. The test fails against the previous
attributes and passes with the fix. A fresh candidate is still required to
prove corrected Windows output and successful legal/source archive assembly.

### H2 2.5.252: MPL-2.0

H2 explicitly offers MPL-2.0 or EPL-1.0. This distribution uses MPL-2.0.
[MPL sections 3.1, 3.2, and 3.4](https://www.mozilla.org/en-US/MPL/2.0/)
require source availability, recipient instructions, and preservation of
applicable source notices. Section 3.3 permits a larger application under
different terms. Consequently this does not require relicensing the separate
Visual Agent code under MPL.

The retained `h2-2.5.252` materials include the upstream license, the
[H2-requested disclaimer](https://h2database.com/html/license.html), and an
exact-version Maven Central source URL and SHA-256. For unmodified H2,
reasonable timely source access with these instructions is the relevant
condition; a custom full-JDK-style build/relink archive is not a universal
MPL requirement. The existing exact source JAR is also collected for delivery.

**Terms assessment:** the selected route is appropriate for the documented
unmodified library. **Final verification:** check the candidate's H2 coordinate
and bytes, retained texts, source notice, and delivered source material. This
finding does not cover another H2 version or an altered H2 implementation.

### Logback Core and Classic 1.6.6: EPL-2.0

[Logback](https://logback.qos.ch/license.html) explicitly offers EPL-2.0 or
LGPL-2.1. The recipient notice selects EPL-2.0, so LGPL relinking requirements
must not be imposed merely because another POM alternative mentions LGPL.
[EPL section 3](https://www.eclipse.org/legal/epl-2.0/) requires availability of
the Program's source under EPL, an accompanying statement identifying the
source route, and preservation of applicable notices. Merely linking to the
library does not make separate application code a Modified Work under its
definition. No additional restrictive license for Logback is introduced here.

The retained `logback-1.6.6` materials include the original license notice,
EPL-2.0 text, and the exact Core/Classic source URLs and SHA-256 values. The
existing source collector also resolves both source JARs from the packaged
runtime graph.

**Terms assessment:** the documented source-and-notice route meets the identified
distribution conditions when actually delivered. **Final verification:** check
the resolved candidate versions, original notices, source statement, source
archives, and absence of downstream restrictions on the EPL-covered code.

### JNA 5.19.1: Apache-2.0 and libffi MIT Notices

The retained JNA root license permits choosing Apache-2.0 instead of LGPL.
The exact upstream
[`native/dispatch.c` header](https://github.com/java-native-access/jna/blob/1a91122853f6ab6f1fb2a4a284a6cf2ed8af0a4d/native/dispatch.c)
also explicitly offers that choice; the native dispatch layer must not be
treated as LGPL-only merely because it is native code. The existing original
libffi license is retained separately. Selecting Apache-2.0 for JNA does not
replace libffi's MIT notices or cover unrelated runtime components.

[Apache section 4](https://www.apache.org/licenses/LICENSE-2.0) requires a license
copy, applicable notices, and prominent notices of modifications. It does not
impose a general corresponding-source or relinking-kit requirement. Likewise,
the retained libffi MIT terms require the copyright and permission notice, not
a GPL-style relinking archive. GPL notices on separate build helpers do not
alone determine the runtime binary's license.

**Terms assessment:** use the documented Apache option for JNA and preserve
libffi's MIT attribution. **Final verification:** inspect packaged original
notices and native payload mapping, including any macOS re-signing/repackaging
changes and the associated modification notice. Do not infer that changed
package hashes mean the source implementation was edited, or silently ignore
the signing transformation.

### Skia Dependency Notices and License Choices

The exact Skia `a3f509bf52564dc8b27940bebe599d77ac0c4ae3` DEPS file pins the
dependency revisions now recorded in `third-party/sources.json`. The local
correction retains 14 additional original texts from those revisions, plus a
project-authored acknowledgment file. Gitiles TEXT responses were decoded to
the original file bytes; upstream wording was not reformatted or rewritten.

| Dependency | Retained material and terms assessment |
| --- | --- |
| Expat | Original COPYING; retain its permission/copyright notice. |
| HarfBuzz | Original COPYING; preserve its permissive copyright/permission terms. |
| libwebp | Original COPYING and PATENTS, keeping the BSD-style conditions and separate patent grant together. |
| libpng | Original composite LICENSE rather than a generic replacement. |
| libjpeg-turbo | Original LICENSE.md and README.ijg; the explicit Independent JPEG Group acknowledgment is added to product legal documentation. |
| zlib | Original Chromium-pinned LICENSE, distinct from the newer AppImage zlib source. Do not identify the two versions as the same component. |
| ICU | Original native ICU license at the Skia-pinned revision, distinct from the separate Graal-shadowed ICU4J record. |
| FreeType | Select FTL for core code, retain LICENSE.TXT, FTL.TXT and the BDF/PCF driver notices; add the required FreeType project credit using the pinned header's 1996-2026 copyright span. |
| D3D12MemoryAllocator | Retain the exact pinned original MIT license; this is a build-declared candidate, not proof that it occurs in every runtime. |

The non-copyleft routes identified here require notices and the stated credits;
they do not impose a general complete-source or relinking obligation. This
does not override separately licensed code, an applicable patent condition, or
the need to disclose source modifications where a selected license requires it.
In particular, keeping Skia's own BSD license is not a substitute for these
dependency notices. The existing retained DNG SDK, Piex and Wuffs texts remain
additional independent materials; Wuffs and Piex are recorded under Apache-2.0,
not an invented MIT alternative.

The regression checks all four mapped Skiko platform runtime records, their
additional original notices, pinned source revisions and explicit JPEG/FreeType
credits. Final binary/package coverage is still checked separately. An
overinclusive notice set must not be used to claim that every candidate is
actually linked on each platform. Additional ANGLE dependencies and applicable
source-file-specific notices remain part of the outstanding review.

### Adobe DNG SDK Terms

The retained Adobe DNG SDK agreement was checked against its exact pinned
upstream file. Section 1 permits use, derived software and distribution without
a royalty. Section 2 requires preservation of notices, and the original
agreement starts with the required DNG technology acknowledgment. Both are
retained unchanged. The separate documentation permission prohibits modifying
Adobe documentation; this project does not rewrite that documentation or the
agreement. Do not use Adobe/DNG trademarks to endorse Visual Agent.

Section 5 contains an additional indemnification condition for distribution in
a commercial product. This assessment uses the maintainer-confirmed current
noncommercial distribution plan; revisit that condition if the distribution
plan changes. This is not a claim that a beta or free download automatically
eliminates all contractual conditions. The agreement does not impose a general
corresponding-source requirement. Final review still verifies original notices
in the actual packages containing the DNG code; an unknown Windows symbol
table must not be treated as proof of absence.

### AppImage libfuse: LGPL-2.1 Section 6(a)

The [retained libfuse LGPL text](third-party/libfuse-3.15.0/LGPL2.txt) permits
the source-and-relink route: supply the library's complete source and used
changes together with the work using the library, in source and/or object
form, so recipients can modify the library and relink. A separate proprietary
object-file delivery is not required when suitable complete work source is
provided. Retain the license, the notice that the runtime uses libfuse, and
recipients' modification/debugging rights.

The retained materials include the complete libfuse 3.15.0 archive, the runtime
source, its static-link Makefile, the original mount.c patch, and the upstream
Docker build recipe. In candidate workflow `42651138364`, the dependency
installer verified the original libfuse checksum, applied that patch, rebuilt
the library, and linked a functional static runtime. Thus the existing recipe
has actually built a runtime using patched libfuse; the failed legal job must
not be reported as a failed native source build.

The local recipient guide `libfuse-3.15.0/SOURCE-AND-RELINK.txt` now explains
the supplied archives, extending the existing patch, rebuilding through the
upstream Docker script, and selecting the rebuilt runtime with appimagetool.
The Docker recipe copies the patch directory before building libfuse. The
regression test checks the instructions against these actual retained recipe
and patch files rather than inventing a second build system.

**Terms assessment:** this is the appropriate LGPL source-and-relink route for
the documented static library. The identified MIT/BSD/Zlib companion libraries
have their own notice conditions; a byte-identical historical Alpine build is
not itself a universal license condition. Do not turn historical package-version
uncertainty into an invented requirement that every permissive library needs
complete corresponding source. Preserve those provenance limitations honestly.

**Final verification:** confirm the recipient source archive actually contains
the complete described inputs and notices, and that it accompanies the exact
AppImage being reviewed. Check the prominent libfuse notice in the legal
materials. The baseline workflow failed before those final archives could be
assembled, so delivery is not yet proven and no AppImage release is approved.

The current local packaging correction replaces the previously downloaded
prebuilt runtime with a runtime built from the retained source archive by the
same Gradle task that packages the AppImage. The verifier checks the output and
copies that exact file into the package task's declared input; after packaging,
the task compares the AppImage's runtime prefix byte-for-byte with that file.
The packaging wrapper stamps the runtime with the short commit from the exact
source archive using the version format from the pinned upstream release
workflow, then verifies the reported version. The package-notice collector now
records the final AppImage hash, runtime prefix hash and size, source archive
hash and commit, and original license texts for the mapped runtime and static
components; all remain explicitly unreviewed. Gradle dry-run and local evidence
tests confirm the task wiring and evidence checks, but no Linux/Docker package
candidate has yet verified the resulting AppImage. The
upstream Dockerfile still uses unpinned Alpine package revisions;
recipient-ready source/relink delivery, exact package review, and LGPL
obligation review remain open.

The local prepared source-material directory contains all eleven archives
declared for this runtime; each archive's SHA-256 matches the registry. No
source-built Linux runtime or final AppImage package is present in this macOS
workspace, so the new package-bound manifest and final AppImage hash still need
to be produced by the Linux package workflow.

### Zulu OpenJDK Portions: Vendor Offer Pass-Through

GPLv2 section 3(c), included in the
[vendor's licensing information](https://docs.azul.com/core/tpls/october-2026/zulu27_jdk_tpl.html),
provides a specific noncommercial redistribution route for binaries received
with a qualifying source offer: retain the information received about that
offer. Supplying complete source under section 3(a) is an alternative, not a
universal additional requirement when section 3(c) is applicable. The maintainer
has confirmed this project's noncommercial distribution plan.

The exact Zulu archives' `readme.txt` files give an actual three-year,
physical-distribution-cost-limited OpenJDK source offer and a platform-specific
request code. The four inventories from workflow `42649987764` retain these
previously verified exact-build offer hashes:

| Platform | Retained vendor readme SHA-256 |
| --- | --- |
| Linux x64 | `76adb733da785cd1f257bef2cfadeed5a30178e8ac1179590b5776c860609683` |
| macOS ARM64 | `ba5a54776c2990ee75398f258223515fee4f72bf3ce888603e42fe1f5947c7a3` |
| macOS x64 | `b9748e40acbc0b4933f63e886f98ec629f7231645c0cd5caa471a01c902e85bf` |
| Windows x64 | `8d0152e9611c44e4bce41ffa06a05befe4823a5ef48b35549a62246fed538312` |

**Evidence-supported delivery route for the covered portions:** pass through
the received vendor offer for OpenJDK portions under GPLv2 with the Classpath
Exception, preserving the original notice, request code, GPL text, and
exception. The exact non-publishing candidate contains the original
platform-specific offer in all six package evidence trees, with hashes matching
the received archives. Given the maintainer-confirmed noncommercial
distribution, the evidence supports GPLv2 section 3(c) for those covered
portions; a new project source offer is not required for them. This does not
clear separately licensed JDK components or establish that every such component
is within Azul's offer. The incomplete Java-only source package must not be
presented as complete native JDK source.

This selection is deliberately scoped. The readme's OpenJDK offer does not by
itself classify every separately licensed native component in the JDK. Those
must be mapped to the actual retained runtime modules and applicable vendor
notices. In particular, a vendor-wide TPL entry is not proof that an optional
component is shipped. The project's native build selects an explicit module
list; its inspected local runtime has no `com.azul.warp.sidecar` module. That
local observation is not substituted for the exact CI runtime's module list.
Final review must also cover the jlink and signing transformations and retain
the relevant installation/build instructions. No whole-runtime clearance is
claimed from the offer hash alone.

Eclipse Temurin 27 was evaluated as a possible vendor replacement. Adoptium's
[platform announcement](https://adoptium.net/news/2026/09/jdk27-macos-x64-removal)
states that JDK 27 is not published for macOS x64. It is therefore not a
drop-in replacement for the current four-platform JDK 27 build matrix; using
multiple JDK vendors would require separate package and runtime-legal review
per platform. The current evidence does not support switching vendors as a
release shortcut.

The retained platform manifests were inspected directly: their runtimeLegal
lists describe the complete source-JDK legal tree, including javadoc and other
modules not explicitly selected by the application. The preparation task copies
that source tree intentionally. These paths are evidence of supplied notices,
not proof of which modules or native libraries survived jlink. The packaged-
artifact CSV primarily maps application JARs and does not fill this gap. The
runtime provenance report now records both source and packaged `MODULES` lists
from each JDK image's `release` file, alongside hashes of `release` and
`lib/modules`; this makes the selected modules reviewable without retaining the
JDK image or adding another scanner. Follow-up inventory workflow
[`42873741012`](https://github.com/heckenmann/visual-agent/actions/runs/42873741012)
for PR head `590d94c1c23a99c3e53fde2a411946772027e7f7` passed on all four
platforms. Every report records the exact source runtime version
`27.0.1.0.3+1`, non-empty source and packaged module lists, and their module
hashes; `java.base` is present in both lists for every platform. The four
reports still identify 96 no-detected-notice/no-supplement entries apiece: 95
Apache-2.0 metadata variants and the separately mapped `jrt-fs` entry. The
follow-up inventory verifies module capture, not the separate mapping of every
native library. Do not infer linkage from a legal-tree filename.

### Apache Notice Source-Archive Triage (2026-11-11)

The exact Linux AppImage manifest from the non-publishing candidate workflow
[`43064376109`](https://github.com/heckenmann/visual-agent/actions/runs/43064376109)
contains 95 Maven coordinates with no notice detected in the packaged JAR and
no curated supplement; the 96th such entry is the separately mapped JDK
`jrt-fs` module. Each Maven row reports at least one Apache-2.0 license option,
although some POM metadata has additional or empty license entries that still
need reconciliation.

For triage, exact-version Maven `-sources.jar` availability and archive paths
were inspected for all 95 coordinates. Ninety-three were retrieved from Maven
Central; the AndroidX `core-common:2.2.0` source archive was retrieved from
Google's Maven repository. None of those 94 source archives contained a path
with `NOTICE` in its name. The remaining
`listenablefuture:9999.0-empty-to-avoid-conflict-with-guava` binary is a
2,199-byte metadata-only JAR and has no source JAR. No source archive
contained a project-level NOTICE file; the Compose Material Icons archive
contains generated `Copyright.kt` source filenames, not a NOTICE file.

This is a repeatable source-archive filename triage, not a review of each
upstream repository's release contents and not a legal conclusion that no
NOTICE obligation applies. The repository-level NOTICE status, the special
`listenablefuture` attribution, and the additional/empty POM license entries
remain open for component-by-component review. The canonical Apache license
text is present, but its presence alone does not resolve those items. The
candidate manifests remain `unreviewed` and `redistributionApproved: false`.

## Remaining Decisions Before Exact-Hash Approval

### Candidate Inventory: Unreviewed Permissive Dependencies

The four platform inventory artifacts for PR head
`b8f2204316d4cb41ce7a386bb09d4de2f1323b5d` (workflow `42678989326`) were
downloaded and inspected. Each platform report contains 110 packaged dependency
coordinates with `noticeStatus: no-detected-notice` and no curated supplement.
109 have declared-license metadata; one is the bundled JDK `jrt-fs` entry
delegated to `runtimeLegal`, without a module-specific link in this manifest.
97 rows mention Apache-2.0, 9 mention MIT, and 5 mention BSD; two rows declare
more than one option, so these counts overlap. A common Apache-2.0 text is
already retained, but that alone does not establish applicable project-specific
notices.

Fourteen distinct coordinates declared MIT and/or BSD terms without any
package-detected or curated license text in that captured workflow:
`org.antlr:ST4:4.3.4`,
`org.codehaus.mojo:animal-sniffer-annotations:1.27`,
`org.antlr:antlr-runtime:3.5.3`, `org.antlr:antlr4-runtime:4.13.1`,
`org.ow2.asm:asm:9.11.1`, `org.nibor.autolink:autolink:0.12.0`,
`com.github.hypfvieh:dbus-java-core:5.2.2`,
`com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.2.2`,
`io.github.vinceglb:filekit-core-jvm:0.17.0`,
`io.github.vinceglb:filekit-dialogs-compose-jvm:0.17.0`,
`io.github.vinceglb:filekit-dialogs-jvm:0.17.0`, `com.knuddels:jtokkit:1.1.0`,
`com.google.protobuf:protobuf-java:4.36.2`, and
`org.reactivestreams:reactive-streams:1.0.4`. These should be checked against
the exact upstream artifacts for the required copyright/permission notices and
any existing NOTICE content. In the current worktree, original license files
from the corresponding release tags have now been retained and mapped by the
captured artifact hashes. The ANTLR 3 runtime also has distinct copyright
headers extracted from its exact published source JAR. Regression tests verify
the material hashes, source coordinates, and that these records remain
unreviewed; the registry still cannot approve redistribution.

Fresh four-platform inventories for PR head
`ac7492c09bd9ec9cd8009d60383f897523d8767b` (workflow `42688634430`) now show
the 14 candidate coordinates with their hash-matched supplements in every
platform manifest. The manifests contain 265 components on Linux, Windows, and
macOS arm64, and 263 on macOS x64 because the platform dependency graph differs.
They contain 35, 35, 35, and 33 supplemented component entries respectively;
the variation is also platform-specific. Each platform still has 96 entries
with neither a detected archive notice nor a curated supplement. Those entries
are unreviewed candidates, not a determination that every one has a missing
legal notice. The exact four manifests classify 95 of those entries as variants
of Apache-2.0 metadata and one as the separately mapped bundled-JDK `jrt-fs`.
The inventory also retains Apache 2.0 license text from Spring Framework
artifacts. A canonical copy is now additionally retained as
`third-party/Apache-2.0.txt`, pinned by SHA-256 and the Apache Foundation's
official URL in `third-party/sources.json`, and is copied by the existing
curated-material packaging paths. This covers delivery of the common license
text, but it does not establish whether any particular upstream work carries a
`NOTICE` with additional attributions. That obligation still needs review
against the relevant upstream materials. All four manifests keep
`redistributionApproved` false.

The JDK entry is now better mapped. Each fresh platform manifest identifies
`bundled-jdk:27:jrt-fs` at `lib/jrt-fs.jar` and records the matching Azul
`source-jdk.properties` and vendor `readme.txt` in the recipient package. The
readme offers, for three years, a machine-readable copy of the OpenJDK-based
source portions on request and includes a version code. This is concrete
pass-through evidence for the exact runtime build, not proof that the offer's
legal applicability has been independently approved. The OpenJDK 27.0.1
update-branch [`JrtfsJar.gmk`](https://github.com/openjdk/jdk27u/blob/53eadcb2b7896ba55b86bcf5378cb3fab612063c/make/JrtfsJar.gmk)
build rule compiles `jrt-fs.jar` from `src/java.base/share/classes`, including
the `jdk/internal/jimage` and `jdk/internal/jrtfs` packages; the shipped runtime
also carries the `java.base` legal files. This supports mapping the compatibility
JAR to the `java.base` source/license material rather than treating it as an
unidentified standalone module. Retain the vendor offer unchanged in every
recipient package and verify its terms against the exact transformed runtime
before relying on the noncommercial GPLv2 section 3(c) route.

The exact published POMs resolve two previously open metadata conflicts:
Animal Sniffer Annotations 1.27 inherits the MIT-only `animal-sniffer-parent`
1.27 POM, and ASM 9.11.1's artifact POM declares only BSD-3-Clause. The extra
Apache alternatives in the generated Gradle report are not supported by those
exact artifact POMs. Their pinned original license texts are retained and the
candidate inventories include the hash-matched supplements. The final release
files still need hash-bound review. This is not final clearance: the ANTLR 3
notice set needs confirmation against the distributed binary, and the upstream
`NOTICE` status for Apache-licensed dependencies requires obligation-by-
obligation review. The full native/runtime, source-delivery, and final
recipient-package review remains open.

### Exact Candidate Archive Audit (2026-11-11)

I downloaded the source and legal archive artifacts from candidate workflow
[`43064376109`](https://github.com/heckenmann/visual-agent/actions/runs/43064376109)
and inspected their ZIP contents. The corresponding-sources archive is
126,288,266 bytes, SHA-256
`9965c5b2c42af322b45b93f588367012c1a417a6294473c03c1c37ddb2d2ac50`, and
contains 89 files. It includes the pinned AppImage runtime, libfuse, squashfuse,
musl, mimalloc, zlib and zstd source/package inputs; selected Maven source JARs;
the Zulu source-package candidate; and legal/source scope documents. This
confirms archive contents and integrity, not completeness of corresponding
source or applicability of any license route.

The legal archive's six platform manifests contain 263–265 component records
each. Every component and runtime-legal section is explicitly `unreviewed`, all
six set `redistributionApproved: false`, and each has 96 components with no
detected package notice or curated supplement. The archive is review evidence,
not a release-ready legal bundle. Those 96 entries, the remaining
component-specific review questions, and exact final recipient-package review
still prevent recording release clearance.

The same exact source archive was revalidated in non-publishing candidate run
[`43114821781`](https://github.com/heckenmann/visual-agent/actions/runs/43114821781)
for commit `e250bb1d244fa31c4620e4cfa09385fdd74895a4`. All platform package
builds and smoke tests completed successfully, and the legal/source-material
job succeeded. The source archive retained the same size, entry count, and
SHA-256. This revalidates technical packaging only; the inventory statuses
remain unreviewed and no redistribution approval was recorded.

### Source-JAR NOTICE Scan Limitation (2026-11-12)

The Linux notice artifact from run `43114821781` contains 265 component records;
129 have no notice detected in the packaged JAR and no matching supplement in
that run. Of those, 99 carry Apache-2.0 in their generated license metadata.
The exact-version Maven `sources` classifier was available for 97 of those
coordinates from Maven Central, and none of those source JARs contained a file
named `NOTICE` or `NOTICE.*`. The AndroidX `core-common:2.2.0` source JAR was
also retrieved from Google's Maven repository and contained no such file. The
remaining Guava `listenablefuture:9999.0-empty-to-avoid-conflict-with-guava`
artifact is an empty conflict-avoidance artifact; its packaged JAR contains no
notice file.

This scan does **not** establish that the upstream projects have no root-level
NOTICE. The gRPC and Netty root notices documented below are concrete examples
of notices absent from published source JARs. Therefore, source-JAR absence is
not a basis for closing these candidates. The 99 entries still need their
upstream distribution/repository notice scope checked, and the 129-entry result
is from the public `e250bb1d` candidate before the current local supplement
changes. A new CI inventory is required to assess those changes. Apache-2.0
section 4(d) makes this distinction material: a copy of the license is always
required, while NOTICE attributions are conditional on the Work including such
a file ([official Apache license](https://www.apache.org/licenses/LICENSE-2.0.html)).

### gRPC Java v1.85.1 Upstream NOTICE

The exact [upstream `NOTICE.txt`](https://github.com/grpc/grpc-java/blob/v1.85.1/NOTICE.txt)
at commit `54c634aed4b5c78a29d0fa51d9282e9538722543` records modified OkHttp,
Envoy, protoc-gen-validate, and UDPA portions. It is absent from the nine
published gRPC source JARs; `grpc-netty-shaded` contains a narrower embedded
notice that does not include these attributions. The original root notice is
now retained unchanged and hash-bound to all nine gRPC source artifacts. All
six candidate platform manifests contain these nine modules, so the collector
will include the upstream notice with every exact packaged artifact. A
regression test checks the hash-bound mapping and leaves review status
unresolved until the notice scope and final recipient packages are reviewed.

The package inventory distinguishes the resolved binary JAR digest
(`resolvedArtifactSha256`) from the source-JAR digest recorded in `sources.json`
(`sourceArtifactSha256`). They identify different files and must not be
compared. Notice collection verifies the packaged JAR against
`packageSha256`, associates supplements by the exact Maven coordinate, and
records the source-JAR digest separately. It does not download or re-verify
source JARs during notice collection.

### Netty 4.2.20.Final Upstream NOTICE

The exact [Netty `NOTICE.txt`](https://github.com/netty/netty/blob/netty-4.2.20.Final/NOTICE.txt)
at commit `4fb6a5f308382b4bee1bff3ef80b45ea8b35166d` is absent from the
published source artifacts inspected for the 20 Netty coordinates in the six
candidate platform manifests. The original file is retained unchanged and
hash-bound to each exact source-artifact hash. The audited candidate's macOS
DNS resolver JAR contains `libnetty_resolver_dns_native_macos_aarch_64.jnilib`;
its HTTP/3 JAR contains no embedded Quiche or BoringSSL native binaries. The
current desktop build switches the GitHub update client to Spring's JDK HTTP
connector and excludes the optional native DNS resolver. The package verifier
now requires the resolver to be absent. Until newly generated platform
artifacts pass that verifier, this historical candidate finding remains open;
the APSL scope need not be resolved for a candidate that demonstrably does not
include the resolver binary.

The exact Netty 4.2.20.Final source JAR contains `netty_resolver_dns_macos.c`
and `dnsinfo.h`. The C source includes that header and calls
`dns_configuration_copy()`. The header includes both Netty's Apache-2.0 notice
and Apple's APSL-2.0 notice. Netty's public [licensing discussion](https://github.com/netty/netty/discussions/14956)
remains unresolved: maintainers note that the header is only needed to compile,
but do not determine whether the compiled native binary is affected. The source
scope remains unresolved and is retained as historical evidence; the clean
release path is to omit this optional binary and verify that all final packages
do so. Noncommercial distribution alone would not resolve the terms if the
binary were included.

The exact source JAR was inspected and its source-artifact hash is retained in
the registry. Netty's original `LICENSE.dnsinfo.txt` header notice and the full
APSL-2.0 text remain hash-checked as historical review evidence, but the
unshipped native resolver source archive is excluded from the release source
bundle. A regression test verifies the legal-text hashes, all 20 historical
Netty notice mappings, and collector output. Current candidate inventories and
final recipient-package review must confirm that the optional native resolver
is no longer distributed.

The official [GFTC](https://www.oracle.com/downloads/licenses/graal-free-license.html)
and [GraalVM FAQ](https://www.graalvm.org/faq/) were checked again. Neither
explicitly addresses replacement of a native library's macOS signature. The
Native Image output clarification does not explicitly authorize later
re-signing. A changed packaged hash alone proves changed bytes, not whether
the contractual unmodified condition is satisfied. Do not infer permission or
a prohibition solely from that hash.

The exact macOS packaging transformation is now confirmed locally. The
Compose Multiplatform 1.12.2 `AbstractJPackageTask` uses its
`MacJarSignFileCopyingProcessor` when preparing macOS JAR inputs
([pinned upstream implementation](https://github.com/JetBrains/compose-multiplatform/blob/v1.12.2/gradle-plugins/compose/src/main/kotlin/org/jetbrains/compose/desktop/application/tasks/AbstractJPackageTask.kt)).
In the generated arm64 `.app`, the embedded
`libpolyglotisolate.dylib` has an ad-hoc signature and SHA-256
`583c51584c7a5f3480a6d5bbd2791ddbe1518be6e797d5fdac539e2b542c4be7`; the
original Maven JAR's same entry has SHA-256
`ae42b10cd620aa83630f12e2d2394f8bbca5c0a5043cd444569dd4ca7c2b18f9`. The
packaged JAR itself is also not byte-identical to the Maven artifact. This is
evidence of a real native-library signing transformation, not merely an outer
bundle signature. These hashes are from a local development package, not the
final release candidate.

The narrow unresolved clarification is therefore whether the applicable GFTC
permits the Compose packager's ad-hoc re-signing of the native code embedded in
`org.graalvm.js:js-isolate-darwin-aarch64:25.5.5.0.3`, where the executable
sections and distribution terms otherwise remain unchanged. The official
GFTC does not expressly answer this. Obtain an applicable Oracle clarification
or qualified assessment, and verify the same transformation in the final
candidate, before recording this component's clearance. No inquiry has been
submitted on the maintainer's behalf.

1. Inspect all candidate package groups and legal/source archives, not only
   application tests or the earlier platform inventories.
2. Update the version-bound Graal evidence for the selected graph, including
   actual platform artifacts, Oracle GFTC conditions where applicable, and
   macOS signing transformations.
3. Complete the native Skia/ANGLE component notice/source review and verify the
   AppImage source-and-relink materials in the final recipient archives.
4. Verify the selected Zulu offer pass-through route in the final packages and
   finish the separately licensed native-component/module mapping. Do not
   identify another vendor's sources or a Java-only archive as complete matching
   source for this runtime.
5. Record the exact reviewed files, hashes, source commit, review attribution,
   and evidence digest only after every required condition is met. A successful
   integrity check cannot substitute for these findings.

There is no requirement here to obtain a generic external release certificate.
Specific unresolved license terms still require evidence or clarification;
being a noncommercial beta does not by itself resolve them.

## Local Validation of Prepared Corrections

After the ANGLE notice and CI-test corrections, the complete Gradle quality
gate passed again (3m 50s), followed by package verification (48s). Direct
byte comparisons confirm that both executable development JARs and the native
macOS app retain all four new ANGLE dependency notices unchanged. These are
local development artifacts, not the final versioned cross-platform candidate.

The baseline PR's release-contract job failed because its real-project input
tracking test forced offline resolution of the desktop runtime classpath.
The runner lacked the newly selected Spring Boot RC2 and clipboard artifacts;
preparing legal resources alone does not resolve that full classpath. The test
now permits normal dependency resolution, like the build it inspects, while
retaining all assertions. Synthetic dependency-free fixtures remain offline.

- The complete `./gradlew ktlintCheck check test --no-daemon` gate passes.
- `./gradlew verifyPackages --no-daemon` passes for both executable JARs and
  the native application launcher.
- The complete 150-test release-script run passes, including the corrected
  real-project dependency-resolution path. The subsequently extended 23-test
  supplement class also passes, including the ANGLE revision/notice regression.
- All 54 Graal artifact rows from the four current platform inventories match
  their registered original-artifact hashes.
- The rebuilt server JAR, desktop JAR, and local native application contain all
  four new GraalJS legal/source notices with their registered original bytes.
- Temporary artifact-download directories were removed. Only small generated
  inventory metadata remains in ignored build reports; no downloaded runtimes
  or source archives were added to Git for this review.

These checks validate the prepared corrections, not the outstanding final
release decision. They do not substitute local development JDK/package hashes
for the exact CI candidate runtime and final download hashes.

## ORT Tooling Assessment (2026-11-12)

The latest official release is ORT 98.0.0 (2026-11-05). The official
documentation confirms support for Gradle dependency analysis, source download,
source scanning through configured scanners such as ScanCode, policy
evaluation, and reports including SPDX and license/copyright attributions. Its
binary distribution requires Java 25 or later, compatible with this project's
JDK 27 environment. ORT is Apache-2.0 licensed.

ORT could replace or consolidate parts of dependency metadata collection,
source retrieval, scanning, and report generation. It does not eliminate the
need to inspect the exact delivered JARs, native installers, AppImage runtime,
and bundled JDK: those are package-content and platform-runtime facts, not
just the Gradle dependency graph. Nor does an analyzer, scanner, or generated
report make the final legal determination. Exact-artifact inventory and
package-bound verification therefore remain necessary for this release.

Decision: do not introduce ORT as a new release dependency during this closure
effort. First complete the already collected component-level review and
recipient-material gaps with the current evidence. Evaluate migration to ORT
afterward as a separate maintenance change, retaining the exact package
inventory checks that ORT does not replace. This avoids adding a new toolchain
and scan configuration without removing the release's principal native/package
verification work.

References: [ORT releases](https://github.com/oss-review-toolkit/ort/releases),
[ORT introduction](https://oss-review-toolkit.org/ort/docs/intro),
[Gradle analyzer](https://oss-review-toolkit.org/ort/docs/plugins/package-managers/Gradle),
[downloader](https://oss-review-toolkit.org/ort/docs/tools/downloader),
[scanner](https://oss-review-toolkit.org/ort/docs/tools/scanner),
[reporter](https://oss-review-toolkit.org/ort/docs/tools/reporter), and
[official ORT repository](https://github.com/oss-review-toolkit/ort).
