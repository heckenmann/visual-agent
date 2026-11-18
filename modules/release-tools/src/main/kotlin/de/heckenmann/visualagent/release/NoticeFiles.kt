package de.heckenmann.visualagent.release

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.apache.commons.compress.archivers.zip.ZipFile
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.CRC32
import kotlin.io.path.name

/** Maximum retained bytes per original legal text. */
internal const val MAX_NOTICE_BYTES = 8 * 1024 * 1024

/** Maximum retained evidence bytes across one package collection. */
internal const val MAX_NOTICE_TOTAL_BYTES = 64 * 1024 * 1024

/** Computes SHA-256 without loading an entire package or source archive into memory. */
internal fun noticeSha256(file: Path): String {
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

/** Resolves a declared file only within the authoritative package root. */
internal fun confinedNoticeFile(
    root: Path,
    relative: String,
): Path {
    require(safeEvidencePath(relative)) { "Unsafe packaged artifact path" }
    val file = root.resolve(relative)
    require(Files.isRegularFile(file) && file.toRealPath().startsWith(root)) { "Missing or escaping packaged artifact" }
    return file
}

/** Retains recognized original JAR notices with path, symlink, CRC, and size checks. */
internal fun retainJarNotices(
    archive: Path,
    output: Path,
    hash: String,
    total: LongArray,
): List<JsonObject> {
    val notices = mutableListOf<JsonObject>()
    val seen = mutableSetOf<String>()
    val names = Regex("^(license|licence|notice|copying|copyright|disclaimer|authors)([._-].*)?$", RegexOption.IGNORE_CASE)
    ZipFile.builder().setPath(archive).get().use { jar ->
        jar.entries.asSequence().forEach { entry ->
            require(safeEvidencePath(entry.name) && safeEvidencePath(entry.rawName.toString(Charsets.UTF_8))) {
                "Unsafe notice archive path"
            }
            val basename = entry.name.substringAfterLast('/')
            if (entry.isDirectory || !names.matches(basename) || basename.endsWith(".class", ignoreCase = true)) return@forEach
            require(seen.add(entry.name)) { "Duplicate notice entry" }
            require(!entry.isUnixSymlink) { "Linked notice entry" }
            require(entry.size in 1..MAX_NOTICE_BYTES.toLong()) { "Empty or oversized notice entry" }
            val content = jar.getInputStream(entry).use { it.readNBytes(MAX_NOTICE_BYTES + 1) }
            total[0] += content.size
            require(content.size.toLong() == entry.size && total[0] <= MAX_NOTICE_TOTAL_BYTES) { "Notice extraction limit exceeded" }
            val crc = CRC32().apply { update(content) }
            require(crc.value == entry.crc) { "Corrupt notice entry" }
            val target = output.resolve("artifacts/$hash").resolve(entry.name)
            Files.createDirectories(target.parent)
            require(!Files.exists(target) || Files.readAllBytes(target).contentEquals(content)) { "Conflicting notice output" }
            Files.write(target, content)
            notices.add(
                buildJsonObject {
                    put("entry", entry.name)
                    put("path", output.relativize(target).toString().replace('\\', '/'))
                    put("sha256", noticeSha256(target))
                    put("size", content.size)
                },
            )
        }
    }
    return notices.sortedBy { it.text("entry") }
}

/** Retains the unique packaged runtime legal directory without following linked files. */
internal fun retainRuntimeNotices(
    root: Path,
    output: Path,
    total: LongArray,
): JsonObject {
    val candidates =
        Files.walk(root).use { paths ->
            paths.filter { it.name == "java-runtime" && it.parent?.name == "legal" && it.parent?.parent?.name == "resources" }.toList()
        }
    require(candidates.size == 1) { "Package must contain exactly one resources/legal/java-runtime directory" }
    val directory = candidates.single()
    require(!Files.isSymbolicLink(directory) && Files.isDirectory(directory) && directory.toRealPath().startsWith(root)) {
        "Runtime legal directory is missing or escaping the package"
    }
    val retained = mutableListOf<JsonObject>()
    Files.walk(directory).use { paths ->
        paths.sorted().forEach { file ->
            require(!Files.isSymbolicLink(file)) { "Linked runtime legal entry" }
            if (!Files.isRegularFile(file)) return@forEach
            require(file.toRealPath().startsWith(directory.toRealPath())) { "Runtime legal entry escapes its package directory" }
            val size = Files.size(file)
            total[0] += size
            require(size <= MAX_NOTICE_BYTES && total[0] <= MAX_NOTICE_TOTAL_BYTES) { "Runtime legal material extraction limit exceeded" }
            val target = output.resolve("runtime-legal").resolve(directory.relativize(file))
            Files.createDirectories(target.parent)
            Files.write(target, Files.readAllBytes(file))
            retained.add(
                buildJsonObject {
                    put("packagePath", root.relativize(file).toString().replace('\\', '/'))
                    put("path", output.relativize(target).toString().replace('\\', '/'))
                    put("sha256", noticeSha256(file))
                    put("size", size)
                },
            )
        }
    }
    require(retained.isNotEmpty()) { "Package runtime legal directory is empty" }
    return buildJsonObject {
        put("packagePath", root.relativize(directory).toString().replace('\\', '/'))
        put("reviewStatus", "unreviewed")
        put("files", JsonArray(retained))
    }
}
