package de.heckenmann.visualagent.release

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

/** The three required inputs binding an AppImage binary to its source-built runtime. */
data class AppImageNoticeInputs(
    /** Final portable application package. */
    val packaged: Path,
    /** Source-built standalone Type 2 runtime. */
    val runtime: Path,
    /** Source archive matching the pinned runtime declaration. */
    val sourceArchive: Path,
)

/** Retains runtime source identity and legal texts after validating the actual embedded prefix. */
internal fun retainAppImageEvidence(
    inputs: AppImageNoticeInputs,
    output: Path,
    total: LongArray,
    declaration: RuntimePackageEvidence,
    execute: (List<String>) -> String = { runEvidenceCommand(it, output) },
): JsonObject {
    val (packaged, runtime, sourceArchive) = inputs
    require(listOf(packaged, runtime, sourceArchive).all { !Files.isSymbolicLink(it) && Files.isRegularFile(it) }) {
        "AppImage package, runtime, and source archive must be regular files"
    }
    val sourceHash = noticeSha256(sourceArchive)
    val source = declaration.runtimeSource
    require(sourceHash == source.sourceArtifactSha256) { "AppImage runtime source archive does not match the pinned evidence" }
    val runtimeSize = Files.size(runtime)
    require(runtimeSize + total[0] <= MAX_NOTICE_TOTAL_BYTES) { "AppImage runtime evidence size limit exceeded" }

    /** Resolves the sole mutable digest region used by appimagetool. */
    fun section(file: Path): Pair<Int, Int> =
        AppImageRuntime.parseDigestSection(execute(listOf("readelf", "--wide", "--sections", file.toString())))
    AppImageRuntime.verifyPrefix(runtime, packaged, section(runtime), section(packaged))
    val version = execute(listOf(runtime.toRealPath().toString(), "--appimage-version")).trim()
    require(version.contains("https://github.com/AppImage/type2-runtime/commit/${source.upstreamCommit.take(7)}")) {
        "Built AppImage runtime does not identify its pinned source commit"
    }
    total[0] += runtimeSize
    val evidence = listOf("AppImage:type2-runtime:20251108" to source) + declaration.linkedComponentEvidence.toList()
    val components =
        evidence.map { (coordinate, component) ->
            val materials =
                component.materials.map { material ->
                    total[0] += material.content.size
                    require(total[0] <= MAX_NOTICE_TOTAL_BYTES) { "AppImage legal evidence size limit exceeded" }
                    val slug = coordinate.lowercase().replace(Regex("[^a-z0-9.-]+"), "-")
                    val target = output.resolve("appimage-runtime-legal/$slug/${material.metadata.text("path").substringAfterLast('/')}")
                    Files.createDirectories(target.parent)
                    Files.write(target, material.content)
                    buildJsonObject {
                        put("path", output.relativize(target).toString().replace('\\', '/'))
                        put("sha256", material.metadata.text("sha256"))
                        put("sourcePath", material.metadata.text("path"))
                    }
                }
            buildJsonObject {
                put("coordinate", coordinate)
                put("sourceArtifactSha256", component.sourceArtifactSha256)
                put("upstreamCommit", component.upstreamCommit)
                put("proposedLicense", component.proposedLicense)
                put("reviewStatus", "unreviewed")
                put("remaining", component.remaining)
                put("materials", JsonArray(materials))
            }
        }
    return buildJsonObject {
        put("reviewStatus", "unreviewed")
        put("packagePath", packaged.name)
        put("packageSha256", noticeSha256(packaged))
        put("runtimeName", runtime.name)
        put("runtimeSha256", noticeSha256(runtime))
        put("runtimeSize", runtimeSize)
        put("sourceArchiveSha256", sourceHash)
        put("sourceArchivePath", declaration.metadata.text("sourceArchivePath"))
        put(
            "sourceArchives",
            JsonArray(
                declaration.sourceArchives.toSortedMap().map { (path, digest) ->
                    buildJsonObject {
                        put("path", path)
                        put("sha256", digest)
                    }
                },
            ),
        )
        put("sourceCommit", source.upstreamCommit)
        put("reportedVersion", version)
        put("embeddedPrefixVerified", true)
        put("components", JsonArray(components))
    }
}
