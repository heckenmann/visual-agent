# Release Materials

Issue #410 tracks license notices and source materials shipped with Visual Agent.
The project reuses Gradle License Report for dependency metadata and packages
original notices with the application. The legal archive contains recipient-facing
license materials; the corresponding-sources archive contains collected source and
build inputs for components that need them.

## Release Workflow

The maintainer creates a GitHub draft release and manually starts the publisher
from the trusted default branch. The workflow selects a successful versioned
candidate built from the exact tag commit, verifies exactly ten packages and two
recipient archives are present, rejects empty files and review-only archives,
attaches the files, and publishes the draft last. Missing, extra, corrupt, or
review-only files stop the workflow.

This is a build and asset check, **not legal clearance**. The manual dispatch is
the maintainer's explicit publication action. The workflow does not infer license
compliance from POM metadata, a green build, or the presence of a license file.
Consult the [current review](release-review.md) for known gaps in component
attribution, source delivery, and native runtime coverage. Do not describe this
automation as legal advice or as an enforced restriction on repository writers.

The latest non-publishing candidate is [workflow run
43490076062](https://github.com/heckenmann/visual-agent/actions/runs/43490076062),
source commit `85db38169cfdf6ba644b93dd6191fdf4df48729d`. All ten package builds,
all ten platform smoke tests, and legal/source archive generation passed. The run
is non-publishing; its files are candidate evidence, not final public release
assets. Known unresolved findings remain documented in the current review; this
workflow does not mark them complete.

`scripts/verify-release-assets.py` checks only the recipient file set and archive
integrity. The separate reviewer-attribution/hash registry was removed: the
publisher already selects a successful candidate run for the exact tag commit,
and publishes the artifacts from that run. Source-input and archive-content hashes
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
