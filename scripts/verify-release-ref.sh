#!/usr/bin/env bash
# Validate before building and again before writing a draft. Never move a tag.
set -euo pipefail
numeric='(0|[1-9][0-9]*)'
identifier='(0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)'
semver="^v$numeric\\.$numeric\\.$numeric(-$identifier(\\.$identifier)*)?(\\+[0-9A-Za-z-]+(\\.[0-9A-Za-z-]+)*)?$"
[[ "$RELEASE_TAG" =~ $semver ]] || { echo 'Expected a v-prefixed SemVer tag.' >&2; exit 1; }
# Reruns retain the original event: only an initial tag creation is releasable.
jq -e --arg ref "refs/tags/$RELEASE_TAG" \
  '.ref == $ref and .created == true and .forced == false and .deleted == false' \
  "$GITHUB_EVENT_PATH" >/dev/null || {
  echo 'Release requires an initial tag creation; tag updates and deletions are rejected.' >&2
  exit 1
}
tag_commit="$(gh api "repos/$GH_REPO/commits/$RELEASE_TAG" --jq .sha)"
[[ "$tag_commit" == "$SOURCE_COMMIT" ]] || { echo 'Release tag moved or does not match the build commit.' >&2; exit 1; }
relation="$(gh api "repos/$GH_REPO/compare/$SOURCE_COMMIT...master" --jq .status)"
[[ "$relation" == 'ahead' || "$relation" == 'identical' ]] || {
  echo 'Release commit must be contained in master.' >&2
  exit 1
}
