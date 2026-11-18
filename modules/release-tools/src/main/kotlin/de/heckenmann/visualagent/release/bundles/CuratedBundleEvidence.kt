package de.heckenmann.visualagent.release.bundles

import de.heckenmann.visualagent.release.EvidenceJson
import de.heckenmann.visualagent.release.safeEvidencePath
import de.heckenmann.visualagent.release.text
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Validates curated originals, referenced licenses, and all pinned runtime source archive mappings. */
internal fun curatedBundleEvidence(files: List<BundleEntry>): Map<String, JsonObject> {
    val byName = files.associateBy { it.name }
    val registryPath = requireNotNull(byName["sources.json"]?.source) { "Third-party material registry is missing" }
    val registry = EvidenceJson.read(registryPath)
    val declared = mutableMapOf<String, String>()
    registry.getValue("materials").jsonArray.forEach { element ->
        val material = element.jsonObject
        val path = material.text("path")
        require(safeEvidencePath(path) && path !in declared) { "Unsafe or duplicate curated legal-material path: $path" }
        val hash = material.text("sha256")
        require(byName[path]?.sha256() == hash) { "Curated legal material is missing or has a hash mismatch: $path" }
        declared[path] = hash
    }
    val components = (registry["components"] ?: JsonArray(emptyList())).jsonArray.map { it.jsonObject }
    val references =
        components
            .flatMap {
                (it["materialPaths"] ?: JsonArray(emptyList())).jsonArray.map { path ->
                    path.jsonPrimitive.content
                }
            }.toMutableSet()
    (registry["embeddedAssets"] ?: JsonArray(emptyList())).jsonArray.forEach { asset ->
        asset.jsonObject["licenseMaterialPath"]
            ?.jsonPrimitive
            ?.content
            ?.takeIf { it.isNotEmpty() }
            ?.let(references::add)
    }
    require(references.all { it in declared }) { "Curated materials are not declared in the registry" }
    val identities = mutableMapOf<String, Pair<String, String>>()
    components.forEach { component ->
        (component["sourceArtifacts"] ?: JsonObject(emptyMap())).jsonObject.forEach { (coordinate, hash) ->
            val digest = hash.jsonPrimitive.content
            require(
                coordinate !in identities && Regex("[0-9a-f]{64}").matches(digest),
            ) { "Duplicate or invalid curated source artifact identity" }
            val commit = component.text("upstreamCommit")
            require(Regex("[0-9a-f]{40}").matches(commit)) { "Curated source artifact has no exact upstream commit" }
            identities[coordinate] = digest to commit
        }
    }
    val packages = mutableMapOf<String, JsonObject>()
    (registry["runtimePackages"] ?: JsonArray(emptyList())).jsonArray.forEach { element ->
        val entry = element.jsonObject
        val name = entry.text("package")
        val coordinate = entry.text("runtimeSourceArtifact")
        require(name !in packages && coordinate in identities) { "Invalid or duplicate runtime package evidence mapping" }
        val linked = entry.getValue("linkedComponents").jsonArray
        require(
            linked.isNotEmpty() &&
                linked.all {
                    it.jsonPrimitive.content in identities
                },
        ) { "Runtime package has no valid mapped linked components" }
        val records = entry.getValue("sourceArchives").jsonArray
        require(records.isNotEmpty()) { "Runtime package has no source archive inventory" }
        val archives = mutableMapOf<String, String>()
        records.forEach { record ->
            val path = record.jsonObject.text("path")
            val hash = record.jsonObject.text("sha256")
            require(safeEvidencePath(path) && path !in archives && Regex("[0-9a-f]{64}").matches(hash)) {
                "Invalid or duplicate runtime source archive mapping"
            }
            archives[path] = hash
        }
        require(archives[entry.text("sourceArchivePath")] == identities.getValue(coordinate).first) {
            "Runtime source archive hash differs from its curated component identity"
        }
        packages[name] =
            buildJsonObject {
                put("runtimeSourceArtifact", coordinate)
                put("runtimeSourceArtifactSha256", identities.getValue(coordinate).first)
                put("runtimeSourceCommit", identities.getValue(coordinate).second)
                put("linkedComponents", linked)
                put(
                    "sourceArchives",
                    JsonArray(
                        archives.toSortedMap().map { (path, digest) ->
                            buildJsonObject {
                                put("path", path)
                                put("sha256", digest)
                            }
                        },
                    ),
                )
                put("sourceArchiveFiles", buildJsonObject { archives.forEach { (path, hash) -> put(path, hash) } })
                val coordinates = listOf(coordinate) + linked.map { it.jsonPrimitive.content }
                put("componentHashes", buildJsonObject { coordinates.forEach { put(it, identities.getValue(it).first) } })
                put("componentCommits", buildJsonObject { coordinates.forEach { put(it, identities.getValue(it).second) } })
            }
    }
    return packages
}
