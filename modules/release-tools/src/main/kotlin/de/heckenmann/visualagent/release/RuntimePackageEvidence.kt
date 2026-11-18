package de.heckenmann.visualagent.release

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path

/** A package declaration linked to exact source artifacts and archive hashes. */
data class RuntimePackageEvidence(
    /** Original declaration, including package and primary source archive paths. */
    val metadata: JsonObject,
    /** Pinned source evidence for the runtime. */
    val runtimeSource: SupplementalComponent,
    /** Pinned evidence for every linked component. */
    val linkedComponentEvidence: Map<String, SupplementalComponent>,
    /** Unique source archive paths and their SHA-256 hashes. */
    val sourceArchives: Map<String, String>,
) {
    companion object {
        /** Loads package mappings without converting pending evidence into publication approval. */
        fun load(
            registry: Path?,
            supplements: Map<String, SupplementalComponent>,
        ): Map<String, RuntimePackageEvidence> {
            if (registry == null) return emptyMap()
            val data = EvidenceJson.read(registry)
            require(
                data.getValue("schemaVersion").jsonPrimitive.intOrNull == 1 &&
                    data.getValue("redistributionApproved").jsonPrimitive.booleanOrNull == false,
            ) {
                "Runtime package evidence cannot grant redistribution approval"
            }
            val result = mutableMapOf<String, RuntimePackageEvidence>()
            (data["runtimePackages"] ?: JsonArray(emptyList())).jsonArray.forEach { element ->
                val entry = element.jsonObject
                val name = entry.text("package")
                require(Regex("[a-z0-9-]+").matches(name) && name !in result) { "Invalid or duplicate runtime package identity" }
                val archivePath = entry.text("sourceArchivePath")
                require(safeEvidencePath(archivePath)) { "Unsafe runtime source archive path" }
                val coordinate = entry.text("runtimeSourceArtifact")
                require(coordinate in supplements) { "Runtime package is missing its pinned source evidence" }
                val linked = entry.getValue("linkedComponents").jsonArray.map { it.jsonPrimitive.content }
                require(linked.isNotEmpty()) { "Runtime package is missing its pinned source or component mappings" }
                require(linked.size == linked.toSet().size) { "Runtime package contains duplicate linked components" }
                require(linked.all { it in supplements }) { "Runtime package references a component without pinned source evidence" }
                val archives = entry.getValue("sourceArchives").jsonArray
                require(archives.isNotEmpty()) { "Runtime package has no pinned source archive inventory" }
                val hashes = mutableMapOf<String, String>()
                archives.forEach { archive ->
                    val declaration = archive.jsonObject
                    val path = declaration.text("path")
                    val hash = declaration.text("sha256")
                    require(safeEvidencePath(path) && path !in hashes && Regex("[0-9a-f]{64}").matches(hash)) {
                        "Invalid or duplicate runtime source archive evidence"
                    }
                    hashes[path] = hash
                }
                val source = supplements.getValue(coordinate)
                require(hashes[archivePath] == source.sourceArtifactSha256) {
                    "Runtime source archive mapping does not match its component hash"
                }
                result[name] = RuntimePackageEvidence(entry, source, linked.associateWith(supplements::getValue), hashes)
            }
            return result
        }
    }
}
