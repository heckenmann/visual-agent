#!/usr/bin/env python3
"""Retain original notices from hash-verified packaged JARs, without legal approval."""

import argparse
import csv
import hashlib
import json
import pathlib
import re
import stat
import zipfile


MAX_NOTICE_BYTES = 8 * 1024 * 1024
MAX_TOTAL_BYTES = 64 * 1024 * 1024
NOTICE_NAME = re.compile(r"^(license|licence|notice|copying|copyright|disclaimer|authors)([._-].*)?$", re.I)


def sha256(file):
    digest = hashlib.sha256()
    with file.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def confined_file(root, relative):
    path = pathlib.PurePosixPath(relative)
    if not relative or "\\" in relative or ":" in relative or path.is_absolute() or ".." in path.parts:
        raise ValueError("Unsafe packaged artifact path")
    candidate = root.joinpath(*path.parts)
    if not candidate.resolve().is_relative_to(root) or not candidate.is_file():
        raise ValueError("Missing or escaping packaged artifact")
    return candidate


def collect(package_root, inventory, output):
    """Collect byte-exact evidence; absence and presence both remain unreviewed."""
    root = package_root.resolve(strict=True)
    if output.is_symlink() or (output.exists() and any(output.iterdir())):
        raise ValueError("Output must be a new or empty directory")
    output.mkdir(parents=True, exist_ok=True)
    components = []
    seen_paths = set()
    total = 0
    with inventory.open(newline="", encoding="utf-8") as stream:
        rows = list(csv.DictReader(stream))
    if not rows:
        raise ValueError("Packaged artifact inventory is empty")
    for row in rows:
        relative = row["packagePath"]
        if relative in seen_paths:
            raise ValueError("Duplicate packaged artifact path")
        seen_paths.add(relative)
        archive = confined_file(root, relative)
        actual_hash = sha256(archive)
        if actual_hash != row["packageSha256"]:
            raise ValueError("Packaged artifact does not match inventory: " + relative)
        coordinate = row["coordinate"]
        if coordinate.startswith("project '"):
            continue
        notices = []
        seen_entries = set()
        with zipfile.ZipFile(archive) as jar:
            for entry in jar.infolist():
                name = pathlib.PurePosixPath(entry.filename)
                if name.is_absolute() or ".." in name.parts or "\\" in entry.filename or ":" in entry.filename:
                    raise ValueError("Unsafe notice archive path")
                if entry.is_dir() or not NOTICE_NAME.fullmatch(name.name) or name.suffix.lower() == ".class":
                    continue
                if entry.filename in seen_entries:
                    raise ValueError("Duplicate notice entry")
                seen_entries.add(entry.filename)
                if stat.S_ISLNK(entry.external_attr >> 16):
                    raise ValueError("Linked notice entry")
                if entry.file_size <= 0 or entry.file_size > MAX_NOTICE_BYTES:
                    raise ValueError("Empty or oversized notice entry")
                with jar.open(entry) as stream:
                    content = stream.read(MAX_NOTICE_BYTES + 1)
                total += len(content)
                if len(content) != entry.file_size or total > MAX_TOTAL_BYTES:
                    raise ValueError("Notice extraction limit exceeded")
                target = output / "artifacts" / actual_hash / name
                target.parent.mkdir(parents=True, exist_ok=True)
                if target.exists() and target.read_bytes() != content:
                    raise ValueError("Conflicting notice output")
                target.write_bytes(content)
                notices.append({
                    "entry": entry.filename,
                    "path": target.relative_to(output).as_posix(),
                    "sha256": hashlib.sha256(content).hexdigest(),
                    "size": len(content),
                })
        components.append({
            "coordinate": coordinate,
            "packagePath": relative,
            "packageSha256": actual_hash,
            "reviewStatus": "unreviewed",
            "noticeStatus": "retained" if notices else "no-detected-notice",
            "notices": sorted(notices, key=lambda notice: notice["entry"]),
        })
    report = {
        "schemaVersion": 1,
        "redistributionApproved": False,
        "inventorySha256": sha256(inventory),
        "components": sorted(components, key=lambda component: component["packagePath"]),
    }
    (output / "manifest.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package-root", type=pathlib.Path, required=True)
    parser.add_argument("--inventory", type=pathlib.Path, required=True)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    arguments = parser.parse_args()
    collect(arguments.package_root, arguments.inventory, arguments.output)


if __name__ == "__main__":
    main()
