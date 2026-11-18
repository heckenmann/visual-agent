#!/bin/sh
set -eu
if [ "$1" = build ]; then exit 0; fi
[ "$1" = run ]
for argument do
  case "$argument" in
    *:/ws) source_directory="${argument%:/ws}" ;;
    *:/ws/out) output_directory="${argument%:/ws/out}" ;;
  esac
done
test -n "${source_directory:-}"
test -n "${output_directory:-}"
source_version="$(cat "$source_directory/src/runtime/version")"
cat > "$output_directory/runtime-x86_64" <<RUNTIME
#!/bin/sh
case "\$1" in
  --appimage-help) echo --appimage-extract ;;
  --appimage-version) echo "AppImage runtime version: $source_version" ;;
esac
RUNTIME
chmod +x "$output_directory/runtime-x86_64"
