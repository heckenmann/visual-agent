#!/usr/bin/env bash
set -eu
printf '%s\n' "$*" >> "$FIXTURE/log"
case "$*" in
  "api "*"/actions/runs/"*"/jobs?per_page=100"*) cat "$FIXTURE/jobs.json" ;;
  "api "*"/actions/runs/"*"/artifacts?per_page=100"*) cat "$FIXTURE/artifacts.json" ;;
  "api "*"/actions/runs/"*" --jq .run_attempt") echo "${CURRENT_ATTEMPT:-1}" ;;
  "api "*"/actions/runs/"*) cat "$FIXTURE/source-run.json" ;;
  "api repos/owner/repo/actions/artifacts/"*"/zip")
    cat "$FIXTURE/$(basename "$(dirname "$2")").zip" ;;
  "api --paginate "*"/actions/workflows?per_page=100"*) echo 42 ;;
  "api --paginate --slurp "*"/releases/7/assets?per_page=100"*)
    if [[ -f "$FIXTURE/uploaded-release" ]]; then
      cat "$FIXTURE/uploaded.json"
    else
      echo "${INITIAL_ASSETS:-[[]]}"
    fi ;;
  "api --paginate "*"/releases?per_page=100"*)
    [[ "$EXISTING" != error ]] || exit 43
    if [[ "$EXISTING" != none || -f "$FIXTURE/created" ]]; then echo '{"id":7,"tag_name":"v1.2.3"}'; fi ;;
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
  "api -X DELETE repos/owner/repo/releases/assets/41") ;;
  "release create "*) touch "$FIXTURE/created" ;;
  "release view "*) echo 7 ;;
  "release upload "*) touch "$FIXTURE/uploaded-release" ;;
  "run view "*) cat "$FIXTURE/failure.log" ;;
  *) echo "Unexpected gh arguments: $*" >&2; exit 99 ;;
esac
