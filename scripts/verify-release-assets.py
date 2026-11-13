"""Check that a successful candidate contains the recipient release files."""

import argparse
import pathlib
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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--assets", type=pathlib.Path, required=True)
    args = parser.parse_args()
    try:
        verify(args.assets)
    except (ValueError, OSError) as error:
        parser.exit(1, f"Release asset check: {error}\n")
    print("All ten packages and both recipient legal/source archives are present and readable.")


if __name__ == "__main__":
    main()
