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

from third_party_supplements import load_supplements


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


def runtime_legal_directory(root):
    candidates = [
        path
        for path in root.rglob("java-runtime")
        if path.parent.name == "legal" and path.parent.parent.name == "resources"
    ]
    if len(candidates) != 1:
        raise ValueError("Package must contain exactly one resources/legal/java-runtime directory")
    directory = candidates[0]
    if directory.is_symlink() or not directory.is_dir() or not directory.resolve().is_relative_to(root):
        raise ValueError("Runtime legal directory is missing or escaping the package")
    return directory


def retain_runtime_legal(root, output, total):
    directory = runtime_legal_directory(root)
    relative_root = directory.relative_to(root).as_posix()
    retained = []
    for path in sorted(directory.rglob("*")):
        if path.is_symlink():
            raise ValueError("Linked runtime legal entry")
        if not path.is_file():
            continue
        if not path.resolve().is_relative_to(directory.resolve()):
            raise ValueError("Runtime legal entry escapes its package directory")
        size = path.stat().st_size
        if size > MAX_NOTICE_BYTES or total[0] + size > MAX_TOTAL_BYTES:
            raise ValueError("Runtime legal material extraction limit exceeded")
        relative = path.relative_to(directory).as_posix()
        content_hash = sha256(path)
        target = output / "runtime-legal" / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(path.read_bytes())
        total[0] += size
        retained.append({
            "packagePath": f"{relative_root}/{relative}",
            "path": target.relative_to(output).as_posix(),
            "sha256": content_hash,
            "size": size,
        })
    if not retained:
        raise ValueError("Package runtime legal directory is empty")
    return {"packagePath": relative_root, "reviewStatus": "unreviewed", "files": retained}


def load_license_metadata(license_report):
    """Index Gradle's declared license metadata by exact module and version."""
    try:
        report = json.loads(license_report.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError("Unable to read Gradle license report") from error
    dependencies = report.get("dependencies") if isinstance(report, dict) else None
    if not isinstance(dependencies, list):
        raise ValueError("Gradle license report has no dependency list")
    indexed = {}
    for dependency in dependencies:
        if not isinstance(dependency, dict):
            raise ValueError("Invalid dependency entry in Gradle license report")
        module_name = dependency.get("moduleName")
        module_version = dependency.get("moduleVersion")
        if not isinstance(module_name, str) or not module_name or not isinstance(module_version, str) or not module_version:
            raise ValueError("Gradle license report contains a dependency without exact coordinates")
        key = f"{module_name}:{module_version}"
        licenses = dependency.get("moduleLicenses", [])
        urls = dependency.get("moduleUrls", [])
        if not isinstance(licenses, list) or not isinstance(urls, list):
            raise ValueError("Invalid license metadata in Gradle license report")
        normalized_licenses = []
        for license_entry in licenses:
            if not isinstance(license_entry, dict):
                raise ValueError("Invalid declared license entry in Gradle license report")
            name = license_entry.get("moduleLicense")
            url = license_entry.get("moduleLicenseUrl")
            if (name is not None and not isinstance(name, str)) or (url is not None and not isinstance(url, str)):
                raise ValueError("Invalid declared license name or URL in Gradle license report")
            normalized_licenses.append({"name": name, "url": url})
        if any(not isinstance(url, str) for url in urls):
            raise ValueError("Invalid module URL in Gradle license report")
        metadata = {
            "moduleName": module_name,
            "moduleVersion": module_version,
            "moduleUrls": urls,
            "declaredLicenses": normalized_licenses,
        }
        if key in indexed and indexed[key] != metadata:
            raise ValueError("Conflicting license report entries for exact dependency: " + key)
        indexed[key] = metadata
    return indexed


def license_metadata_for(coordinate, indexed):
    if coordinate.startswith("bundled-jdk:"):
        return {
            "source": "packaged-runtime-legal-evidence",
            "reviewStatus": "unreviewed",
            "status": "see-runtimeLegal",
        }
    module_name, separator, version = coordinate.rpartition(":")
    if not separator or ":" not in module_name:
        raise ValueError("Packaged external artifact has invalid Maven coordinates: " + coordinate)
    metadata = indexed.get(coordinate)
    if metadata is None:
        raise ValueError("No exact Gradle license metadata for packaged artifact: " + coordinate)
    return {
        "source": "gradle-license-report",
        "reviewStatus": "unreviewed",
        "status": "reported" if metadata["declaredLicenses"] else "no-license-declaration",
        **metadata,
    }


def collect(package_root, inventory, output, license_report, project_license, supplements=None):
    """Collect byte-exact evidence; absence and presence both remain unreviewed."""
    root = package_root.resolve(strict=True)
    supplemental = load_supplements(supplements)
    license_metadata = load_license_metadata(license_report)
    if project_license.is_symlink() or not project_license.is_file():
        raise ValueError("Project license must be a regular file")
    project_license_hash = sha256(project_license)
    if output.is_symlink() or (output.exists() and any(output.iterdir())):
        raise ValueError("Output must be a new or empty directory")
    output.mkdir(parents=True, exist_ok=True)
    components = []
    project_artifacts = []
    seen_paths = set()
    total = [0]
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
        if not coordinate or coordinate in {"UNMATCHED", "AMBIGUOUS"}:
            raise ValueError("Packaged artifact has no unique resolved component identity: " + relative)
        if coordinate.startswith("project '"):
            project_artifacts.append({
                "coordinate": coordinate,
                "packagePath": relative,
                "packageSha256": actual_hash,
                "licenseEvidence": {
                    "source": "repository-license-file",
                    "path": "LICENSE",
                    "sha256": project_license_hash,
                    "reviewStatus": "unreviewed",
                },
            })
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
                total[0] += len(content)
                if len(content) != entry.file_size or total[0] > MAX_TOTAL_BYTES:
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
        component = {
            "coordinate": coordinate,
            "packagePath": relative,
            "packageSha256": actual_hash,
            "reviewStatus": "unreviewed",
            "licenseMetadata": license_metadata_for(coordinate, license_metadata),
            "noticeStatus": "retained" if notices else "no-detected-notice",
            "notices": sorted(notices, key=lambda notice: notice["entry"]),
        }
        extra = supplemental.get(coordinate)
        if extra:
            if extra["expectedSourceSha256"] != row.get("sourceSha256"):
                raise ValueError("Supplement does not match exact source artifact")
            retained = []
            for material in extra["materials"]:
                content = material["content"]
                total[0] += len(content)
                if total[0] > MAX_TOTAL_BYTES:
                    raise ValueError("Notice extraction limit exceeded")
                target = output / "supplements" / actual_hash / material["path"]
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(content)
                retained.append({
                    "path": target.relative_to(output).as_posix(),
                    "sha256": material["sha256"],
                    "sourceUrl": material["url"],
                })
            component["supplement"] = {
                "sourceSha256": row["sourceSha256"],
                "proposedLicense": extra["proposedLicense"],
                "upstreamCommit": extra["upstreamCommit"],
                "remaining": extra["remaining"],
                "materials": retained,
            }
        components.append(component)
    runtime_legal = retain_runtime_legal(root, output, total)
    report = {
        "schemaVersion": 1,
        "redistributionApproved": False,
        "inventorySha256": sha256(inventory),
        "licenseReportSha256": sha256(license_report),
        "projectLicenseSha256": project_license_hash,
        "components": sorted(components, key=lambda component: component["packagePath"]),
        "projectArtifacts": sorted(project_artifacts, key=lambda artifact: artifact["packagePath"]),
        "runtimeLegal": runtime_legal,
    }
    (output / "manifest.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package-root", type=pathlib.Path, required=True)
    parser.add_argument("--inventory", type=pathlib.Path, required=True)
    parser.add_argument("--license-report", type=pathlib.Path, required=True)
    parser.add_argument("--project-license", type=pathlib.Path, required=True)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    parser.add_argument("--supplements", type=pathlib.Path)
    arguments = parser.parse_args()
    collect(
        arguments.package_root,
        arguments.inventory,
        arguments.output,
        arguments.license_report,
        arguments.project_license,
        arguments.supplements,
    )


if __name__ == "__main__":
    main()
