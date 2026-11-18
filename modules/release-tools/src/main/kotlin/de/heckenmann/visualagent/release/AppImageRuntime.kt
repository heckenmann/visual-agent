package de.heckenmann.visualagent.release

import java.nio.file.Files
import java.nio.file.Path

/** Validates source-built AppImage runtime provenance while allowing the generated MD5 field. */
object AppImageRuntime {
    private val sectionPattern =
        Regex(
            """^\s*\[\s*\d+]\s+\.digest_md5\s+\S+\s+[0-9a-fA-F]+\s+([0-9a-fA-F]+)\s+([0-9a-fA-F]+)\b""",
            RegexOption.MULTILINE,
        )

    /** Reads the sole 16-byte digest section's file offset from wide readelf section output. */
    fun parseDigestSection(output: String): Pair<Int, Int> {
        val matches = sectionPattern.findAll(output).toList()
        require(matches.size == 1) { "Expected one .digest_md5 section, found ${matches.size}." }
        val groups = matches.single().groupValues
        val offset = groups[1].toInt(16)
        val size = groups[2].toInt(16)
        require(size == 16) { "Unexpected .digest_md5 section size: $size." }
        return offset to size
    }

    /** Rejects changed runtime bytes, section layout, placeholder values, or Type 2 markers. */
    fun verifyPrefix(
        sourceRuntime: Path,
        appImage: Path,
        sourceSection: Pair<Int, Int>,
        embeddedSection: Pair<Int, Int>,
    ) {
        val source = Files.readAllBytes(sourceRuntime)
        val (offset, size) = sourceSection
        require(sourceSection == embeddedSection) { "The source runtime and AppImage MD5 sections do not match." }
        require(offset >= 0 && size == 16 && offset.toLong() + size <= source.size) {
            "The source runtime MD5 section extends beyond the runtime."
        }
        val end = offset + size
        require((offset until end).all { source[it] == 0.toByte() }) {
            "The source runtime MD5 section is not an empty placeholder."
        }
        val prefix = Files.newInputStream(appImage).use { it.readNBytes(source.size) }
        require(prefix.size == source.size) { "The AppImage is shorter than its source runtime." }
        require((offset until end).any { prefix[it] != source[it] }) { "The AppImage runtime MD5 section was not generated." }
        require(source.indices.all { it in offset until end || source[it] == prefix[it] }) {
            "The AppImage runtime differs outside appimagetool's MD5 section."
        }
        require(prefix.size >= 11 && prefix[8] == 65.toByte() && prefix[9] == 73.toByte() && prefix[10] == 2.toByte()) {
            "The embedded runtime does not have the AppImage Type 2 marker."
        }
    }
}
