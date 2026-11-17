package de.heckenmann.visualagent.release.packaging

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import java.nio.file.Files
import java.nio.file.Path

/** Synthetic native and executable-JAR images consumed by the actual Gradle legal verifiers. */
internal class BundledLegalFixture(
    parent: Path,
) {
    val root: Path = Files.createTempDirectory(parent, "legal-")
    val upstream: Path = root.resolve("docs/licensing/third-party")
    val entries = mutableListOf<Pair<String, ByteArray>>()

    init {
        write("LICENSE", "Synthetic project MIT\n")
        write("docs/licensing/third-party/sources.json", "{\"redistributionApproved\":false}\n")
        write("docs/licensing/third-party/LICENSE.txt", "Original upstream terms\r\n")
        for (name in listOf("sources.json", "LICENSE.txt")) {
            val bytes = Files.readAllBytes(upstream.resolve(name))
            val target = root.resolve("native/legal/third-party/$name")
            Files.createDirectories(target.parent)
            Files.write(target, bytes)
            entries.add("META-INF/visual-agent/legal/third-party/$name" to bytes)
        }
        val original = Files.readAllBytes(root.resolve("LICENSE"))
        val license = root.resolve("native/legal/visual-agent/LICENSE")
        Files.createDirectories(license.parent)
        Files.write(license, original)
        entries.add("META-INF/visual-agent/legal/visual-agent/LICENSE" to original)
        jar()
        write("settings.gradle.kts", "rootProject.name = \"bundled-legal-fixture\"\n")
        val repository = Path.of(System.getProperty("release.tools.repository"))
        val logic = repository.resolve("buildSrc/build/libs/buildSrc.jar").toString().replace('\\', '/')
        write(
            "build.gradle.kts",
            """
            import de.heckenmann.visualagent.gradle.verifyNativeProjectLegalMaterials
            import de.heckenmann.visualagent.gradle.verifyJarProjectLegalMaterials
            import java.util.jar.JarFile
            buildscript { dependencies { classpath(files("$logic")) } }
            tasks.register("verifyNative") { doLast { verifyNativeProjectLegalMaterials(file("native"), projectDir) } }
            tasks.register("verifyJar") { doLast { JarFile(file("app.jar")).use { verifyJarProjectLegalMaterials(it, projectDir) } } }
            """.trimIndent(),
        )
    }

    fun jar() {
        ZipArchiveOutputStream(root.resolve("app.jar")).use { archive ->
            entries.forEach { (name, content) ->
                archive.putArchiveEntry(ZipArchiveEntry(name))
                archive.write(content)
                archive.closeArchiveEntry()
            }
        }
    }

    private fun write(
        name: String,
        text: String,
    ) {
        val path = root.resolve(name)
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
    }
}
