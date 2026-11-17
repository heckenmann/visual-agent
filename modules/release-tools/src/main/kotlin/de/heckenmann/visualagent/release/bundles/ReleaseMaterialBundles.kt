package de.heckenmann.visualagent.release.bundles

import de.heckenmann.visualagent.release.noticeSha256
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

/** Complete archive-builder inputs; recipient mode changes selection, never legal approval. */
data class BundleRequest(
    /** Repository root with authoritative legal documents. */
    val projectRoot: Path,
    /** Retained source archives and delivery scope. */
    val sourceMaterials: Path,
    /** Six platform notice-evidence directories. */
    val noticeEvidence: Path,
    /** Destination for both ZIP files. */
    val outputDirectory: Path,
    /** Select recipient-facing contents without review-only markers. */
    val recipientCandidate: Boolean = false,
)

/** Builds deterministic legal and source archives while preserving evidence validation. */
object ReleaseMaterialBundles {
    /** Review-only marker used by existing release guards. */
    const val REVIEW_NOTICE =
        "REVIEW EVIDENCE ONLY. This archive is not a complete legal or corresponding-source delivery, " +
            "does not establish redistribution approval, and must not be published as a release asset. " +
            "See issue #410 and the included source-delivery.md for verified scope and open items.\n"

    /** Creates both archives after validating source mappings and every platform manifest. */
    fun build(request: BundleRequest): Pair<Path, Path> {
        require(!Files.isSymbolicLink(request.sourceMaterials)) { "Required material directory is missing or linked" }
        val project = request.projectRoot.toRealPath()
        val sourcesRoot = request.sourceMaterials.toRealPath()
        val output = resolvedBundleOutput(request.outputDirectory)
        require(!output.startsWith(sourcesRoot)) { "Output directory must not be inside source materials" }
        val required = listOf("LICENSE", "docs/licensing/README.md", "docs/licensing/source-delivery.md").map(project::resolve)
        require(
            required.all { !Files.isSymbolicLink(it) && Files.isRegularFile(it) },
        ) { "Required project legal documents are missing or linked" }
        val sources = bundleFiles(sourcesRoot)
        require(sources.any { it.name.startsWith("archives/") }) { "Source evidence archive directory is empty" }
        require(sources.any { it.name == "source-delivery.md" }) { "Source-delivery scope statement is missing from source materials" }
        val legal = bundleFiles(project.resolve("docs/licensing/third-party"))
        val runtimes = curatedBundleEvidence(legal)
        val byName = sources.associateBy { it.name }
        runtimes.values.forEach { runtime ->
            runtime.getValue("sourceArchiveFiles").jsonObject.forEach { (name, hash) ->
                require(
                    byName[name]?.sha256() == hash.jsonPrimitive.content,
                ) { "Pinned runtime source archive is missing or changed: $name" }
            }
        }
        val notices = packageNoticeEvidence(request.noticeEvidence, noticeSha256(required.first()), runtimes)
        val legalEntries =
            if (request.recipientCandidate) {
                recipientLegalFiles(project, legal, request.noticeEvidence)
            } else {
                listOf(
                    BundleEntry("LICENSE", source = required[0]),
                    BundleEntry("licensing/README.md", source = required[1]),
                    BundleEntry("licensing/source-delivery.md", source = required[2]),
                ) +
                    legal.map { it.copy(name = "licensing/third-party/${it.name}") } + notices
            }
        val sourceEntries =
            if (request.recipientCandidate) {
                recipientSourceFiles(sources)
            } else {
                sources.map {
                    it.copy(name = "source-materials/${it.name}")
                }
            }
        val marker = if (request.recipientCandidate) null else REVIEW_NOTICE
        Files.createDirectories(output)
        val legalArchive = output.resolve("visual-agent-legal.zip")
        val sourceArchive = output.resolve("visual-agent-corresponding-sources.zip")
        writeBundleArchive(legalArchive, legalEntries, marker)
        writeBundleArchive(sourceArchive, sourceEntries, marker)
        return legalArchive to sourceArchive
    }
}

private fun resolvedBundleOutput(path: Path): Path {
    val absolute = path.toAbsolutePath().normalize()
    if (Files.exists(absolute)) return absolute.toRealPath()
    return requireNotNull(absolute.parent).let { resolvedBundleOutput(it).resolve(absolute.fileName) }
}
