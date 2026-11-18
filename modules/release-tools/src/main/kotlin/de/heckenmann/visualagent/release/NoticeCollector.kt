package de.heckenmann.visualagent.release

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.apache.commons.csv.CSVFormat
import java.nio.file.Files
import java.nio.file.Path

/** Server-independent package evidence inputs; paths are explicit rather than inferred from the host. */
data class NoticeCollectionRequest(
    /** Extracted package directory. */
    val packageRoot: Path,
    /** CSV inventory binding package paths to coordinates and hashes. */
    val inventory: Path,
    /** New or empty destination for original notice bytes. */
    val output: Path,
    /** Exact Gradle dependency license report. */
    val licenseReport: Path,
    /** Repository project license. */
    val projectLicense: Path,
    /** Optional hash-bound supplemental registry. */
    val supplements: Path? = null,
    /** Optional complete set of AppImage provenance inputs. */
    val appImage: AppImageNoticeInputs? = null,
)

/** Collects package notices without interpreting their presence as redistribution approval. */
object NoticeCollector {
    /** Validates each inventoried artifact and returns retained, byte-exact legal evidence. */
    fun collect(request: NoticeCollectionRequest): JsonObject {
        val root = request.packageRoot.toRealPath()
        val supplemental = SupplementalEvidence.load(request.supplements)
        val runtimePackages = RuntimePackageEvidence.load(request.supplements, supplemental)
        val metadata = loadNoticeLicenseMetadata(request.licenseReport)
        require(!Files.isSymbolicLink(request.projectLicense) && Files.isRegularFile(request.projectLicense)) {
            "Project license must be a regular file"
        }
        val projectHash = noticeSha256(request.projectLicense)
        val output = request.output
        require(!Files.isSymbolicLink(output)) { "Output must be a new or empty directory" }
        if (Files.exists(output)) {
            require(Files.list(output).use { it.findAny().isEmpty }) { "Output must be a new or empty directory" }
        }
        Files.createDirectories(output)
        val rows =
            Files.newBufferedReader(request.inventory).use { reader ->
                CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader).use { parser ->
                    parser.records.map { it.toMap() }
                }
            }
        require(rows.isNotEmpty()) { "Packaged artifact inventory is empty" }
        val seen = mutableSetOf<String>()
        val components = mutableListOf<JsonObject>()
        val projects = mutableListOf<JsonObject>()
        val total = longArrayOf(0)
        rows.forEach { row ->
            val relative = row.getValue("packagePath")
            require(seen.add(relative)) { "Duplicate packaged artifact path" }
            val archive = confinedNoticeFile(root, relative)
            val hash = noticeSha256(archive)
            require(hash == row.getValue("packageSha256")) { "Packaged artifact does not match inventory: $relative" }
            val coordinate = row.getValue("coordinate")
            require(coordinate.isNotEmpty() && coordinate !in setOf("UNMATCHED", "AMBIGUOUS")) {
                "Packaged artifact has no unique resolved component identity: $relative"
            }
            val resolvedHash = row["resolvedArtifactSha256"].orEmpty()
            require(Regex("[0-9a-f]{64}").matches(resolvedHash)) { "Packaged artifact has no valid resolved artifact hash: $relative" }
            if (coordinate.startsWith("project '")) {
                projects.add(
                    buildJsonObject {
                        put("coordinate", coordinate)
                        put("packagePath", relative)
                        put("packageSha256", hash)
                        put(
                            "licenseEvidence",
                            buildJsonObject {
                                put("source", "repository-license-file")
                                put("path", "LICENSE")
                                put("sha256", projectHash)
                                put("reviewStatus", "unreviewed")
                            },
                        )
                    },
                )
            } else {
                val notices = retainJarNotices(archive, output, hash, total)
                components.add(
                    buildJsonObject {
                        put("coordinate", coordinate)
                        put("packagePath", relative)
                        put("packageSha256", hash)
                        put("resolvedArtifactSha256", resolvedHash)
                        put("reviewStatus", "unreviewed")
                        put("licenseMetadata", noticeLicenseMetadata(coordinate, metadata))
                        put("noticeStatus", if (notices.isEmpty()) "no-detected-notice" else "retained")
                        put("notices", JsonArray(notices))
                        supplemental[coordinate]?.let { put("supplement", retainSupplement(it, output, hash, total)) }
                    },
                )
            }
        }
        val runtime = retainRuntimeNotices(root, output, total)
        val appImage =
            request.appImage?.let { inputs ->
                val declaration =
                    requireNotNull(runtimePackages["linux-appimage"]) { "No pinned AppImage runtime evidence mapping is configured" }
                retainAppImageEvidence(inputs, output, total, declaration)
            }
        return buildJsonObject {
            put("schemaVersion", 1)
            put("redistributionApproved", false)
            put("inventorySha256", noticeSha256(request.inventory))
            put("licenseReportSha256", noticeSha256(request.licenseReport))
            put("projectLicenseSha256", projectHash)
            put("components", JsonArray(components.sortedBy { it.text("packagePath") }))
            put("projectArtifacts", JsonArray(projects.sortedBy { it.text("packagePath") }))
            put("runtimeLegal", runtime)
            appImage?.let { put("appImageRuntime", it) }
        }.also { writeNoticeManifest(output.resolve("manifest.json"), it) }
    }

    private fun retainSupplement(
        evidence: SupplementalComponent,
        output: Path,
        hash: String,
        total: LongArray,
    ): JsonObject {
        val retained =
            evidence.materials.map { material ->
                total[0] += material.content.size
                require(total[0] <= MAX_NOTICE_TOTAL_BYTES) { "Notice extraction limit exceeded" }
                val target = output.resolve("supplements/$hash").resolve(material.metadata.text("path"))
                Files.createDirectories(target.parent)
                Files.write(target, material.content)
                buildJsonObject {
                    put("path", output.relativize(target).toString().replace('\\', '/'))
                    put("sha256", material.metadata.text("sha256"))
                    put("sourceUrl", material.metadata.text("url"))
                }
            }
        return buildJsonObject {
            put("sourceArtifactSha256", evidence.sourceArtifactSha256)
            put("proposedLicense", evidence.proposedLicense)
            put("upstreamCommit", evidence.upstreamCommit)
            put("remaining", evidence.remaining)
            put("materials", JsonArray(retained))
        }
    }
}
