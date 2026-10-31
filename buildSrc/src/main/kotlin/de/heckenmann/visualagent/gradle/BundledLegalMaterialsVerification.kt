package de.heckenmann.visualagent.gradle

import java.io.File
import java.nio.file.Files
import java.util.jar.JarFile

private const val MAX_LEGAL_BYTES = 8 * 1024 * 1024

/** Verify curated original texts inside a native image without changing its signed contents. */
fun verifyNativeProjectLegalMaterials(
    resources: File,
    projectRoot: File,
) {
    verifyProjectLegalMaterials(projectRoot) { relative ->
        resources.resolve("legal/$relative").takeIf(File::isFile)?.let(::readLegalFile)
    }
}

/** Verify curated original texts in the executable JAR, independently of its nested libraries. */
fun verifyJarProjectLegalMaterials(
    archive: JarFile,
    projectRoot: File,
) {
    verifyProjectLegalMaterials(projectRoot) { relative ->
        val name = "META-INF/visual-agent/legal/$relative"
        check(archive.entries().asSequence().count { it.name == name } <= 1) { "Duplicate bundled legal entry: $relative" }
        archive.getJarEntry(name)?.let { entry ->
            check(entry.size in 1..MAX_LEGAL_BYTES.toLong()) { "Invalid bundled legal entry size: $relative" }
            archive.getInputStream(entry).use { stream ->
                stream.readNBytes(MAX_LEGAL_BYTES + 1).also { bytes ->
                    check(bytes.size.toLong() == entry.size) { "Invalid bundled legal content size: $relative" }
                }
            }
        }
    }
}

private fun verifyProjectLegalMaterials(
    projectRoot: File,
    read: (String) -> ByteArray?,
) {
    val upstream = projectRoot.resolve("docs/licensing/third-party")
    check(upstream.resolve("sources.json").isFile) { "Curated legal source registry is missing" }
    val files = sortedMapOf("visual-agent/LICENSE" to projectRoot.resolve("LICENSE"))
    upstream
        .walkTopDown()
        .onEnter { directory ->
            check(!Files.isSymbolicLink(directory.toPath())) { "Linked legal directories require explicit review" }
            true
        }.filter(File::isFile)
        .forEach { source ->
            files["third-party/${source.relativeTo(upstream).invariantSeparatorsPath}"] = source
        }
    check(files.size > 2) { "Curated upstream legal texts are missing" }
    files.forEach { (relative, source) ->
        val original = readLegalFile(source)
        val retained = read(relative)
        check(retained != null && retained.contentEquals(original)) { "Bundled legal file is missing or changed: $relative" }
    }
}

private fun readLegalFile(file: File): ByteArray {
    check(!Files.isSymbolicLink(file.toPath()) && file.length() in 1..MAX_LEGAL_BYTES.toLong()) {
        "Missing, linked, or oversized legal file: ${file.name}"
    }
    return file.inputStream().use { stream ->
        stream.readNBytes(MAX_LEGAL_BYTES + 1).also { bytes ->
            check(bytes.size in 1..MAX_LEGAL_BYTES) { "Invalid legal content size: ${file.name}" }
        }
    }
}
