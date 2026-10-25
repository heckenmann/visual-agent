package de.heckenmann.visualagent.desktop

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Verifies the actual desktop runtime without Compose rules or test-dispatcher factories. */
class DesktopMainDispatcherTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `Gradle supplies only a classpath file reference to the test worker`() {
        assertNull(System.getProperty("visualagent.desktop.dispatcherProbeClasspath"))
        assertTrue(Files.isRegularFile(classpathFile()))
    }

    @Test
    fun `production classpath provides a functioning main dispatcher in a fresh JVM`() {
        val properties = Properties()
        Files.newBufferedReader(classpathFile(), Charsets.UTF_8).use { properties.load(it) }
        val classpath = assertNotNull(properties.getProperty("classpath"))
        val java =
            Path.of(
                System.getProperty("java.home"),
                "bin",
                if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java",
            )
        // Java argument files avoid Windows command-line limits for the full desktop classpath.
        val arguments = temporary.newFile("dispatcher.args").toPath()
        val quotedClasspath = classpath.replace("\\", "\\\\").replace("\"", "\\\"")
        Files.writeString(arguments, "-cp\n\"$quotedClasspath\"\n${DesktopDispatcherProbe::class.java.name}\n")
        val process =
            ProcessBuilder(
                java.toString(),
                "@$arguments",
            ).redirectErrorStream(true).start()
        try {
            val output = process.inputStream.bufferedReader().use { it.readText() }
            assertEquals(0, process.waitFor(), output)
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private fun classpathFile(): Path = Path.of(assertNotNull(System.getProperty("visualagent.desktop.dispatcherProbeClasspathFile")))
}
