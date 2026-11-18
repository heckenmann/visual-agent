#!/usr/bin/env bash
# Called only after all required jobs pass; uploads never publish the draft.
set -euo pipefail
assets_dir="${1:?Expected recipient asset directory}"
# Reject incomplete or unexpected local files before any GitHub write, then verify
# that this workflow still owns the intended tag and source commit.
bash scripts/release-tools.sh verify-assets --assets "$assets_dir"
bash scripts/verify-release-ref.sh
prerelease=false
[[ "${RELEASE_TAG%%+*}" != *-* ]] || prerelease=true
# List by tag without treating authentication/network failures as a missing draft.
releases="$(gh api --paginate "repos/$GH_REPO/releases?per_page=100" --jq '.[] | {id,tag_name}')"
release_id="$(jq -sr --arg tag "$RELEASE_TAG" '[.[] | select(.tag_name == $tag)] | if length > 1 then error("Duplicate releases for tag") else .[0].id // empty end' <<< "$releases")"
if [[ -z "$release_id" ]]; then
  # A missing draft may be created; an existing published release must never be
  # converted or overwritten. The matching-draft guard below applies to both paths.
  gh release create "$RELEASE_TAG" --repo "$GH_REPO" --verify-tag --draft \
    --prerelease="$prerelease" --title "$RELEASE_TAG" --generate-notes
  release_id="$(gh release view "$RELEASE_TAG" --repo "$GH_REPO" --json databaseId --jq .databaseId)"
fi
release="$(gh api "repos/$GH_REPO/releases/$release_id")"
# Re-read authoritative release state immediately before uploading. Retries must
# retain draft status and the tag's stable/prerelease classification.
jq -e --arg tag "$RELEASE_TAG" --argjson prerelease "$prerelease" \
  '.tag_name == $tag and .draft == true and .prerelease == $prerelease' <<< "$release" >/dev/null || {
  echo 'Release must be a matching draft with the correct prerelease classification.' >&2
  exit 1
}
if ! jq -e --arg tag "$RELEASE_TAG" '.name == $tag' <<< "$release" >/dev/null; then
  gh api --method PATCH "repos/$GH_REPO/releases/$release_id" -f "name=$RELEASE_TAG" --silent
fi
# Only these verified files are replaced on retry; unrelated assets and notes survive.
mapfile -t assets < <(find "$assets_dir" -maxdepth 1 -type f -print | sort)
gh release upload "$RELEASE_TAG" "${assets[@]}" --repo "$GH_REPO" --clobber
# Successful upload commands alone do not prove completeness. Compare every remote
# asset's name, state, byte size, and SHA-256 with the verified local build outputs.
# Slurp all API pages so missing or duplicated assets cannot hide across pages.
gh api --paginate --slurp "repos/$GH_REPO/releases/$release_id/assets?per_page=100" \
  | bash scripts/release-tools.sh verify-assets --assets "$assets_dir" --uploaded
printf 'Draft %s has all twelve verified assets. Review it and publish through GitHub.\n' "$RELEASE_TAG"
