# Corresponding-source delivery worklist

Refs #410. This document records verified source inputs and remaining work, not
redistribution approval. The publication gate remains closed.

## Embedded Font Asset

The packaged PDFBox 3.0.8 JAR contains
`org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf`. Its SHA-256
(`76d04c18ea243f426b7de1f3ad208e927008f961dc5945e5aad352d0dfde8ee8`) exactly
matches that font in the official Liberation Fonts 2.1.5 upstream release source
archive (archive SHA-256
`fb22c2109dcbb25202aeb6202c0a58fd2e536dbf7c72879c4bbfa2b8a9a75d60`). The
release identifies commit `4b0192046158094654e865245832c66d2104219e`, and the
upstream repository states that the font software is licensed under SIL Open
Font License 1.1. The packaged JAR's `META-INF/LICENSE` contains the Red Hat
copyright, the Reserved Font Name, and the full OFL 1.1 text; the same original
upstream `LICENSE` is retained at `third-party/liberation-fonts-2.1.5/LICENSE`
and included with packaged legal materials. The OFL permits bundling the font
with software, requires copyright/license information to accompany a distributed
font, and prohibits selling the font by itself. The asset-specific review is
recorded in `third-party/sources.json`. This resolves the font's notice/license
review only: it is not approval of the complete release package, its source
bundle, or other components. The source archive is retained as provenance and
contains the font design sources and documentation.

The checksum-pinned source archive is downloaded into the generated source
materials at `archives/liberation-fonts/liberation-fonts-2.1.5.tar.gz`; it is not
stored as a repository binary. It includes the matching `.sfd` font design source
and the upstream license. Its integrity and inclusion are covered by the release
source-material test.

## Verified Maven Source Inputs

The following exact-version source archives resolve from the same selected runtime
dependency graph used for packaging. Their source variants were verified by the
release investigation on 2026-10-31 and 2026-11-07. Hashes identify the exact
downloaded bytes, not a legal determination of completeness.

| Component | Source archive | SHA-256 |
| --- | --- | --- |
| H2 2.5.252 | [h2-2.5.252-sources.jar](https://repo.maven.apache.org/maven2/com/h2database/h2/2.5.252/h2-2.5.252-sources.jar) | `ff3d277f962df614283d0a754949eea1e4685ed645f603a590f19c5dbbdb83b3` |
| Logback Core 1.6.6 | [logback-core-1.6.6-sources.jar](https://repo.maven.apache.org/maven2/ch/qos/logback/logback-core/1.6.6/logback-core-1.6.6-sources.jar) | `2da2e76aa79f98770ffba58311b495e0e710b8bc6f46504249d039262668fe96` |
| Logback Classic 1.6.6 | [logback-classic-1.6.6-sources.jar](https://repo.maven.apache.org/maven2/ch/qos/logback/logback-classic/1.6.6/logback-classic-1.6.6-sources.jar) | `e3a8a1a00701c54f0b23c2c1427c307e9481da6c0696a7abf3a38a77322288d0` |
| JNA 5.19.1 | [jna-5.19.1-sources.jar](https://repo.maven.apache.org/maven2/net/java/dev/jna/jna/5.19.1/jna-5.19.1-sources.jar) | `0136f719ddd91d92d2368aa2c59a0c436810ac0b9c29024044383758b0e99786` |
| Skiko AWT 0.150.2 | [skiko-awt-0.150.2-sources.jar](https://repo.maven.apache.org/maven2/org/jetbrains/skiko/skiko-awt/0.150.2/skiko-awt-0.150.2-sources.jar) | `7a10e5e7ec5363ed6ea3b089fd5d619d0088e9e6c05459a33da256a95f9b1d4b` |
| GraalJS language 25.5.5.0.3 | [js-language-25.5.5.0.3-sources.jar](https://repo.maven.apache.org/maven2/org/graalvm/js/js-language/25.5.5.0.3/js-language-25.5.5.0.3-sources.jar) | `691844c58fa8efef16d550b9a7646674ca78f08751402c250ffc31857184e28b` |

The current GraalJS language source variant is collected from the selected
runtime graph alongside its original `LICENSE_GRAALJS`,
`THIRD_PARTY_LICENSE_GRAALJS.txt`, and source-availability instructions in the
curated package materials. This preserves the language implementation's own
copyright and separately licensed code notices rather than relying solely on
the SDK's generic UPL text. It is not the native isolate's source or approval
under GFTC, and does not resolve the other native-runtime obligations.

The H2 source JAR is currently present in the generated local source-material
directory with the recorded SHA-256. The resulting source archive is still
review-only and unpublished; do not present source availability as satisfied
until the exact H2 source material and its recipient instructions accompany the
release.

The H2 archive contains 847 files including Java sources and runtime resources.
The Logback Core archive contains 521 files including Java sources and its Maven
POM. File counts alone do not establish complete build inputs or license coverage.
Keep the pinned upstream commits in `third-party/sources.json` as additional
provenance; do not substitute a moving branch or a different patch version.

### Graal-Shadowed ICU4J

The exact packaged coordinate `org.graalvm.shadowed:icu4j:25.4.4.1.1` declares
`Unicode/ICU License` in the generated dependency metadata. Its packaged Maven
artifact SHA-256 is
`02f9e8a76ed2f8fa80d53054ca4632c7058354010df27c8f4e7e7443543f7e1e`; the
matching source JAR was downloaded from Maven Central and hashed as
`d9e5d5bb018f074a531bb123fe59932df0be0dd3fd377deb27e502bb080d65af`.
That source archive identifies ICU 78.2 and Unicode 17.0. The original Unicode
license from the pinned ICU 78.2 upstream commit
`f1b3db8ecd39d5b3a6eff4d5641b176c7f914dfb` is retained at
`third-party/icu4j-78.2/LICENSE` and included in generated legal evidence.
This is provenance, not independent confirmation that the shadowed binary
matches that upstream source or a legal approval. Those questions remain open
in `third-party/sources.json`.

These source archives have explicit scope limits. JNA's Maven archive contains
the Java bindings but no libffi native source tree; the retained libffi license
text is notice evidence only. Skiko's Maven archive contains Kotlin bindings,
not the native Skia/ANGLE source trees or their build inputs. Neither archive
resolves the corresponding native-component review.

The audited candidate included the Netty macOS DNS resolver. The current build
switches the GitHub update client to Spring's JDK HTTP connector and excludes
that optional native resolver from the desktop distribution. The four-platform
inventory for PR head `f886c07138912645ce0e10f579c8731e49ca22c3` confirms the
coordinate is absent from the current candidate. The source archive and Apple
Public Source License 2.0 text are retained as historical review evidence, not
as a conclusion that the former binary was APSL-covered or that its delivery
obligations were satisfied. Repeat the absence check against the final release
artifacts. The exact Maven Central
source archive used for the historical review has SHA-256
`98a33e5adbbe58cb084ed4ca61350fa0a6311f4f8e54bc8cd823a402d0609c42`. Its
version/hash provenance remains in `sources.json`; it is not included in the new
release source bundle because the corresponding native artifact is no longer a
runtime dependency.

### Historical GraalVM UPL 1.0 Runtime Artifacts

Earlier four-platform package inventories contained 12 distinct GraalVM
25.4.4.1.1 runtime artifact coordinates whose resolved metadata declares UPL
1.0. `nativebridge` was absent from the macOS x64 inventory; the other recorded
coordinates were present there. Their source-artifact hashes match the curated
records on each platform where they are packaged. The original `sdk/LICENSE.md`
is retained from the exact upstream release tag `vm-25.4.4.1.1`, which resolves
to commit `95ce1499c8c96ab7d5a6697c5b4bf42160f3b68b`, at
`third-party/graalvm-25.4.4.1.1/UPL-1.0.txt` (SHA-256
`4a9972e09a394d6a9f693d3e8343d4918e5c0c25863665320a7fd3b6dcac4571`). The
notice collector attaches it only to the exact artifact hashes recorded in
`third-party/sources.json`.

This is historical evidence, not a license selection or approval. The
`js-language` POM also declares MIT, and its alternative must be reviewed. The
platform-specific `js-isolate` artifacts from earlier candidates are separately
recorded under Oracle's GraalVM Free Terms and Conditions (GFTC); their POM
alternatives must not be treated as proof that UPL applies. The current runtime
configuration no longer requests isolate artifacts because they fail to start
on the required JDK 27 and had been silently falling back to the constrained
policy. New platform package inventories are still required to confirm the
resulting runtime graph and notices. Source-to-binary correspondence, copyright
scope, final-package presence, and redistribution terms for the current package
remain unreviewed.

### Skiko Native Renderer and Windows ANGLE Runtime

The pinned [Skiko 0.150.2 build configuration](https://github.com/JetBrains/skiko/blob/caed3841b8725429bb9b9805ff162a40eb7bce47/skiko/build.gradle.kts)
lists the static Skia libraries used for its native targets, plus macOS
`piex`/`dng_sdk` and Windows `d3d12allocator`. Inspection of the exact Skiko
0.150.2 Maven runtime binaries shows that this build declaration is not a
reliable platform-availability list: DNG SDK, Piex, and Wuffs symbol families
are present in the Linux x64 and both macOS binaries, despite the build file's
macOS-only declaration for DNG SDK and Piex. The Windows x64 DLL is stripped;
its symbol table does not establish whether DNG SDK or Piex is present, so that
platform remains unknown. The Windows binary does contain Wuffs-identifying
strings. These observations are recorded in `third-party/sources.json` and do
not establish that the current release candidates contain byte-identical
runtime payloads.

The pinned Skiko source's
[`gradle.properties`](https://github.com/JetBrains/skiko/blob/caed3841b8725429bb9b9805ff162a40eb7bce47/skiko/gradle.properties)
selects Skia `m150-a3f509bf52` and ANGLE `ec4d8f8e4d`. The exact Skia source
revision's [`DEPS`](https://github.com/JetBrains/skia/blob/m150-a3f509bf52/DEPS)
pins external source revisions, but Skia itself describes these as revisions
currently tested, not an endorsement or a definitive list of code linked into
the released binaries. The Skia project's original BSD license is retained at
`third-party/skia-m150-a3f509bf52/LICENSE`; this does not cover the individual
terms of its external libraries. The pinned `DEPS` file SHA-256 is
`a50325e3a26b81e36d37fa96d3ed9a66442b553de9c7a4fcc3da83bbfb3953ad`.

For Windows, the pinned Skiko `AdditionalRuntimeLibraries.kt` constructs the
ANGLE release URL from tag `ec4d8f8e4d` and selects only `libEGL.dll` and
`libGLESv2.dll` for its runtime JAR. The official
[angle-pack release](https://github.com/JetBrains/angle-pack/releases/tag/ec4d8f8e4d)
zip `Angle-ec4d8f8e4d-windows-Release-x64.zip` was downloaded and inspected
(SHA-256 `c7cc48c4d73f84b5aeae31103b9d144642de517ed29e74f63f39225a9217e5f6`).
It contains those two DLLs, their import libraries/PDBs, and
`d3dcompiler_47.dll`; the Skiko runtime JAR contains only the two selected ANGLE
DLLs and checksum sidecars. Both selected DLLs match the Maven artifact
`skiko-awt-runtime-angle-windows-x64:0.150.2` byte-for-byte. Its SHA-256 is
`8b09a0b92fabcbbb7cf9f61a66f684a5a4aaffc4d037e73454c35e7c5bddebbf`, and the
individual DLL hashes are recorded in the release evidence. The ANGLE source
checkout resolves `ec4d8f8e4d` to
`ec4d8f8e4d3f1a9e64430abd8b6b436253732adf`; the package build applies two
ANGLE patches stored in the pinned [`angle-pack` source commit](https://github.com/JetBrains/angle-pack/tree/0a5e6ab42f7c4759b7f3ae18963327e3e574995):
[`disable-compute-build-timestamp`](https://github.com/JetBrains/angle-pack/blob/0a5e6ab42f7c4759b7f3ae18963327e3e574995/0001-Disable-compute-build-timestamp.patch)
and [`fix-Windows-UI-Composition-SwapChain`](https://github.com/JetBrains/angle-pack/blob/0a5e6ab42f7c4759b7f3ae18963327e3e574995/0002-Fix-Windows-UI-Composition-SwapChain.patch).
The source commit's checkout script clones the Chromium ANGLE repository and
checks out the requested source revision. The exact ANGLE [`DEPS`](https://chromium.googlesource.com/angle/angle/+/ec4d8f8e4d3f1a9e64430abd8b6b436253732adf/DEPS)
file SHA-256 is `ac50b94298fe32c4a874d505837881589c8c3571f34c2f34c06379e675c0b69c`.
ANGLE's original BSD license is retained at
`third-party/angle-ec4d8f8e4d/LICENSE`.

These are source and Maven-artifact provenance findings, not proof that the
current candidate Windows installer contains those exact DLLs or that all
embedded dependencies' notices and sources are complete. The DNG SDK, Piex,
and Wuffs license texts have now been retained from the exact Skia `DEPS`
revisions: `dng_sdk` `dbe0a676450d9b8c71bf00688bb306409b779e90`, Piex
`bb217acdca1cc0c16b704669dd6f91a1b509c406`, and Wuffs
`e3f919ccfe3ef542cfc983a82146070258fb57f8`. Their records remain
`unreviewed` for final package clearance. The current
[redistribution review](release-review.md) records the DNG agreement assessment,
the selected FreeType core FTL option, and the additional original notices now
retained for Expat, HarfBuzz, libwebp, libpng, libjpeg-turbo, zlib, native ICU,
FreeType and D3D12MemoryAllocator at their Skia-pinned revisions. Explicit JPEG
and FreeType acknowledgments are included separately. These materials do not
establish that all candidates occur on every platform; complete native mapping
and final-package delivery still require verification. No whole-release
clearance is implied by this component assessment.

The Linux AppImage from package-smoke workflow run `42388152203` (source commit
`c754df8ee2b8dd0235d6ffe355c30cc1c10be017`, AppImage SHA-256
`8596fdd05feffc299febae7ddf8833b85c725fdae66b81d6a03d88e9a91f717a`) was
downloaded and its SquashFS extracted. Its `libskiko-linux-x64.so` hash is
`c733456df0b83210d9e79b90a581c4e005c28d2ef2c4cd17d146d74f7a60f378`, exactly
matching the inspected Skiko 0.150.2 Linux Maven native library. The AppImage's
application legal directory contains only H2, Logback, and JNA materials from
that earlier source commit; the later-collected DNG SDK, Piex, Wuffs, and libffi
texts are absent. This is historical smoke-build evidence, not a final release
candidate. The extracted SquashFS also does not expose the external Type 2
runtime's own static dependency obligations; those need a separate exact
runtime/package mapping.

The Windows MSI from the same historical smoke run was also downloaded and
extracted (MSI SHA-256
`f4fee660052080643573c0d356abbb74691865b101a2f6ec85f8968a9e5bfaaf`). Its
`Visual Agent/app/skiko-windows-x64.dll` SHA-256 is
`e90e2e5ae3d48c7694b6cf5948953f6d007fe40478b1040353040acef118dd41`, matching
the Windows native DLL extracted from Skiko 0.150.2 Maven runtime JAR
`eebad490c2828198e86e641cbd44958617867ce70f893b21852f07d0be099c0a`. The
Windows application legal directory likewise contains only the historical H2,
JNA, and Logback materials. The DLL is stripped; absence of a readable DNG SDK
or Piex symbol/string is not proof that either is absent. The package evidence
and unresolved mapping are recorded in `third-party/sources.json`.

The exact [JNA 5.19.1 upstream tag archive](https://codeload.github.com/java-native-access/jna/tar.gz/refs/tags/5.19.1)
was separately downloaded and inspected. Its SHA-256 is
`33a9e27b5a6eaa024bb117325392d1ccba7ca63f95d201230ad7c33d60f54c23`, and the
tag resolves to commit `1a91122853f6ab6f1fb2a4a284a6cf2ed8af0a4d`, matching the
JNA source commit recorded in `third-party/sources.json`. Unlike the Maven
`sources.jar`, this archive contains the native JNA dispatch sources, build
files, and the full `native/libffi` 3.4.4 source tree. JNA's 5.19.1 change log
records updating the embedded libffi to commit
`1f14b3fa92d4442a60233e9596ddec428a985e3c` and rebuilding its native libraries.
The archive also contains `native/libffi/LICENSE` and
`native/libffi/LICENSE-BUILDTOOLS`; the latter identifies specific bundled
build/test helpers as GPLv2. This is source-archive scope, not evidence that
those helper files are present in the packaged JNA binaries. The upstream
[license discussion](https://github.com/java-native-access/jna/issues/1691)
distinguishes build tools from Maven runtime artifacts, while JNA maintainers
have separately proposed adding libffi's copyright notice to binary
distributions in [PR #1739](https://github.com/java-native-access/jna/pull/1739).
That PR is still open. Our curated libffi notice is therefore retained, but the
complete JNA upstream archive is not yet part of a recipient-facing source
bundle. The recorded JNA Apache-2.0 and libffi MIT terms do not, on the current
evidence, make that full archive a corresponding-source release blocker. Treat
the archive as provenance/build evidence unless review of the exact shipped
payload or another applicable term establishes a source-delivery requirement.
Do not infer that the GPLv2-licensed build/test helpers in the upstream source
tree are included in JNA's packaged runtime binaries, and do not interpret
upstream scanner findings as a final license determination.

The four executable JARs from package-smoke run `42388152203` each contain the
same `jna-5.19.1.jar` bytes (SHA-256
`4fb141dd8ef6b0585ffceea4bc49602fbc6312fa977e2c488794ea3e6aafecae`), matching
the resolved Maven artifact. The Linux x64 DEB, RPM, and AppImage plus Windows
x64 MSI also contain that exact JAR hash. It carries 24 platform-dispatch
binary resources, including Linux, macOS, and Windows variants.

The pinned JNA 5.19.1 source archive (SHA-256
`33a9e27b5a6eaa024bb117325392d1ccba7ca63f95d201230ad7c33d60f54c23`) confirms
that the native dispatch sources (`dispatch.c`, `callback.c`, `dispatch.h`,
`dll-callback.c`, and `protect.h`) are dual-licensed under LGPL-2.1-or-later or
Apache-2.0. The Maven JAR retains the full `META-INF/LICENSE` and `META-INF/AL2.0`
texts, so the Apache-2.0 option and its notice are present in the redistributed
artifact. The pinned build file links the bundled libffi archive statically;
the original libffi MIT text is separately retained and included in curated
package materials. The source archive's `LICENSE-BUILDTOOLS` applies to
`msvcc.sh` and specified test helpers, not to the compiled runtime; those helper
files are not packaged in the JNA JAR or included in the recipient source
bundle. Thus the GPL build-tool text is not treated as a runtime dependency.
This source-level mapping and notice presence still require independent review
against the final release package; it does not establish overall clearance.

The native macOS package keeps the JNA JAR structure but its embedded Mach-O
resources are transformed during packaging. In the local ARM64 app image,
`darwin-aarch64` and `darwin-x86-64` resources retain source UUIDs
`BEFEF0FB-C7C5-305A-8B79-ED4851A90E7F` and
`52D255E2-288A-3E03-8844-11F6E4164F3F`; both packaged files pass code-signature
verification. In the macOS x64 DMG from run `42388152203`, the x86_64 resource
is byte-identical to the Maven input, while the arm64 resource has a different
hash but retains its source UUID. The x64-build arm64 resource is not signed.
The archive-level JNA hashes differ because of these embedded-resource changes.
The Maven source JAR contains Java bindings, not the native dispatch or libffi
source trees. The libffi license is retained. The remaining JNA review is to
map applicable attribution notices to each packaged native payload; a full
source archive is required only if an applicable license or separate agreement
requires it.

The same task also retains checksum-pinned AppImage Type 2 runtime source inputs
under `archives/appimage-runtime/`. These are source-evidence archives, not yet a
recipient-ready corresponding-source package:

| Component | Source archive | SHA-256 |
| --- | --- | --- |
| AppImage type2-runtime commit `dd6cebedcbddde9c82f89b011e8e1d40b6e43868` | [source archive](https://codeload.github.com/AppImage/type2-runtime/tar.gz/dd6cebedcbddde9c82f89b011e8e1d40b6e43868) | `f5fec23be76e50e2445ed2d018bac49b367490fc483c62c4637e99ec705d27ba` |
| libfuse 3.15.0 | [source archive](https://github.com/libfuse/libfuse/releases/download/fuse-3.15.0/fuse-3.15.0.tar.xz) | `70589cfd5e1cff7ccd6ac91c86c01be340b227285c5e200baa284e401eea2ca0` |
| squashfuse 0.5.2 | [source archive](https://github.com/vasi/squashfuse/archive/0.5.2.tar.gz) | `db0238c5981dabbd80ee09ae15387f390091668ca060a7bc38047912491443d3` |
| musl 1.2.5 upstream source | [source archive](https://musl.libc.org/releases/musl-1.2.5.tar.gz) | `a9a118bbe84d8764da0ea0d28b3ab3fae8477fc7e4085d90102b8596fc7c75e4` |
| Alpine v3.21 musl packaging recipe and patches at commit `5245849e14d213abe18d589b4b80d65793327940` | [source archive](https://github.com/alpinelinux/aports/archive/5245849e14d213abe18d589b4b80d65793327940.tar.gz) | `e63fe34178270d2d8cc74f1bab303ec1868150606e66f6da2c16221e6ec86034` |
| mimalloc 2.1.7 upstream source | [source archive](https://github.com/microsoft/mimalloc/archive/refs/tags/v2.1.7.tar.gz) | `0eed39319f139afde8515010ff59baf24de9e47ea316a315398e8027d198202d` |
| Alpine v3.21 mimalloc2 packaging recipe and patch at commit `5a1a5dc3216a69e3cd66a57ee07cb5ef01b8be39` | [source archive](https://github.com/alpinelinux/aports/archive/5a1a5dc3216a69e3cd66a57ee07cb5ef01b8be39.tar.gz) | `6f4f50fca873868e754053c828d8336a531dbca365c678dc75db8111873d87e1` |
| zstd 1.5.6 upstream source | [source archive](https://github.com/facebook/zstd/archive/v1.5.6.tar.gz) | `30f35f71c1203369dc979ecde0400ffea93c27391bfd2ac5a9715d2173d92ff7` |
| Alpine v3.21 zstd packaging recipe at commit `5c2ddf18f193dafdd25ae7391d96c7e30766eecd` | [source archive](https://github.com/alpinelinux/aports/archive/5c2ddf18f193dafdd25ae7391d96c7e30766eecd.tar.gz) | `cdc49cb68d5358eefcc83955bf285ce50fbcf6b223b271ef71e1008402c201f0` |
| zlib 1.4 upstream source | [source archive](https://www.zlib.net/fossils/zlib-1.4.tar.gz) | `02dd159783677a749c04adbdd02b55b8a2bcc7e054f51b76b549199a916ad584` |
| Alpine v3.21 zlib packaging recipe at commit `d3332621d44c2c4cf52e52217b16674db190212c` | [source archive](https://github.com/alpinelinux/aports/archive/d3332621d44c2c4cf52e52217b16674db190212c.tar.gz) | `050415c464375727d855a14abb0cdf2d549bda3b32ddc3c0017256e552b3ec09` |

The GitLab archive endpoint returned HTTP 418 from GitHub-hosted runners. These
four packaging snapshots therefore use Alpine's official GitHub mirror at the
same pinned commits; the full commit archives are retained and checksum-pinned.
These current-candidate snapshots replace earlier musl r9 and zlib 1.3.1
recipe candidates; the historical AppImage release analysis below remains
separate from the rebuilt candidate inputs.

Original legal texts have also been retained from the pinned source archives:
AppImage runtime `LICENSE`, libfuse `LICENSE` and `LGPL2.txt`, squashfuse
`LICENSE`, and musl `COPYRIGHT`. Their paths and hashes are recorded in
`third-party/sources.json` and the texts are included in generated distribution
legal resources. They document the upstream terms and attribution; they do not
prove that the archived commits or candidate Alpine packages are the exact code
linked into the expired runtime build. In particular, the libfuse LGPL text is
not a substitute for the required static-link relinking/source delivery.

The runtime source tree records its libfuse patch and pinned libfuse/squashfuse
inputs. Alpine's official [`3.21-stable` history](https://gitlab.alpinelinux.org/alpine/aports/-/commits/3.21-stable/main/musl/APKBUILD)
shows that [commit `efd4d5df7be0ea48d98512671f676fc5ceb14899`](https://gitlab.alpinelinux.org/alpine/aports/-/commit/efd4d5df7be0ea48d98512671f676fc5ceb14899)
was the latest change to `main/musl/APKBUILD` before the runtime release date. That
recipe declares musl `1.2.5-r9`, MIT, and the Alpine patch set, including
`CVE-2025-26519.patch`. This is the best available build-time package-recipe
evidence, not direct proof of the APK bytes resolved by the expired runtime
build. For mimalloc, Alpine's [v3.21 package record](https://pkgs.alpinelinux.org/package/v3.21/community/x86_64/mimalloc2)
lists `mimalloc2` `2.1.7-r0`, built 2024-07-23 from commit
`5a1a5dc3216a69e3cd66a57ee07cb5ef01b8be39`; the pinned APKBUILD declares MIT
and the upstream v2.1.7 source URL. The matching repository APK was downloaded
and its `.PKGINFO` confirms that package version, commit, and license; its SHA-256
is `fb91cf2044e28fd2e6786efa2953d3e371bf949879688da88268db2085099858`. This
supports `mimalloc2` `2.1.7-r0` as the candidate available to the 2025 runtime
build, but does not prove the expired build resolved that exact APK. Therefore
the exact musl APK bytes and the actual runtime package resolution remain
unconfirmed. The Alpine zstd recipe and package record identify `zstd-static`
`1.5.6-r2` (declared `BSD-3-Clause OR GPL-2.0-or-later`); the archived zlib
package metadata identifies `zlib-static` `1.3.1-r2` (`Zlib`). Those versions
describe the historical upstream runtime investigation, not the newer candidate
build. The retained candidate build log instead records Alpine musl-dev
`1.2.5-r12`, mimalloc2 `2.1.7-r0`, zstd-static `1.5.6-r2`, and zlib-static
`1.4-r0`; the candidate source bundle must use the matching musl r12 and zlib
1.4 sources/recipes. This does not retroactively make the earlier candidate
source archive complete. The historical package records and current candidate
inputs must not be mixed. Required notices and LGPL static-link relinking
materials remain subject to review; do not present these archives alone as
legal clearance.

The pinned runtime source archive also contains `BUILD.md`, `src/runtime/runtime.c`,
the static-link `src/runtime/Makefile`, the Docker build definition, the dependency
installer, and `patches/libfuse/mount.c.diff`. The installer checks the official
libfuse 3.15.0 source-archive hash before applying that patch and building its
static library; the runtime Makefile links `-lfuse3` with `-static -static-pie`.
Together, these files document a practical path for rebuilding the runtime with
a modified libfuse. They are stronger than a license text alone, but they do not
prove the expired CI job produced the distributed runtime from this commit or
these exact dependency packages. Under [LGPL 2.1 section 6(a)](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.en.html),
the recipient must be able to modify the library and relink the work that uses
it. Before relying on that route, the recipient bundle still needs the complete
libfuse source and changes, the runtime source (or suitable object code), all
build inputs needed for relinking, and clear instructions verified against a
working build. The [upstream build guide](https://github.com/AppImage/type2-runtime/blob/dd6cebedcbddde9c82f89b011e8e1d40b6e43868/BUILD.md)
documents its Docker and chroot approaches. The full LGPL text is retained; this
is not legal clearance.

The Linux package task builds the embedded AppImage runtime from this retained
source recipe and checks that the resulting x86_64 runtime is static and
executable before packaging it. The resulting binary is written to the Gradle
build directory and passed directly to `appimagetool`; the workflow no longer
builds and discards a separate runtime. This binds the packaged runtime to the
retained source commit. The packaging workflow also records the final AppImage
hash, verifies the runtime prefix against the source-built binary, records the
runtime/source archive hashes and commit, and retains the original license
texts for the runtime plus the mapped libfuse, squashfuse, musl, mimalloc, zstd,
and zlib inputs in the package-bound notice evidence. The evidence is marked
unreviewed and does not independently prove each static dependency's presence.
The recipe still uses unpinned Alpine package revisions and does not by itself
establish complete corresponding-source or LGPL relinking compliance. Candidate workflow
[42651138364](https://github.com/heckenmann/visual-agent/actions/runs/42651138364)
successfully completed the standalone build check for source commit
`4163037e6b17d4d973f425726ab31208e017b881`. This verifies that the current
upstream recipe builds in CI; it does not prove
the historical distributed runtime's exact linked inputs or independently
exercise a recipient's custom libfuse modification.

## Delivery Approach

`./gradlew :desktop:prepareLibrarySourceMaterials` collects the six
selected-library source archives and the checksum-pinned AppImage source archives
as partial evidence under
`modules/desktop/build/reports/dependency-license/library-source-materials/`.
It uses Gradle's [source-variant artifact view](https://docs.gradle.org/current/userguide/artifact_views.html)
on the selected desktop runtime graph, without independent version resolution.
Missing source resolution fails rather than silently skipping a component. The
directory includes original curated legal inputs and this scope statement. It is
not the complete corresponding-sources ZIP and is not published automatically.
The `Build and smoke release packages` workflow now creates deterministic
`visual-agent-legal.zip` and `visual-agent-corresponding-sources.zip` review
archives from these inputs. Both contain an explicit `REVIEW-ONLY.txt` warning
and a SHA-256 manifest; neither is represented as complete or cleared. The legal
evidence archive also contains original notices extracted from runtime JARs in
Linux x64, macOS ARM64/x64, and Windows x64 native app images, with per-platform
package-hash-bound manifests. Platform-specific native payloads still require
separate review. Both archives are workflow artifacts only.
The trusted release workflow still requires a separate review bound to the exact
commit and all twelve final release files before it can attach anything to a
public release. The four-platform `License inventory` workflow also collects
and uploads the source-material directory as review evidence, not as a public
release asset.

## Current macOS ARM64 Notice Triage

The locally generated notice manifest for the macOS ARM64 app image on 2026-11-07
contains 263 JAR records: 136 have an original in-JAR license/notice file retained,
and 127 have no such file detected. All 127 coordinates are unique. The existing
Gradle License Report contains metadata for 126 of them; the remaining entry is
the bundled JDK `jrt-fs` module, which must be reviewed against the retained source
JDK legal materials rather than Maven metadata. A missing embedded text is a
review item, not proof of a license violation. The report's POM metadata is also
not proof of complete or legally sufficient notices. The generated
`third-party-notices/manifest.json`, dependency report, and package inventory are
local build evidence and are regenerated by the documented inventory commands;
they are not checked-in clearance or a final all-platform review.

Grouping the 126 Maven-backed rows by their raw POM declarations yields 21
distinct license-name strings and 29 distinct license-URL values. These are
metadata groups, not 21 or 29 distinct legal texts: aliases, duplicate choices,
and component-specific URLs still require source inspection. All 127 rows remain
unreviewed, and this local ARM64 triage is not the final release candidate.

Use existing Gradle dependency resolution and archive tasks rather than adding a
second dependency scanner. Keep downloaded source artifacts in build outputs, not
in Git. The existing Gradle License Report remains the
metadata inventory; its reports are not a substitute for source delivery.

1. Resolve exact source classifiers for the remaining applicable packaged
   components. Match them to the actual packaged dependency coordinates, not the
   broader build/test dependency inventory. Fail on missing or changed required
   inputs.
2. Retain upstream license headers and include the applicable full license texts
   alongside each component. Inspect upstream build files and generated sources
   before deciding whether Maven source archives are sufficient.
3. Assemble the required inputs into `visual-agent-corresponding-sources.zip`
   beside the packages and `visual-agent-legal.zip`. Include a manifest identifying
   component version, upstream URL, source archive hash, and any modifications.
4. Include a recipient-facing source-availability notice in the application legal
   materials and legal bundle. Point to the actual versioned release asset only
   after that asset is assembled and checked. Do not claim an unpublished file
   is available or make a source offer the project cannot fulfill.
5. Verify source and legal bundles against the same exact package set before
   recording a genuine review in `release-approvals.json`.

## License-Specific Review

- H2's proposed option is MPL-2.0. Review sections 3.1 and 3.2, retain the original
  notices, inform recipients how to obtain the covered source, and include the
  binary liability disclaimer that H2 explicitly requests. The exact disclaimer
  example from H2's [official license FAQ](https://h2database.com/html/license.html)
  is retained separately as `third-party/h2-2.5.252/H2-DISCLAIMER.txt`, with its
  SHA-256 registered in `third-party/sources.json`; it does not replace either
  original H2 license file. A separate `SOURCE-AVAILABILITY.txt` points to the
  exact H2 2.5.252 Maven Central source JAR and its verified SHA-256. H2's FAQ
  says source need not be provided when H2 is unmodified, but the sufficiency of
  this recipient route remains subject to the final release review. Mozilla's
  [official FAQ](https://www.mozilla.org/en-US/MPL/2.0/FAQ/) specifically notes
  additional notice considerations when redistributing libraries rather than
  unchanged complete applications.
- Logback's proposed option is EPL-2.0. Section 3.1(a) requires the Program to
  be made available as Source Code and requires a statement explaining how
  recipients can obtain it. A version-specific `SOURCE-AVAILABILITY.txt` now
  links both Logback 1.6.6 source JARs from Maven Central and gives their verified
  hashes; the exact-version source JARs are also retained in generated local
  source materials. The URLs, included notice and full EPL text still require
  final recipient-route review. Review section 3.1(b)'s binary-distribution
  conditions and sections 3.2/3.3 for source licensing and retained notices. The
  [official EPL-2.0 text](https://www.eclipse.org/legal/epl-2.0/) remains
  authoritative. Do not label the whole application EPL: the project's own code
  remains separately MIT-licensed.
- The project is noncommercial by owner confirmation. This does not remove the
  source/notice requirements above; do not infer a general noncommercial waiver.

## Other Unresolved Source Inputs

### Zulu JDK 27.30.109 CA Source Offer

The pinned runtime is Zulu `27.0.1.0.3+1` / `27.30.109 CA`. The official Azul
metadata API returned the following platform packages and SHA-256 values; each
downloaded archive matched that API digest. Each package's root `readme.txt`
contains the same three-year, cost-limited source-on-request terms, plus a
platform-specific version code to include in a request:

Native packaging now requests the Azul vendor rather than any JDK 27. This is
necessary because local Gradle auto-provisioning previously selected Eclipse
Temurin while GitHub workflows install Zulu. The local Azul toolchain currently
resolved by Foojay is `27.0.1+9` / `Zulu27.30+107-CA`, not the exact CI-pinned
`27.0.1.0.3+1` / `Zulu27.30.109-CA` build. Therefore the local legal-material
run validates the retention path only; release evidence must come from the
workflow package built with its exact pinned Zulu archive and be checked against
that archive's version and provenance.

| Platform | JDK archive SHA-256 | `readme.txt` SHA-256 | Source-request version code |
| --- | --- | --- | --- |
| macOS ARM64 | `88dec2f8609e7c4162778cbaef5437f00febd3c498a7c0e77277d1f4ac8f839d` | `ba5a54776c2990ee75398f258223515fee4f72bf3ce888603e42fe1f5947c7a3` | `5e151ba7-1206-4cce-8cc4-1b268ec6dfb9` |
| macOS x64 | `88cf4b1d04ed677d88e53716fb0c960b89917a0234cea6555ba9aa5d37961b83` | `b9748e40acbc0b4933f63e886f98ec629f7231645c0cd5caa471a01c902e85bf` | `6f304e60-e3ae-4c03-83b8-9759b0969469` |
| Linux x64 | `c128817926e4eebb30868b6ba082028f493afa716e459e34049bde0ab49a9829` | `76adb733da785cd1f257bef2cfadeed5a30178e8ac1179590b5776c860609683` | `a0349d72-fd69-4fda-809e-0ca4b1477bee` |
| Windows x64 | `70e0aff2bfc1a9468ef464f97c96b295188c15f5a6f7fec3429a4b924b8b338d` | `8d0152e9611c44e4bce41ffa06a05befe4823a5ef48b35549a62246fed538312` | `e5506cdc-7af5-45dd-b122-378f01c329d9` |

The existing `prepareRuntimeLegalMaterials` task copies the selected JDK's
`readme.txt` byte-for-byte into the packaged runtime legal directory and records
its original hash. I ran that task with the actual macOS ARM64 Azul JDK; it
reported `Azul Systems, Inc.`, `Zulu27.30+109-CA`, and
`27.0.1.0.3+1`, and the retained readme matched the downloaded source byte for
byte. For macOS x64, Linux x64, and Windows x64, I separately downloaded each
archive, verified its archive digest against Azul's API, extracted only the
root readme, and confirmed the offer text and platform-specific code. This
confirms the packaging input and a recipient-visible offer-information
route. The latest non-publishing package candidate
[`43114821781`](https://github.com/heckenmann/visual-agent/actions/runs/43114821781)
was inspected directly: its legal archive contains the original 838-byte,
platform-specific Azul `vendor/readme.txt` in all six package evidence trees.
Each file's SHA-256 matches the exact platform hash above. The text identifies
OpenJDK portions under GPLv2 with the Classpath Exception and says Azul will
provide their complete machine-readable source for three years from receipt,
for no more than the physical distribution cost, on request. It includes the
vendor contact and each archive's request/version code.

The project owner confirmed that this distribution is noncommercial. The exact
candidate evidence supports the predicates stated in GPLv2 section 3(c) for
the OpenJDK portions covered by this received vendor offer: the product is
noncommercial, the exact object-code archives were received with the offer
information, and recipients receive that same original information unchanged.
A new project-authored source offer is therefore not required for those
covered portions when relying on section 3(c). This is a scoped reading of the
published license and exact package evidence, not clearance of every JDK
component. It does not settle separately licensed native components, whether
they are within the offer, or the effect of platform-specific jlink/signing
transformations; those remain in the runtime component review. No request was
sent to Azul. The supplementary Java-only source package remains non-authoritative
as complete source and is not used to broaden the vendor offer.

An Azul CA source package is also publicly available from the official APT
repository: `zulu27-ca-src` version `27.0.1.0.3-5`, described by Azul as Zulu
`27.30+109` / JDK `27.0.1.0.3-b1`. The package URL is
[`zulu27-ca-src_27.0.1.0.3-5_amd64.deb`](https://repos.azul.com/zulu/deb/pool/main/z/zulu/27/zulu27-ca-src_27.0.1.0.3-5_amd64.deb)
and its SHA-256 is
`c803bd7fa69e94c8f1a7370e00dd98212480d8648d6e27eed06c27b97bd4b6b4`, matching
the official APT package index and a fresh download. The package metadata
identifies Azul Systems, Inc. as maintainer and describes the source as
`27.0.1.0.3-b1`. Its `lib/src.zip` has SHA-256
`c7bda31ba74a5bf2772835ef754bf4a608b11f70869094e81d884d5d2d39ff14` and
contains 15,166 `.java` files. The package also contains an Azul copyright
file and Debian changelog. The checksum-pinned package is now collected by
`:desktop:prepareZuluSourcePackage` and staged by
`:desktop:prepareReleaseSourceMaterials` under
`archives/zulu-jdk/zulu27-ca-src_27.0.1.0.3-5_amd64.deb` for the generated
source-evidence archive. It is not downloaded by each platform inventory job.
This is supplementary Java-source evidence:
the package does not contain native JDK source files or the complete build
inputs, and its `27.0.1.0.3-b1` label is not by itself proof of byte-for-byte
correspondence to the `27.0.1.0.3+1` runtime archive. It is not a substitute for
reviewing the bundled offer or delivering any source required by the applicable
terms.

The four-platform License inventory run
[`42385467454`](https://github.com/heckenmann/visual-agent/actions/runs/42385467454)
provides exact-build package evidence: each platform's `runtime-provenance.properties`
records `source.java-runtime-version=27.0.1.0.3+1`, and each generated
`package-files.txt` lists the retained source-offer file at the following package
path:

| Platform | Package path |
| --- | --- |
| Linux x64 | `Visual Agent/lib/app/resources/legal/java-runtime/README.txt` |
| macOS ARM64 | `Visual Agent.app/Contents/app/resources/legal/java-runtime/README.txt` |
| macOS x64 | `Visual Agent.app/Contents/app/resources/legal/java-runtime/README.txt` |
| Windows x64 | `Visual Agent/app/resources/legal/java-runtime/README.txt` |

This verifies the exact source JDK version recorded by the build and the
recipient-facing file's presence in the inventoried package layout; it does not
prove legal sufficiency or replace verification of the final published downloads.

A newer successful [package smoke workflow run
`42388152203`](https://github.com/heckenmann/visual-agent/actions/runs/42388152203)
from source commit `c754df8ee2b8dd0235d6ffe355c30cc1c10be017` emitted runtime
provenance for all six native outputs (DEB, RPM, AppImage, Windows, macOS ARM64,
and macOS x64). Every report identifies `Azul Systems, Inc.` and
`source.java-runtime-version=27.0.1.0.3+1`, confirming that the current package
matrix is built from the same exact Zulu version. The provenance reports also
distinguish source and packaged runtime/tool hashes; macOS packaging changes
some runtime bytes, so those transformations still need to be assessed against
the applicable terms. This workflow-dispatch run is package evidence, not a
reviewed release candidate, and does not replace the final per-package offer
file and legal-material checks.

The run's `runtime-provenance-*` artifacts record exact source and packaged
`keytool` hashes. Linux DEB/RPM/AppImage share the same source and packaged hash
(`1e5000ee47a2d98b9306be3c6779bb94fe3f8a10e4bca41c800e5117d52a7961`), and the
Windows hashes also match each other
(`7d908aef39ede2ae747aafaf864b1aa9717d90473037a26e671b0963421f4db9`). In both
cases the source and packaged `keytool` hashes are identical. macOS ARM64 changes
from source `4dc7e9b134073e1fb29f36449bd6a5ccea308be7307d87adefd9e54252dad164`
to packaged `be259538dbd0313f7b02e13dc50ceb6ab71f56230f89fca0667a99d46bc86e7a`;
macOS x64 changes from source
`67cdb39364e729cca08e18c0ea4b45ee21c55ebb8d3321cd42aa8d1a5a59f28b` to
packaged `363fe63d2bb3e483aa36b95fcce92a9be654b284987f44a1edd89fc83c8c7e79`.
This gives a reproducible per-platform comparison for the retained runtime
inputs. The differing macOS hashes are consistent with the documented signing
stage, but the hashes alone do not prove the transformation's legal sufficiency.

The public [October 2026 Azul TPL](https://docs.azul.com/core/tpls/october-2026/zulu27_jdk_tpl.html)
is titled `Zulu 27.30 (27.0.1+9)`. Azul's [October 24, 2026 release notes](https://docs.azul.com/core/release-notes)
identify `27.30.109 (CA)` as JDK `27.0.1.0.3`, based on `27.30.108`, with the
underlying OpenJDK version `27.0.1+9`. This reconciles the apparent naming
difference with the exact CI archive's `27.0.1.0.3+1` runtime version; it is not,
by itself, a version-mismatch finding. The TPL describes a three-year
source-on-request offer for covered open-source portions: for three years from
receipt, Azul says it will provide a complete machine-readable source copy on a
customary software-interchange medium, at no more than the physical
source-distribution cost, on request to its published contact address. The
exact platform archives also retain Azul's package `readme.txt`, including its
platform-specific request code, in each native package. These are concrete
vendor-provided offer details, not merely a generic upstream source URL.

The same TPL lists CRIU under GPLv2 and LZ4 with different licenses for library
versus non-library files. Its introduction describes Azul's offer for source
portions it is required to provide; the package inventory does not map those
TPL entries to specific files in each exact runtime, and the TPL is titled
`27.0.1+9` while the shipped archive reports `27.0.1.0.3+1`. Azul's release
notes reconcile those version identifiers, but a competent license review must
still confirm that the TPL and package-specific offer cover the exact received
platform binaries and remain effective for our redistribution, including the
macOS packaging/signing transformations. GPLv2 section 3(c) is limited to
noncommercial distribution of an object-code copy received with a qualifying
section 3(b) offer; the project's noncommercial status alone does not establish
that condition. Keep the offer information in every final package, map any
covered runtime components, and obtain that review before relying on this route.
The Java-only `src.zip` remains supplementary evidence, not a replacement for
the offer or complete corresponding source.

A public-repository check of the [Zulu OpenJDK organization](https://github.com/zulu-openjdk)
found packaging/container repositories but no JDK 27 source repository; the
public [meta-zulu-java recipe tree](https://github.com/zulu-openjdk/meta-zulu-java/tree/master/recipes-devtools)
currently contains only a JDK 8 recipe. This is evidence that an exact public
source archive was not found in those official repositories, not evidence that
Azul cannot provide source through its published request route. Eclipse Temurin
27.0.1.0.2+1 publishes an exact, hash-addressed source archive, but its JDK 27
platform matrix omits Intel macOS. Switching the complete product matrix to
Temurin would remove or change the currently supported macOS x64 package; no
such product change has been selected. The Temurin source archive's published
SHA-256 is `8350986b31cda1c8cb88679272ae4ca9eb948c66e628643b87b0f40717dd8cc3`.
See the [Temurin source asset](https://github.com/adoptium/temurin27-binaries/releases/download/jdk-27.0.1.0.2%2B1/OpenJDK27U-jdk-sources_27.0.1.0.2_1.tar.gz),
[published release metadata](https://github.com/adoptium/temurin27-binaries/releases/tag/jdk-27.0.1.0.2%2B1),
[Temurin's JDK 27 platform note](https://adoptium.net/news/2026/09/jdk27-macos-x64-removal),
and the [exact Zulu TPL](https://docs.azul.com/core/tpls/october-2026/zulu27_jdk_tpl.html).
- AppImage Type 2 runtime `20251108`: the downloaded official x86_64 asset matches
  the project-pinned SHA-256 `2fca8b443c92510f1483a883f60061ad09b46b978b2631c807cd873a47ec260d`.
  Its embedded runtime version string contains the source URL ending in
  `commit/dd6cebe`. At the pinned upstream source commit
  `dd6cebedcbddde9c82f89b011e8e1d40b6e43868`, the release workflow writes the
  checked-out commit hash into `src/runtime/version`, which `runtime.c` embeds
  in the `--appimage-version` output. This ties the exact pinned runtime hash to
  that source commit. The commit includes a reproducible libfuse 3.15.0 download
  checksum, a local libfuse patch, and a pinned squashfuse 0.5.2 source checksum.
  Its Alpine 3.21 Dockerfile also
  installs musl, mimalloc, zstd, and zlib, but does not pin those APK revisions.
  The original Actions job log now returns HTTP 410. However, the official
  [`runtime-x86_64.debug` release asset](https://github.com/AppImage/type2-runtime/releases/download/20251108/runtime-x86_64.debug)
  (SHA-256 `1f3c4a8439e344d74b61bfd0610b89e8499453791bbc3c5b4ae38c181fe54942`)
  has the same GNU Build ID (`a87aaf5da1bf2ef30becedd1aa58e217818fb503`) as
  the pinned runtime binary, so it is the matching debug companion, not a
  separate build. Its DWARF paths identify musl source version 1.2.5 and the
  libfuse 3.15.0 build directory; the runtime's strings identify libfuse 3.15.0,
  squashfuse 0.5.2, zstd 1.5.6, and zlib 1.3.1. The debug symbol table also
  contains code families for mimalloc, squashfuse, FUSE, zstd, and zlib, tying
  those components to this exact runtime binary. The `mi_version` function in
  that binary returns `217`; the retained upstream mimalloc 2.1.7 source defines
  `MI_MALLOC_VERSION` as `217` and returns it from `mi_version` ([header](https://github.com/microsoft/mimalloc/blob/v2.1.7/include/mimalloc.h),
  [implementation](https://github.com/microsoft/mimalloc/blob/v2.1.7/src/options.c)).
  This identifies the upstream mimalloc source release, but not the exact Alpine
  package revision, applied package patch, or compiler/linker flags. Alpine
  package-history records and checksum-pinned recipe snapshots narrow the
  candidate builds to musl `1.2.5-r9`, mimalloc2 `2.1.7-r0`, zstd-static
  `1.5.6-r2`, and zlib-static `1.3.1-r2`. The expired runtime job log still
  prevents proving the exact Alpine APK bytes/build revisions or static library
  objects used. Their
  upstream sources, Alpine packaging recipes, patches, and original license
  texts are retained as partial evidence, not as proof of exact linked build
  inputs. Preserve that distinction. Retain the
  applicable notices and resolve LGPL
  relinking delivery for static libfuse before release. The appimagetool license
  does not cover the embedded runtime.

  The upstream release's exact build is identifiable as Actions run
  [`20037536435`](https://github.com/AppImage/type2-runtime/actions/runs/20037536435),
  created on 2025-12-08 from the pinned source commit above; the x86_64 runtime
  and debug assets were uploaded by that run. Its logs now return HTTP 410. I
  recovered the four matching x86_64 Alpine APK candidates from a public mirror
  and checked their `.PKGINFO` metadata and archive hashes. The metadata names
  these official Alpine recipe commits and package build times:

  | Candidate APK | Package build time (UTC) | Alpine recipe commit | APK SHA-256 |
  | --- | --- | --- | --- |
  | `musl-1.2.5-r9.apk` | 2025-02-13 17:26:26 | [`efd4d5d`](https://gitlab.alpinelinux.org/alpine/aports/-/commit/efd4d5df7be0ea48d98512671f676fc5ceb14899) | `d830ea70bddeef87c80e6d16a488c194839cdd3e15b04c3e060c0ca0d96e5047` |
  | `mimalloc2-2.1.7-r0.apk` | 2024-07-23 19:55:07 | [`5a1a5dc`](https://gitlab.alpinelinux.org/alpine/aports/-/commit/5a1a5dc3216a69e3cd66a57ee07cb5ef01b8be39) | `fb91cf2044e28fd2e6786efa2953d3e371bf949879688da88268db2085099858` |
  | `zstd-static-1.5.6-r2.apk` | 2024-12-25 07:14:12 | [`5c2ddf1`](https://gitlab.alpinelinux.org/alpine/aports/-/commit/5c2ddf18f193dafdd25ae7391d96c7e30766eecd) | `c82da00816b8dfec70f91d0f1d979aa9558bf5c4e1a15ab354f05c6dba5e7ca1` |
  | `zlib-static-1.3.1-r2.apk` | 2024-09-18 11:26:19 | [`4c2722e`](https://gitlab.alpinelinux.org/alpine/aports/-/commit/4c2722e7ecc1c5e3ba882429044a101497121223) | `13434b6ff1e3d31b734f53638f0838b73690160b9605b5549c1e732177e7db03` |

  These hash-addressed archives and Alpine recipe commits strengthen the
  historical candidate evidence, but do **not** prove that the upstream runtime
  build resolved or linked these exact APKs. Its Dockerfile does not pin APK
  versions or a repository snapshot, and the original build logs are unavailable.
  Keep this distinction explicit; this evidence alone does not resolve the
  AppImage source/relink obligation.

The upstream `continuous` asset is not a substitute for evidence about the
shipped `20251108` runtime. Its [successful build
run](https://github.com/AppImage/type2-runtime/actions/runs/39320578745) records
source commit `fedfad51acc2860b2916a1eef32ba4a500ff31ef`, a matching runtime/debug
GNU Build ID `ef655b05ac26feebfd3bc24ec7ccb30389cefc33`, and build-time Alpine
package versions. The signed x86_64 runtime SHA-256 is
`617aa77314d57c8b8e4fae9c3f0b35af7b30d5529cb69e98f64f92510ed98a46`; its
signature verifies against the release's included AppImage runtime key (key
fingerprint `570C77ACEA40C0F1B758902CBF96CCA56490F695`; identity is not
independently certified here). The later build records musl `1.2.5-r11`, zlib
`1.3.2-r1`, zstd `1.5.6-r2`, and mimalloc `2.1.7-r0`, while its source also
changes the runtime's temporary-directory permissions from `0755` to `0700`
and adds compiler path remapping. The shipped runtime instead identifies zlib
`1.3.1` and comes from source commit `dd6cebed...`. Thus the newer run proves
that upstream now records more build detail; it does not fill the older
runtime's missing build log or establish its linked objects. Do not substitute
the newer artifact or its package list in the 20251108 source record without a
separate product change and package smoke test.

The current versioned candidate no longer embeds that upstream binary. In
workflow run [`43368865856`](https://github.com/heckenmann/visual-agent/actions/runs/43368865856),
the Linux AppImage job rebuilt the runtime from the checksum-pinned
`type2-runtime` source archive at commit `dd6cebedcbddde9c82f89b011e8e1d40b6e43868`.
The build log records Alpine packages musl `1.2.5-r12`, mimalloc `2.1.7-r0`,
zstd `1.5.6-r2`, and zlib `1.4-r0`; the static link command includes FUSE 3,
Squashfuse, mimalloc, zstd, and zlib. The source-material inputs include the
matching upstream sources and pinned Alpine recipe snapshots (musl recipe commit
`5245849e14d213abe18d589b4b80d65793327940`, mimalloc recipe commit
`5a1a5dc3216a69e3cd66a57ee07cb5ef01b8be39`, zstd recipe commit
`5c2ddf18f193dafdd25ae7391d96c7e30766eecd`, and zlib recipe commit
`d3332621d44c2c4cf52e52217b16674db190212c`). The candidate verifier confirmed
that the packaged AppImage embeds this source-built runtime, allowing only the
MD5 section generated by `appimagetool` to differ. This supersedes the missing
original-build-input evidence for the old `20251108` runtime for this candidate
only. It improves the source/relink evidence for LGPL libfuse 3.15.0, but a
competent review must still confirm that the delivered materials meet the
applicable LGPL requirements; a successful build and matching recipe versions
are not themselves legal clearance.

- JNA 5.19.1 now has a Maven source archive plus a separately retained libffi
  license text. This does not prove that the source archive contains all native
  build inputs or that every embedded native payload is mapped. Other packaged
  native/font resources still require attribution and review against actual bytes.
- Skiko AWT 0.150.2 now has its Maven wrapper-source archive and pinned upstream
  LICENSE/NOTICE retained. For the current macOS ARM64 app image, the selected
  `skiko-awt-runtime-macos-arm64:0.150.2` source JAR SHA-256 is
  `abea519fa519f7245412011cc3ab7a5852cb333b3dc3cac08a83f5bd806c7bec`. Its
  archive contains the two native libraries and their `.sha256` sidecars, but
  no LICENSE-, NOTICE-, or copyright-named entries; the matching `skiko-awt` wrapper
  JAR likewise has no such entries. The curated upstream LICENSE/NOTICE must
  therefore be delivered separately. The retained NOTICE identifies
  Android-derived code, not all statically linked native dependencies.
  The arm64 Mach-O payload maps to the separately packaged
  `Contents/app/libskiko-macos-arm64.dylib`: source and packaged UUIDs both equal
  `89603BA2-F348-3A29-BEB1-1FFC26B9E7FF`; source and packaged file SHA-256 values
  are respectively `680e40272519123174e97630c92549e2672a3f4deca1744f30f4a472af64b744`
  and `c290e083c2bdec9f4d7faf6e5a99974a03941313ec1ff1384824f6264c3261fc`. The
  x86_64 Mach-O retained inside the packaged runtime JAR also preserves UUID
  `3F31E223-119D-3004-BFAA-7AD9C30E0BCF`; its source and packaged file SHA-256
  values are `c4392e16dddd1181e46ecf58cc55ec0564fe9deaeb03efa58ae778712a28d962`
  and `5bcdc6c7dce7e15901078d6af69bf52c6d5fa83019a8ec41790c760f4b3dcd6d`.
  Both packaged Mach-O signatures verify. The binary hashes differ because
  packaging re-signs the dylibs. This maps the two Skiko Mach-O payloads for this
  one package, not their statically linked
  Skia libraries or other platform artifacts. It does not establish complete
  third-party notices or corresponding Skia/ANGLE source/build inputs.
  In the successful package-smoke run `42388152203` for master commit
  `c754df8ee2b8dd0235d6ffe355c30cc1c10be017`, each executable JAR's nested
  platform runtime JAR matched the same-version Maven artifact byte-for-byte:

  | Runtime artifact | SHA-256 in Maven and executable JAR |
  | --- | --- |
  | `skiko-awt-runtime-linux-x64:0.150.2` | `c6c1f9fa37464fd0bcca6e486a6b35307b6e2436199de9d009f877121de1c5c2` |
  | `skiko-awt-runtime-macos-arm64:0.150.2` | `abea519fa519f7245412011cc3ab7a5852cb333b3dc3cac08a83f5bd806c7bec` |
  | `skiko-awt-runtime-macos-x64:0.150.2` | `f77b1f3a76e94daba2676780e37807697d51d300e31cacc43699709f3ccdff96` |
  | `skiko-awt-runtime-windows-x64:0.150.2` | `eebad490c2828198e86e641cbd44958617867ce70f893b21852f07d0be099c0a` |

  The macOS x64 DMG from that run also contains the expected x86_64 dylib. Its
  source and packaged UUIDs both equal `3F31E223-119D-3004-BFAA-7AD9C30E0BCF`,
  the respective payload SHA-256 values are
  `f370f97c28e265dc4c2f5fa911b6373bb80f3ac5e2dfa20c92546c95a9692556` and
  `3689bf6199d8ad1d14ab56d550f7f34071e06ffc6274d053ea99cc87d0a8d95b`, and its
  packaged signature verifies. The run is a package-mapping evidence source, not
  a review of the current local candidate or license sufficiency.

  The Linux x64 DEB from the same run contains the Skiko runtime JAR with only
  its manifest after packaging, plus `opt/visual-agent/lib/app/libskiko-linux-x64.so`.
  That `.so` is byte-for-byte equal to the payload in Maven artifact
  `skiko-awt-runtime-linux-x64:0.150.2` (SHA-256
  `c733456df0b83210d9e79b90a581c4e005c28d2ef2c4cd17d146d74f7a60f378`); its ELF
  Build ID is `161c51dbcd0a95f529bfb9092945a8aefd52073d`. The packaged transformed
  runtime JAR hash is `5b21b00dd2cd789ddddfa340be52c72ccc16883bbad6eddf49b97699234775c0`.
  The Linux x64 AppImage from the same run contains the same `.so` bytes and
  ELF Build ID; its transformed runtime JAR has only the manifest. The Windows
  x64 MSI cabinet's `skiko-windows-x64.dll` payload is byte-for-byte equal to
  the Maven runtime artifact (SHA-256
  `e90e2e5ae3d48c7694b6cf5948953f6d007fe40478b1040353040acef118dd41`). The
  Linux x64 RPM uses the RPM v6 stripped-CPIO payload: file index 119 resolves
  through the RPM header to
  `/opt/visual-agent/lib/app/libskiko-linux-x64.so`, size 29,640,320 bytes, with
  header and extracted-payload SHA-256
  `c733456df0b83210d9e79b90a581c4e005c28d2ef2c4cd17d146d74f7a60f378`. This
  matches the same Maven runtime artifact and the DEB/AppImage payloads. These
  RPM, AppImage, and MSI checks cover direct Skiko payload mapping for the
  successful package-smoke run `42388152203` at commit
  `c754df8ee2b8dd0235d6ffe355c30cc1c10be017`; they do not review the current
  local candidate or establish license sufficiency. None of the hash/UUID
  checks resolves the statically linked Skia/ANGLE component notices or
  corresponding-source obligations.
- Historical GraalJS 25.4.4.1.1 candidate included the non-community isolate runtime. On macOS ARM64,
  Compose Desktop Gradle Plugin 1.12.2 re-signs embedded dylibs while copying
  dependency JARs. The inspected isolate dylib retains its Mach-O UUID and both
  signatures validate, but the signature blob and whole-file hash change. Confirm
  that this required packaging transformation is permitted by the applicable
  GFTC terms, and include the required notices/source information. The community
  UPL artifacts are only an alternative if equivalent isolate security controls
  remain available. No substitution or redistribution clearance has been made.

  Maven Central source JARs for the selected Linux x64, macOS ARM64, and Windows
  x64 `org.graalvm.js:js-isolate-*` artifacts were downloaded and hashed as
  `ba5a69a402d960c44e77b2bc08b631378646b1d2e6324c1b68448889b326a40d`,
  `81c71eea20f3c272c69d9d6ba3ed392f3651562150f3a4f34401004fcb403821`, and
  `b4e84d35e3140441fab8f3aa8b969a8a509e4c6fb141833cddebdf6f4a86c752`,
  respectively. These are hashes of the exact versioned Maven Central source
  JARs. The historical notice manifest records packaged-file hashes rather than
  source-JAR hashes; in particular, macOS signing transforms the packaged JAR.
  A release-candidate inventory must bind each source JAR hash to its packaged
  artifact before the supplement collector can use this evidence. The audited
  macOS x64 package has no isolate artifact.
  The curated legal materials now include a short recipient-facing reference to
  Oracle's complete GFTC terms, bound to these exact source hashes in
  `third-party/sources.json`. This reference is not the agreement itself and
  the current no-charge public GitHub release plan addresses the GFTC fee
  condition for this channel. Any paid distribution, bundle, or service
  involving the Program requires renewed review. The macOS re-signing and final
  package proprietary-marking checks remain unresolved; no overall
  redistribution clearance is claimed. This isolate and signing investigation
  concerns historical candidates only; current dependency configuration no
  longer requests platform isolate binaries.

### Cross-Platform Nested Native Payloads And Notices

The successful four-platform `License inventory` run
[`42385467454`](https://github.com/heckenmann/visual-agent/actions/runs/42385467454)
at source commit `3ceeae9dc1fdf38ac366cd445ed8175596cee613` provides additional
package evidence. Its native-asset report confirms that the executable
distributions contain JNA dispatch binaries for multiple operating systems,
gRPC shaded tcnative/epoll binaries, Netty QUIC native binaries for Linux,
macOS and Windows, GraalJS isolate binaries, Truffle attach binaries, PDFBox AFM
resources and the embedded Liberation font. This is a snapshot of that run's
resolved package graph, not proof of the current candidate's exact bytes.

The run's extracted-notice manifests contain 264 packaged JAR component records
for Linux x64, macOS ARM64, and Windows x64, and 262 for macOS x64. Each platform
retains detected notices from 136 components; 126 (macOS x64) or 128 (the other
platforms) have no notice matching the collector's filename rules. All records
remain `unreviewed`. The coordinate difference is limited to platform-specific
GraalJS isolate artifacts, plus `org.graalvm.sdk:nativebridge` being absent from
the macOS x64 inventory. For shared coordinates, the notice status and extracted
notice hashes match across platforms. The four reports and extracted files were
downloaded and checked locally; packaging them together produced a 12 MB
review-only legal archive containing 1,569 files. This historical run is
evidence for its source commit only, not a release-candidate approval.

The package-to-runtime-artifact report narrows the `UNMATCHED` rows to three
JARs on every platform: the project's own `desktop-1.0.0-plain` JAR, the
platform-specific `skiko-awt-runtime` JAR, and the bundled JDK's `jrt-fs.jar`.
They are different from third-party Maven artifacts, so the POM-derived
dependency report cannot resolve them. The project JAR's MIT license is handled
separately; the selected Skiko runtime and JDK runtime require their own exact
payload/provenance review. The other `no-detected-notice` entries are not
automatically missing legal notices: their inventory metadata mostly reports
Apache-2.0, UPL-1.0, MIT, and BSD variants, while the report still requires
artifact-level verification. The Linux report also labels the two Logback JARs
`LGPL-2.1-only` and H2 `MPL 2.0`; those are known metadata/selection cases with
separate curated license evidence and must not be resolved by blindly copying
the POM labels. The remaining three unmatched artifacts are still not covered
by that dependency metadata: the project JAR, Skiko runtime, and JDK `jrt-fs.jar`.
These are triage findings from the historical run, not final conclusions about
the current release candidate.

The same run's original-notice manifest confirms that the `grpc-netty-shaded`
JAR contains and retains its Apache license/NOTICE plus the BoringSSL,
Tomcat-native and Maven-wrapper license texts. The `netty-codec-native-quic`
JAR retains its license/NOTICE and a large set of embedded third-party license
files, including BoringSSL, Bouncy Castle and quiche-related material. JNA's
Apache license is retained separately, and its libffi notice is now present in
the local curated inputs. However, the curated directory copied into release
packages is not automatically populated from the inventory's hash-addressed
notice archive. These retained-in-CI texts must still be reviewed and delivered
to recipients through the final legal materials where applicable.

The historical notice manifest reports `no-detected-notice` for the platform GraalJS
isolate artifacts, `truffle-api`, `netty-resolver-dns-native-macos`, and
`netty-transport-native-epoll`. The concise dependency CSV selects UPL for the
Graal isolate/Truffle artifacts and Apache-2.0 for the Netty components. The
exact isolate POM declares GFTC, MIT and UPL, while the same run's extended
Gradle report preserves all three. Upstream GraalJS documentation says
`-isolate` artifacts are GFTC-only. Treating the concise CSV or the POM's list
of alternatives as the final license result would therefore be incorrect;
resolve the exact received artifact's terms rather than selecting an
alternative mechanically. Metadata alone does not substitute for checking the
upstream package and its license/notice terms.

The [official GraalJS license notes](https://github.com/oracle/graaljs#license)
describe `js-isolate-community` as UPL-licensed, and the [official sandbox
documentation](https://github.com/oracle/graal/blob/master/docs/security/polyglot-sandbox.md)
states that community isolate artifacts support sandbox policies starting with
GraalVM 25.1. Direct startup probes for the macOS ARM64 community artifact
against the required Azul Zulu JDK 27.0.1 failed for both 25.4.4.1.1 and
25.5.5.0.3 with `NoSuchMethodError` for
`JavaLangAccess.addEnableNativeAccess(Module)`. The official
[GraalVM 25.5 release notes](https://www.graalvm.org/release-notes/25.5/)
identify 25.5.5.0.3 as based on OpenJDK 25; they do not claim JDK 27
compatibility. The previous context factory caught isolate startup failures and
fell back to `CONSTRAINED`, so service-level tests passed without exercising an
isolate. The reviewed candidate source removes the isolate dependency and chooses
the constrained policy explicitly. The six-platform versioned candidate in
workflow run 43368865856 contains no `js-isolate` artifacts. This does not
provide isolate-level guest heap or CPU quotas, and establishes absence only for
that candidate, not for future or final release packages.
Investigate these artifacts' upstream materials and map
the results to the exact native payloads; do not interpret scanner
`no-detected-notice` as proof that a notice is legally absent or unnecessary.
Skiko and PDFBox have their own separate evidence above. The Liberation Sans
font's exact asset and OFL notice requirements have been reviewed; package-wide
release and source-delivery obligations remain open.

Before publication, regenerate this evidence from the final release source for
all six package variants (Linux AppImage, DEB, RPM, macOS ARM64/x64, and Windows),
compare artifact identities with the reviewed source commit, and include all
applicable original notices in the shipped legal bundle. Versioned candidate
run 43368865856 completed this six-variant technical inventory, but it is tied
to an unmerged branch commit and is not the final release package set. The
inventory narrows the remaining work; it does not establish legal sufficiency or
release approval.

No item in this document closes #410. Candidate run 43368865856 generated
hash-bound review evidence and recipient-candidate legal and source archives for
all six package variants. Their listed contents and hashes were verified, but
the candidate is not the final release artifact set and the archives have not
been published to recipients. The recipient-facing source-availability path,
component completeness review, competent obligation review, and final clearance
are still outstanding.

## Tooling Evaluation

ORT was evaluated against this release's needs. Its Gradle analyzer can inventory
dependency metadata; its downloader/scanner can fetch and inspect source; and its
reporters can emit NOTICE, SPDX, and CycloneDX output. However, it does not remove
the need to inspect the exact final platform packages, identify bundled JDK and
AppImage static components, or map macOS code-signing transformations back to
their resolved inputs. The project already has Gradle License Report for dependency
metadata and a Gradle source-variant task tied to the selected runtime graph. Adding
ORT now would introduce a separate toolchain and scanner lifecycle while leaving
the package-specific checks in place, so it is not justified for this beta closure
work. Reconsider it only if a concrete coverage or maintenance gap remains after
the current finite inventory and delivery work is complete. This is a tooling-scope
decision, not a compliance or redistribution approval.

References: [ORT analyzer](https://oss-review-toolkit.org/ort/docs/tools/analyzer),
[downloader](https://oss-review-toolkit.org/ort/docs/tools/downloader),
[scanner](https://oss-review-toolkit.org/ort/docs/tools/scanner), and
[reporter](https://oss-review-toolkit.org/ort/docs/tools/reporter).
