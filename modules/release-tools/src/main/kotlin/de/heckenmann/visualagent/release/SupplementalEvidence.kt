package de.heckenmann.visualagent.release

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Original material metadata and its unchanged binary content. */
data class SupplementalMaterial(
    /** Original declaration, including path, URL, and content hash. */
    val metadata: JsonObject,
    /** Exact retained bytes; text encodings and line endings are not normalized. */
    val content: ByteArray,
)

/** Exact artifact evidence with unresolved review requirements and original materials. */
data class SupplementalComponent(
    /** SHA-256 of the source artifact tied to the exact coordinate. */
    val sourceArtifactSha256: String,
    /** Proposed license declaration, without implying publication approval. */
    val proposedLicense: JsonElement,
    /** Exact upstream commit identity. */
    val upstreamCommit: String,
    /** Explicit unresolved review items. */
    val remaining: JsonElement,
    /** Referenced original legal texts. */
    val materials: List<SupplementalMaterial>,
)

/** Loads hash-bound upstream legal evidence without granting redistribution approval. */
object SupplementalEvidence {
    /** Reads an optional registry and validates original bytes and all component references. */
    fun load(registry: Path?): Map<String, SupplementalComponent> {
        if (registry == null) return emptyMap()
        val data = EvidenceJson.read(registry)
        require(
            data.getValue("schemaVersion").jsonPrimitive.intOrNull == 1 &&
                data.getValue("redistributionApproved").jsonPrimitive.booleanOrNull == false,
        ) {
            "Supplemental evidence cannot grant redistribution approval"
        }
        val root = registry.toAbsolutePath().parent.toRealPath()
        val materials = mutableMapOf<String, SupplementalMaterial>()
        data.getValue("materials").jsonArray.forEach { element ->
            val metadata = element.jsonObject
            val relative = metadata.text("path")
            require(safeEvidencePath(relative) && relative !in materials) { "Unsafe or duplicate supplemental material path" }
            val source = root.resolve(relative)
            require(!Files.isSymbolicLink(source) && Files.isRegularFile(source) && source.toRealPath().startsWith(root)) {
                "Missing or escaping supplemental material"
            }
            require(Files.size(source) in 1..8L * 1024 * 1024) { "Empty or oversized supplemental material" }
            val bytes = Files.readAllBytes(source)
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            require(digest == metadata.text("sha256")) { "Changed supplemental legal text" }
            val url = URI(metadata.text("url"))
            require(url.scheme == "https" && !url.host.isNullOrEmpty() && url.rawUserInfo == null) {
                "Supplemental source must be a public HTTPS URL"
            }
            materials[relative] = SupplementalMaterial(metadata, bytes)
        }
        val result = mutableMapOf<String, SupplementalComponent>()
        data.getValue("components").jsonArray.forEach { element ->
            val component = element.jsonObject
            val remaining = component.getValue("remaining")
            require(component.text("reviewStatus") == "unreviewed" && remaining.jsonArray.isNotEmpty()) {
                "Supplemental evidence must retain explicit pending review"
            }
            val commit = component.text("upstreamCommit")
            require(Regex("[0-9a-f]{40}").matches(commit)) { "Supplemental component requires an exact upstream commit" }
            val paths = component.getValue("materialPaths").jsonArray.map { it.jsonPrimitive.content }
            require(paths.isNotEmpty() && paths.toSet().size == paths.size && paths.all { it in materials }) {
                "Incomplete supplemental material references"
            }
            component.getValue("sourceArtifacts").jsonObject.forEach { (coordinate, digest) ->
                val hash = digest.jsonPrimitive.content
                require(coordinate !in result && Regex("[0-9a-f]{64}").matches(hash)) {
                    "Duplicate or invalid supplemental artifact identity"
                }
                result[coordinate] =
                    SupplementalComponent(
                        hash,
                        component.getValue("proposedLicense"),
                        commit,
                        remaining,
                        paths.map { materials.getValue(it) },
                    )
            }
        }
        return result
    }
}
