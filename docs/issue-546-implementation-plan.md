# Issue #546 Implementation Plan

## Objective and Scope

Replace all repository-owned Python tooling with Gradle-managed Kotlin and
platform shell orchestration while preserving release, packaging, licensing
and verification behavior. The policy is defined in [Architecture](architecture.md).
The implementation base is `c3c4c8d9`; development uses
`codex/issue-546-remove-python`. See [issue #546](https://github.com/heckenmann/visual-agent/issues/546).

The baseline contained 17 Python files: six implementations/helpers and eleven
test drivers. The migration covers workflows and path filters, Gradle tasks,
script callers, test discovery, operational instructions, and use-case entry points.
Original upstream legal texts and historical audit evidence remain unchanged.

## Implementation Sequence

1. Inventory files and callers, capture the legacy regression baseline.
2. Evaluate maintained libraries and establish a tooling-only Gradle boundary.
3. Migrate asset verification, AppImage prefix checks, and supplemental registries.
4. Migrate notice collection and legal/source archives; compare identical fixtures.
5. Migrate real Gradle packaging fixtures through TestKit.
6. Replace POSIX smoke orchestration, retaining native runtime independence.
7. Update callers and documentation, remove all legacy implementations and tests,
   enforce the language policy, and verify full quality and real package checks.

Steps 1–6, the source/caller migration, and local quality/package verification in
step 7 are implemented. Supported-platform package/launch CI remains outstanding.
The implementation is being recorded in the commit sequence below.

## Commit Plan

Use one topic branch and one focused PR for the complete language migration. Split
the history by responsibility in the following order. Each commit includes its
production code, equivalent regression coverage, affected callers, and directly
related use-case/operational documentation. Reference `Refs #546` in commit bodies;
do not close the issue while platform verification is outstanding.

1. **`docs(build): define repository tooling language policy`**
   - Commit the policy in `AGENTS.md` and `docs/architecture.md`, together with this
     implementation plan and its library/layout rationale.
   - Keep the explicit migration-input qualification until the implementation
     commits remove the existing scripts. Introduce the executable policy guard last.
2. **`refactor(build): migrate release asset verification to Kotlin`**
   - Establish `:release-tools`, its Gradle/catalog/quality-gate integration, the
     generated CLI distribution, and the small commented shell entry point.
   - Add `ReleaseAssets`, its CLI command and asset/workflow fixtures. Switch draft
     attachment and same-run verifier distribution/download in `release.yml`.
   - Remove the asset verifier and its asset/workflow Python tests. Update UC 0000155
     and the recipient-verification instructions.
   - Add only currently implemented CLI branches and dependencies; integration-test
     task wiring and later commands arrive with their corresponding commits.
3. **`refactor(build): migrate package notice and runtime evidence collection`**
   - Add strict JSON/CSV input handling, notice extraction/manifests, supplemental
     and runtime mappings, AppImage prefix/version evidence, and bounded subprocesses.
   - Include collector, AppImage, registry, and failure tests plus required libraries.
     Switch package/license-inventory collection and the desktop embedded-runtime check.
   - Remove replaced collector/AppImage/supplement test drivers and the collector
     implementation. Keep the two Python helpers still imported by the source driver
     until commit 5. Update UC 0000157 and collector documentation with the callers.
4. **`refactor(build): migrate legal and source archive generation`**
   - Add the bundle package, recipient selection, deterministic ZIP writing,
     evidence validation, CLI branch, and complete positive/negative archive fixtures.
   - Switch both review and recipient archive workflow commands; remove the old
     builder and its test driver. Retain the documented ZIP-container difference,
     identical extracted bytes/manifests, and repeated-output reproducibility evidence.
5. **`test(build): migrate release packaging contracts to Gradle TestKit`**
   - Add real source/version/runtime/bundled-legal fixture tests, shared TestKit/TAR
     helpers, test-only XZ, and the tagged integration task included in module `check`.
   - Remove the four replaced packaging/version/source test drivers and the two
     remaining implementation helpers once their final consumers are migrated.
   - Update UC 0000156 and the packaging test entry points.
6. **`refactor(ci): migrate POSIX package smoke tests to Bash`**
   - Add the extensively commented POSIX harness and its real-process/command tests,
     including timeout, shutdown escalation, process isolation, and cleanup failures.
   - Switch Linux/macOS launch commands, remove Python-only native-container setup,
     and retain the Windows harness and the entire OS/architecture/artifact matrix.
   - Remove the old POSIX implementation and smoke driver. Switch release-contract
     discovery to both Kotlin test tasks; update UC 0000146 and local test commands.
7. **`test(build): enforce the repository tooling language policy`**
   - Add the repository inventory/invocation guard after all Python files are gone.
     Complete the caller/path-filter audit and final verification record.
   - Ensure all operational documentation describes the final commands and keep
     original upstream license texts and historical audit records unchanged.

Several commits touch shared files such as `ReleaseToolsMain.kt`, the module build
script, the version catalog, and `release.yml`. Reconstruct their intermediate
contents deliberately rather than staging their entire final versions in commit 2.
In particular, `bundleRequest` references bundle types and must arrive with commit 4.
Move affected workflow assertions with each caller change, including assertions
inside a legacy driver that is removed in a later commit. This prevents intermediate
commits from referencing absent symbols or retaining stale invocation expectations.

Validate each reconstructed commit in a separate clean worktree: format affected
modules, run the applicable smoke/contract tests, then the required
`./gradlew ktlintCheck check test` before committing. Native package verification
and supported-platform CI remain required for the final branch. Preserve the
pre-existing `.gitignore` edit outside this change; do not stage it incidentally.

## Issue Update Plan

- Before recording implementation commits, add `status: in progress` while retaining
  `enhancement`. Keep the goal, scope, and original acceptance criteria intact.
- After each commit, add the English progress comment required by AGENTS.md: link
  its hash, summarize the migrated responsibility and verification, and identify
  the remaining work. Use the relevant existing use-case links.
- Add an implementation/status section to the issue body linking this plan and,
  once created, the single PR. Record the isolated compiled CLI decision, library
  choices, `.kt`/`.kts` distinction, and ZIP-container difference explicitly.
- Mark acceptance items only from matching evidence. Record the 17 removed files,
  105 regular plus 23 integration tooling tests, successful local quality/package
  checks, and manual registry verification as local evidence, with the commit tested.
  Revalidate those results after reconstructing the commit sequence.
- Leave native startup and the complete supported-platform CI criteria unchecked
  until the actual jobs succeed. Link the exact runs and distinguish fixture,
  package-structure, and real-artifact startup results.
- After all required CI passes, update the remaining checklist and final summary.
  Issue closure follows the explicitly authorized PR/merge workflow; do not close
  it merely because local checks pass.

The user authorized commit creation, push, PR creation, and issue updates on
2026-11-17, satisfying the explicit confirmation required by AGENTS.md.

## Tooling Boundary and Commands

`:release-tools` is an isolated Gradle Application module with no project dependencies.
Application modules must not depend on it, and its dependencies must not enter
application runtime classpaths or distributable packages.

```bash
./gradlew :release-tools:installDist
bash scripts/release-tools.sh verify-assets --assets DIRECTORY [--uploaded]
bash scripts/release-tools.sh verify-supplements --registry FILE
bash scripts/release-tools.sh collect-notices --package-root DIRECTORY --inventory FILE --output DIRECTORY --license-report FILE --project-license FILE
bash scripts/release-tools.sh build-bundles --source-materials DIRECTORY --notice-evidence DIRECTORY --output-directory DIRECTORY [--recipient-candidate]
bash scripts/release-tools.sh verify-appimage-runtime SOURCE_RUNTIME APPIMAGE
./gradlew :release-tools:test :release-tools:releaseIntegrationTest
```

The collector also accepts supplemental and complete AppImage input options; see
[UC 0000157](usecases/uc_0000157_retain_third_party_notices.md). Uploaded verification
reads all pages of GitHub asset metadata from standard input. The wrapper locates
the generated launcher while preserving the caller's working directory and arguments.

The CLI uses compiled `.kt` sources; `.gradle.kts` configures its build. Official
[Kotlin console applications](https://kotlinlang.org/docs/jvm-get-started.html) and
[Gradle's Application plugin](https://docs.gradle.org/current/userguide/application_plugin.html)
provide this model, including generated Unix/Windows launchers and `installDist`.
[Kotlin scripts](https://kotlinlang.org/docs/command-line.html) are valid alternatives;
this is a project choice for shared, substantial verification logic and tests,
not a universal rule against `.kts`. No custom scripting host, separate compiler
installation, or ad hoc script dependency resolution is needed.

## Library Decisions

- Reuse catalog-pinned Apache-2.0 `kotlinx.serialization` for JSON trees, evaluated
  through [klibs.io](https://klibs.io/project/Kotlin/kotlinx.serialization) and
  [upstream](https://github.com/Kotlin/kotlinx.serialization).
- Use Apache-2.0 Jackson Core, version-managed by the existing Spring Boot BOM,
  for [strict duplicate-key detection](https://javadoc.io/static/tools.jackson.core/jackson-core/3.2.2/tools.jackson.core/tools/jackson/core/StreamReadFeature.html)
  before constructing JSON trees. Escaped and nested duplicate keys must fail;
  no custom JSON parser, Spring runtime, or databinding integration is introduced.
- Use Apache-2.0 [Commons Compress](https://github.com/apache/commons-compress)
  1.28.0 for Unix ZIP metadata, including symlink detection, and deterministic
  archive writing. The JDK ZIP API alone lacks required Unix metadata.
- Use Apache-2.0 [Commons CSV](https://github.com/apache/commons-csv) 1.14.1 for
  quoted inventory fields and header-based records instead of a custom CSV parser.
- Use NIO, `MessageDigest`, and CRC32 for confinement, hashes, and explicit CRC/size
  validation. Maintained archive libraries do not replace entry/aggregate limits
  or the application's evidence and path policies.
- Use official [Gradle TestKit](https://docs.gradle.org/current/userguide/test_kit.html)
  with the current distribution and test JDK for actual fixture builds. State is
  under the module's ignored `build/` directory. Tagged integration tests prepare
  actual source materials and are included in `check`.
- Test-only [XZ for Java](https://tukaani.org/xz/java.html) 1.12, under 0BSD, supplies
  XZ decoding for source TAR inspection. It is absent from CLI/application runtimes.

Material decisions are also linked from UC 0000157. Kotlin source size, KDoc,
unused-code, formatting, and module dependency checks include the new tooling.

## Preserved Contracts

The Kotlin tests retain malformed/missing/duplicate metadata failures, traversal
and symlink escape rejection, bounded reads, corrupt ZIP handling, exact evidence
hashes, source-versus-binary identities, pending legal review qualifications, and
all twelve local/remote recipient asset identities. Workflow fixtures retain tag
and ref guards, same-run artifact provenance, draft-only attachment, retry behavior,
existing notes/unrelated assets, and manual-dispatch publication restrictions.

The source integration suite checks seven required source JARs, Zulu hashes, all
11 AppImage source archives/recipes, libfuse relink instructions, Liberation font
sources, original legal bytes, missing dependencies, version propagation, bytecode
mismatches, and runtime/bundled-legal corruption. These execute production Gradle
logic rather than replacing it with test-only implementations.

The POSIX entry point is `scripts/smoke-release-posix.sh`. It preserves package
metadata lookup, isolated user/data roots, readiness markers, nonempty H2 state,
workspace creation, bounded startup/shutdown, process-group ownership, uninstall,
and bounded DMG detach retries. Xvfb publishes its allocated display through
`-displayfd`; a running process alone does not establish readiness. Bash requires
no external JVM or Kotlin installation for native packages. JAR checks retain an
explicit system JDK. Windows retains its existing PowerShell harness.

Both release CI and the independent `release-contracts` job invoke the two tooling
test tasks. Native smoke containers no longer install Python. All 17 legacy files
are removed; a repository inventory test rejects script extensions, shebangs,
setup actions, and direct interpreter invocations that violate the language policy.

## Comparison Evidence and Limits

The legacy suite ran 173 tests, initially with four runtime-fixture failures caused
by a Java-24 launcher loading Java-25 build-task classes. Rerunning all 16 runtime
fixtures with the required JDK 27 passed. These were launcher mismatches, not
legal-material behavior changes.

An identical collector fixture produced byte-for-byte equal output directories,
including the manifest. A complete synthetic six-platform archive fixture compared
both review/recipient modes and both ZIP outputs: entry order, every extracted byte,
hash manifests, timestamps, Unix permissions, and compression methods match.
Repeated Kotlin outputs are byte-for-byte reproducible.

ZIP container bytes intentionally follow Commons Compress's standard stored-ZIP
encoding: extraction version 1.0 and, for streamed content, a reserved local ZIP64
size field. The former writer emitted version 2.0 without that field. Whole-archive
hashes therefore change across the migration; same-run hashes are computed and
verified as before. No custom ZIP-header patching imitates the removed writer.
Payload identities, recipient selection, review markers, and approval remain intact.

Final local verification passed on 2026-11-17:

- After module formatting, `./gradlew ktlintCheck check test verifyPackages`
  completed successfully, including coverage, KDoc, LOC, module boundaries,
  both executable JARs, and the current-platform native distribution verifier.
- The tooling suite executed 105 regular tests and 23 integration tests. Twelve
  POSIX process/command fixtures include success, startup failure, timeout,
  shutdown escalation, uninstall failure, DMG cleanup, and unrelated-process isolation.
- A manual installed-CLI invocation verified the real registry's 103 supplemental
  artifacts and one runtime mapping, retaining its explicit unreviewed status.
- Both executable JAR inventories contain no release-tool classes/module, Commons
  CSV, or Commons Compress. The enforced project dependency graph also excludes
  tooling from application runtime dependencies.
- Bash syntax and `git diff --check` pass. Repository script/caller audit found no
  remaining Python implementation; upstream references in original legal/audit
  materials remain intentionally unchanged.
- No `build/visual-agent-smoke.*` scratch directories remain; disk free space is
  56 GiB after local package verification.

Mocked commands and local package structure checks do not prove native startup.
Required real package-and-launch CI across the supported OS, architecture, and
artifact matrix remains necessary before issue completion. Commit/push requires
the explicit user confirmation defined in AGENTS.md.
