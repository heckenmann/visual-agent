package de.heckenmann.visualagent.release.bundles

import de.heckenmann.visualagent.release.EvidenceJson
import de.heckenmann.visualagent.release.text
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.nio.file.Files
import java.nio.file.Path

/** The complete native-platform notice matrix required by both review and recipient archives. */
internal val bundleNoticePlatforms: Set<String> = setOf("linux-appimage", "linux-deb", "linux-rpm", "macos-arm64", "macos-x64", "windows")

/** Returns optional array-valued evidence fields without normalizing their contents. */
internal fun JsonObject.array(name: String): JsonArray = (get(name) ?: JsonArray(emptyList())).jsonArray

/** Validates all six platform manifests and binds each retained byte to its declared hash. */
internal fun packageNoticeEvidence(
    root: Path,
    projectHash: String,
    runtimes: Map<String, JsonObject>,
): List<BundleEntry> {
    val directories = mutableMapOf<String, String>()
    bundleFiles(root).map { it.name.substringBefore('/') }.toSet().forEach { directory ->
        val platform = directory.removePrefix("license-notices-")
        require(platform !in directories) { "Duplicate package notice evidence for $platform" }
        directories[platform] = directory
    }
    require(directories.keys == bundleNoticePlatforms) { "Platform notice evidence mismatch" }
    return directories.toSortedMap().flatMap { (platform, directory) ->
        val files = bundleFiles(root.resolve(directory))
        val byName = files.associateBy { it.name }
        val manifestPath = requireNotNull(byName["manifest.json"]?.source) { "Notice evidence manifest is missing for $platform" }
        val manifest = EvidenceJson.read(manifestPath)
        require(manifest["redistributionApproved"].toString() == "false" && manifest.array("components").isNotEmpty()) {
            "Notice evidence is not explicitly unreviewed for $platform"
        }
        require(
            manifest.text("projectLicenseSha256") == projectHash,
        ) { "Project license evidence does not match the legal bundle for $platform" }
        manifest.array("components").forEach { value ->
            val component = value.jsonObject
            require(component.text("reviewStatus") == "unreviewed") { "Notice evidence component is not marked unreviewed for $platform" }
            val supplements = component["supplement"]?.jsonObject?.array("materials") ?: JsonArray(emptyList())
            validateBundleMaterials(component.array("notices") + supplements, byName, false)
        }
        val runtime = manifest.getValue("runtimeLegal").jsonObject
        require(runtime.text("reviewStatus") == "unreviewed" && runtime.array("files").isNotEmpty()) {
            "Runtime legal evidence is missing, reviewed, or empty for $platform"
        }
        val referenced = validateBundleMaterials(runtime.array("files"), byName, true)
        require(referenced == byName.keys.filter { it.startsWith("runtime-legal/") }.toSet()) {
            "Runtime legal evidence is incomplete or has unmanifested files for $platform"
        }
        if (platform == "linux-appimage") {
            val expected = requireNotNull(runtimes[platform]) { "AppImage runtime evidence is missing" }
            val actual = manifest.getValue("appImageRuntime").jsonObject
            validateAppImageBundleEvidence(actual, expected, byName)
        } else {
            require("appImageRuntime" !in manifest) { "Unexpected AppImage runtime evidence for $platform" }
        }
        files.map { it.copy(name = "package-notice-evidence/$platform/${it.name}") }
    }
}

private fun validateBundleMaterials(
    materials: List<kotlinx.serialization.json.JsonElement>,
    files: Map<String, BundleEntry>,
    sizes: Boolean,
): Set<String> =
    materials
        .map { value ->
            val material = value.jsonObject
            val name = material.text("path")
            val file = requireNotNull(files[name]) { "Notice evidence file is missing: $name" }
            require(file.sha256() == material.text("sha256")) { "Notice evidence hash mismatch: $name" }
            if (sizes) {
                require(Files.size(requireNotNull(file.source)) == material.getValue("size").jsonPrimitive.longOrNull) {
                    "Runtime legal evidence size mismatch: $name"
                }
            }
            name
        }.toSet()

private fun validateAppImageBundleEvidence(
    actual: JsonObject,
    expected: JsonObject,
    files: Map<String, BundleEntry>,
) {
    require(actual.text("reviewStatus") == "unreviewed" && actual["embeddedPrefixVerified"].toString() == "true") {
        "AppImage runtime evidence does not match its pinned package mapping"
    }
    for (field in listOf("packageSha256", "runtimeSha256", "sourceArchiveSha256")) {
        require(Regex("[0-9a-f]{64}").matches(actual.text(field))) { "AppImage runtime evidence has an invalid hash" }
    }
    require(
        actual["sourceArchiveSha256"] == expected["runtimeSourceArtifactSha256"] &&
            actual["sourceArchives"] == expected["sourceArchives"] &&
            actual["sourceCommit"] == expected["runtimeSourceCommit"],
    ) {
        "AppImage runtime evidence does not match its pinned package mapping"
    }
    val components = actual.getValue("components").jsonArray.map { it.jsonObject }
    val coordinates = expected.array("linkedComponents").map { it.jsonPrimitive.content }.toSet() + expected.text("runtimeSourceArtifact")
    require(components.map { it.text("coordinate") }.toSet() == coordinates) { "AppImage runtime component evidence is incomplete" }
    val referenced =
        components
            .flatMap { component ->
                val coordinate = component.text("coordinate")
                require(
                    component.text("reviewStatus") == "unreviewed" &&
                        component["sourceArtifactSha256"] == expected.getValue("componentHashes").jsonObject[coordinate] &&
                        component["upstreamCommit"] == expected.getValue("componentCommits").jsonObject[coordinate],
                ) {
                    "AppImage runtime component evidence is not marked unreviewed or has changed provenance"
                }
                validateBundleMaterials(component.array("materials"), files, false)
            }.toSet()
    require(referenced == files.keys.filter { it.startsWith("appimage-runtime-legal/") }.toSet()) {
        "AppImage runtime legal materials are incomplete or unmanifested"
    }
}
