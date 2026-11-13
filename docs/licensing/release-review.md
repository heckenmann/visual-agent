# Beta Redistribution Review

Refs #410 and UC 0000155. Review started on 2026-11-09 by Codex at the
maintainer's explicit request. This is an attributed, AI-assisted engineering
review of license conditions and distribution evidence, not an external legal
opinion or an OpenChain certification. Do not attribute this review to a human
or organization that has not performed it.

## Candidate Evidence

### Latest Versioned Candidate (Non-Publishing)

- The latest versioned non-publishing candidate is [workflow run
  43451994879](https://github.com/heckenmann/visual-agent/actions/runs/43451994879),
  with tag input `v0.1.0` and source commit
  `4073f130de8bdeee7b01a4127349b5109b642435`. Its metadata artifact says
  `candidate=true`; the workflow did not publish a release or create/move a tag.
  All ten package jobs and the legal/source-material generation job passed.
  Nine of ten package smoke jobs passed. The only failure was the macOS x64
  DMG smoke job: the application started and created its isolated database,
  then `hdiutil detach /dev/disk2 -force` returned exit 16, `Resource busy`.
  This is a cleanup failure after the smoke assertions passed, not a failed app
  startup. A bounded retry for this specific transient result is implemented
  locally. The mounted-image validation-error path uses the same retry, and a
  detach failure there is chained without replacing the original validation
  error. Four focused regression tests cover those paths; the complete release
  script suite passes 87 tests. The fix has not yet been run in CI.
- The six platform manifests in the run's internal review artifact each have
  261 component rows; all 1,566 rows remain `unreviewed`, every manifest sets
  `redistributionApproved: false`, and none contains a `js-isolate` coordinate.
  These exact candidate inventory facts supersede the earlier four-platform
  inventory cited below, but do not constitute component-level legal review.
- Deduplicating the six manifests by Maven coordinate yields 260 distinct
  components, not 1,566 independent review cases. Of these, 128 have no
  automatically detected notice in the packaged artifact; 69 have exact
  curated supplemental materials in the candidate legal bundle. The remaining
  59 are 58 components whose reported license metadata includes Apache 2.0
  variants and the bundled JDK `jrt-fs` entry. The recipient candidate contains
  the generic Apache-2.0 text and all six platform-specific Zulu vendor offer
  files. For the 58 Apache-metadata components, 57 exact-version source JARs
  were inspected (56 from Maven Central and one from Google Maven); none had a
  `NOTICE`-named entry. The remaining Guava `listenablefuture` coordinate is an
  empty conflict-avoidance artifact: its POM and binary were available, but no
  source JAR was published at the tested Maven Central coordinate and neither
  contained a `NOTICE` entry. This narrows the notice-specific follow-up, but
  does not validate all declared license metadata, source correspondence, or
  obligations beyond NOTICE-file presence.
- GitHub reports the recipient legal artifact as 2,003,026 bytes with artifact
  SHA-256 `d3f69e62d76c1ec2479d01f7d68945a3e6a4f75c7f754aea16fe948b51f5abb7`.
  The corresponding-sources artifact is 126,250,832 bytes with artifact
  SHA-256 `586737be40605a66e15c176900730b7d648dae4f89f47240d9c7393aa3a3de53`.
  Its inspected ZIP contains 21 entries, including the pinned AppImage runtime
  inputs, libfuse and relink materials, Zulu source package, and the selected
  Maven source archives. These are GitHub workflow-artifact hashes and sizes,
  not final public release asset hashes. The review-only artifact is separate
  (2,985,142 bytes; SHA-256
  `9c061b508ee57db906d8fd4a5c7340aac9919e553b555d690d8f711f9f163eba`) and
  must never be attached as the recipient packet.
- The exact `jakarta.annotation:jakarta.annotation-api:3.0.0` binary embeds
  `META-INF/LICENSE.md` and `META-INF/NOTICE.md`; their candidate hashes match
  files at pinned upstream commit
  `d456606ef104b5602700d7a2414f812e48a8a694`. The NOTICE declares EPL-2.0 and
  a conditional GPL-2.0-with-Classpath-Exception secondary license. Both
  original files are present in each of the six candidate recipient packages.
  The local curated index now exposes them in the About license panel as well.
  This closes a document-discovery gap only: the component remains unreviewed,
  the selected license route still needs competent review, and a new candidate
  must be built from the updated source before final hash review.
- This candidate's successful builds, recipient archives, package inventories,
  and smoke results establish technical delivery evidence only. Because the
  manifest rows remain unreviewed, the macOS x64 DMG smoke job failed during
  cleanup, and no redistribution approval is recorded, this is not a
  release-ready or approved release.

### Previous Versioned Candidate (Run 43368865856; Non-Publishing)

- Candidate workflow [run 43368865856](https://github.com/heckenmann/visual-agent/actions/runs/43368865856)
  used tag input `v0.1.0` and source commit
  `30232f4318c5fd594c1166822a2f832b78120821`. Its uploaded metadata says
  `candidate=true`. All ten package jobs, all ten artifact smoke tests, and
  legal/source material generation passed. This workflow does not create or move
  a Git tag, attach public release assets, or publish a release.
- The six platform manifests in `visual-agent-legal-review` were inspected
  remotely. The artifact SHA-256 is
  `66c8dcc1327dbac81d66f358ffc8cdf7accd4ffa12ed0a62c6098d27204538ae` and its
  nested review ZIP SHA-256 is
  `de3b14be776e58ca213b4a0c2d1ee5212565815e4bea0bd645e43836a4935ac1`. Each
  platform has 261 component rows and 257 unique Maven coordinates; all 1,566
  component rows remain `unreviewed`, and all six manifests set
  `redistributionApproved: false`. The manifests contain no GraalJS
  `js-isolate` artifacts. JDK legal-file counts are 243 for Linux AppImage, DEB,
  RPM, and Windows, 242 for macOS ARM64, and 241 for macOS x64. These are
  inventory facts, not legal approval.
- Across the six manifests, 260 distinct coordinates occur: 256 are common to
  all platforms and four are platform-specific Skiko runtime variants. Of the
  260 distinct entries, 128 have no notice detected in the packaged artifact.
  The evidence maps 69 of those to exact curated supplemental materials, 58 to
  Apache-2.0 metadata with the Apache license text present in the recipient
  bundle, and one to bundled-JDK `jrt-fs` legal materials. These are inventory
  and document-presence checks only; they do not validate every license
  declaration, required attribution, or legal obligation.
- A direct read of the candidate's Linux AppImage manifest confirms that its
  Gradle License Report metadata is not itself a reviewed license conclusion.
  In particular, H2 2.5.252 reports an unnamed license plus EPL 1.0 and MPL 2.0;
  Logback 1.6.6 reports EPL-2.0 and LGPL-2.1-only; JNA 5.19.1 reports
  Apache-2.0 and LGPL-2.1-or-later; and `jakarta.annotation-api` 3.0.0 reports
  EPL-2.0 plus GPL-2.0 with the Classpath Exception. The candidate recipient
  archive contains the curated H2 and Logback materials, JNA's Apache and
  libffi texts, and the exact `jakarta.annotation` LICENSE/NOTICE files for all
  six package variants. H2, Logback, and the JDK `jrt-fs` have no notice detected
  inside their individual packaged artifacts. These facts confirm delivery
  paths, not that the reported metadata is accurate, that all applicable terms
  are represented, or that source obligations are met. The exact upstream
  license terms and these metadata discrepancies still require component-level
  review; none of these rows is marked reviewed.
- The recipient-candidate legal artifact `visual-agent-legal` has outer SHA-256
  `2e03a757ee0c7d13148f759ffaa8689b6117c814f3e3f0a855be77bd148bd413`. Its
  nested `visual-agent-legal.zip` SHA-256 is
  `70084cf0754e0c3bb822f53e32c73f63db5633c9c651ae718447a91c045bc3cf`; the ZIP
  has 3,557 entries and 3,556 manifest rows, with every listed file present and
  hash-correct. It contains six platform-specific folders, the project license,
  and third-party materials; no internal review documents or package manifests
  were found. This verifies archive integrity and separation, not whether every
  legal obligation is fulfilled.
- The recipient-candidate corresponding-sources artifact has outer SHA-256
  `b992b28c0ac19ae3e4a112e3ca7252501b1789066a594c7a781c54af019e752c`. Its
  nested `visual-agent-corresponding-sources.zip` SHA-256 is
  `4c0bd9ace054e91d60b621565bb90f1b82c8b4aed1e880733668cc5f0ccee101`; all 20
  listed files match its manifest. It contains the pinned AppImage runtime,
  libfuse, squashfuse, musl, mimalloc, zlib, and zstd inputs, plus the selected
  Maven sources, Liberation Fonts sources, and supplementary Zulu source
  package. No internal registry, review document, or package-notice manifest
  is present. Its contents and hashes do not establish that the source is
  legally sufficient for every component.
- The Linux AppImage job's retained workflow log confirms that this candidate
  rebuilt, rather than reused, the upstream `20251108` runtime binary. It built
  from pinned `type2-runtime` commit `dd6cebedcbddde9c82f89b011e8e1d40b6e43868`,
  recorded the linked FUSE 3/Squashfuse/mimalloc/zstd/zlib libraries and Alpine
  package versions (musl `1.2.5-r12`, mimalloc `2.1.7-r0`, zstd `1.5.6-r2`,
  zlib `1.4-r0`), and passed the binary-level check that the AppImage embeds
  the source-built runtime except for the generated MD5 section. The recipient
  source candidate includes the source archives and pinned Alpine recipe
  snapshots for these runtime dependencies. This narrows the old-runtime source
  gap documented in `source-delivery.md`; it does not constitute an LGPL review
  or establish legal sufficiency of the relinking materials.
- The current PR-head four-platform inventory, [run
  43393143407](https://github.com/heckenmann/visual-agent/actions/runs/43393143407)
  at source commit `4073f130de8bdeee7b01a4127349b5109b642435`, was downloaded
  and checked for Linux x64, Windows x64, macOS ARM64, and macOS x64. Each
  `packaged-artifacts.csv` contains 276 rows and 272 unique coordinates: 260
  Maven artifact rows covering 256 unique external Maven coordinates, 15
  first-party project modules, and the bundled JDK `jrt-fs` entry. The
  `licenses.csv` has an exact metadata row for every external Maven coordinate;
  its only 16 unmatched coordinates are the 15 project modules and `jrt-fs`.
  All four reports have the same counts. Their package-file and artifact reports
  also contain no Graal `js-isolate`. These are current dependency/inventory
  facts, not component-level license conclusions or final DEB/RPM/AppImage/DMG/
  MSI/JAR release-hash evidence.
- The review archive is deliberately separate from the recipient candidate:
  it contains internal review notes, the curation registry, and unreviewed
  package inventories. It must not be distributed as the recipient legal
  packet. The candidate run is not the final published artifact set; final
  release files still require exact-hash review after the release source is
  merged and the trusted publisher builds them.
- `release-approvals.json` remains empty. No redistribution approval is
  recorded, and no release is cleared.

### Previous Candidate Details (Run 43305484301)

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
- At the time of this historical candidate, the bundle-builder change had only
  been made locally and no new archives had been generated. It was subsequently
  committed in PR #533 and validated by the latest versioned candidate above.
  Recipient-candidate mode emits only the project license, curated legal texts
  for coordinates present in package inventories, and hash-verified
  package/runtime notice files. Its corresponding-source candidate contains
  collected source archives and a generated path/hash index rather than internal
  review notes and the full curation registry. Both omit internal status
  manifests and historical materials for components absent from the candidate.
  Repeated inventory rows can map the same Maven coordinate to distinct notice
  bytes, so recipient paths include the notice hash and identical repeated
  entries are deduplicated. The focused bundle tests pass, including checks for
  these exclusions and repeated-coordinate handling. This does not clear the
  historical artifacts above or establish legal completeness of the new ones.
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

Checked on 2026-11-13, the public [`v0.1.0` pre-release](https://github.com/heckenmann/visual-agent/releases/tag/v0.1.0)
has no attached release assets. Its tag points to source commit
`355c256ed493a4a8e8acaa38e53b298d66c7c7f5`. Therefore that GitHub release
currently offers no installer, executable JAR, legal bundle, or corresponding-
source archive for download. This observation does not establish whether anyone
obtained binaries through another channel or an expired CI artifact, and it is
not a redistribution approval. Any future binary release still requires the
exact-artifact review and approval described in `README.md` in this directory.

The current `master` workflow order is also unsafe for a future publication:
`package-smoke.yml` starts on the GitHub `release.published` event, so the
release is public before package verification and the license-approval gate in
`release.yml` runs. A failed gate cannot undo that publication. PR #533 contains
a draft-first publisher that performs verification before making the release
public, but it remains open and has not changed the current `master` workflow.
Do not publish another release until that ordering is fixed on the default
branch and the exact release candidate has passed the gate.

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
development dependency from ANGLE's DEPS file as a shipped component. The
separate D3D12MemoryAllocator dependency is configured by Skiko for its Windows
JVM runtime; it is not part of the ANGLE DLL inventory.

The pinned ANGLE BUILD.gn nevertheless includes compression and xxHash.
Its Abseil switch is `angle_has_build && is_clang`; final compiler settings
must therefore be checked rather than assuming Abseil is absent on Windows.
Original notices are now retained for bundled xxHash, DEPS-pinned Abseil
`2401283880a451e9ea10787920117fb67695abb4`, and zlib
`e8ff3f35551275330696606d7fef3a2a0f73a831`. The compression wrapper explicitly
refers to Chromium's BSD license, so that license is retained separately at
the DEPS-pinned Chromium revision, not replaced by zlib's license.

These permissive notices do not themselves require publishing Visual Agent
source or a bespoke relinking framework. The exact
[Skiko `v0.150.2` Windows JVM configuration](https://github.com/JetBrains/skiko/blob/v0.150.2/skiko/build.gradle.kts#L1453-L1455)
requests `d3d12allocator` as a static Skia link input. Its retained original MIT
license requires the copyright and permission notice, which is included in the
curated native materials. The exact Maven Windows runtime DLL hash
`e90e2e5ae3d48c7694b6cf5948953f6d007fe40478b1040353040acef118dd41` matches the
historical MSI payload; string scanning the stripped DLL did not identify the
allocator, so the pinned build configuration is the evidence for the link
input. Reconfirm the runtime-to-package hash and notice in the exact release
candidate. This scoped assessment is not final clearance: Khronos header
attribution, source-file-specific notices, actual ANGLE dependencies, and both
final ANGLE DLL payloads still require verification.

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

JNA's 5.19.1 upstream [`LICENSE`](https://github.com/java-native-access/jna/blob/5.19.1/LICENSE)
states the same Apache-2.0-or-LGPL-2.1-or-later choice. Upstream
[PR #1739](https://github.com/java-native-access/jna/pull/1739) separately
proposes adding libffi's copyright and permission notice to JNA binary
distributions; it remains open. The candidate recipient bundle already
contains the pinned 5.19.1 `native/libffi/LICENSE` bytes as
`third-party/jna-5.19.1/libffi-LICENSE`, matching the locally recorded SHA-256
`2c9c2acb9743e6b007b91350475308aee44691d96aa20eacef8e199988c8c388`. This
confirms the notice is delivered in the candidate bundle, not that the open
upstream proposal has been merged or that every final native payload has been
reviewed.

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
| D3D12MemoryAllocator | Skiko's exact Windows JVM configuration requests it as a static link input; retain the pinned MIT notice and verify the exact final Windows package hash. |

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
corresponding-source requirement. The pinned Windows JVM build configuration
maps DNG code as absent; a stripped symbol table or string scan alone would not
prove absence. Final review still verifies original notices in the actual
packages containing the DNG code.

Adobe's [DNG SDK and specification page](https://www.adobe.com/support/downloads/dng/dng_sdk.html)
was checked on 2026-11-13; Adobe lists it as updated on 2026-11-10. It publishes
a separate royalty-free patent grant for software that reads or writes
DNG-compliant files. Its required prominent notice is byte-for-byte the same
sentence retained in `third-party/dng-sdk-dbe0a676/DNG-PATENT-NOTICE.txt` and
placed in project source and documentation below. The exact Skia revision bundled through Skiko contains
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

The exact pinned Android source repository classifies this SDK as
`BY_EXCEPTION_ONLY` and includes a warning for Google's package consumers
([pinned METADATA](https://android.googlesource.com/platform/external/dng_sdk/+/dbe0a676450d9b8c71bf00688bb306409b779e90/METADATA)).
Google's [public third-party license guidance](https://opensource.google/documentation/reference/thirdparty/licenses)
describes `by_exception_only` as Google's classification for packages whose
terms conflict with Google's own uses; it is not an extra term in Adobe's
license agreement. For this project, treating that classification as a
downstream restriction would go beyond the license text. Visual Agent relies
on the retained Adobe SDK agreement and patent license, not on Google granting
permission. No external consultation is needed or has been made for this
classification. The remaining DNG review is the actual Adobe-term check and
the exact binary, prominent-notice, and final-package verification described
above; the component remains `unreviewed` until those release-specific checks
are complete. This is an evidence-based engineering interpretation, not legal
advice or a qualified legal opinion.

The notice is also kept beside the Skiko dependency in the desktop build source.
The local `verifyPackages` run confirmed curated legal files in the executable
JARs and macOS native package; the supplement regression test checks the exact
notice in the project README, UI decoder source, build source, and hash-pinned
material. This closes the local notice-placement task, but not final recipient
archive/package hash review or the exact Windows/Linux candidate mapping.

The exact [Skiko `v0.150.2` build configuration](https://github.com/JetBrains/skiko/blob/v0.150.2/skiko/build.gradle.kts)
statically links `piex` and `dng_sdk` for native targets and macOS JVM targets;
the Windows JVM target adds `d3d12allocator` but does not add either DNG library.
The matching [Skia `m150-a3f509bf52` configuration](https://github.com/JetBrains/skia/blob/m150-a3f509bf52/gn/skia.gni#L873)
sets `skia_use_piex = !is_win && !is_canvaskit`, while its
[RAW-codec target](https://github.com/JetBrains/skia/blob/m150-a3f509bf52/BUILD.gn#L1486-L1500)
requires `skia_use_dng_sdk`, JPEG decoding, and Piex. Therefore the Windows JVM
build disables the RAW/DNG codec at the Skia feature level as well as omitting
the DNG/Piex link libraries. The Windows MSI DLL matches the Maven runtime
artifact at SHA-256
`eebad490c2828198e86e641cbd44958617867ce70f893b21852f07d0be099c0a`; its stripped
symbols and lack of DNG-specific strings are consistent with, but are not the
basis for, this source-configuration conclusion. For the pinned Skiko/Skia
build, record DNG SDK and Piex as absent from the Windows JVM runtime and retain
the existing positive binary evidence for Linux x64 and both macOS runtimes.
This resolves component mapping for those exact Maven runtime binaries, not
future builds or the complete release package. Keep the DNG patent notice in
source and recipient documentation, and continue exact final-package/hash
verification; no redistribution approval is implied.

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

**Candidate technical review:** versioned non-publishing candidate run
[`43368865856`](https://github.com/heckenmann/visual-agent/actions/runs/43368865856)
generated a recipient-facing source archive. Its inspected contents include the
pinned libfuse source, applied patch, Type 2 runtime source, build/relink
instructions, source inputs and build recipes for the statically linked runtime
dependencies, and a SHA-256 manifest. This is technical evidence for the
LGPL-2.1 section 6(a) source-and-relink route, not a legal opinion or approval
of every linked component. The release workflow attaches this archive and the
separate recipient legal archive alongside the ten packages, then checks the exact
12-file payload against a review bound to the release tag and source commit.
The candidate is not a final release payload. Rebuild from the release source,
verify the final AppImage/source archive hashes and their contents, and complete
the remaining component review before publication. No AppImage release is
approved.

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

The source-material directory prepared for workflow `43187339406` was
historical evidence and is no longer retained locally. The later versioned
candidate `43368865856` rebuilt the runtime and generated a new source archive;
its remote contents and manifest hashes are documented in the latest candidate
section above. That evidence supersedes this candidate's source-input mismatch,
but does not settle whether the exact source/relink materials satisfy LGPL-2.1
section 6(a).

#### Historical Candidate Alpine Inputs Found in the Retained CI Log

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

This exposed a real mismatch in this historical candidate: its downloaded
`visual-agent-corresponding-sources.zip` contains zlib 1.3.1 and the musl r9
recipe, not the zlib 1.4 and musl r12 sources/recipe reported by its build log.
The local catalogue correction did not repair that already-produced archive;
only the later candidate's newly generated source archive addresses this
mismatch.
The log records package versions but not APK archive hashes; the exact static
object mapping and whether all retained sources are sufficient for recipient
relinking still require review. This evidence is technical, not redistribution
approval.

#### Zstandard License Choice

The candidate runtime provenance and corresponding-source archive identify
`zstd-static 1.5.6-r2`, statically linked as `-lzstd`. Alpine v3.21's package
record declares `BSD-3-Clause OR GPL-2.0-or-later`; the exact upstream
[Zstandard 1.5.6 release](https://github.com/facebook/zstd/tree/794ea1b0afca0f020f4e57b6732332231fb23c70)
also offers BSD or GPLv2. This project selects BSD-3-Clause and retains the
original upstream `LICENSE`, avoiding reliance on the GPL alternative for this
component. The versioned candidate's AppImage manifest binds the retained
notice to SHA-256
`7055266497633c9025b777c78eb7235af13922117480ed5c674677adc381c9d8` and the
runtime to SHA-256
`8c33bf1ac8a1145c0289a56781806835253a8dc81f7378cf9e742c195505a299`.
This closes the license-option question for that candidate only. Recheck the
exact final AppImage and recipient legal archive hashes after the source is
merged; the separate LGPL-2.1 review for statically linked libfuse remains
open. This scoped engineering assessment is not legal advice or release
clearance.

References: [Alpine v3.21 zstd package record](https://pkgs.alpinelinux.org/package/v3.21/main/x86/zstd),
[Zstandard 1.5.6 upstream README](https://github.com/facebook/zstd/blob/794ea1b0afca0f020f4e57b6732332231fb23c70/README.md),
[Zstandard 1.5.6 upstream LICENSE](https://github.com/facebook/zstd/blob/794ea1b0afca0f020f4e57b6732332231fb23c70/LICENSE).

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

This confirms that historical archive's integrity, not source completeness
against its actual static runtime inputs. The later candidate run
`43368865856` rebuilt the runtime with the matching pinned source/recipe inputs
and verified the generated archive remotely, as described above. It improves
the evidence for that candidate only. A qualified review of LGPL-2.1 section
6(a) and exact final release package/source hashes remains outstanding.

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
[run 43368865856](https://github.com/heckenmann/visual-agent/actions/runs/43368865856)
was checked directly: all six platform inventories retain the original vendor
readme, and each hash matches the corresponding previously inspected Zulu
archive. The matching source archive contains the supplementary
`zulu27-ca-src_27.0.1.0.3-5_amd64.deb`; its `lib/src.zip` has 15,166 Java files
but no C/C++ files or OpenJDK build metadata. It is therefore not treated as
complete corresponding source for the native JDK runtime:

| Package inventory | Retained vendor readme SHA-256 |
| --- | --- |
| Linux AppImage, DEB, RPM | `76adb733da785cd1f257bef2cfadeed5a30178e8ac1179590b5776c860609683` |
| macOS ARM64 | `ba5a54776c2990ee75398f258223515fee4f72bf3ce888603e42fe1f5947c7a3` |
| macOS x64 | `b9748e40acbc0b4933f63e886f98ec629f7231645c0cd5caa471a01c902e85bf` |
| Windows x64 | `8d0152e9611c44e4bce41ffa06a05befe4823a5ef48b35549a62246fed538312` |

The `visual-agent-legal` candidate archive in that same run was also inspected:
it contains all six files at
`platforms/<platform>/java-runtime/vendor/readme.txt`, with hashes matching
this table. The archive has a recipient-oriented
`platforms/<platform>/` layout, with root `LICENSE`, `README-LICENSES.txt`,
third-party license texts, and `MANIFEST.sha256`. Inspection of the archived
file list found only distribution materials and the checksum manifest, with no
internal review registry, package inventory, or `sources.json`; the earlier
note claiming otherwise was incorrect. All six vendor offer files were hashed
from the archive and match the platform table above. This verifies candidate
delivery layout, not legal completeness or final package identity: the run
used source commit `30232f4318c5fd594c1166822a2f832b78120821`, which predates
the current PR head. Rebuild the candidate from the final merged release
source, then inspect the actual release artifact hashes and recipient-facing
contents before treating it as final evidence.

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

### Historical Candidate Inventory (2026-11-11)

The detailed inventory below preserves the earlier 2026-11-11 triage and is
not the current candidate count. It covers older workflow runs `42678989326`,
`42688634430`, and `43064376109`. For the current six-variant candidate, use
the counts and archive hashes in [Latest Versioned Candidate](#latest-versioned-candidate-non-publishing)
above. In particular, the older 110/96/14 counts must not be treated as current
package contents. Current component rows remain unreviewed until an actual
obligation review is recorded.

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

**Historical conditional finding:** the signing analysis below applies to the
older package workflow and source revision identified there, which contained
the GFTC `js-isolate` artifacts. The later non-publishing candidate
[`43368865856`](https://github.com/heckenmann/visual-agent/actions/runs/43368865856)
reported no `js-isolate` artifact in any of its six platform package
inventories. Therefore the older re-signing finding is retained as evidence,
not treated as proof that the later candidate contains a GFTC component or as
an independently current blocker. Regenerate and inspect all inventories from
the exact final release source: if a GFTC artifact is present, repeat this
conditional review for its exact package transformation; if absent, record that
absence against the final artifact hashes.

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

This GFTC clarification is not an active blocker for the latest candidate:
workflow 43451994879, at source commit
`4073f130de8bdeee7b01a4127349b5109b642435`, contains no `js-isolate` coordinate
in any of its six platform manifests. Keep the historical transformation
evidence, confirm absence in the exact final package set, and reopen the GFTC
question only if the dependency returns. No third party has been contacted.
This conditional finding does not change the unreviewed status of other GraalJS
artifacts or grant redistribution approval.

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
2. Confirm the final six-platform packages continue to exclude Graal `js-isolate`.
   The four-platform current-PR inventory and the earlier six-platform
   candidate both show it absent, so the historical GFTC/macOS-signing finding
   is conditional rather than an active blocker. Reopen it only if the exact
   final package graph includes an isolate artifact. Continue the separate
   selected GraalJS language/runtime notice and metadata review.
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
  JONI was removed in 20.1.0. This supports the interpretation that the MIT
  declaration is stale metadata from the removed engine, but the exact POM still
  declares MIT and no upstream file-to-license mapping explains that declaration.
  Keep this artifact unreviewed until a competent reviewer determines whether
  the metadata is stale and whether any separate MIT attribution belongs in the
  recipient packet; no third party has been contacted. Source/text provenance
  is verified, but the applicable-license mapping, final recipient-archive
  inclusion, and final artifact hash review remain open.
  Evidence: [Maven POM](https://repo.maven.apache.org/maven2/org/graalvm/js/js-language/25.5.5.0.3/js-language-25.5.5.0.3.pom),
  [pinned upstream build metadata](https://github.com/oracle/graaljs/blob/dd265d542fad72d0e7e1ae4032ef7c040911e700/graal-js/mx.graal-js/suite.py#L523-L528),
  [exact source JAR](https://repo.maven.apache.org/maven2/org/graalvm/js/js-language/25.5.5.0.3/js-language-25.5.5.0.3-sources.jar),
  and [pinned upstream changelog](https://github.com/oracle/graaljs/blob/dd265d542fad72d0e7e1ae4032ef7c040911e700/CHANGELOG.md#L385-L398).
- The rebuilt server JAR, desktop JAR, and local native application contain all
  four new GraalJS legal/source notices with their registered original bytes.
- Temporary artifact-download directories were removed. Only small generated
  inventory metadata remains in ignored build reports; no downloaded runtimes
  or source archives were added to Git for this review.

These checks validate the prepared corrections, not the outstanding final
release decision. They do not substitute local development JDK/package hashes
for the exact CI candidate runtime and final download hashes.

### Candidate Metadata Triage (2026-11-13)

I downloaded only the six `license-notices-*` evidence artifacts from candidate
run `43451994879` and deduplicated their component rows by exact coordinate. The
260 unique coordinates are all still marked `unreviewed`. A broad, non-normative
grouping of their Gradle License Report strings found 221 coordinates with an
Apache-named declaration and no GPL/EPL/LGPL/MPL/GFTC-like string, 32 with other
declared strings and no such Apache/special-term string, six with at least one
GPL/EPL/LGPL/MPL/GFTC-like string, and one bundled JDK `jrt-fs` entry with no
declared metadata. These buckets are only a way to order human review: metadata
strings may contain alternatives, be stale, or omit embedded/native terms, and
they do not determine which license applies. The six flagged coordinates are
Logback Classic/Core 1.6.6 (EPL-2.0 and LGPL-2.1-only reported), H2 2.5.252
(EPL-1.0 and MPL-2.0 reported), Jakarta Annotations 3.0.0 (EPL-2.0 or GPL-2.0
with Classpath Exception reported), and JNA/JNA Platform 5.19.1 (Apache-2.0 and
LGPL-2.1-or-later reported). The local registry already records proposed
routes and the retained source/notice materials for these four review groups.
The exact-version upstream publications clarify the metadata as follows:

- Logback 1.6.6 is explicitly dual-licensed under EPL-2.0 or LGPL-2.1; the
  registry's proposed EPL-2.0 route is consistent with the upstream license
  statement. Both exact source JARs, the EPL text, and source-availability
  material are present in the generated source-material tree. See the
  [upstream license](https://github.com/qos-ch/logback/blob/v_1.6.6/LICENSE.txt).
- H2 2.5.252 explicitly offers MPL-2.0 or EPL-1.0. The registry proposes
  MPL-2.0; its original license, binary disclaimer, source-availability
  statement, and exact source JAR are retained. H2's own guidance specifically
  describes the unmodified-binary distribution case and its notice needs. See
  the [versioned upstream license](https://github.com/h2database/h2database/blob/version-2.5.252/LICENSE.txt)
  and [H2 license guidance](https://h2database.com/html/license.html).
- JNA 5.19.1's versioned upstream license explicitly offers Apache-2.0 or
  LGPL-2.1-or-later. The registry proposes Apache-2.0 and separately retains
  libffi's MIT notice for the statically linked native component; both exact
  JNA source JARs are included in generated source materials. See the
  [versioned JNA license](https://github.com/java-native-access/jna/blob/5.19.1/LICENSE).
- Jakarta Annotations 3.0.0 retains the original upstream LICENSE and NOTICE,
  which identify EPL-2.0 and the conditional GPL-2.0-with-Classpath-Exception
  secondary-license route. Its exact source JAR is included in generated
  source materials. The conditions for that secondary route are not assumed;
  see the [versioned upstream license](https://github.com/jakartaee/common-annotations-api/blob/3.0.0/LICENSE.md)
  and [NOTICE](https://github.com/jakartaee/common-annotations-api/blob/3.0.0/NOTICE.md).

These publications and generated-material checks narrow the metadata ambiguity;
they do not decide legal applicability or verify the eventual public packages.
The final recipient archive and package hashes still need review, including
JNA's signed macOS payload and the exact EPL/secondary-license terms. All four
groups remain `unreviewed`, and no redistribution approval was added. The
downloaded candidate evidence was removed after the count; authoritative
manifests and matching hashes remain the linked workflow artifacts.

### Apache NOTICE Checks: HikariCP, Gson, and JetBrains Annotations

The Linux AppImage candidate manifest maps `com.zaxxer:HikariCP:7.1.0` to
packaged JAR SHA-256
`2be45c641b841e81df87292e181f2c203500c5f0e9a1676f135927387f3cf40b` and
reports no embedded license/NOTICE entry. The exact upstream POM identifies
version 7.1.0, tag `HikariCP-7.1.0`, and Apache-2.0. Its pinned root `LICENSE`
is the Apache-2.0 license text; a `NOTICE` file at that same tag is absent.
The recipient candidate includes the project's Apache-2.0 license text. Under
Apache-2.0 section 4(d), additional attribution text is conditional on the
upstream Work including a NOTICE file. On this exact evidence, no
HikariCP-specific NOTICE supplement is identified for 7.1.0. See the
[versioned POM](https://raw.githubusercontent.com/brettwooldridge/HikariCP/HikariCP-7.1.0/pom.xml),
[versioned LICENSE](https://raw.githubusercontent.com/brettwooldridge/HikariCP/HikariCP-7.1.0/LICENSE),
[pinned NOTICE path](https://raw.githubusercontent.com/brettwooldridge/HikariCP/HikariCP-7.1.0/NOTICE),
and [Apache-2.0 section 4](https://www.apache.org/licenses/LICENSE-2.0.html).

This closes only the upstream NOTICE lookup for the exact HikariCP 7.1.0
candidate artifact. It does not review all Apache-metadata dependencies,
replace final public-package hash checks, or grant redistribution approval;
the candidate component row remains `unreviewed`.

The same exact-candidate review also covered two additional components:

- `com.google.code.gson:gson:2.14.0` has candidate JAR SHA-256
  `2cbd119bf1961c28788310963dc80ba65f58cdeec1dd139c8bdb1240faa2c36f` and no
  detected JAR notice. Its versioned POM declares Apache-2.0, and the exact
  upstream release tag has a LICENSE but no root `NOTICE` or `NOTICE.txt`.
  The recipient candidate includes the generic Apache-2.0 text. See the
  [versioned POM](https://raw.githubusercontent.com/google/gson/gson-parent-2.14.0/gson/pom.xml),
  [versioned LICENSE](https://raw.githubusercontent.com/google/gson/gson-parent-2.14.0/LICENSE),
  and [pinned NOTICE path](https://raw.githubusercontent.com/google/gson/gson-parent-2.14.0/NOTICE).
- `org.jetbrains:annotations:23.0.0` has candidate JAR SHA-256
  `7b0f19724082cbfcbc66e5abea2b9bc92cf08a1ea11e191933ed43801eb3cd05` and no
  detected JAR notice. The matching upstream version tag's `LICENSE.txt`
  identifies Apache-2.0; root `NOTICE` and `NOTICE.txt` are absent. The
  recipient candidate includes the generic Apache-2.0 text. See the
  [versioned LICENSE](https://raw.githubusercontent.com/JetBrains/java-annotations/23.0.0/LICENSE.txt)
  and [pinned NOTICE path](https://raw.githubusercontent.com/JetBrains/java-annotations/23.0.0/NOTICE).
- `net.minidev:json-smart:2.6.1` (JAR SHA-256
  `30f83e94e2ed0fe0e090b5d00fcae8bc0c95aa6883d3b9601ea67fc51418237e`) and
  `net.minidev:accessors-smart:2.6.1` (JAR SHA-256
  `05d0bbc1ca20163b8ed60a45596d95ed526a71732c47e5cc58e6454d6a5c9694`) both
  have no detected JAR notice. Their exact Maven POMs declare Apache-2.0 and
  point to the same upstream repository; its `v2.6.1` tag has a LICENSE but no
  root `NOTICE` or `NOTICE.txt`. The recipient candidate includes the generic
  Apache-2.0 text. See the [json-smart POM](https://repo.maven.apache.org/maven2/net/minidev/json-smart/2.6.1/json-smart-2.6.1.pom),
  [accessors-smart POM](https://repo.maven.apache.org/maven2/net/minidev/accessors-smart/2.6.1/accessors-smart-2.6.1.pom),
  [versioned LICENSE](https://raw.githubusercontent.com/netplex/json-smart-v2/v2.6.1/LICENSE),
  and [pinned NOTICE path](https://raw.githubusercontent.com/netplex/json-smart-v2/v2.6.1/NOTICE).

For these five artifacts, this narrows only the conditional upstream NOTICE
question for the exact candidate versions. It does not clear other Apache
dependencies, establish final public-package hashes, or change the component
rows' `unreviewed` status or the empty redistribution-approval registry.

### Kotlin Standard-Library NOTICE Scope

The Linux AppImage candidate contains these Kotlin 2.4.21 artifacts, each with
`noticeStatus: no-detected-notice` and no notice entry in its JAR: `kotlin-stdlib`
(SHA-256 `bd8250210584cb659847dce9cda660f8e9906e5def7fbebcea93dc6dcb6a88a3`),
`kotlin-reflect` (`7c9e8bda3cafa85b65692065acd22578b2843292b4e4baa8361bdc9175746e88`),
`kotlin-stdlib-jdk7` (`6fce867d4c24c92a8b64c966317d3ac1d4b787a00e1aa6d2b76b1cedd3a0c082`),
and `kotlin-stdlib-jdk8` (`eb83cbc07ee68fcd5f229b2ed1d66c7a0175c56ac1dca1457cce4b1702004259`).
Their exact Maven POMs declare Apache-2.0. The Kotlin `v2.4.21` source tree
does contain `license/NOTICE.txt`, but that file explicitly scopes itself to
the Kotlin Compiler distribution; it is not a general Kotlin-runtime NOTICE.
The same tag has no NOTICE or LICENSE file under `libraries/stdlib`, and the
candidate package inventory contains no Kotlin compiler coordinate. The
recipient candidate includes the generic Apache-2.0 text. See the
[versioned compiler NOTICE](https://raw.githubusercontent.com/JetBrains/kotlin/v2.4.21/license/NOTICE.txt),
[versioned license](https://raw.githubusercontent.com/JetBrains/kotlin/v2.4.21/license/LICENSE.txt),
[stdlib source path](https://github.com/JetBrains/kotlin/tree/v2.4.21/libraries/stdlib),
and [Kotlin 2.4.21 release](https://github.com/JetBrains/kotlin/releases/tag/v2.4.21).

This evidence does not treat the compiler-distribution NOTICE as applicable to
the separately published standard-library artifacts. The four candidate rows
remain `unreviewed` pending final package/hash review; no redistribution
approval was added.

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
