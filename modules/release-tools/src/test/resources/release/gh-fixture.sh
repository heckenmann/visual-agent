#!/usr/bin/env bash
set -eu
printf '%s\n' "$*" >> "$FIXTURE/log"
case "$*" in
  "api "*"/commits/"*) echo "$TAG_COMMIT" ;;
  "api "*"/compare/"*) echo "$RELATION" ;;
  "api --paginate --slurp "*) cat "$FIXTURE/uploaded.json" ;;
  "api --paginate "*)
    [[ "$EXISTING" != error ]] || exit 43
    if [[ "$EXISTING" != none || -f "$FIXTURE/created" ]]; then
      jq -n --arg tag "$RELEASE_TAG" '{id:7,tag_name:$tag}'
    fi ;;
  "api "*"/releases/7")
    draft=true; [[ "$EXISTING" != published ]] || draft=false
    jq -n --arg tag "$RELEASE_TAG" --arg title "${RELEASE_TITLE:-$RELEASE_TAG}" --argjson draft "$draft" --argjson preview "$PREVIEW" \
      '{id:7,name:$title,tag_name:$tag,draft:$draft,prerelease:$preview,body:"Maintainer notes",assets:[{name:"extra.txt"}]}' ;;
  "api --method PATCH "*) ;;
  "release create "*) touch "$FIXTURE/created" ;;
  "release view "*) echo 7 ;;
  "release upload "*) ;;
  *) echo "Unexpected gh arguments: $*" >&2; exit 99 ;;
esac
