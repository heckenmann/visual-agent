# Corresponding-source delivery worklist

Refs #410. This document records verified source inputs and remaining work, not
redistribution approval. The publication gate remains closed.

## Verified Maven Source Inputs

The following exact-version source archives were downloaded from Maven Central
and opened as ZIP archives during the release investigation on 2026-10-31.
Hashes identify those downloaded bytes, not a legal determination of completeness.

| Component | Source archive | SHA-256 |
| --- | --- | --- |
| H2 2.5.252 | [h2-2.5.252-sources.jar](https://repo.maven.apache.org/maven2/com/h2database/h2/2.5.252/h2-2.5.252-sources.jar) | `ff3d277f962df614283d0a754949eea1e4685ed645f603a590f19c5dbbdb83b3` |
| Logback Core 1.6.6 | [logback-core-1.6.6-sources.jar](https://repo.maven.apache.org/maven2/ch/qos/logback/logback-core/1.6.6/logback-core-1.6.6-sources.jar) | `2da2e76aa79f98770ffba58311b495e0e710b8bc6f46504249d039262668fe96` |
| Logback Classic 1.6.6 | [logback-classic-1.6.6-sources.jar](https://repo.maven.apache.org/maven2/ch/qos/logback/logback-classic/1.6.6/logback-classic-1.6.6-sources.jar) | `e3a8a1a00701c54f0b23c2c1427c307e9481da6c0696a7abf3a38a77322288d0` |

The H2 archive contains 847 files including Java sources and runtime resources.
The Logback Core archive contains 521 files including Java sources and its Maven
POM. File counts alone do not establish complete build inputs or license coverage.
Keep the pinned upstream commits in `third-party/sources.json` as additional
provenance; do not substitute a moving branch or a different patch version.

## Delivery Approach

`./gradlew :desktop:prepareLibrarySourceMaterials` now collects this partial
evidence under `modules/desktop/build/reports/dependency-license/library-source-materials/`.
It uses Gradle's [source-variant artifact view](https://docs.gradle.org/current/userguide/artifact_views.html)
on the selected desktop runtime graph, without independent version resolution.
Missing source resolution fails rather than silently skipping a component. The
directory includes original curated legal inputs and this scope statement. It is
not the complete corresponding-sources ZIP and is not published automatically.
The four-platform `License inventory` workflow also collects and uploads this
directory with its review evidence; this upload is not a public release asset.

Use existing Gradle dependency resolution and archive tasks rather than adding a
second dependency scanner or a custom source downloader. Keep source artifacts
in build outputs, not in Git. The existing Gradle License Report remains the
metadata inventory; its reports are not a substitute for source delivery.

1. Resolve exact source classifiers for the applicable packaged components.
   Match them to the actual packaged dependency coordinates, not the broader
   build/test dependency inventory. Fail on missing or changed required inputs.
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
  notices, and inform recipients how to obtain the covered source. Mozilla's
  [official FAQ](https://www.mozilla.org/en-US/MPL/2.0/FAQ/) specifically notes
  additional notice considerations when redistributing libraries rather than
  unchanged complete applications.
- Logback's proposed option is EPL-2.0. Review section 3.1's source-availability
  notice and binary-distribution conditions, plus sections 3.2 and 3.3 for source
  licensing and retained notices. The [official license](https://www.eclipse.org/legal/epl-2.0/)
  remains authoritative. Do not label the whole application EPL: the project's
  own code remains separately MIT-licensed.
- The project is noncommercial by owner confirmation. This does not remove the
  source/notice requirements above; do not infer a general noncommercial waiver.

## Other Unresolved Source Inputs

- Zulu CA JDK `27.0.1.0.3+1`: obtain the complete exact-build corresponding source
  and relevant build material. A Java-only `src.zip` or another vendor's OpenJDK
  revision does not prove equivalence. The owner deferred contacting Azul while
  other legal materials are addressed; no request has been sent.
- AppImage Type 2 runtime `20251108`: identify the exact shipped runtime's static
  LGPL components, matching source and build/relink requirements. The license of
  appimagetool alone does not cover the embedded runtime.
- JNA/native payloads and other packaged native/font resources: finish component
  attribution and determine applicable obligations from actual packaged bytes.

No item in this document closes #410. The source bundle, recipient notice,
component completeness review, and final clearance are not yet implemented.
