package de.heckenmann.visualagent.release.packaging

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.CompressorStreamFactory
import java.nio.file.Files
import java.nio.file.Path

/** Inspects gzip/XZ source archives through Commons Compress without extracting host files. */
internal fun sourceTarEntries(
    path: Path,
    retain: (String) -> Boolean = { false },
): Map<String, String> {
    val result = mutableMapOf<String, String>()
    Files.newInputStream(path).buffered().use { input ->
        CompressorStreamFactory().createCompressorInputStream(input).use { compressed ->
            TarArchiveInputStream(compressed).use { archive ->
                while (true) {
                    val entry = archive.nextEntry ?: break
                    result[entry.name] =
                        if (entry.isFile && retain(entry.name)) {
                            require(entry.size <= 8 * 1024 * 1024) { "Unexpectedly large source fixture text" }
                            archive.readNBytes(8 * 1024 * 1024 + 1).toString(Charsets.UTF_8)
                        } else {
                            ""
                        }
                }
            }
        }
    }
    return result
}
