"""Verify that an AppImage embeds the source-built Type 2 runtime."""

import pathlib
import re
import subprocess
import sys


DIGEST_SECTION_SIZE = 16
DIGEST_SECTION = re.compile(
    r"^\s*\[\s*\d+\]\s+\.digest_md5\s+\S+\s+[0-9a-fA-F]+\s+([0-9a-fA-F]+)\s+([0-9a-fA-F]+)\b",
    re.MULTILINE,
)


def digest_section_range(elf_path):
    """Return the file offset and size of the AppImage MD5 section."""
    result = subprocess.run(
        ["readelf", "--wide", "--sections", str(elf_path)],
        check=True,
        capture_output=True,
        text=True,
    )
    return parse_digest_section_range(result.stdout)


def parse_digest_section_range(readelf_output):
    """Parse the file offset and size of .digest_md5 from readelf output."""
    matches = list(DIGEST_SECTION.finditer(readelf_output))
    if len(matches) != 1:
        raise ValueError(f"Expected one .digest_md5 section, found {len(matches)}.")
    offset, size = (int(value, 16) for value in matches[0].groups())
    if size != DIGEST_SECTION_SIZE:
        raise ValueError(f"Unexpected .digest_md5 section size: {size}.")
    return offset, size


def verify_runtime_prefix(source_runtime, appimage, source_section, embedded_section):
    """Allow only appimagetool's generated MD5 section to differ in the prefix."""
    source = pathlib.Path(source_runtime).read_bytes()
    offset, size = source_section
    if (offset, size) != embedded_section:
        raise ValueError("The source runtime and AppImage MD5 sections do not match.")
    if offset + size > len(source):
        raise ValueError("The source runtime MD5 section extends beyond the runtime.")
    if source[offset:offset + size] != bytes(size):
        raise ValueError("The source runtime MD5 section is not an empty placeholder.")

    with pathlib.Path(appimage).open("rb") as packaged_file:
        packaged_prefix = packaged_file.read(len(source))
    if len(packaged_prefix) != len(source):
        raise ValueError("The AppImage is shorter than its source runtime.")
    if packaged_prefix[offset:offset + size] == source[offset:offset + size]:
        raise ValueError("The AppImage runtime MD5 section was not generated.")
    if packaged_prefix[:offset] != source[:offset] or packaged_prefix[offset + size:] != source[offset + size:]:
        raise ValueError("The AppImage runtime differs outside appimagetool's MD5 section.")
    if packaged_prefix[8:11] != b"AI\x02":
        raise ValueError("The embedded runtime does not have the AppImage Type 2 marker.")


def main(arguments):
    if len(arguments) != 2:
        raise ValueError("Usage: verify_appimage_runtime_embedded.py <source-runtime> <appimage>")
    source_runtime, appimage = map(pathlib.Path, arguments)
    source_section = digest_section_range(source_runtime)
    embedded_section = digest_section_range(appimage)
    verify_runtime_prefix(source_runtime, appimage, source_section, embedded_section)
    print("AppImage embeds the source-built runtime; only appimagetool's generated MD5 section differs.")


if __name__ == "__main__":
    try:
        main(sys.argv[1:])
    except (OSError, subprocess.CalledProcessError, ValueError) as error:
        print(error, file=sys.stderr)
        raise SystemExit(1) from error
