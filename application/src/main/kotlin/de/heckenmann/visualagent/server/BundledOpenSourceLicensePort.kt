package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.protocol.OpenSourceLicenseDocument
import de.heckenmann.visualagent.protocol.OpenSourceLicensePort
import de.heckenmann.visualagent.protocol.ProjectMetadata
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.stereotype.Component

/** Reads the exact curated legal files copied into the application artifact at build time. */
@Component
class BundledOpenSourceLicensePort : OpenSourceLicensePort {
    override fun documents(): List<OpenSourceLicenseDocument> {
        val loader = BundledOpenSourceLicensePort::class.java.classLoader

        /** Loads one bundled UTF-8 legal document and preserves its package path. */
        fun readDocument(
            resourcePath: String,
            component: String,
            displayPath: String,
        ): OpenSourceLicenseDocument {
            val text =
                loader
                    .getResourceAsStream(resourcePath)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: error("Bundled license document is missing: $displayPath")
            return OpenSourceLicenseDocument(component, displayPath, text)
        }

        val componentIndex =
            loader
                .getResourceAsStream(THIRD_PARTY_INDEX)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("Bundled third-party license index is missing")
        val index = Json.parseToJsonElement(componentIndex).jsonObject
        val componentEntries = index.getValue("components").jsonArray
        val paths =
            componentEntries.flatMap { entry ->
                val component =
                    entry.jsonObject
                        .getValue("sourceArtifacts")
                        .jsonObject.keys
                        .sorted()
                        .joinToString(", ")
                entry.jsonObject.getValue("materialPaths").jsonArray.map { path ->
                    component to path.jsonPrimitive.content
                }
            }
        val embeddedComponents =
            index.getValue("embeddedComponents").jsonArray.flatMap { entry ->
                val component =
                    entry.jsonObject
                        .getValue("name")
                        .jsonPrimitive.content
                entry.jsonObject.getValue("materialPaths").jsonArray.map { path ->
                    component to path.jsonPrimitive.content
                }
            }
        val embeddedAssets =
            index.getValue("embeddedAssets").jsonArray.map { entry ->
                entry.jsonObject
                    .getValue("name")
                    .jsonPrimitive.content to
                    entry.jsonObject
                        .getValue("licenseMaterialPath")
                        .jsonPrimitive.content
            }
        val projectLicense =
            readDocument(PROJECT_LICENSE, ProjectMetadata.DISPLAY_NAME, "LICENSE")
        return (
            listOf(projectLicense) +
                (paths + embeddedComponents + embeddedAssets).map { (component, path) ->
                    require(isSafeRelativePath(path)) { "Unsafe bundled license path" }
                    readDocument("$THIRD_PARTY_ROOT/$path", component, path)
                }
        ).distinctBy { it.path }.sortedWith(compareBy({ it.component }, { it.path }))
    }

    private fun isSafeRelativePath(path: String): Boolean =
        path.isNotBlank() &&
            !path.startsWith('/') &&
            '\\' !in path &&
            path.split('/').none { it == ".." || it.isBlank() }

    private companion object {
        const val PROJECT_LICENSE = "META-INF/visual-agent/legal/visual-agent/LICENSE"
        const val THIRD_PARTY_ROOT = "META-INF/visual-agent/legal/third-party"
        const val THIRD_PARTY_INDEX = "$THIRD_PARTY_ROOT/sources.json"
    }
}
