# Beta Redistribution Review

Refs #410 and UC 0000155. Review started on 2026-11-09 by Codex at the
maintainer's explicit request. This is an attributed, AI-assisted engineering
review of license conditions and distribution evidence, not an external legal
opinion or an OpenChain certification. Do not attribute this review to a human
or organization that has not performed it.

## Current Candidate (Non-Publishing)

- Package/smoke source: `0ad834255a825fa7b4d7c7edee5962fd9a3fcd63` (PR #533).
- Current inventory/documentation source: `dc43fbc1f8ebbed8b563112def3b56d6bdaed490` (PR #533).
- Versioned non-publishing candidate: tag input `v0.1.0`, source
  `dc43fbc1f8ebbed8b563112def3b56d6bdaed490`, workflow
  [run 43305484301](https://github.com/heckenmann/visual-agent/actions/runs/43305484301).
  Its uploaded candidate manifest confirms `candidate=true`. All ten platform
  package jobs, all ten package smoke jobs, and the legal/source archive job
  completed successfully. The workflow does not publish a release or create or
  move a Git tag. Its corresponding-sources artifact is 126,393,816 bytes. It
  was inspected through GitHub's remote artifact reference without storing the
  archive on the host, whose free disk space is low.
- The six platform manifests in the exact-run legal review artifact were
  inspected from its remote file reference without extracting them to local
  disk. The outer artifact SHA-256 `094449782c34110fe587139ffdbfd7d7bce1e5f0caba02960c0badd92fe894f5`
  matches GitHub's artifact digest. Each inventory contains 261 components:
  136 with retained package notices and 125 with no notice detected. All 261
  component statuses and the runtime status are `unreviewed`, and every
  manifest has `redistributionApproved: false`. The bundled-JDK legal-file
  counts are 243 for Linux AppImage, Linux DEB, Linux RPM, and Windows, 242 for
  macOS ARM64, and 241 for macOS x64. None contains a GraalJS `js-isolate`
  artifact. Manifest SHA-256 values are Linux AppImage
  `fb629aaef409d3c3a7fad28df0aadd48d7987812016644fe23041efa0f6c3eb1`, Linux DEB
  `9ea71a56d4831ed241ee83fdb7a146b11eeec109685352cf9972b469194753a6`, Linux RPM
  `1ab36dda764cb44e438d0613f04ad5f5fffeb0dc39f1be2bf75d4c3e3f0b6407`, macOS ARM64
  `d25a11d8fed93f8efcee3ab9b7ceee22d2ca0274d7e0297409e9e320f074e77e`, macOS x64
  `3f5c90519b3464da40718bc1a6e58a2ecc960bd04fb7459aee91c38ec32de566`, and Windows
  `ce53f04cbbc823aa2e67f8a669043c6be9a26985ec77a071b6aed769097ff29d`. The
  embedded `sources.json` hash `0e247649e18e0368b43a8f06b9b7cf82a85f684a2f6cf6f2cc241f8aa82c4500`
  matches the repository file. The AppImage runtime SHA-256 is
  `b9a99a36aa0fea7b850aef2726a6fbc805199edb62c854f8233bf5ba8b1713c5`, and its
  source archive is hash-bound as
  `f5fec23be76e50e2445ed2d018bac49b367490fc483c62c4637e99ec705d27ba`.
  The corresponding-source archive is 127,365,083 bytes and contains 99 entries,
  including a 98-row `MANIFEST.sha256`. Every listed file exists and matches its
  SHA-256; no missing, extra, or mismatched files were found. It includes the
  AppImage runtime sources and pinned source/packaging inputs for libfuse,
  squashfuse, musl, mimalloc, zlib, and zstd, plus source archives for H2, JNA,
  GraalJS, Logback, Skiko, and Liberation fonts. The embedded
  `visual-agent-corresponding-sources.zip` SHA-256 is
  `f7c0835faba0e9c0b2793aae04400cdc63fa33a1b7528d8a59ee9d9735fc64b1`; the
  enclosing artifact SHA-256 is
  `094449782c34110fe587139ffdbfd7d7bce1e5f0caba02960c0badd92fe894f5`.
  The archive remains a review artifact, not a recipient-facing release asset.
  Integrity and listed contents do not establish legal sufficiency, complete
  coverage, or recipient delivery. All component and runtime statuses remain
  `unreviewed`, and redistribution remains unapproved.
- The six package manifests were also deduplicated by Maven coordinate to avoid
  treating repeated platform copies as separate review work. They contain 260
  unique coordinates: 255 are present in all six packages and five are
  platform-specific Skiko runtime variants. Each manifest has 261 rows because
  `io.netty:netty-codec-native-quic:4.2.20.Final` appears five times in each
  package inventory. At the unique-coordinate level, 132 entries consistently
  have a retained package notice and 128 consistently have `no-detected-notice`;
  the latter is an inventory result, not proof that a legally required notice
  is absent. Grouping those 128 by the declared license metadata (a triage aid,
  not a legal determination) gives 100 Apache-related, 11 UPL-related, three
  EPL/LGPL/MPL, four BSD-related, eight MIT-related, one Unicode/ICU, and one
  without declared license metadata. This reduces the review to distinct
  components and license families rather than six repeated package lists.
- The recipient-candidate legal archive from this run, outer artifact
  `visual-agent-legal` (SHA-256
  `dc8789890684071dbef2070d9bc418cba7b682267a89b12548ed768ca263da76`), was
  checked remotely. Its nested ZIP has 4,396 entries and 4,395 rows in
  `MANIFEST.sha256`; all listed hashes match, with no missing entries. It
  includes the generic Apache-2.0 text and the per-platform JDK legal trees.
  The archive has no `REVIEW-ONLY.txt`, but it embeds internal
  `licensing/README.md` and package manifests that explicitly say clearance is
  incomplete. It is therefore a hash-verified candidate artifact, not yet a
  clean recipient-facing legal packet and not a released asset.
- The bundle builder has since been changed locally so recipient-candidate mode
  emits only the project license, curated legal texts for coordinates present
  in the package inventories, and hash-verified package/runtime notice files.
  The corresponding-source candidate now contains the collected source archives
  and a generated path/hash index instead of internal review notes and the full
  curation registry. Both omit internal status manifests and historical
  materials for components absent from the candidate. Repeated inventory rows
  can map the same Maven coordinate to distinct notice bytes, so recipient paths
  include the notice hash and identical repeated entries are deduplicated. The
  22 focused bundle tests pass, including checks for these exclusions and
  repeated-coordinate handling. This change is not in the current PR head and no
  new archives have been generated; the artifacts above remain unsuitable until
  the change is reviewed and a new workflow run confirms their contents.
- The package-notice evidence further clarifies the 128 `no-detected-notice`
  rows: 69 unique coordinates have exact supplemental material, 58 have
  Apache-2.0 metadata and the full Apache license is included in each
  distribution, and the remaining coordinate is bundled JDK `jrt-fs`, covered
  by the captured JDK runtime legal tree. Apache's release guidance says one
  full copy of the license per distribution is sufficient
  ([Apache release policy](https://www.apache.org/legal/release-policy.html)).
  This resolves the apparent missing-license-text count for this candidate's
  collected evidence; it does not establish that the detected notice set or
  license metadata is exhaustive, nor review other source, attribution, or
  redistribution conditions.
- The non-versioned [full package and smoke workflow](https://github.com/heckenmann/visual-agent/actions/runs/43282355080)
  completed successfully for this exact source. All ten package variants and
  all platform/container smoke jobs passed. The workflow received no
  release-tag input (`tag=` and `candidate=false` in its release metadata); it
  did not publish a release or create/move a tag.
- The AppImage package-notice artifact from this run has inventory SHA-256
  `70244ab8513eb2948f8f65902839ac300dd8ae6448688fee2fd5bfc808d05231`, 261
  component records, `redistributionApproved: false`, and all component and
  runtime review statuses set to `unreviewed`. Its runtime record identifies
  Type 2 runtime commit `dd6cebedcbddde9c82f89b011e8e1d40b6e43868`, packaged
  runtime SHA-256
  `b2af9aa016b8a724b2b79c5d12c3af729839ba99ce970dff34f226351ffa190b`, and
  source archive SHA-256
  `f5fec23be76e50e2445ed2d018bac49b367490fc483c62c4637e99ec705d27ba`.
- The `visual-agent-corresponding-sources.zip` embedded in this run's GitHub
  artifact was independently downloaded and hashed as
  `fb633971382be640243fe00b0a7095c3bbcbdaccc26ad457fbf30b4a8d317a9f`.
  Its 98 payload files and manifest were previously verified. It includes the
  exact Alpine recipes for zlib `1.4-r0` and musl `1.2.5-r12`, matching the
  AppImage build log; the former recorded zlib `1.3.1` mismatch is resolved
  for this candidate. This is evidence that the archived inputs match those
  reported build revisions, not a qualified conclusion that LGPL-2.1 section
  6(a)'s static-link relinking/source delivery is sufficient.
- The preceding PR head `0f95daaeecd8be9bccc89f51488d5802c64c26c2` passed
  the four-platform [license inventory workflow](https://github.com/heckenmann/visual-agent/actions/runs/43294540149).
  Its Linux, Windows x64, macOS ARM64, and macOS x64 manifests contain 261
  component records each. Each reports 136 records with retained notices,
  125 as `no-detected-notice`, all 261 component review statuses as
  `unreviewed`, runtime legal review as `unreviewed`, and
  `redistributionApproved: false`. The manifest SHA-256 values are Linux
  `080b2f3268d4f9724a6748da337ec5e21eebe6331440aaecdfb999ea908b038c`,
  Windows x64 `733f2aa889022f83c38924a598f171bf2de041d73f3cd92751e8e5084ce7575f`,
  macOS ARM64 `ec19eaaab94ac5ef4b0c46b5b7f6162c6f7767b2f1228b0a71eb5d39f0a18d66`,
  and macOS x64 `2ca3a7204338b761e92ad7bde382bb101c6ba1f0465a0c3eaec7934ceb4371e0`.
  All four artifacts embed identical `sources.json` bytes with SHA-256
  `0e247649e18e0368b43a8f06b9b7cf82a85f684a2f6cf6f2cc241f8aa82c4500`,
  matching the registry in this commit. `no-detected-notice` is an inventory
  observation, not a finding that a component lacks a required notice.
- That preceding PR head passed its Gradle tests, release workflow contracts,
  package-and-launch check, and Windows smoke process contract in [workflow
  run 43294540166](https://github.com/heckenmann/visual-agent/actions/runs/43294540166).
  The only source changes since the full package/smoke candidate at
  `0ad834255a825fa7b4d7c7edee5962fd9a3fcd63` are this review document, the
  legal-material registry, and its regression tests; no application or package
  build inputs changed.
- The latest four-platform inventory workflow, [run
  43301076142](https://github.com/heckenmann/visual-agent/actions/runs/43301076142),
  passed for current PR head `dc43fbc1f8ebbed8b563112def3b56d6bdaed490`.
  Linux x64, Windows x64, macOS ARM64, and macOS x64 each contain 261
  component records: 136 with a retained notice and 125 with no notice detected
  in the package. Each also records 243 bundled-JDK legal files. The manifest
  SHA-256 values are Linux `293b0a4646c01eeb9408607f8f0301235e0bf494241912aa3c05b95871a6edb7`,
  Windows `c17b954b69bef1b81f0a9b473160799311848e0f2668bd15353e17697c4f2c1f`,
  macOS ARM64 `02c7cc7b379042c8e8259839a413be9be0dfeaeb04e377d428e1fd6bb0e5e44b`,
  and macOS x64 `01b68b0c68afa54f71c912353cd92462e2986f04b09d8f4b3572f0460d06f24f`.
  All four manifests report `redistributionApproved: false`; all 261 component
  records and the runtime legal section remain `unreviewed`.
- None of the four current package manifests contains a
  `org.graalvm.js:js-isolate-*` component. This verifies the removal of the
  previously inventoried GFTC isolate artifacts for the current dependency
  graph and removes the earlier isolate-specific macOS re-signing question from
  this candidate. Ordinary GraalJS and Truffle artifacts remain in the packages
  and retain their separate unresolved notice/source review. Historical GFTC
  records and reference documents in the registry are evidence about earlier
  candidates, not components present in these four inventories.
- The current PR head also passed [Gradle and package/launch checks in run
  43301075599](https://github.com/heckenmann/visual-agent/actions/runs/43301075599).
  The dependency/build inputs are unchanged from the successful full package
  and smoke run at `0ad834255a825fa7b4d7c7edee5962fd9a3fcd63`; changes since then
  are limited to this review document, the legal-material registry, and its
  regression tests.
- The package/smoke success and source-archive match establish technical
  evidence for this non-versioned candidate only. They do not establish
  complete legal coverage, qualified review, final release-asset hashes,
  recipient delivery or redistribution approval. The archive has not been
  attached to a public release, and no release is cleared.
- The approval registry remains unchanged and no release is cleared. The
  candidate archives are not a substitute for component-level review,
  confirming source/relink sufficiency, or approval of exact final release
  package hashes.

Earlier candidate and inventory records below are historical evidence for the
specific workflow runs and source revisions they name. They must not be read
as describing the current candidate above.

## Existing GitHub Pre-release

Checked on 2026-11-12, the public [`v0.1.0` pre-release](https://github.com/heckenmann/visual-agent/releases/tag/v0.1.0)
has no attached release assets. Its tag points to source commit
`355c256ed493a4a8e8acaa38e53b298d66c7c7f5`. Therefore that GitHub release
currently offers no installer, executable JAR, legal bundle, or corresponding-
source archive for download. This observation does not establish whether anyone
obtained binaries through another channel or an expired CI artifact, and it is
not a redistribution approval. Any future binary release still requires the
exact-artifact review and approval described in `README.md` in this directory.

The branch was rebased onto newer dependencies before this review. In
particular, GraalJS is now `25.5.5.0.3`, while the retained Graal supplements
identify `25.4.4.1.1`. The older artifact hashes cannot approve the new graph.
Spring Boot, Spring AI, and CMP Clipboard versions also changed; their actual
selected runtime dependencies must be checked in the new package inventories.

### Earlier Non-Publishing Candidate Evidence (PR #533)

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

#### Unmapped Notice Candidates: Upstream Revision Triage

For Linux inventory workflow
[`43137911416`](https://github.com/heckenmann/visual-agent/actions/runs/43137911416),
the 66 components without a package-detected notice or coordinate-matched
supplement were compared with the exact-version Maven POMs. POMs were retrieved
for 58 coordinates; these expose 42 distinct SCM URL/tag pairs. Eight entries
had no usable SCM reference in the queried Maven Central POM, including an
AndroidX artifact hosted outside that repository and records with no SCM element.
Most returned SCM elements do not declare a release tag.

This is a source-location index, not a NOTICE review: a POM's SCM URL does not
prove the release revision or whether a repository-root NOTICE applies to the
packaged artifact. The shared Apache-2.0 text is already retained, but the
conditional attribution scope still needs verification against the exact
upstream release materials. Resolve the 42 repository groups at evidenced
release revisions, and investigate the eight missing SCM references from their
official artifact sources; do not infer a tag or mark these candidates reviewed
from POM metadata alone.

As a first exact-tag pass, the version-matching source trees for
[Spring AI `v2.1.0-RC2`](https://github.com/spring-projects/spring-ai/tree/1bd756c58d3cad64e8f5870dc929622837ac2236),
[Touchlab Stately `2.1.0`](https://github.com/touchlab/Stately/tree/6ae0eb15df77dc4e44a2e1b6707afd5702cf0298),
[MobileByteLabs KmpToolkit `v3.5.37`](https://github.com/MobileByteLabs/KmpToolkit/tree/ea9fc99eb01df97948f5bfdc765e225dc2c1063a),
and [multiplatform-markdown-renderer `v0.46.0`](https://github.com/mikepenz/multiplatform-markdown-renderer/tree/f3437710ca1d9f1ca73b5b71072aebe6c53a5887)
contain no file whose name begins with `NOTICE`. The full trees were available
for these refs. The
[Compose Multiplatform `v1.12.2` tree](https://github.com/JetBrains/compose-multiplatform/tree/ab304f2538657d9f7077bdb40797f5db618b4c84)
contains only `examples/imageviewer/NOTICE.md`; it is sample-specific, not a
root notice for the packaged library modules. The Kotlin `v2.4.21` root
directory has no NOTICE file, although GitHub marks its recursive tree response
truncated, so this establishes root absence only. The exact kotlinx.coroutines,
kotlinx.serialization, and kotlinx.atomicfu trees each contain
`license/NOTICE.txt`; those library notices are retained by the local source
registry, but the current CI inventories predate those additions. These checks narrow notice triage only;
they do not establish artifact-to-tag identity or complete license clearance.

The same manifests include 241-243 bundled-JDK legal files per platform; the
runtime legal section is also `unreviewed`. The Linux provenance report records
Zulu `27.0.1.0.3+1`, its exact source and packaged module lists, and hashes for
both lists. Package-generation and inventory success verify technical
packaging and provenance capture only. They do not resolve the outstanding
Apache NOTICE, GraalJS signing, Skia/ANGLE, AppImage source/relink, JDK-module,
and final recipient-archive reviews described below. No approval registry
entry or release tag was changed.

The PR was subsequently rebased to `fa627c62fc8416d959779707adc7a7ca2a7f906f`.
The regular PR checks and four-platform inventory workflow pass for that head,
but the separate non-publishing package run
[`43145746309`](https://github.com/heckenmann/visual-agent/actions/runs/43145746309)
failed only in the Linux AppImage job. `appimagetool` successfully assembled
the package, then the old verifier rejected its runtime because the tool writes
the generated 16-byte `.digest_md5` ELF section into the embedded runtime.
The other native package jobs succeeded, but their smoke jobs were skipped when
the matrix failed; this run therefore does not provide a complete package-smoke
candidate.

The run did upload notice manifests for Linux DEB, Linux RPM, Windows, macOS
ARM64, and macOS x64. Their manifests contain 263 components each except
macOS x64 (261); every component and runtime-legal section is `unreviewed`, and
every manifest has `redistributionApproved: false`. Each has 136 retained
component notices and 127 without a package-detected notice, except macOS x64
with 125. No AppImage notice manifest was uploaded. These are useful exact
package inventories, but the five manifests do not replace the missing AppImage
evidence or establish release clearance.

A local correction now verifies the packaged runtime against the retained
source-built runtime while permitting only that documented digest-section
rewrite. It also checks the ELF section layout, the zero-filled placeholder and
that the digest field changed, all remaining runtime-prefix bytes, and the
AppImage Type 2 marker. The release-script suite passes 169 tests locally, and
the Gradle task wiring has been checked with a dry run. These local changes are
uncommitted and have not been tested by the Linux package workflow. A new CI
package run is still needed before updating package-bound legal evidence; no
redistribution approval is recorded.

The current local source registry has 38 dependency records and three embedded
component records, all marked `unreviewed`. Its two package-evidence records are
explicitly historical smoke artifacts, and its AppImage runtime source list is
not yet bound to a newly built package hash. Therefore neither the passing
inventory jobs nor the local fix establish final recipient-package completeness.

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

### Kotlinx Coroutines 1.11.0 Upstream NOTICE Evidence

The exact upstream tag `1.11.0` resolves to commit
`8564f65764d3d05893cec026c6e94250e2b23874`. Its
[`license/NOTICE.txt`](https://raw.githubusercontent.com/Kotlin/kotlinx.coroutines/8564f65764d3d05893cec026c6e94250e2b23874/license/NOTICE.txt)
identifies itself as the Apache section 4(d) notice for the kotlinx.coroutines
library. The original text is retained at
`docs/licensing/third-party/kotlinx-coroutines-1.11.0/NOTICE` with SHA-256
`1ab6de67ef738bffae63f9aa6a8798a35e87341c83ea28d09c8dc8ca6993232a`.
The four published source JARs are mapped by their exact SHA-256 values in
`sources.json`; the Linux candidate package inventory identifies the same four
runtime coordinates with no embedded notices.

This is a targeted notice-evidence correction, not final review. The existing
four-platform manifests predate this addition; the next inventory run must
confirm each exact package hash receives the notice. Attribution scope and final
recipient-facing archive inclusion remain unreviewed.

### Kotlinx Serialization 1.12.0 Upstream NOTICE Evidence

The exact upstream tag `v1.12.0` resolves to commit
`4c28c3e33c145da536f1f4a671f70e5eb5b62b9c`. Its
[`license/NOTICE.txt`](https://raw.githubusercontent.com/Kotlin/kotlinx.serialization/4c28c3e33c145da536f1f4a671f70e5eb5b62b9c/license/NOTICE.txt)
identifies itself as the Apache section 4(d) notice for the
kotlinx.serialization library. Its original text, including the upstream
header's `kotlix.serialization` spelling, is retained unchanged at
`docs/licensing/third-party/kotlinx-serialization-1.12.0/NOTICE` with SHA-256
`ec77bba5c852830abfdefcdefbf4a22577ff4df79ec323a898d5100ca1d6f8d6`.
The Linux inventory for PR head `fa627c62` identifies both JVM runtime
coordinates without embedded notices. Their published source-JAR hashes are
mapped in `sources.json`.

This targeted correction has not yet been run through a new four-platform
inventory. Package-specific attribution scope and inclusion in final recipient
materials remain unreviewed.

### Kotlinx Atomicfu 0.28.0 Upstream NOTICE Evidence

The exact upstream tag resolves to commit
`f206f5b4052efbe97c696559c673a35350598ffa`. Its
[`license/NOTICE.txt`](https://raw.githubusercontent.com/Kotlin/kotlinx.atomicfu/f206f5b4052efbe97c696559c673a35350598ffa/license/NOTICE.txt)
identifies itself as the Apache section 4(d) notice for kotlinx.atomicfu. The
original file is retained unchanged at
`docs/licensing/third-party/kotlinx-atomicfu-0.28.0/NOTICE` with SHA-256
`2b1db3aa8302414ea2bb1be5391110268b4611a11c9c3d28e34bf3acfddc9ef4`; its
upstream spelling errors are preserved rather than silently corrected. The
published `atomicfu-jvm:0.28.0` source JAR hash is pinned in `sources.json`.

The current Linux candidate inventory did not identify an embedded notice for
this coordinate. The exact source/tag relationship, package-specific
attribution scope, and final recipient archive inclusion remain unreviewed;
four-platform inventory evidence is still required.

### Local Package Verification After Rebase (2026-11-12)

On the rebased local worktree, all 169 release-script tests, the full Gradle
gate (`ktlintCheck check test`), and `verifyPackages` passed. The generated
macOS app contains the zlib 1.4 license with the exact curated SHA-256
`e32ff4e00d9d94930537635291da39e7e612703334bf6fde8c7f1686fe8a45a2`.
`verifyPackages` validates the two executable JARs and the local macOS
distribution only; it does not build or validate a Linux AppImage.

The current local `prepareLibrarySourceMaterials` task also passes. Its
generated AppImage source-material directory contains musl 1.2.5 with the
Alpine musl-dev 1.2.5-r12 recipe, and zlib 1.4 with the Alpine zlib-static
1.4-r0 recipe, all matching their pinned SHA-256 values in `sources.json`. The
libfuse `SOURCE-AND-RELINK.txt` is present. This verifies source retrieval and
manifest integrity, not correspondence to a newly built AppImage or
sufficiency under LGPL-2.1 section 6(a).

At the historical PR #533 head `598366e803e8559640d6f8a20ed5a75449fc2bd2`,
inventory workflow `43194021811` showed `js-isolate` on Linux x64, macOS ARM64,
and Windows x64, but not macOS x64. That evidence predates the dependency
removal. The current four-platform inventory for PR head `dc43fbc1` confirms
that no platform `js-isolate-*` artifact is present. All 38 component records
remain unreviewed and the release registry still grants no redistribution
clearance.

### Verified Graal Evidence Gap and Local Correction

The Linux notice manifest from inventory workflow `42649987764` contains 14
Graal `25.5.5.0.3` artifacts. Each has `no-detected-notice` and no matching
supplement. This is a concrete missing-material mapping, not a conclusion based
only on a POM license name. The corresponding Linux, macOS ARM64, and Windows
inventories provide the original JAR hashes. These are historical package
records; the latest four-platform inventory for PR #533 at `dc43fbc1` confirms
that no platform `js-isolate-*` artifact remains in the current package graph.

The local registry retains the 16 coordinates observed in that historical
candidate across those platforms, without deleting earlier records. The
official Graal release's UPL text is byte-identical to the retained original;
its SHA-256 is recorded in the new entry, avoiding a duplicate license file.
The shadowed ICU source JAR from that candidate has SHA-256
`485f54aaa23f47224ae0fe8c12c622314dfb46a0ca93f5c3d7e2683281c612d6`;
`VersionInfo.java` identifies ICU 78.2 and Unicode 17.0, matching the retained
original ICU license. A version-specific GFTC reference covers the three
historical isolate platform coordinates without asserting that macOS re-signing
is allowed.

The language implementation separately retains its own original
`LICENSE_GRAALJS` and `THIRD_PARTY_LICENSE_GRAALJS.txt` from the exact upstream
release commit. These contain Oracle, Lucent, V8, Unicode, and other applicable
notices that a generic SDK UPL copy cannot replace. Its exact-version source
archive is now included through the existing Gradle runtime source-variant
collector, with recipient instructions and hash recorded in
`source-delivery.md`. This does not imply that the historical native isolate was
UPL-licensed.

The catalog-version regression test and supplemental-material integrity tests
pass for those evidence updates. They are not themselves a license approval.

### JDK 27 Isolate Failure And Runtime Exclusion

A direct runtime probe on the required JDK 27 reproduced
`NoSuchMethodError: jdk.internal.access.JavaLangAccess.addEnableNativeAccess`
when creating GraalJS `SandboxPolicy.UNTRUSTED` contexts with the selected
25.5.5.0.3 platform isolate. The service had caught that startup failure and
continued with `SandboxPolicy.CONSTRAINED`, so ordinary service tests did not
prove that the isolate was working. The runtime configuration removes the
platform isolate dependency and uses the same constrained host-access policy
explicitly. The latest four-platform inventory confirms no `js-isolate-*`
artifact is present in the current package graph, resolving the isolate-
specific GFTC/macOS-signing question for this candidate. It does not resolve
the remaining GraalJS notices, source, or security-boundary review.

The official GraalVM embedding guide documents Community Edition isolate
artifacts (`*-isolate-community`) separately from Oracle's GFTC isolate
artifacts. The exact macOS ARM64 Community artifact for GraalJS 25.5.5.0.3 is
published with MIT and UPL-1.0 metadata. I tested that artifact with the same
`UNTRUSTED` sandbox options on Temurin 27+35 and Zulu 27.0.1+9; both fail during
isolate startup with the same `JavaLangAccess.addEnableNativeAccess(Module)`
linkage error. The same test returns 42 on Temurin 25.0.3. Maven Central lists
25.5.5.0.3 as the latest version of this platform artifact as of 2026-11-12.
This isolates the current failure to JDK 27 compatibility rather than the
GFTC-versus-Community artifact choice. Switching to the Community variant alone
would not restore the quotas while retaining the project's JDK 27 runtime.
The documented `engine.IsolateMode=external` option was also tested on Zulu
27.0.1+9 and fails with the same linkage error before the external worker starts.

References: [GraalVM embedding guide](https://www.graalvm.org/latest/reference-manual/embed-languages/),
[macOS ARM64 Community artifact POM](https://repo.maven.apache.org/maven2/org/graalvm/js/js-isolate-darwin-aarch64-community/25.5.5.0.3/js-isolate-darwin-aarch64-community-25.5.5.0.3.pom).

This is also a security release blocker, not only a lost-quota limitation.
GraalVM's `SandboxPolicy` API describes `CONSTRAINED` as intended for trusted,
potentially buggy applications and explicitly says that untrusted code is not
supported under any policy other than `UNTRUSTED`. Since the JavaScript tool
executes model-supplied scripts, the constrained JDK 27 fallback must not be
treated as an equivalent sandbox or approved for release as the current
model-facing execution path. Keep the feature unavailable for untrusted scripts
until a compatible `UNTRUSTED` runtime or a separately reviewed process boundary
is implemented and verified. Do not weaken the JDK 27 requirement implicitly.

Reference: [GraalVM SandboxPolicy API](https://www.graalvm.org/25.0/javadoc/sdk/org/graalvm/polyglot/SandboxPolicy.html).

The constrained policy denies host classes, filesystem and network APIs,
process creation, native access, and polyglot access. The service retains its
outer timeout/cancellation behavior, but Graal's isolate-only guest-memory and
CPU quotas are no longer available. No separate-process resource boundary is
implemented. This runtime choice is not an overall redistribution approval;
other component, source-delivery, notice, and package-review items remain open.

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

Adobe's current [DNG SDK and specification page](https://www.adobe.com/support/downloads/dng/dng_sdk.html#dng-specification-patent-license)
publishes a separate royalty-free patent grant for software that reads or writes
DNG-compliant files. The exact Skia revision bundled through Skiko contains
`SkRawCodec`, which uses Adobe's SDK to parse and validate DNG files and render
their raw image data ([pinned source](https://github.com/google/skia/blob/a3f509bf52564dc8b27940bebe599d77ac0c4ae3/src/codec/SkRawCodec.cpp)).
Visual Agent calls Skia's encoded-image decoder for canvas image content, so
this is a reachable product image-decoding path, not merely unused code found
inside a library. This establishes that the product has a reachable DNG-reading
path. Whether that implementation meets the patent grant's eligibility terms
and the effect of its conditions, including notice and revocation, still needs
qualified review.

The patent license requires the notice `This product includes DNG technology
under license by Adobe.` to appear prominently in source code and documentation.
The notice is in the root README, beside the decoder call in the UI source, and
in the hash-pinned recipient material. Tests verify those source/documentation
placements. Final recipient archives and package coverage still need exact
version/hash review. This patent grant is separate from the DNG SDK's copyright
license and its commercial-product indemnity clause; no final clearance is
claimed from the source-level evidence alone.

The notice is also kept beside the Skiko dependency in the desktop build source.
The local `verifyPackages` run confirmed curated legal files in the executable
JARs and macOS native package; the supplement regression test checks the exact
notice in the project README, UI decoder source, build source, and hash-pinned
material. This closes the local notice-placement task, but not final recipient
archive/package hash review or the exact Windows/Linux candidate mapping.

The exact [Skiko `v0.150.2` build configuration](https://github.com/JetBrains/skiko/blob/v0.150.2/skiko/build.gradle.kts)
statically links `piex` and `dng_sdk` for native targets and macOS JVM targets;
the Windows JVM target adds `d3d12allocator` but does not explicitly add either
DNG library.
The corresponding [Skia `m150-a3f509bf52` raw-codec target](https://github.com/JetBrains/skia/blob/m150-a3f509bf52/BUILD.gn#L1572-L1586)
is enabled only when `skia_use_dng_sdk`, `skia_use_libjpeg_turbo_decode`, and
`skia_use_piex` are all enabled. Inspection of the hash-pinned Linux x64 and
both macOS runtime artifacts detects DNG-specific symbols. The Windows MSI's
Skiko DLL matches the Maven runtime artifact at SHA-256
`eebad490c2828198e86e641cbd44958617867ce70f893b21852f07d0be099c0a`; a string
scan found no DNG-specific identifiers, only Adobe APP14 JPEG strings. Because
the Windows binary is stripped and string scans cannot prove absence, exact DNG
presence in the Windows release payload remains unresolved. Keep the DNG patent
notice in source and recipient documentation; do not treat the conditional
build rule or missing strings as proof that the final Windows artifact contains
or omits the decoder.

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
materials. The initial baseline workflow failed before those final archives
could be assembled; a later non-publishing candidate run is documented below.
No AppImage release is approved.

The current local packaging correction replaces the previously downloaded
prebuilt runtime with a runtime built from the retained source archive by the
same Gradle task that packages the AppImage. The verifier checks the output and
copies that exact file into the package task's declared input; after packaging,
the verifier compares the AppImage's runtime prefix with that file. AppImage's
`appimagetool` writes its generated 16-byte `.digest_md5` ELF section into the
runtime prefix after embedding it. The comparison therefore permits changes
only in that reserved section, verifies that the source section was an empty
placeholder and that appimagetool filled it, and requires every other prefix
byte and the Type 2 marker to match exactly. The
[pinned upstream appimagetool implementation](https://github.com/AppImage/appimagetool/blob/8c8c91f762b412a19f4e8d2c4b35afb98f2d7c81/src/appimagetool.c)
documents and performs this digest write. The packaging wrapper stamps the
runtime with the short commit from the exact source archive using the version
format from the pinned upstream release
workflow, then verifies the reported version. The package-notice collector now
records the final AppImage hash, runtime prefix hash and size, source archive
hash and commit, and original license texts for the mapped runtime and static
components; all remain explicitly unreviewed. The earlier statement that no
Linux package candidate existed is superseded: non-publishing workflow
[`43187339406`](https://github.com/heckenmann/visual-agent/actions/runs/43187339406)
built the AppImage and passed its container smoke test. Its runtime-provenance
artifact records the source and packaged module lists and matching keytool
hashes. This confirms a technical package candidate, not the completeness of
the corresponding-source/relink materials or license clearance. The upstream
Dockerfile still uses unpinned Alpine package revisions; recipient source/relink
review, exact final-package review, and the LGPL obligation review remain open.

The local prepared source-material directory contains all eleven archives
declared for this runtime; each archive's SHA-256 matches the registry. The
source-built Linux runtime and AppImage package exist as artifacts of workflow
`43187339406`, not in this macOS workspace. Their existence does not settle
whether the candidate's materials are sufficient to modify and relink the
embedded LGPL library; that remains part of the recipient-source review.

#### Candidate Alpine Inputs Found in the Retained CI Log

Unlike the expired upstream 2025 build log, the AppImage job log for candidate
workflow [`43187339406`](https://github.com/heckenmann/visual-agent/actions/runs/43187339406)
is still available. BuildKit recorded the base image as
`alpine:3.21@sha256:bc21487101ae47172509130ee1092cc273a831968d64b617776d1f69c81e42ab`.
The `apk add` output records `musl-dev 1.2.5-r12`, `mimalloc2 2.1.7-r0`,
`zstd-static 1.5.6-r2`, and `zlib-static 1.4-r0`; the runtime build command
statically links `-lzstd -lz -lfuse3 -lmimalloc` and uses the Alpine musl
toolchain. The official Alpine v3.21 package records identify musl recipe
commit `5245849e14d213abe18d589b4b80d65793327940` and zlib recipe commit
`d3332621d44c2c4cf52e52217b16674db190212c`. Their full recipe snapshots and
the official zlib 1.4 source archive are now pinned in the local source
catalogue.

This exposed a real mismatch in the earlier candidate: its downloaded
`visual-agent-corresponding-sources.zip` contains zlib 1.3.1 and the musl r9
recipe, not the zlib 1.4 and musl r12 sources/recipe reported by its build log.
The local catalogue correction does not repair that already-produced archive.
A new package-and-source candidate must be built and downloaded, then its
artifact hashes and internal manifests rechecked before it can be considered.
The log records package versions but not APK archive hashes; the exact static
object mapping and whether all retained sources are sufficient for recipient
relinking still require review. This evidence is technical, not redistribution
approval.

#### Direct Candidate Source-Archive Inspection (2026-11-12)

I downloaded `visual-agent-corresponding-sources.zip` from the same non-publishing
workflow `43187339406` and verified its SHA-256 against the recorded candidate
hash `d911aa648006492230d8ff4df8bbf61292e6c7119ad989a57715bc40268fe40f`.
The archive contains 98 entries: 97 payload files, each listed in
`MANIFEST.sha256`, plus the manifest itself. All 97 payload hashes match. All
eleven AppImage source archives declared by the source registry at commit
`62896caf` are present under `source-materials/archives/appimage-runtime/` and
match that historical registry's hashes. The archive also includes the
recipient `SOURCE-AND-RELINK.txt` and no `REVIEW-ONLY.txt` marker. The retained
AppImage build log subsequently revealed that two of those pinned candidate
inputs are stale: zlib 1.3.1 and Alpine musl recipe r9 do not match the actual
zlib-static 1.4-r0 and musl-dev 1.2.5-r12 installed for that package build.

This confirms the archive's internal integrity, not source completeness against
the candidate's actual static runtime inputs. A new package-and-source candidate
using the corrected source catalogue is required. Even after that rebuild, a
qualified reviewer must determine whether the exact package/source pair
satisfies every LGPL-2.1 section 6(a) condition; it has not been published or
bound to a versioned release tag or final release asset hash.

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
request code. The current versioned, non-publishing candidate
[run 43305484301](https://github.com/heckenmann/visual-agent/actions/runs/43305484301)
was checked directly: all six platform inventories retain the original vendor
readme, and each hash matches the corresponding previously inspected Zulu
archive:

| Package inventory | Retained vendor readme SHA-256 |
| --- | --- |
| Linux AppImage, DEB, RPM | `76adb733da785cd1f257bef2cfadeed5a30178e8ac1179590b5776c860609683` |
| macOS ARM64 | `ba5a54776c2990ee75398f258223515fee4f72bf3ce888603e42fe1f5947c7a3` |
| macOS x64 | `b9748e40acbc0b4933f63e886f98ec629f7231645c0cd5caa471a01c902e85bf` |
| Windows x64 | `8d0152e9611c44e4bce41ffa06a05befe4823a5ef48b35549a62246fed538312` |

The `visual-agent-legal` candidate archive in that same run was also inspected:
it contains all six files at
`package-notice-evidence/<platform>/runtime-legal/vendor/readme.txt`, with
hashes matching this table. This proves the offer text reached the CI archive,
but not a recipient-ready layout: that archive also includes internal package
manifests and review metadata. The local bundle-builder change places these
files under `platforms/<platform>/java-runtime/vendor/readme.txt`; that revised
archive still needs a new CI run and content review before it can be treated as
the recipient bundle.

**Evidence-supported delivery route for the covered portions:** pass through
the received vendor offer for OpenJDK portions under GPLv2 with the Classpath
Exception, preserving the original notice, request code, GPL text, and
exception. The exact non-publishing candidate contains the original
platform-specific offer in all six package evidence trees, with hashes matching
the received archives. This is evidence for a possible GPLv2 section 3(c)
pass-through route for the covered OpenJDK portions, given the maintainer-
confirmed noncommercial distribution; a new project source offer may not be
required if the route is valid for the exact distributions. A qualified review
must still confirm the offer's applicability to these received binaries, their
downstream recipients, and the `jlink`/signing transformations. This does not
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
requires the resolver to be absent. The current four-platform inventories for
PR head `f886c07138912645ce0e10f579c8731e49ca22c3` confirm that the coordinate
is absent on Linux, Windows, macOS ARM64, and macOS x64. This resolves resolver
presence in the current candidate only; it does not review the historical
binary or approve future release packages. A release inventory must repeat
the check.

The exact Netty 4.2.20.Final source JAR contains `netty_resolver_dns_macos.c`
and `dnsinfo.h`. The C source includes that header and calls
`dns_configuration_copy()`. The header includes both Netty's Apache-2.0 notice
and Apple's APSL-2.0 notice. Apache Legal issue
[LEGAL-700](https://issues.apache.org/jira/browse/LEGAL-700) was closed after a
comment interpreted both licenses as applying to the header, likely because it
began as APSL-covered material and received Apache-licensed modifications.
Netty's public [licensing discussion](https://github.com/netty/netty/discussions/14956)
still does not determine the effect on the compiled native binary. The prudent
release path is to omit this optional binary rather than infer Apache-only
coverage. The four-platform package and inventory checks passed for current PR
head `dc43fbc1f8ebbed8b563112def3b56d6bdaed490`; the exact final release packages
must be checked again. Noncommercial distribution alone would not resolve the
terms if the binary were included.

The exact source JAR was inspected and its source-artifact hash is retained in
the registry. Netty's original `LICENSE.dnsinfo.txt` header notice and the full
APSL-2.0 text remain hash-checked as historical review evidence, but the
unshipped native resolver source archive is excluded from the release source
bundle. A regression test verifies the legal-text hashes, all 20 historical
Netty notice mappings, and collector output. The current candidate inventories
confirm that the optional resolver is absent; final recipient-package review
must repeat that check for the actual release artifacts.

The official [GFTC](https://www.oracle.com/downloads/licenses/graal-free-license.html)
and [GraalVM FAQ](https://www.graalvm.org/faq/) were checked again. Neither
explicitly addresses replacement of a native library's macOS signature. The
Native Image output clarification does not explicitly authorize later
re-signing. A changed packaged hash alone proves changed bytes, not whether
the contractual unmodified condition is satisfied. Do not infer permission or
a prohibition solely from that hash.

The exact macOS packaging transformation is confirmed both in the pinned
Compose Multiplatform 1.12.2 implementation and in the non-publishing package
workflow. Its `AbstractJPackageTask` uses `MacJarSignFileCopyingProcessor` for
macOS JAR inputs; this processor invokes the macOS signer on embedded dylibs
([file-copy processor](https://github.com/JetBrains/compose-multiplatform/blob/v1.12.2/gradle-plugins/compose/src/main/kotlin/org/jetbrains/compose/desktop/application/internal/files/MacJarSignFileCopyingProcessor.kt),
[signer](https://github.com/JetBrains/compose-multiplatform/blob/v1.12.2/gradle-plugins/compose/src/main/kotlin/org/jetbrains/compose/desktop/application/internal/MacSigner.kt)).
When no developer identity is configured, the plugin still applies an ad-hoc
signature. Oracle's [GFTC](https://www.oracle.com/downloads/licenses/graal-free-license.html)
permits redistribution of the unmodified Program without associated fees and
prohibits removing proprietary-rights markings; it does not expressly address
this signature transformation.

The macOS ARM64 DMG from package workflow
[`43187339406`](https://github.com/heckenmann/visual-agent/actions/runs/43187339406),
for source `62896caf507abd29a8f992d1ce5dd65b59e6f655`, was downloaded and
inspected. Its SHA-256 is
`4093af1f94000ee07cf693143d002b7db72b2cfd7147e9dfc1a06f2b774e7e8f`. The
GFTC JAR inside it has SHA-256
`9a435f63d781eec60cfcad490422df681aacbb815f1e7a83997b519efb7a87c9`; the
matching package-inventory row records `b5caf26dfcfa07fa34072a303bd5384127b59d2850ddb1af5113b82eca66996c`,
and the resolved Maven JAR hash is
`41bdbf425dc998ebb6a6061eff9fa1e42ae4a762b980bffd98e4bda7e793f015`. A
direct comparison of the Maven JAR and the DMG's nested JAR found nine file
entries in each: only `META-INF/resources/engine/js-isolate-darwin-aarch64/libvm/libpolyglotisolate.dylib`
changed; no entry was added or removed. Thus the actual native payload is
re-signed, not just the outer app bundle, while the JAR entry set is otherwise
preserved. This proves the technical transformation, not whether it satisfies
GFTC's unmodified-Program condition.

The narrow unresolved clarification is whether the applicable GFTC permits
the Compose packager's ad-hoc re-signing of the native code embedded in
`org.graalvm.js:js-isolate-darwin-aarch64:25.5.5.0.3`. The official terms do not
expressly answer this. Obtain an applicable Oracle clarification or qualified
assessment, then verify and bind the same transformation to the final
versioned candidate before recording this component's clearance. No inquiry
has been submitted on the maintainer's behalf; redistribution approval remains
false.

The documented Compose property `compose.desktop.mac.sign=false` is not a
workaround for this transformation. A local `:desktop:createDistributable`
run with that property still produced a macOS app-image JAR whose embedded
isolate library was changed from the resolved Maven JAR. The property controls
the package-signing configuration; the pinned plugin still runs its macOS JAR
copying processor and ad-hoc signs embedded dylibs on Apple Silicon. This
recheck confirms there is no supported Gradle flag in the current setup that
preserves this GFTC payload byte-for-byte.

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
- The exact GraalJS 25.5.5.0.3 `js-language` binary and source-JAR SHA-256
  values match the registry. Its `LICENSE_GRAALJS` and
  `THIRD_PARTY_LICENSE_GRAALJS.txt` also byte-match the files at the pinned
  upstream commit. The source repository's root `LICENSE` is a different file
  and is not a substitute. The Maven binary JAR contains no license/NOTICE
  entries, so the version-bound legal bundle must carry the implementation
  texts explicitly. The POM and pinned GraalJS build metadata declare both
  UPL-1.0 and MIT, with the build metadata attributing MIT to JONI. However,
  the exact binary and source JARs contain no package/class names or source
  references identifying JONI, the retained
  third-party notice has no JONI attribution, and the upstream changelog says
  JONI was removed in 20.1.0. This is an unresolved upstream metadata
  discrepancy; do not infer that MIT is either applicable or irrelevant without
  confirmation. Source/text provenance is verified, but the discrepancy,
  applicable-license mapping, final recipient-archive inclusion, and final
  artifact hash review remain open.
  Evidence: [Maven POM](https://repo.maven.apache.org/maven2/org/graalvm/js/js-language/25.5.5.0.3/js-language-25.5.5.0.3.pom),
  [pinned upstream build metadata](https://github.com/oracle/graaljs/blob/dd265d542fad72d0e7e1ae4032ef7c040911e700/graal-js/mx.graal-js/suite.py#L523-L528),
  and [pinned upstream changelog](https://github.com/oracle/graaljs/blob/dd265d542fad72d0e7e1ae4032ef7c040911e700/CHANGELOG.md#L243-L255).
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
