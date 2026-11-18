package de.heckenmann.visualagent.release.bundles

import de.heckenmann.visualagent.release.EvidenceJson
import de.heckenmann.visualagent.release.safeEvidencePath
import de.heckenmann.visualagent.release.text
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

/** Selects applicable recipient licenses while excluding internal review records and historical-only components. */
internal fun recipientLegalFiles(
    project: Path,
    legal: List<BundleEntry>,
    notices: Path,
): List<BundleEntry> {
    val registry = EvidenceJson.read(project.resolve("docs/licensing/third-party/sources.json"))
    val byName = legal.associateBy { it.name }
    val coordinates = mutableSetOf<String>()
    val selected = sortedMapOf<String, BundleEntry>()
    var apache = false
    val roots = sortedMapOf<String, Path>()
    Files.list(notices).use { paths ->
        paths.filter(Files::isDirectory).sorted().forEach { root ->
            val platform = root.fileName.toString().removePrefix("license-notices-")
            require(platform !in roots) { "Duplicate package notice evidence for $platform" }
            roots[platform] = root
        }
    }

    /** Retains one hash-verified material and rejects conflicting destination paths. */
    fun retain(
        root: Path,
        platform: String,
        material: JsonObject,
        category: String,
        coordinate: String? = null,
    ) {
        val entry = recipientEvidenceFile(root, platform, material, category, coordinate)
        val previous = selected[entry.name]
        require(previous == null || previous.sha256() == entry.sha256()) { "Conflicting recipient legal material for ${entry.name}" }
        selected[entry.name] = entry
    }
    roots.forEach { (platform, root) ->
        val manifest = EvidenceJson.read(root.resolve("manifest.json"))
        manifest.array("components").forEach { value ->
            val component = value.jsonObject
            val coordinate = component["coordinate"]?.jsonPrimitive?.content
            coordinate?.takeIf { it.isNotEmpty() }?.let(coordinates::add)
            val metadata = component["licenseMetadata"]?.jsonObject
            apache = apache ||
                metadata?.array("declaredLicenses")?.any {
                    it.jsonObject["name"]
                        ?.jsonPrimitive
                        ?.content
                        .orEmpty()
                        .contains("apache", ignoreCase = true)
                } == true
            component.array("notices").forEach { retain(root, platform, it.jsonObject, "notices", coordinate) }
            component["supplement"]
                ?.jsonObject
                ?.array(
                    "materials",
                )?.forEach { retain(root, platform, it.jsonObject, "supplements", coordinate) }
        }
        manifest["runtimeLegal"]?.jsonObject?.array("files")?.forEach { retain(root, platform, it.jsonObject, "java-runtime") }
        manifest["appImageRuntime"]?.jsonObject?.array("components")?.forEach { value ->
            val component = value.jsonObject
            val coordinate = component.text("coordinate")
            coordinates.add(coordinate)
            component.array("materials").forEach { retain(root, platform, it.jsonObject, "appimage-runtime", coordinate) }
        }
    }
    val paths = mutableSetOf<String>()
    registry.array("components").forEach { value ->
        val component = value.jsonObject
        if (component
                .getValue("sourceArtifacts")
                .jsonObject.keys
                .any { it in coordinates }
        ) {
            component.array("materialPaths").forEach { paths.add(it.jsonPrimitive.content) }
        }
    }
    registry.array("embeddedAssets").forEach { value ->
        val asset = value.jsonObject
        if (asset["containerCoordinate"]?.jsonPrimitive?.content in coordinates) {
            asset["licenseMaterialPath"]
                ?.jsonPrimitive
                ?.content
                ?.takeIf { it.isNotEmpty() }
                ?.let(paths::add)
        }
    }
    if (apache) paths.add("Apache-2.0.txt")
    require(paths.all { it in byName }) { "Recipient legal material is missing" }
    val result =
        listOf(
            BundleEntry("LICENSE", source = project.resolve("LICENSE")),
            BundleEntry(
                "README-LICENSES.txt",
                bytes =
                    (
                        "This archive contains the Visual Agent license and third-party license/notice materials.\n" +
                            "Platform-specific package and runtime materials are grouped under platforms/.\n" +
                            "Each included component remains subject to its accompanying license.\n" +
                            "MANIFEST.sha256 lists the SHA-256 hashes of the included files.\n"
                    ).toByteArray(),
            ),
        ) +
            paths.sorted().map { byName.getValue(it).copy(name = "third-party/$it") } + selected.values
    require(result.map { it.name }.toSet().size == result.size) { "Recipient legal archive contains duplicate paths" }
    return result
}

private fun recipientEvidenceFile(
    root: Path,
    platform: String,
    material: JsonObject,
    category: String,
    coordinate: String?,
): BundleEntry {
    val relative = material.text("path")
    require(safeEvidencePath(relative)) { "Recipient legal material is missing or escaping: $platform/$relative" }
    val source = root.resolve(relative)
    require(!Files.isSymbolicLink(source) && Files.isRegularFile(source) && source.toRealPath().startsWith(root.toRealPath())) {
        "Recipient legal material is missing or escaping: $platform/$relative"
    }
    val entry = BundleEntry(relative, source = source)
    require(entry.sha256() == material.text("sha256")) { "Recipient legal material hash mismatch: $platform/$relative" }
    val target =
        if (category == "java-runtime" || category == "appimage-runtime") {
            val prefix = if (category == "java-runtime") "runtime-legal/" else "appimage-runtime-legal/"
            require(relative.startsWith(prefix)) { "Unexpected runtime legal path: $platform/$relative" }
            "platforms/$platform/$category/${relative.removePrefix(prefix)}"
        } else {
            require(!coordinate.isNullOrEmpty()) { "Package legal material has no component coordinate: $platform/$relative" }
            val slug = coordinate.replace(Regex("[^A-Za-z0-9._+-]+"), "_")
            "platforms/$platform/$category/$slug/${material.text("sha256")}/${relative.substringAfterLast('/')}"
        }
    return entry.copy(name = target)
}

/** Builds recipient source input selection without including internal review registries. */
internal fun recipientSourceFiles(files: List<BundleEntry>): List<BundleEntry> {
    val archives = files.filter { it.name.startsWith("archives/") }
    require(archives.isNotEmpty()) { "Recipient source archive has no source inputs" }
    val lines =
        listOf(
            "Source materials for the matching Visual Agent release are listed below.",
            "The paired visual-agent-legal.zip contains applicable license and source-availability notices.",
            "Obtain both archives from the same versioned release; MANIFEST.sha256 records file hashes.",
            "",
            "Included source inputs:",
        ) +
            archives.map { "${it.sha256()}  source-materials/${it.name}" } + ""
    return listOf(BundleEntry("README-SOURCES.txt", bytes = lines.joinToString("\n").toByteArray())) +
        archives.map { it.copy(name = "source-materials/${it.name}") }
}
