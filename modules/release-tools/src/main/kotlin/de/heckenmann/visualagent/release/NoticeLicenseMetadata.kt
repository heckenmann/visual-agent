package de.heckenmann.visualagent.release

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.file.Path

/** Reads exact Gradle dependency identities and preserves every declared license alternative. */
internal fun loadNoticeLicenseMetadata(report: Path): Map<String, JsonObject> {
    val indexed = mutableMapOf<String, JsonObject>()
    EvidenceJson.read(report).getValue("dependencies").jsonArray.forEach { element ->
        val dependency = element.jsonObject
        val name = dependency.text("moduleName")
        val version = dependency.text("moduleVersion")
        require(name.isNotEmpty() && version.isNotEmpty()) { "Gradle license report contains a dependency without exact coordinates" }
        val urls = (dependency["moduleUrls"] ?: JsonArray(emptyList())).jsonArray
        require(urls.all { it.jsonPrimitive.isString }) { "Invalid module URL in Gradle license report" }
        val licenses =
            (dependency["moduleLicenses"] ?: JsonArray(emptyList())).jsonArray.map { license ->
                val fields = license.jsonObject
                buildJsonObject {
                    for ((from, to) in listOf("moduleLicense" to "name", "moduleLicenseUrl" to "url")) {
                        val value = fields[from] ?: JsonNull
                        require(
                            value == JsonNull || value.jsonPrimitive.isString,
                        ) { "Invalid declared license name or URL in Gradle license report" }
                        put(to, value)
                    }
                }
            }
        val metadata =
            buildJsonObject {
                put("moduleName", name)
                put("moduleVersion", version)
                put("moduleUrls", urls)
                put("declaredLicenses", JsonArray(licenses))
            }
        val coordinate = "$name:$version"
        require(coordinate !in indexed || indexed[coordinate] == metadata) {
            "Conflicting license report entries for exact dependency: $coordinate"
        }
        indexed[coordinate] = metadata
    }
    return indexed
}

/** Requires exact Maven metadata; runtime artifacts point to their separately retained legal evidence. */
internal fun noticeLicenseMetadata(
    coordinate: String,
    indexed: Map<String, JsonObject>,
): JsonObject =
    buildJsonObject {
        put("reviewStatus", "unreviewed")
        if (coordinate.startsWith("bundled-jdk:")) {
            put("source", "packaged-runtime-legal-evidence")
            put("status", "see-runtimeLegal")
        } else {
            require(coordinate.substringBeforeLast(':', "").contains(':')) {
                "Packaged external artifact has invalid Maven coordinates: $coordinate"
            }
            val metadata = requireNotNull(indexed[coordinate]) { "No exact Gradle license metadata for packaged artifact: $coordinate" }
            put("source", "gradle-license-report")
            put("status", if (metadata.getValue("declaredLicenses").jsonArray.isEmpty()) "no-license-declaration" else "reported")
            metadata.forEach { (key, value) -> put(key, value) }
        }
    }
