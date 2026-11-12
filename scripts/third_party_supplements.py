"""Load version/hash-bound upstream legal evidence, never publication approval."""

import hashlib
import json
import pathlib
import re
import urllib.parse


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate supplemental metadata key")
        result[key] = value
    return result


def load_supplements(registry):
    if registry is None:
        return {}
    data = json.loads(registry.read_text(encoding="utf-8"), object_pairs_hook=unique_object)
    if data["schemaVersion"] != 1 or data["redistributionApproved"] is not False:
        raise ValueError("Supplemental evidence cannot grant redistribution approval")
    root = registry.parent.resolve()
    materials = {}
    for material in data["materials"]:
        relative = material["path"]
        path = pathlib.PurePosixPath(relative)
        if path.is_absolute() or ".." in path.parts or "\\" in relative or ":" in relative or relative in materials:
            raise ValueError("Unsafe or duplicate supplemental material path")
        source = root.joinpath(*path.parts)
        if source.is_symlink() or not source.resolve().is_relative_to(root) or not source.is_file():
            raise ValueError("Missing or escaping supplemental material")
        if not 0 < source.stat().st_size <= 8 * 1024 * 1024:
            raise ValueError("Empty or oversized supplemental material")
        content = source.read_bytes()
        if hashlib.sha256(content).hexdigest() != material["sha256"]:
            raise ValueError("Changed supplemental legal text")
        url = urllib.parse.urlsplit(material["url"])
        if url.scheme != "https" or not url.hostname or url.username or url.password:
            raise ValueError("Supplemental source must be a public HTTPS URL")
        materials[relative] = {**material, "content": content}
    result = {}
    for component in data["components"]:
        if component["reviewStatus"] != "unreviewed" or not component["remaining"]:
            raise ValueError("Supplemental evidence must retain explicit pending review")
        if not re.fullmatch(r"[0-9a-f]{40}", component["upstreamCommit"]):
            raise ValueError("Supplemental component requires an exact upstream commit")
        paths = component["materialPaths"]
        if not paths or len(set(paths)) != len(paths) or any(path not in materials for path in paths):
            raise ValueError("Incomplete supplemental material references")
        for coordinate, digest in component["sourceArtifacts"].items():
            if coordinate in result or not re.fullmatch(r"[0-9a-f]{64}", digest):
                raise ValueError("Duplicate or invalid supplemental artifact identity")
            result[coordinate] = {
                "sourceArtifactSha256": digest,
                "proposedLicense": component["proposedLicense"],
                "upstreamCommit": component["upstreamCommit"],
                "remaining": component["remaining"],
                "materials": [materials[path] for path in paths],
            }
    return result


def load_runtime_packages(registry, supplements):
    """Load package-to-runtime source mappings without implying legal clearance."""
    if registry is None:
        return {}
    data = json.loads(registry.read_text(encoding="utf-8"), object_pairs_hook=unique_object)
    if data.get("schemaVersion") != 1 or data.get("redistributionApproved") is not False:
        raise ValueError("Runtime package evidence cannot grant redistribution approval")
    packages = {}
    for package in data.get("runtimePackages", []):
        name = package.get("package")
        runtime_source = package.get("runtimeSourceArtifact")
        source_archive = package.get("sourceArchivePath")
        source_archives = package.get("sourceArchives")
        linked_components = package.get("linkedComponents")
        if not isinstance(name, str) or not re.fullmatch(r"[a-z0-9-]+", name) or name in packages:
            raise ValueError("Invalid or duplicate runtime package identity")
        if not isinstance(source_archive, str) or not source_archive:
            raise ValueError("Runtime package has no source archive path")
        path = pathlib.PurePosixPath(source_archive)
        if path.is_absolute() or ".." in path.parts or "\\" in source_archive or ":" in source_archive:
            raise ValueError("Unsafe runtime source archive path")
        if not isinstance(runtime_source, str) or runtime_source not in supplements:
            raise ValueError("Runtime package is missing its pinned source evidence")
        if not isinstance(linked_components, list) or not linked_components:
            raise ValueError("Runtime package is missing its pinned source or component mappings")
        if len(set(linked_components)) != len(linked_components):
            raise ValueError("Runtime package contains duplicate linked components")
        if any(component not in supplements for component in linked_components):
            raise ValueError("Runtime package references a component without pinned source evidence")
        if not isinstance(source_archives, list) or not source_archives:
            raise ValueError("Runtime package has no pinned source archive inventory")
        archive_hashes = {}
        for archive in source_archives:
            archive_path = archive.get("path")
            archive_digest = archive.get("sha256")
            safe_archive_path = pathlib.PurePosixPath(archive_path or "")
            if (
                not isinstance(archive_path, str)
                or safe_archive_path.is_absolute()
                or ".." in safe_archive_path.parts
                or "\\" in archive_path
                or ":" in archive_path
                or archive_path in archive_hashes
                or not isinstance(archive_digest, str)
                or not re.fullmatch(r"[0-9a-f]{64}", archive_digest)
            ):
                raise ValueError("Invalid or duplicate runtime source archive evidence")
            archive_hashes[archive_path] = archive_digest
        if archive_hashes.get(source_archive) != supplements[runtime_source]["sourceArtifactSha256"]:
            raise ValueError("Runtime source archive mapping does not match its component hash")
        packages[name] = {
            **package,
            "runtimeSource": supplements[runtime_source],
            "linkedComponentEvidence": {coordinate: supplements[coordinate] for coordinate in linked_components},
            "sourceArchives": archive_hashes,
        }
    return packages
