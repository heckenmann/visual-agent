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
                "expectedSourceSha256": digest,
                "proposedLicense": component["proposedLicense"],
                "upstreamCommit": component["upstreamCommit"],
                "remaining": component["remaining"],
                "materials": [materials[path] for path in paths],
            }
    return result
