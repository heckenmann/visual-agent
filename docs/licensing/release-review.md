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

**Selected route for assessment:** pass through the received vendor offer for
the OpenJDK portions under GPLv2 with the Classpath Exception, preserving the
original notice, request code, GPL text, and exception. Do not issue a new
project source offer that the project cannot fulfill. The incomplete Java-only
source package must not be presented as complete native JDK source.

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

The retained platform manifests were inspected directly: their runtimeLegal
lists describe the complete source-JDK legal tree, including javadoc and other
modules not explicitly selected by the application. The preparation task copies
that source tree intentionally. These paths are therefore evidence of supplied
notices, not proof of which modules or native libraries survived jlink. The
packaged-artifact CSV primarily maps application JARs and does not fill this gap.
For the final candidate, inspect each packaged runtime's own `release` MODULES
field (or its `bin/java --list-modules` output) and native-library inventory.
Do not introduce another scanner or infer linkage from a notice filename.

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

This is an evidence improvement, not final clearance. The exact applicability
of the multiple metadata choices for Animal Sniffer and ASM remains unresolved,
the ANTLR 3 notice set needs confirmation against the distributed binary, and
the captured package manifests predate these newly added materials. Build and
inspect fresh packages before claiming that recipients receive them. The JDK
`jrt-fs` mapping and the much larger set of other components without
package-detected notices also remain part of the full obligation review.

The official [GFTC](https://www.oracle.com/downloads/licenses/graal-free-license.html)
and [GraalVM FAQ](https://www.graalvm.org/faq/) were checked again. Neither
explicitly addresses replacement of a native library's macOS signature. The
Native Image output clarification does not explicitly authorize later
re-signing. A changed packaged hash alone proves changed bytes, not whether
the contractual unmodified condition is satisfied. Do not infer permission or
a prohibition solely from that hash.

The narrow unresolved clarification is: may a recipient redistribute
`org.graalvm.js:js-isolate-darwin-aarch64:25.5.5.0.3` with only its macOS code
signature replaced during standard application packaging, while executable
code, required notices, and no-fee distribution remain unchanged? Obtain an
applicable Oracle clarification or qualified assessment before recording this
component's clearance. No inquiry has been submitted on the maintainer's behalf.

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
