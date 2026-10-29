# UC 0000156: Retain Runtime Legal Materials

## Goal

Keep public source-JDK identity and original vendor notices with native packages,
even when jlink removes most build metadata from the trimmed runtime.

## Main Flow

1. Gradle uses the same resolved JDK for legal materials and native packaging.
2. A cacheable build task retains the vendor's legal tree and root notice/readme
   files verbatim, resolving only file links that remain inside that source JDK.
3. It writes an allowlisted set of public build metadata and SHA-256 identities
   for the original modules image, keytool, jlink, jpackage, release, and copied notices.
4. Compose includes these materials through its documented common app-resources
   directory. The project's MIT license is included separately.
5. Native verification checks the source identities and unchanged notices, then
   records packaged modules/keytool hashes separately outside the signed bundle.
   jlink and macOS jpackage legitimately transform these binaries; this report
   records the actual output rather than claiming byte equality with the source JDK.

## Failure Flow

Missing mandatory legal files, missing exact runtime/build metadata, empty inputs,
external file links, or linked legal directories stop packaging. Unknown host
metadata and absolute JDK installation paths are not added to the provenance file.

## Limits

The materials identify and retain inputs; they are not complete corresponding
source, a dependency-wide notice bundle, or automatic redistribution clearance.
The release approval registry remains unchanged and #410 remains open.

## Tool Calls

- None.

## Code Entry Points

- `buildSrc/src/main/kotlin/de/heckenmann/visualagent/gradle/PrepareRuntimeLegalMaterials.kt`
- `modules/desktop/build.gradle.kts`
- `scripts/test_release_runtime.py`
