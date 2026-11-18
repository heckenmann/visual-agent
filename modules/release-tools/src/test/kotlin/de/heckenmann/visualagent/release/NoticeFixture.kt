package de.heckenmann.visualagent.release

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import java.nio.file.Files
import java.nio.file.Path

/** Creates independent package/inventory fixtures for migrated collector contracts. */
internal class NoticeFixture(
    parent: Path,
) {
    val root: Path = Files.createTempDirectory(parent, "notice-")
    val packaged: Path = Files.createDirectory(root.resolve("package"))
    val archive: Path = packaged.resolve("library.jar")
    val runtime: Path = packaged.resolve("Visual Agent.app/Contents/app/resources/legal/java-runtime")
    val request: NoticeCollectionRequest

    init {
        val legal = runtime.resolve("legal/java.base/LICENSE")
        Files.createDirectories(legal.parent)
        Files.writeString(legal, "Zulu runtime license")
        Files.createDirectories(runtime.resolve("vendor"))
        Files.writeString(runtime.resolve("vendor/readme.txt"), "Zulu source offer information")
        val report =
            Files.writeString(
                root.resolve("licenses.json"),
                """{"dependencies":[{"moduleName":"example:library",
            "moduleVersion":"1","moduleUrls":[],"moduleLicenses":[{"moduleLicense":"MIT","moduleLicenseUrl":null}]}]}""",
            )
        request =
            NoticeCollectionRequest(
                packaged,
                root.resolve("inventory.csv"),
                root.resolve("output"),
                report,
                Files.writeString(root.resolve("LICENSE"), "Project license"),
            )
        archive(listOf("LICENSE" to "license".toByteArray()))
    }

    fun archive(
        entries: List<Pair<String, ByteArray>>,
        coordinate: String = "example:library:1",
    ) {
        ZipArchiveOutputStream(archive).use { jar ->
            entries.forEach { (name, bytes) ->
                jar.putArchiveEntry(ZipArchiveEntry(name))
                jar.write(bytes)
                jar.closeArchiveEntry()
            }
        }
        val hash = noticeSha256(archive)
        Files.writeString(
            request.inventory,
            "packagePath,coordinate,packageSha256,resolvedArtifactSha256\n" +
                "library.jar,$coordinate,$hash,$hash\n",
        )
    }

    fun collect() = NoticeCollector.collect(request)
}
