# Release Materials

Issue #410 tracks license notices and source materials shipped with Visual Agent.
The project reuses Gradle License Report for dependency metadata and packages
original notices with the application. The legal archive contains recipient-facing
license materials; the corresponding-sources archive contains collected source and
build inputs for components that need them.

## Release Workflow

The [release guide](../releases.md) is the operational source of truth. A version
tag push starts builds, legal/source material generation, and package smoke tests
in one workflow. After all jobs succeed, it attaches exactly ten packages and two
recipient archives to a draft. The maintainer publishes through GitHub's UI.
Manual dispatch performs validation only and does not write releases.

This checks build provenance and file/archive integrity, **not legal clearance**.
Consult the [review](release-review.md) for unresolved attribution, source delivery,
and native-runtime findings. Publication does not mark those findings complete.

On November 15, 2026, [v0.1.1](https://github.com/heckenmann/visual-agent/releases/tag/v0.1.1)
was published with all twelve assets using the former candidate/publisher process.
Its source was `9f4b4b558a9656edfb4b76283a7b5a73e83b4291`; the successful package
run was [43501499023](https://github.com/heckenmann/visual-agent/actions/runs/43501499023).
That is historical delivery evidence, not an end-to-end run of the tag-driven workflow.

`scripts/verify-release-assets.py` checks only the recipient file set and archive
integrity. The separate reviewer-attribution/hash registry was removed: the
draft job uses only artifacts from the same successful tag workflow run. Source-input and archive-content hashes
remain where they protect pinned inputs and generated materials; they are not
duplicated in a manual release-approval file.

## Runtime Materials

`./gradlew :desktop:prepareRuntimeLegalMaterials` collects original source-JDK
legal files and vendor readme/notice files into the native application resources.
The project MIT license is separate under `legal/visual-agent`. The native
distribution retains these files before signing; signed bundles are not patched
after packaging. Runtime provenance is recorded outside signed application
bundles. These measures preserve notices and build evidence but do not establish
complete corresponding-source delivery or legal sufficiency.

CI pins Zulu Community Availability JDK `27.0.1.0.3+1`. Its version-specific
source-offer material is retained with packages. Whether this offer satisfies the
conditions for the exact redistributed and transformed runtime must be assessed
from the [source-delivery worklist](source-delivery.md); no source request was
sent. Switching JDK vendors would not automatically remove the obligations.

## Third-Party Notices

The existing Gradle License Report supplies dependency metadata. The package
notice collector retains original notice bytes and adds curated upstream/native
materials where applicable:

```bash
python3 scripts/collect-third-party-notices.py \
  --package-root modules/desktop/build/compose/binaries/main/app \
  --inventory modules/desktop/build/reports/dependency-license/packaged-artifacts.csv \
  --license-report build/reports/dependency-license/licenses-all.json \
  --project-license LICENSE \
  --output build/reports/dependency-license/third-party-notices \
  --supplements docs/licensing/third-party/sources.json
```

The collector verifies packaged JAR hashes and records the metadata snapshot. It
does not turn POM declarations into reviewed license conclusions. Native
components, fonts, selected dual-license terms, required modifications, and
source/relink conditions still require evidence-based review. Reuse the existing
Gradle report and collector rather than adding another scanner or policy engine.

`AboutLibraries` was assessed as an alternative display library; Gradle License
Report remains the inventory source. Neither automatically covers all native
redistribution obligations.
