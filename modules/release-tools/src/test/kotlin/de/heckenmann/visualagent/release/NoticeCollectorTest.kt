package de.heckenmann.visualagent.release

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails

/** Original JAR bytes, metadata identity, and extraction confinement regression fixtures. */
class NoticeCollectorTest {
    @TempDir
    lateinit var root: Path
    private lateinit var request: NoticeCollectionRequest

    @BeforeEach
    fun prepare() {
        val packaged = Files.createDirectories(root.resolve("package"))
        val legal = packaged.resolve("app/resources/legal/java-runtime/legal/java.base/LICENSE")
        Files.createDirectories(legal.parent)
        Files.writeString(legal, "Runtime license")
        val report =
            Files.writeString(
                root.resolve("licenses.json"),
                """{"dependencies":[{
            "moduleName":"example:library","moduleVersion":"1","moduleUrls":["https://example.org"],
            "moduleLicenses":[{"moduleLicense":"Example License","moduleLicenseUrl":"https://example.org/LICENSE"}]}]}""",
            )
        request =
            NoticeCollectionRequest(
                packaged,
                root.resolve("inventory.csv"),
                root.resolve("output"),
                report,
                Files.writeString(root.resolve("LICENSE"), "Project license"),
            )
    }

    @Test
    fun `original bytes metadata and runtime notices are preserved`() {
        val content = "Original copyright\r\nLicense terms\r\n".toByteArray()
        archive("META-INF/LICENSE.txt", content)
        val report = NoticeCollector.collect(request)
        val component =
            report
                .getValue("components")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("retained", component.text("noticeStatus"))
        assertEquals("unreviewed", component.text("reviewStatus"))
        val notice =
            component
                .getValue("notices")
                .jsonArray
                .single()
                .jsonObject
        assertContentEquals(content, Files.readAllBytes(request.output.resolve(notice.text("path"))))
        assertEquals(noticeSha256(request.licenseReport), report.text("licenseReportSha256"))
        assertEquals(
            1,
            report
                .getValue("runtimeLegal")
                .jsonObject
                .getValue("files")
                .jsonArray.size,
        )
    }

    @Test
    fun `unsafe and linked archive notices fail`() {
        for ((index, name) in listOf("../LICENSE", "/LICENSE", "C:/LICENSE", "dir\\LICENSE", "LICENSE").withIndex()) {
            archive(name, "license".toByteArray(), symlink = name == "LICENSE")
            assertFails("Archive path: $name") { NoticeCollector.collect(request.copy(output = root.resolve("bad-$index"))) }
        }
    }

    @Test
    fun `empty notices and mismatched inventory fail`() {
        archive("LICENSE", byteArrayOf())
        assertFails { NoticeCollector.collect(request) }
        archive("LICENSE", "license".toByteArray())
        Files.writeString(request.packageRoot.resolve("library.jar"), "changed")
        assertFails { NoticeCollector.collect(request.copy(output = root.resolve("changed"))) }
    }

    @Test
    fun `exact license version and resolved artifact hash are required`() {
        archive("LICENSE", "license".toByteArray())
        val original = Files.readString(request.inventory)
        Files.writeString(request.inventory, original.replace("example:library:1", "example:library:2"))
        assertFails { NoticeCollector.collect(request) }
        Files.writeString(request.inventory, original.replace(Regex(",[0-9a-f]{64}\\n$"), ",invalid\n"))
        assertFails { NoticeCollector.collect(request.copy(output = root.resolve("invalid-hash"))) }
    }

    private fun archive(
        name: String,
        content: ByteArray,
        symlink: Boolean = false,
    ) {
        val path = request.packageRoot.resolve("library.jar")
        if (!symlink) {
            ZipOutputStream(Files.newOutputStream(path)).use { zip ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        } else {
            ZipArchiveOutputStream(path).use { zip ->
                val entry = ZipArchiveEntry(name)
                if (symlink) entry.unixMode = 0xA1FF
                zip.putArchiveEntry(entry)
                zip.write(content)
                zip.closeArchiveEntry()
            }
        }
        val hash = noticeSha256(path)
        Files.writeString(
            request.inventory,
            "packagePath,coordinate,packageSha256,resolvedArtifactSha256\n" +
                "library.jar,example:library:1,$hash,$hash\n",
        )
    }
}
