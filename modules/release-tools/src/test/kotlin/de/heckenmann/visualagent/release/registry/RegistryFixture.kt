package de.heckenmann.visualagent.release.registry

import de.heckenmann.visualagent.release.EvidenceJson
import de.heckenmann.visualagent.release.SupplementalEvidence
import de.heckenmann.visualagent.release.text
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.nio.file.Path

/** Loads the immutable repository registry independently for each semantic regression test. */
internal class RegistryFixture {
    val root: Path = Path.of(System.getProperty("release.tools.repository"))
    val path: Path = root.resolve("docs/licensing/third-party/sources.json")
    val data: JsonObject = EvidenceJson.read(path)
    val components = SupplementalEvidence.load(path)

    fun record(coordinate: String): JsonObject =
        data
            .getValue("components")
            .jsonArray
            .map { it.jsonObject }
            .single { coordinate in it.getValue("sourceArtifacts").jsonObject }

    fun material(path: String): JsonObject =
        data
            .getValue("materials")
            .jsonArray
            .map { it.jsonObject }
            .single { it.text("path") == path }
}
