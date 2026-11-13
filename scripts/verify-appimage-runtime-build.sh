#!/usr/bin/env bash

set -euo pipefail

archive="${1:?usage: verify-appimage-runtime-build.sh <type2-runtime-source-archive> [output-runtime]}"
output_runtime="${2:-}"
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
cleanup() {
    rm -rf "$temporary_directory"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
source_directory="$temporary_directory/source"
output_directory="$temporary_directory/output"
mkdir -p "$source_directory" "$output_directory"
archive_root="$(tar -tzf "$archive" | sed -n '1p' | cut -d '/' -f 1)"
source_commit="${archive_root#type2-runtime-}"
if [[ "$archive_root" == "$source_commit" || ! "$source_commit" =~ ^[0-9a-f]{40}$ ]]; then
    printf 'Unexpected AppImage runtime source archive root: %s\n' "$archive_root" >&2
    exit 1
fi
source_version="https://github.com/AppImage/type2-runtime/commit/${source_commit:0:7}"
tar -xzf "$archive" --strip-components=1 -C "$source_directory"
printf '%s\n' "$source_version" > "$source_directory/src/runtime/version"

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
version_output="$("$runtime" --appimage-version 2>&1)"
grep -F "$source_version" <<<"$version_output"
if [[ -n "$output_runtime" ]]; then
    mkdir -p "$(dirname "$output_runtime")"
    install -m 755 "$runtime" "$output_runtime"
fi
echo "AppImage runtime built from the retained upstream source recipe and verified as a static x86_64 executable."
