"""Build deterministic, review-only legal and source evidence archives."""

import argparse
import hashlib
import json
import os
import pathlib
import re
import shutil
import tempfile
import zipfile


LEGAL_ARCHIVE = "visual-agent-legal.zip"
SOURCES_ARCHIVE = "visual-agent-corresponding-sources.zip"
EXPECTED_NOTICE_PLATFORMS = frozenset(
    {"linux-appimage", "linux-deb", "linux-rpm", "macos-arm64", "macos-x64", "windows"},
)
REVIEW_ONLY_NOTICE = (
    "REVIEW EVIDENCE ONLY. This archive is not a complete legal or corresponding-source delivery, "
    "does not establish redistribution approval, and must not be published as a release asset. "
    "See issue #410 and the included source-delivery.md for verified scope and open items.\n"
)


def _files_under(root):
    if root.is_symlink() or not root.is_dir():
        raise ValueError(f"Required material directory is missing or linked: {root}")
    files = []
    for path in sorted(root.rglob("*")):
        if path.is_symlink():
            raise ValueError(f"Linked release material is not allowed: {path}")
        if path.is_file():
            relative = path.relative_to(root).as_posix()
            if any(part in ("", ".", "..") for part in pathlib.PurePosixPath(relative).parts):
                raise ValueError(f"Unsafe release material path: {relative}")
            files.append((relative, path))
    return files


def _sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _write_archive(output, files, review_only_notice):
    output.parent.mkdir(parents=True, exist_ok=True)
    manifest = "".join(f"{_sha256(source)}  {name}\n" for name, source in files)
    entries = []
    if review_only_notice is not None:
        entries.append(("REVIEW-ONLY.txt", review_only_notice.encode("utf-8")))

    with tempfile.NamedTemporaryFile(dir=output.parent, prefix=f".{output.name}.", delete=False) as temporary:
        temporary_path = pathlib.Path(temporary.name)

    try:
        with zipfile.ZipFile(temporary_path, "w", compression=zipfile.ZIP_STORED) as archive:
            for name, source in files:
                _write_file(archive, name, source)
            _write_bytes(archive, "MANIFEST.sha256", manifest.encode("utf-8"))
            for name, content in entries:
                _write_bytes(archive, name, content)
        os.replace(temporary_path, output)
    finally:
        temporary_path.unlink(missing_ok=True)


def _write_file(archive, name, source):
    info = _zip_info(name)
    with source.open("rb") as input_stream, archive.open(info, "w") as output_stream:
        shutil.copyfileobj(input_stream, output_stream, length=1024 * 1024)


def _write_bytes(archive, name, content):
    archive.writestr(_zip_info(name), content)


def _zip_info(name):
    info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
    info.compress_type = zipfile.ZIP_STORED
    info.external_attr = 0o100644 << 16
    return info


def _notice_evidence_files(notice_evidence, project_license_sha256, runtime_packages):
    evidence_files = _files_under(notice_evidence)
    evidence_directories = {name.split("/", 1)[0] for name, _ in evidence_files}
    platform_directories = {}
    for directory in evidence_directories:
        platform = directory.removeprefix("license-notices-")
        if platform in platform_directories:
            raise ValueError(f"Duplicate package notice evidence for {platform}")
        platform_directories[platform] = directory

    platforms = sorted(platform_directories)
    if set(platforms) != EXPECTED_NOTICE_PLATFORMS:
        missing = sorted(EXPECTED_NOTICE_PLATFORMS.difference(platforms))
        unexpected = sorted(set(platforms).difference(EXPECTED_NOTICE_PLATFORMS))
        raise ValueError(
            f"Platform notice evidence mismatch (missing: {missing}; unexpected: {unexpected})",
        )
    packaged_files = []
    for platform in platforms:
        if not re.fullmatch(r"[a-z0-9-]+", platform):
            raise ValueError(f"Invalid notice evidence platform: {platform}")
        platform_files = _files_under(notice_evidence / platform_directories[platform])
        by_name = dict(platform_files)
        manifest_path = by_name.get("manifest.json")
        if manifest_path is None:
            raise ValueError(f"Notice evidence manifest is missing for {platform}")
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        if manifest.get("redistributionApproved") is not False or not manifest.get("components"):
            raise ValueError(f"Notice evidence is not explicitly unreviewed for {platform}")
        if manifest.get("projectLicenseSha256") != project_license_sha256:
            raise ValueError(f"Project license evidence does not match the legal bundle for {platform}")
        for component in manifest["components"]:
            if component.get("reviewStatus") != "unreviewed":
                raise ValueError(f"Notice evidence component is not marked unreviewed for {platform}")
            materials = list(component.get("notices", [])) + list(component.get("supplement", {}).get("materials", []))
            for material in materials:
                source = by_name.get(material.get("path"))
                if source is None or _sha256(source) != material.get("sha256"):
                    raise ValueError(f"Notice evidence hash mismatch for {platform}")
        runtime_legal = manifest.get("runtimeLegal")
        if not isinstance(runtime_legal, dict) or runtime_legal.get("reviewStatus") != "unreviewed":
            raise ValueError(f"Runtime legal evidence is missing or reviewed for {platform}")
        runtime_files = runtime_legal.get("files", [])
        if not runtime_files:
            raise ValueError(f"Runtime legal evidence is empty for {platform}")
        referenced_runtime_files = set()
        for material in runtime_files:
            name = material.get("path")
            source = by_name.get(name)
            if source is None or source.stat().st_size != material.get("size") or _sha256(source) != material.get("sha256"):
                raise ValueError(f"Runtime legal evidence hash mismatch for {platform}: {name}")
            referenced_runtime_files.add(name)
        actual_runtime_files = {name for name in by_name if name.startswith("runtime-legal/")}
        if referenced_runtime_files != actual_runtime_files:
            raise ValueError(f"Runtime legal evidence is incomplete or has unmanifested files for {platform}")
        appimage_evidence = manifest.get("appImageRuntime")
        expected_runtime = runtime_packages.get("linux-appimage")
        if platform == "linux-appimage":
            if not isinstance(appimage_evidence, dict) or expected_runtime is None:
                raise ValueError("AppImage runtime evidence is missing")
            if (
                appimage_evidence.get("reviewStatus") != "unreviewed"
                or appimage_evidence.get("embeddedPrefixVerified") is not True
                or not re.fullmatch(r"[0-9a-f]{64}", appimage_evidence.get("packageSha256", ""))
                or not re.fullmatch(r"[0-9a-f]{64}", appimage_evidence.get("runtimeSha256", ""))
                or not re.fullmatch(r"[0-9a-f]{64}", appimage_evidence.get("sourceArchiveSha256", ""))
                or appimage_evidence.get("sourceArchiveSha256")
                != expected_runtime["runtimeSourceArtifactSha256"]
                or appimage_evidence.get("sourceArchives") != expected_runtime["sourceArchives"]
                or appimage_evidence.get("sourceCommit") != expected_runtime["runtimeSourceCommit"]
            ):
                raise ValueError("AppImage runtime evidence does not match its pinned package mapping")
            runtime_components = appimage_evidence.get("components")
            expected_coordinates = {
                expected_runtime["runtimeSourceArtifact"],
                *expected_runtime["linkedComponents"],
            }
            if (
                not isinstance(runtime_components, list)
                or {component.get("coordinate") for component in runtime_components} != expected_coordinates
            ):
                raise ValueError("AppImage runtime component evidence is incomplete")
            referenced_appimage_materials = set()
            for component in runtime_components:
                coordinate = component.get("coordinate")
                expected_hash = expected_runtime["componentHashes"].get(coordinate)
                if (
                    component.get("reviewStatus") != "unreviewed"
                    or component.get("sourceArtifactSha256") != expected_hash
                    or component.get("upstreamCommit") != expected_runtime["componentCommits"].get(coordinate)
                ):
                    raise ValueError("AppImage runtime component evidence is not marked unreviewed")
                for material in component.get("materials", []):
                    name = material.get("path")
                    source = by_name.get(name)
                    if source is None or _sha256(source) != material.get("sha256"):
                        raise ValueError(f"AppImage runtime license material hash mismatch: {name}")
                    referenced_appimage_materials.add(name)
            actual_appimage_materials = {name for name in by_name if name.startswith("appimage-runtime-legal/")}
            if referenced_appimage_materials != actual_appimage_materials:
                raise ValueError("AppImage runtime legal materials are incomplete or unmanifested")
        elif appimage_evidence is not None:
            raise ValueError(f"Unexpected AppImage runtime evidence for {platform}")
        packaged_files.extend(
            (f"package-notice-evidence/{platform}/{name}", source)
            for name, source in platform_files
        )
    if not packaged_files:
        raise ValueError("Package notice evidence is empty")
    return packaged_files


def _verify_curated_materials(legal_root, legal_files):
    files_by_name = dict(legal_files)
    registry_path = files_by_name.get("sources.json")
    if registry_path is None:
        raise ValueError("Third-party material registry is missing")

    registry = json.loads(registry_path.read_text(encoding="utf-8"))
    materials = registry.get("materials")
    if not isinstance(materials, list):
        raise ValueError("Third-party material registry has no materials list")

    declared_materials = {}
    for material in materials:
        name = material.get("path")
        digest = material.get("sha256")
        parts = pathlib.PurePosixPath(name or "").parts
        if not name or pathlib.PurePosixPath(name).is_absolute() or any(part in ("", ".", "..") for part in parts):
            raise ValueError(f"Unsafe curated legal-material path: {name}")
        if name in declared_materials:
            raise ValueError(f"Duplicate curated legal-material path: {name}")
        source = files_by_name.get(name)
        if source is None or _sha256(source) != digest:
            raise ValueError(f"Curated legal material is missing or has a hash mismatch: {name}")
        declared_materials[name] = digest

    referenced_materials = {
        name
        for component in registry.get("components", [])
        for name in component.get("materialPaths", [])
    }
    referenced_materials.update(
        asset["licenseMaterialPath"]
        for asset in registry.get("embeddedAssets", [])
        if asset.get("licenseMaterialPath")
    )
    missing_references = referenced_materials.difference(declared_materials)
    if missing_references:
        raise ValueError(f"Curated materials are not declared in the registry: {', '.join(sorted(missing_references))}")
    declared_components = {}
    for component in registry.get("components", []):
        for coordinate, digest in component.get("sourceArtifacts", {}).items():
            if coordinate in declared_components or not re.fullmatch(r"[0-9a-f]{64}", digest):
                raise ValueError("Duplicate or invalid curated source artifact identity")
            commit = component.get("upstreamCommit")
            if not re.fullmatch(r"[0-9a-f]{40}", commit or ""):
                raise ValueError("Curated source artifact has no exact upstream commit")
            declared_components[coordinate] = {"sha256": digest, "upstreamCommit": commit}
    runtime_packages = {}
    for package in registry.get("runtimePackages", []):
        name = package.get("package")
        source_coordinate = package.get("runtimeSourceArtifact")
        linked_components = package.get("linkedComponents")
        if name in runtime_packages or source_coordinate not in declared_components:
            raise ValueError("Invalid or duplicate runtime package evidence mapping")
        if not isinstance(linked_components, list) or not linked_components:
            raise ValueError("Runtime package has no mapped linked components")
        if any(coordinate not in declared_components for coordinate in linked_components):
            raise ValueError("Runtime package refers to a component without curated legal materials")
        source_archive_records = package.get("sourceArchives")
        if not isinstance(source_archive_records, list) or not source_archive_records:
            raise ValueError("Runtime package has no source archive inventory")
        source_archives = {}
        for record in source_archive_records:
            relative = record.get("path")
            digest = record.get("sha256")
            parts = pathlib.PurePosixPath(relative or "").parts
            if (
                not relative
                or pathlib.PurePosixPath(relative).is_absolute()
                or any(part in ("", ".", "..") for part in parts)
                or "\\" in relative
                or ":" in relative
                or relative in source_archives
                or not re.fullmatch(r"[0-9a-f]{64}", digest or "")
            ):
                raise ValueError("Invalid or duplicate runtime source archive mapping")
            source_archives[relative] = digest
        if source_archives.get(package.get("sourceArchivePath")) != declared_components[source_coordinate]["sha256"]:
            raise ValueError("Runtime source archive hash differs from its curated component identity")
        runtime_packages[name] = {
            "runtimeSourceArtifact": source_coordinate,
            "runtimeSourceArtifactSha256": declared_components[source_coordinate]["sha256"],
            "runtimeSourceCommit": declared_components[source_coordinate]["upstreamCommit"],
            "linkedComponents": linked_components,
            "sourceArchives": [
                {"path": path, "sha256": digest}
                for path, digest in sorted(source_archives.items())
            ],
            "sourceArchiveFiles": source_archives,
            "componentHashes": {
                coordinate: declared_components[coordinate]["sha256"]
                for coordinate in [source_coordinate, *linked_components]
            },
            "componentCommits": {
                coordinate: declared_components[coordinate]["upstreamCommit"]
                for coordinate in [source_coordinate, *linked_components]
            },
        }
    return runtime_packages


def build_bundles(project_root, source_materials, notice_evidence, output_directory, recipient_candidate=False):
    """Create deterministic review archives or recipient candidates; neither mode grants approval."""
    project_root = pathlib.Path(project_root)
    source_materials = pathlib.Path(source_materials)
    notice_evidence = pathlib.Path(notice_evidence)
    if source_materials.is_symlink():
        raise ValueError(f"Required material directory is missing or linked: {source_materials}")
    project_root = project_root.resolve()
    source_materials = source_materials.resolve()
    output_directory = pathlib.Path(output_directory).resolve()
    if output_directory.is_relative_to(source_materials):
        raise ValueError("Output directory must not be inside source materials")

    required_project_files = [
        project_root / "LICENSE",
        project_root / "docs/licensing/README.md",
        project_root / "docs/licensing/source-delivery.md",
    ]
    if any(path.is_symlink() or not path.is_file() for path in required_project_files):
        raise ValueError("Required project legal documents are missing or linked")

    source_files = _files_under(source_materials)
    if not any(name.startswith("archives/") for name, _ in source_files):
        raise ValueError("Source evidence archive directory is empty")
    if not any(name == "source-delivery.md" for name, _ in source_files):
        raise ValueError("Source-delivery scope statement is missing from source materials")

    legal_root = project_root / "docs/licensing/third-party"
    legal_files = _files_under(legal_root)
    runtime_packages = _verify_curated_materials(legal_root, legal_files)
    source_files_by_name = dict(source_files)
    for package in runtime_packages.values():
        for relative, digest in package["sourceArchiveFiles"].items():
            source = source_files_by_name.get(relative)
            if source is None or _sha256(source) != digest:
                raise ValueError(f"Pinned runtime source archive is missing or changed: {relative}")
    package_notice_files = _notice_evidence_files(
        notice_evidence,
        _sha256(required_project_files[0]),
        runtime_packages,
    )
    legal_bundle_files = [
        ("LICENSE", required_project_files[0]),
        ("licensing/README.md", required_project_files[1]),
        ("licensing/source-delivery.md", required_project_files[2]),
        *((f"licensing/third-party/{name}", source) for name, source in legal_files),
        *package_notice_files,
    ]
    source_bundle_files = [
        (f"source-materials/{name}", source)
        for name, source in source_files
    ]

    output_directory.mkdir(parents=True, exist_ok=True)
    review_only_notice = None if recipient_candidate else REVIEW_ONLY_NOTICE
    _write_archive(output_directory / LEGAL_ARCHIVE, legal_bundle_files, review_only_notice)
    _write_archive(output_directory / SOURCES_ARCHIVE, source_bundle_files, review_only_notice)
    return output_directory / LEGAL_ARCHIVE, output_directory / SOURCES_ARCHIVE


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-root", type=pathlib.Path, default=pathlib.Path.cwd())
    parser.add_argument("--source-materials", type=pathlib.Path, required=True)
    parser.add_argument("--notice-evidence", type=pathlib.Path, required=True)
    parser.add_argument("--output-directory", type=pathlib.Path, required=True)
    parser.add_argument(
        "--recipient-candidate",
        action="store_true",
        help="Omit the review-only marker to produce hashable recipient candidates; this does not approve them.",
    )
    arguments = parser.parse_args()
    try:
        build_bundles(
            arguments.project_root,
            arguments.source_materials,
            arguments.notice_evidence,
            arguments.output_directory,
            arguments.recipient_candidate,
        )
    except (OSError, ValueError) as error:
        parser.exit(1, f"Release material bundling failed: {error}\n")


if __name__ == "__main__":
    main()
