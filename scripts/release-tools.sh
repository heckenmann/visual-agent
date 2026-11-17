#!/usr/bin/env bash
# Run the Gradle-built CLI; build it explicitly before calling release operations.
set -euo pipefail
# Locate the distribution relative to this file, so callers may use another working
# directory. Keep their working directory unchanged because CLI paths are caller-owned.
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
launcher="$repo_root/modules/release-tools/build/install/release-tools/bin/release-tools"
# Build explicitly through Gradle instead of downloading a compiler or resolving
# dependencies during verification. CI can reuse the exact distribution from its run.
if [[ ! -x "$launcher" ]]; then
  echo 'Release tooling is missing. Run ./gradlew :release-tools:installDist first.' >&2
  exit 1
fi
# Preserve argument boundaries, standard input, signals, and the verifier's exit code.
# Uploaded-asset verification consumes paginated GitHub JSON through standard input.
exec "$launcher" "$@"
