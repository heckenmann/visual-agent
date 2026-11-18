package de.heckenmann.visualagent.release

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Source/runtime binding fixtures migrated from the Python AppImage verifier suite. */
class AppImageRuntimeTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `readelf parser requires exactly one sixteen byte section`() {
        val section = "  [ 4] .digest_md5 PROGBITS 00000000000020a0 0020a0 000010 00 WA 0 0 1\n"
        assertEquals(0x20a0 to 16, AppImageRuntime.parseDigestSection(section))
        for (invalid in listOf("no digest section", section + section, section.replace("000010", "000008"))) {
            assertFailsWith<IllegalArgumentException> { AppImageRuntime.parseDigestSection(invalid) }
        }
    }

    @Test
    fun `only generated digest may differ`() {
        val source = ByteArray(39)
        source[8] = 65
        source[9] = 73
        source[10] = 2
        val packaged = source.copyOf()
        (16 until 32).forEach { packaged[it] = (it - 15).toByte() }
        verify(source, packaged + "squashfs".toByteArray())
        assertFailsWith<IllegalArgumentException> { verify(source, source) }
        val changed = packaged.copyOf()
        changed[38] = 1
        assertFailsWith<IllegalArgumentException> { verify(source, changed) }
        assertFailsWith<IllegalArgumentException> { verify(source, packaged.copyOf(30)) }
        val missingMarker = source.copyOf()
        missingMarker[10] = 0
        val badPackaged = packaged.copyOf()
        badPackaged[10] = 0
        assertFailsWith<IllegalArgumentException> { verify(missingMarker, badPackaged) }
        val filledPlaceholder = source.copyOf()
        filledPlaceholder[16] = 1
        assertFailsWith<IllegalArgumentException> { verify(filledPlaceholder, packaged) }
    }

    @Test
    fun `changed or out of bounds section layout fails`() {
        val source = Files.write(root.resolve("runtime"), ByteArray(39))
        val image = Files.write(root.resolve("appimage"), ByteArray(50))
        assertFailsWith<IllegalArgumentException> { AppImageRuntime.verifyPrefix(source, image, 16 to 16, 17 to 16) }
        assertFailsWith<IllegalArgumentException> { AppImageRuntime.verifyPrefix(source, image, 32 to 16, 32 to 16) }
    }

    private fun verify(
        source: ByteArray,
        packaged: ByteArray,
    ) {
        val runtime = Files.write(root.resolve("runtime"), source)
        val image = Files.write(root.resolve("appimage"), packaged)
        AppImageRuntime.verifyPrefix(runtime, image, 16 to 16, 16 to 16)
    }
}
