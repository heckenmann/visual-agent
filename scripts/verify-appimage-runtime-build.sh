#!/usr/bin/env bash

set -euo pipefail

archive="${1:?usage: verify-appimage-runtime-build.sh <type2-runtime-source-archive>}"
expected_sha256="f5fec23be76e50e2445ed2d018bac49b367490fc483c62c4637e99ec705d27ba"
actual_sha256="$(sha256sum "$archive" | cut -d ' ' -f 1)"
if [[ "$actual_sha256" != "$expected_sha256" ]]; then
    printf 'Unexpected AppImage runtime source archive hash: %s\n' "$actual_sha256" >&2
    exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
    echo "Docker is required to run the upstream AppImage runtime build." >&2
    exit 1
fi

temporary_directory="$(mktemp -d)"
trap 'rm -rf "$temporary_directory"' EXIT
source_directory="$temporary_directory/source"
output_directory="$temporary_directory/output"
mkdir -p "$source_directory" "$output_directory"
tar -xzf "$archive" --strip-components=1 -C "$source_directory"

(
    cd "$output_directory"
    ARCH=x86_64 bash "$source_directory/scripts/docker/build-with-docker.sh"
)

runtime="$output_directory/runtime-x86_64"
test -s "$runtime"
file "$runtime" | grep -F 'ELF 64-bit' | grep -F 'x86-64'
if readelf -l "$runtime" | grep -q 'INTERP'; then
    echo "The rebuilt AppImage runtime unexpectedly depends on a dynamic loader." >&2
    exit 1
fi

help_output="$("$runtime" --appimage-help 2>&1)"
grep -F -- '--appimage-extract' <<<"$help_output"
echo "AppImage runtime built from the retained upstream source recipe and verified as a static x86_64 executable."
