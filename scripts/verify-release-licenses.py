"""Fail closed unless the exact release payload has a recorded redistribution review.

This verifies a review's scope and integrity; it does not infer legal clearance from
POM metadata, a permissive SPDX identifier, or the presence of a LICENSE filename.
"""

import argparse
import hashlib
import json
import pathlib
import re


PACKAGE_NAMES = {
    "visual-agent-linux-deb.deb", "visual-agent-linux-rpm.rpm",
    "visual-agent-linux-appimage.AppImage", "visual-agent-linux-x64-jar.jar",
    "visual-agent-macos-arm64-jar.jar", "visual-agent-macos-x64-jar.jar",
    "visual-agent-windows-x64-jar.jar", "visual-agent-macos-arm64.dmg",
    "visual-agent-macos-x64.dmg", "visual-agent-windows.msi",
}
LEGAL_NAMES = {"visual-agent-legal.zip", "visual-agent-corresponding-sources.zip"}


def digest(file):
    checksum = hashlib.sha256()
    with file.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            checksum.update(chunk)
    return checksum.hexdigest()


def checked_file(root, name):
    if not isinstance(name, str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*", name):
        raise ValueError("Unsafe release filename")
    file = root / name
    if file.is_symlink() or not file.is_file() or file.stat().st_size == 0:
        raise ValueError(f"Missing, empty, or symbolic-link release file: {name}")
    return file


def verify(policy_path, assets, tag, source_commit):
    policy = json.loads(policy_path.read_text(encoding="utf-8"))
    if policy.get("schemaVersion") != 1:
        raise ValueError("Unsupported license approval schema")
    review = policy.get("releases", {}).get(tag)
    if not isinstance(review, dict):
        raise ValueError(f"No completed redistribution review for {tag}; publication blocked by issue #410")
    if not re.fullmatch(r"[0-9a-f]{40}", source_commit) or review.get("sourceCommit") != source_commit:
        raise ValueError("License review does not cover this source commit")
    if not isinstance(review.get("reviewedBy"), str) or not review["reviewedBy"].strip():
        raise ValueError("Missing review attribution")
    if not isinstance(review.get("reviewedAt"), str) or not review["reviewedAt"].strip():
        raise ValueError("Missing review date")
    # A reviewed evidence document belongs to the trusted policy checkout, not the
    # downloaded artifacts. The producer cannot approve its own package by adding JSON.
    evidence = checked_file(policy_path.parent, review.get("evidenceFile"))
    if digest(evidence) != review.get("evidenceSha256"):
        raise ValueError("Redistribution review evidence changed")
    hashes = review.get("sha256")
    if not isinstance(hashes, dict) or set(hashes) != PACKAGE_NAMES | LEGAL_NAMES:
        raise ValueError("Review must cover every package, legal bundle, and corresponding-source archive")
    if not assets.is_dir() or {file.name for file in assets.iterdir()} != set(hashes):
        raise ValueError("Release payload differs from the reviewed file set")
    for name, expected in hashes.items():
        if not isinstance(expected, str) or not re.fullmatch(r"[0-9a-f]{64}", expected):
            raise ValueError(f"Invalid reviewed SHA-256: {name}")
        if digest(checked_file(assets, name)) != expected:
            raise ValueError(f"Release file changed after review: {name}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--policy", type=pathlib.Path, required=True)
    parser.add_argument("--assets", type=pathlib.Path, required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--source-commit", required=True)
    args = parser.parse_args()
    try:
        verify(args.policy, args.assets, args.tag, args.source_commit)
    except (ValueError, OSError, TypeError, AttributeError) as error:
        parser.exit(1, f"Release license gate: {error}\n")
    print("Exact release payload matches its recorded redistribution review.")


if __name__ == "__main__":
    main()
