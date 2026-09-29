#!/usr/bin/env bash
set -euo pipefail

package_root="${1:?package root is required}"
output_dir="${2:?output directory is required}"
mkdir -p "$output_dir"

package_files="$output_dir/package-files.txt"
embedded_assets="$output_dir/embedded-assets.txt"
embedded_notices="$output_dir/embedded-notices.txt"

find "$package_root" -type f -print0 |
  while IFS= read -r -d '' file; do
    printf '%s\n' "${file#"$package_root"/}"
  done | LC_ALL=C sort > "$package_files"

: > "$embedded_assets"
: > "$embedded_notices"
jar_count=0
while IFS= read -r -d '' archive; do
  jar_count=$((jar_count + 1))
  relative_archive="${archive#"$package_root"/}"
  jar tf "$archive" |
    awk -v archive="$relative_archive" -v assets="$embedded_assets" -v notices="$embedded_notices" '
      {
        path = tolower($0)
        if (path ~ /\.(so|dylib|dll|jnilib|ttf|otf|ttc|otc|woff|woff2|afm)$/) {
          print archive ": " $0 >> assets
        }
        if (path ~ /(^|\/)(license|notice|copying)(\.|\/|$)/) {
          print archive ": " $0 >> notices
        }
      }
    '
done < <(find "$package_root" -type f -name '*.jar' -print0)
if ((jar_count == 0)); then
  printf 'No JARs found below %s\n' "$package_root" >&2
  exit 1
fi

LC_ALL=C sort -o "$embedded_assets" "$embedded_assets"
LC_ALL=C sort -o "$embedded_notices" "$embedded_notices"
