package de.heckenmann.visualagent.release

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipFile
import kotlin.io.path.name

/** Stateless checks for the twelve recipient release files and their uploaded metadata. */
object ReleaseAssets {
    /** Exact local recipient asset names. Unrelated remote assets remain permitted. */
    val requiredNames: Set<String> =
        setOf(
            "visual-agent-linux-deb.deb",
            "visual-agent-linux-rpm.rpm",
            "visual-agent-linux-appimage.AppImage",
            "visual-agent-linux-x64-jar.jar",
            "visual-agent-macos-arm64-jar.jar",
            "visual-agent-macos-x64-jar.jar",
            "visual-agent-windows-x64-jar.jar",
            "visual-agent-macos-arm64.dmg",
            "visual-agent-macos-x64.dmg",
            "visual-agent-windows.msi",
            "visual-agent-legal.zip",
            "visual-agent-corresponding-sources.zip",
        )

    /** Rejects missing, additional, empty, linked, corrupt, and review-only local assets. */
    fun verify(directory: Path) {
        require(Files.isDirectory(directory)) { "Release asset directory is missing" }
        val files = Files.list(directory).use { it.toList() }
        require(files.map { it.name }.toSet() == requiredNames) {
            "Candidate must contain exactly the ten packages and two legal/source archives"
        }
        files.forEach { file ->
            require(!Files.isSymbolicLink(file) && Files.isRegularFile(file) && Files.size(file) > 0) {
                "Missing, empty, or symbolic-link release file: ${file.name}"
            }
            if (file.name.endsWith(".zip")) verifyArchive(file)
        }
    }

    /** Matches each required upload across all API pages by name, state, size, and SHA-256. */
    fun verifyUploaded(
        directory: Path,
        pages: JsonElement,
    ) {
        require(pages is JsonArray && pages.all { it is JsonArray }) { "Expected paginated release asset metadata" }
        val records = pages.flatMap { it as JsonArray }.map { it.jsonObject }
        requiredNames.forEach { name ->
            val matches = records.filter { it["name"]?.jsonPrimitive?.content == name }
            require(matches.size == 1) { "Missing or duplicate uploaded asset: $name" }
            val record = matches.single()
            val file = directory.resolve(name)
            require(
                record["state"]?.jsonPrimitive?.content == "uploaded" &&
                    record["size"]?.jsonPrimitive?.isString == false &&
                    record["size"]?.jsonPrimitive?.longOrNull == Files.size(file) &&
                    record["digest"]?.jsonPrimitive?.content == "sha256:${sha256(file)}",
            ) { "Uploaded asset does not match the build: $name" }
        }
    }

    private fun sha256(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun verifyArchive(file: Path) {
        ZipFile(file.toFile()).use { archive ->
            archive.entries().asSequence().forEach { entry ->
                require(entry.name.trimEnd('/').substringAfterLast('/') != "REVIEW-ONLY.txt") {
                    "Review-only archive cannot be published: ${file.name}"
                }
                val crc = CRC32()
                var size = 0L
                archive.getInputStream(entry).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        crc.update(buffer, 0, count)
                        size += count
                    }
                }
                require(crc.value == entry.crc && size == entry.size) { "Corrupt release archive: ${file.name}" }
            }
        }
    }
}
