#!/usr/bin/env bash
# Recover a published release only from a fully verified tag-push workflow run.
set -euo pipefail

source_run_id="${SOURCE_RUN_ID:?Expected the completed release workflow run ID}"
[[ "$source_run_id" =~ ^[0-9]+$ ]] || { echo 'Release workflow run ID must be numeric.' >&2; exit 1; }

run="$(gh api "repos/$GH_REPO/actions/runs/$source_run_id")"
workflow_id="$(gh api --paginate "repos/$GH_REPO/actions/workflows?per_page=100" --jq '.workflows[] | select(.path == ".github/workflows/release.yml") | .id')"
jq -e --argjson workflow "$workflow_id" \
  '.workflow_id == $workflow and .event == "push" and .status == "completed" and .conclusion == "failure" and (.head_branch | startswith("v"))' \
  <<< "$run" >/dev/null || {
  echo 'Source run must be a failed tag-push run of the release workflow.' >&2
  exit 1
}
RELEASE_TAG="$(jq -r .head_branch <<< "$run")"
SOURCE_COMMIT="$(jq -r .head_sha <<< "$run")"
attempt="$(jq -er .run_attempt <<< "$run")"

jobs="$(gh api --paginate --slurp "repos/$GH_REPO/actions/runs/$source_run_id/jobs?per_page=100")"
jq -e '
  [.[] | .jobs[]] as $jobs |
  ([$jobs[] | select(.name == "Verify release source" and .conclusion == "success")] | length == 1)
  and ([$jobs[] | select(.name == "Attach verified assets to draft release" and .conclusion == "failure")] | length == 1)
  and ([$jobs[] | select(.conclusion == "failure")] | length == 1)
  and all($jobs[]; .conclusion == "success" or (.name == "Attach verified assets to draft release" and .conclusion == "failure"))
' <<< "$jobs" >/dev/null || {
  echo 'Every source-run job must pass except the published-release draft guard.' >&2
  exit 1
}
attach_job_id="$(jq -r '[.[] | .jobs[] | select(.name == "Attach verified assets to draft release")][0].id' <<< "$jobs")"
jq -e --argjson id "$attach_job_id" '
  [.[] | .jobs[] | select(.id == $id)][0].steps as $steps |
  ([$steps[] | select(.name == "Prepare draft and attach verified assets" and .conclusion == "failure")] | length == 1)
  and all($steps[]; .conclusion == "success" or (.name == "Prepare draft and attach verified assets" and .conclusion == "failure"))
' <<< "$jobs" >/dev/null || {
  echo 'The source run failed outside the expected published-release guard.' >&2
  exit 1
}
failure_log="$(gh run view "$source_run_id" --repo "$GH_REPO" --job "$attach_job_id" --log-failed)"
grep -Fq 'Release must be a matching draft with the correct prerelease classification.' <<< "$failure_log" || {
  echo 'The source run did not fail because the release was already published.' >&2
  exit 1
}

check_tag() {
  local tag_commit
  tag_commit="$(gh api "repos/$GH_REPO/commits/$RELEASE_TAG" --jq .sha)"
  [[ "$tag_commit" == "$SOURCE_COMMIT" ]] || { echo 'Release tag moved since the verified source run.' >&2; exit 1; }
}
check_tag
relation="$(gh api "repos/$GH_REPO/compare/$SOURCE_COMMIT...master" --jq .status)"
[[ "$relation" == 'ahead' || "$relation" == 'identical' ]] || {
  echo 'Release commit must still be contained in master.' >&2
  exit 1
}

scratch="$(mktemp -d "${RUNNER_TEMP:?Expected the GitHub Actions temporary directory}/release-assets.XXXXXX")"
trap 'rm -rf -- "$scratch"' EXIT
assets_dir="$scratch/assets"
tools_dir="modules/release-tools/build/install/release-tools"
artifacts="$(gh api --paginate --slurp "repos/$GH_REPO/actions/runs/$source_run_id/artifacts?per_page=100")"
current_attempt="$(gh api "repos/$GH_REPO/actions/runs/$source_run_id" --jq .run_attempt)"
[[ "$attempt" == "$current_attempt" ]] || { echo 'Source run was restarted during recovery.' >&2; exit 1; }
for artifact in \
  release-verification-tools \
  visual-agent-linux-deb \
  visual-agent-linux-rpm \
  visual-agent-linux-appimage \
  visual-agent-linux-x64-jar \
  visual-agent-macos-arm64-jar \
  visual-agent-macos-x64-jar \
  visual-agent-windows-x64-jar \
  visual-agent-macos-arm64 \
  visual-agent-macos-x64 \
  visual-agent-windows \
  visual-agent-legal \
  visual-agent-corresponding-sources; do
  id="$(jq -er --arg name "$artifact" '
    [.[] | .artifacts[] | select(.name == $name and .expired == false)] |
    if length == 1 then .[0].id else error("Expected one retained artifact: " + $name) end
  ' <<< "$artifacts")"
  target="$assets_dir"
  [[ "$artifact" != release-verification-tools ]] || target="$tools_dir"
  gh api "repos/$GH_REPO/actions/artifacts/$id/zip" > "$scratch/artifact.zip"
  unzip -oq "$scratch/artifact.zip" -d "$target"
done
chmod +x "$tools_dir/bin/release-tools"
bash scripts/release-tools.sh verify-assets --assets "$assets_dir"

prerelease=false
[[ "${RELEASE_TAG%%+*}" != *-* ]] || prerelease=true
releases="$(gh api --paginate "repos/$GH_REPO/releases?per_page=100" --jq '.[] | {id,tag_name}')"
release_id="$(jq -sr --arg tag "$RELEASE_TAG" '[.[] | select(.tag_name == $tag)] | if length == 1 then .[0].id else empty end' <<< "$releases")"
[[ -n "$release_id" ]] || { echo 'Expected exactly one matching published release.' >&2; exit 1; }
release="$(gh api "repos/$GH_REPO/releases/$release_id")"
jq -e --arg tag "$RELEASE_TAG" --argjson prerelease "$prerelease" \
  '.tag_name == $tag and .draft == false and .prerelease == $prerelease' <<< "$release" >/dev/null || {
  echo 'Release must be the matching published release.' >&2
  exit 1
}

remote_assets="$(gh api --paginate --slurp "repos/$GH_REPO/releases/$release_id/assets?per_page=100")"
missing=()
placeholders=()
while IFS= read -r file; do
  name="$(basename "$file")"
  records="$(jq -c --arg name "$name" '[.[][] | select(.name == $name)]' <<< "$remote_assets")"
  count="$(jq length <<< "$records")"
  if [[ "$count" == 0 ]]; then
    missing+=("$file")
    continue
  fi
  [[ "$count" == 1 ]] || { echo "Duplicate published asset: $name" >&2; exit 1; }
  if id="$(jq -er '.[0] | select(.state == "starter" and .size == 0) | .id' <<< "$records")"; then
    placeholders+=("$id")
    missing+=("$file")
    continue
  fi
  size="$(stat -c '%s' "$file")"
  digest="sha256:$(sha256sum "$file" | cut -d ' ' -f 1)"
  jq -e --argjson size "$size" --arg digest "$digest" \
    '.[0].state == "uploaded" and .[0].size == $size and .[0].digest == $digest' <<< "$records" >/dev/null || {
    echo "Published asset differs from this verified build; refusing to replace it: $name" >&2
    exit 1
  }
done < <(find "$assets_dir" -maxdepth 1 -type f -print | sort)

if ((${#missing[@]})); then
  check_tag
  for id in "${placeholders[@]}"; do
    gh api -X DELETE "repos/$GH_REPO/releases/assets/$id"
  done
  gh release upload "$RELEASE_TAG" "${missing[@]}" --repo "$GH_REPO"
fi
gh api --paginate --slurp "repos/$GH_REPO/releases/$release_id/assets?per_page=100" | bash scripts/release-tools.sh verify-assets --assets "$assets_dir" --uploaded
printf 'Published release %s has all twelve verified assets.\n' "$RELEASE_TAG"
