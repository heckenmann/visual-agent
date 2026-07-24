#!/usr/bin/env bash
# Generates the next free use-case filename in docs/usecases/.
# Never reuses numbers from deleted use-case files.
# Usage: ./scripts/next-use-case.sh <description>
#   <description>: short kebab-case description (e.g. "queue_messages_while_busy")
# Output: the full filename (e.g. "docs/usecases/uc_0000090_queue_messages_while_busy.md")

set -euo pipefail

DESCRIPTION="${1:?Usage: $0 <description>}"
BASE_DIR="docs/usecases"

# Highest number from existing files
EXISTING_MAX=$(ls "$BASE_DIR"/uc_*.md 2>/dev/null | sed 's/.*uc_0*//' | sed 's/_.*//' | sort -n | tail -1)
EXISTING_MAX=${EXISTING_MAX:-0}

# Highest number from files ever deleted in git history
# --no-pager prevents interactive pagers from hanging the script.
DELETED_MAX=$(git --no-pager log --all --diff-filter=D --name-only --pretty=format: -- "$BASE_DIR"/uc_*.md 2>/dev/null | sed 's/.*uc_0*//' | sed 's/_.*//' | sort -n | tail -1)
DELETED_MAX=${DELETED_MAX:-0}

# Take the max of both, then add 1
MAX=$(( EXISTING_MAX > DELETED_MAX ? EXISTING_MAX : DELETED_MAX ))
NEXT=$(( MAX + 1 ))
printf "docs/usecases/uc_%07d_%s.md\n" "$NEXT" "$DESCRIPTION"
