"""Check that a successful candidate contains the recipient release files."""

import argparse
import hashlib
import json
import pathlib
import sys
import zipfile


REQUIRED_ASSETS = {
    "visual-agent-linux-deb.deb",
    "visual-agent-linux-rpm.rpm",
    "visual-agent-linux-appimage.AppImage",
    "visual-agent-linux-x64-jar.jar",
    "visual-agent-macos-arm64-jar.jar",
    "visual-agent-macos-x64-jar.jar",
    "visual-agent-windows-x64-jar.jar",
    "visual-agent-macos-arm64.dmg",
    "visual-agent-macos-x64.dmg",
    "visual-agent-windows.msi",
    "visual-agent-legal.zip",
    "visual-agent-corresponding-sources.zip",
}


def verify_archive(path):
    try:
        with zipfile.ZipFile(path) as archive:
            if archive.testzip() is not None:
                raise ValueError(f"Corrupt release archive: {path.name}")
            if any(pathlib.PurePosixPath(name).name == "REVIEW-ONLY.txt" for name in archive.namelist()):
                raise ValueError(f"Review-only archive cannot be published: {path.name}")
    except zipfile.BadZipFile as error:
        raise ValueError(f"Invalid release archive: {path.name}") from error


def verify(assets):
    if not assets.is_dir():
        raise ValueError("Release asset directory is missing")
    files = list(assets.iterdir())
    if {file.name for file in files} != REQUIRED_ASSETS:
        raise ValueError("Candidate must contain exactly the ten packages and two legal/source archives")
    for file in files:
        if file.is_symlink() or not file.is_file() or file.stat().st_size == 0:
            raise ValueError(f"Missing, empty, or symbolic-link release file: {file.name}")
    for name in ("visual-agent-legal.zip", "visual-agent-corresponding-sources.zip"):
        verify_archive(assets / name)


def verify_uploaded(assets, pages):
    """Compare the twelve local files with paginated GitHub asset metadata."""
    if not isinstance(pages, list) or any(not isinstance(page, list) for page in pages):
        raise ValueError("Expected paginated release asset metadata")
    remote = [asset for page in pages for asset in page]
    for name in REQUIRED_ASSETS:
        matches = [asset for asset in remote if asset.get("name") == name]
        if len(matches) != 1:
            raise ValueError(f"Missing or duplicate uploaded asset: {name}")
        uploaded = matches[0]
        file = assets / name
        with file.open("rb") as stream:
            digest = "sha256:" + hashlib.file_digest(stream, "sha256").hexdigest()
        if uploaded.get("state") != "uploaded" or uploaded.get("size") != file.stat().st_size or uploaded.get("digest") != digest:
            raise ValueError(f"Uploaded asset does not match the build: {name}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--assets", type=pathlib.Path, required=True)
    parser.add_argument("--uploaded", action="store_true", help="Verify paginated GitHub asset metadata from stdin")
    args = parser.parse_args()
    try:
        if args.uploaded:
            verify_uploaded(args.assets, json.load(sys.stdin))
        else:
            verify(args.assets)
    except (ValueError, OSError) as error:
        parser.exit(1, f"Release asset check: {error}\n")
    print("All twelve uploaded assets match the build." if args.uploaded else
          "All ten packages and both recipient legal/source archives are present and readable.")


if __name__ == "__main__":
    main()
