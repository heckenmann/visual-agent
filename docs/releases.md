# Release Guide

## Prepare and Publish

1. Merge the intended changes into `master`, including the release workflow itself.
2. Choose an unused `v`-prefixed SemVer tag on a commit contained in `master`:

   ```bash
   git fetch origin master --tags
   git tag -a v0.1.2 origin/master -m "Visual Agent v0.1.2"
   git push origin v0.1.2
   ```

3. Open **Actions → Build and prepare desktop release** and wait for that tag's run
   to succeed. All jobs check out the event commit. Gradle takes the application
   version from the tag without modifying the catalog.
4. Open the draft in **Releases**. Its title is exactly the tag, such as `v0.1.2`.
   Verify its tag, notes, prerelease flag, and twelve
   files: six native packages, four platform-specific JARs, `visual-agent-legal.zip`,
   and `visual-agent-corresponding-sources.zip`. Add or edit release notes as needed.
5. Click **Publish release**. No second workflow or local asset upload is needed.

The workflow validates the tag and its ancestry before building and again before
draft attachment. The original push must create that exact tag without a force
update or deletion. Reruns validate the same original event. All quality gates, platform builds, legal/source-material
generation and ten platform smoke tests must succeed before the draft job starts.
Only the draft job receives `contents: write`; build and smoke jobs have read access.
Its downloads use the current `GITHUB_RUN_ID`, never a different candidate run.
After upload, it reads GitHub's asset metadata back and verifies all twelve files'
uploaded state, byte sizes, and SHA-256 digests against the local build files.

SemVer prerelease suffixes such as `-beta.1` create prereleases; `+build.7` alone
does not. Existing drafts with conflicting prerelease classification are rejected
for maintainer review. Native installer versions use the numeric version core and
retain the existing MSI limits (major/minor ≤255, patch ≤65535). Full versions are
retained in application and JAR metadata. See [local reproduction](setup.md#release-versions).

## Failures and Retries

- If any required job fails, no release attachment runs. An existing draft and its
  assets are left untouched by that failed build.
- Use **Re-run failed jobs** on the same run. Successful jobs and their retained
  artifacts can be reused. Partial draft uploads can be retried this way too.
- Artifact uploads use unique per-platform names and `overwrite: true`, allowing
  rerun jobs to replace their own same-run artifacts without name collisions.
- A draft retry replaces only the twelve expected asset names. It preserves
  maintainer notes and unrelated assets. An existing draft's title is normalized to
  the exact tag. No workflow step publishes the draft.
- Same-tag workflow runs are serialized with cancellation of active runs disabled.
  GitHub may replace an older pending run when another run joins the same group.
- Missing or expired artifacts must not be substituted from another run. Re-run
  all jobs to rebuild them while GitHub still permits reruns; otherwise use a new
  version tag. A rerun uses its original workflow revision and event commit.
- Never move an existing tag. A moved tag or a published release causes attachment
  to fail. Published release assets are not replaced by this workflow.

Uploads are separate API requests, so a failed upload can leave a partially filled
draft. Wait for the draft job's success before publishing. GitHub repository
writers can publish through the UI even while a workflow is running; the workflow
does not impose a repository-wide publication lock.

## Recover a Release Published Before Attachment

If a maintainer publishes the release before the tag-push workflow attaches its
files, wait for the build and smoke jobs to finish. When the only failed job is
the draft-only attachment guard, run **Recover verified assets for published
release** from the default branch and enter the source run ID. The recovery Action
accepts only that exact failure shape and downloads the twelve files by artifact
ID from the verified run. It checks the tag again before modifying the release.
It adds missing files to the matching
published release, preserves its publication status, notes, and unrelated assets,
and refuses to replace completed assets whose size or SHA-256 differs. On retry,
it can clear an empty upload placeholder left by GitHub.
It verifies the final remote asset metadata against the run outputs. No local asset
download or upload is part of this recovery.

The recovery Action is limited to completed tag-push release runs where every
verification, packaging, legal/source-material, and smoke job succeeded. It does
not retry a release run with any other failure.

## Manual Smoke Validation

Run the same workflow through **Run workflow** for a selected branch or tag to
perform validation only. This uses the checked-in catalog version, has no release
tag input, and skips the draft job even when a tag was selected in the UI. It
cannot create, attach to, or publish a release. Use a version tag push for release
preparation.

## Trigger and Tooling Decisions

There is one tag-push entry point and one draft-attachment job. GitHub does not
start `release: created` workflows when a draft is saved. Starting builds on
`release: published` would expose the release before its files were ready and
would conflict with immutable releases. The maintainer therefore publishes only
after automated draft preparation.

The implementation reuses GitHub's artifact actions, `needs` dependencies,
concurrency, GitHub CLI, and the existing Gradle version override and verification
scripts. No application library or custom candidate registry is needed for this
CI orchestration. A workflow-created tag using `GITHUB_TOKEN` generally does not
trigger another push workflow; the documented entry point is a maintainer's push.

Official references:

- [Push, release, and manual trigger semantics](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows)
- [Failed-job reruns and original commit identity](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/re-run-workflows-and-jobs)
- [Concurrency behavior](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency)
- [Draft assets and publication](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)
- [Artifact overwrite support](https://github.com/actions/upload-artifact)
- [GITHUB_TOKEN event behavior](https://docs.github.com/en/actions/concepts/security/github_token)

## Download Status and Review Scope

On November 15, 2026, the published `v0.1.1` release was verified to contain all
twelve recipient assets. It was built and published using the earlier
candidate/publisher process; it is not end-to-end evidence for this new workflow.
See the [downloads](../README.md#download-and-run).

Technical checks prove the covered build, startup, provenance and archive
properties. They do not establish legal clearance or close the unresolved
[distribution audit](dependency-license-audit.md) and [release review](licensing/release-review.md).
