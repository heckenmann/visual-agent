package de.heckenmann.visualagent.release

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Complete non-AppImage notice/inventory failure contracts migrated from the legacy collector suite. */
class NoticeCollectorContractTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `runtime original license and vendor offer retain hashes without absolute paths`() {
        val fixture = NoticeFixture(root)
        val report = fixture.collect()
        val runtime = report.getValue("runtimeLegal").jsonObject
        assertEquals("unreviewed", runtime.text("reviewStatus"))
        assertEquals("Visual Agent.app/Contents/app/resources/legal/java-runtime", runtime.text("packagePath"))
        assertContentEquals(
            "Zulu source offer information".toByteArray(),
            Files.readAllBytes(fixture.request.output.resolve("runtime-legal/vendor/readme.txt")),
        )
        val license =
            runtime
                .getValue(
                    "files",
                ).jsonArray
                .map { it.jsonObject }
                .single { it.text("packagePath").endsWith("java.base/LICENSE") }
        assertEquals(noticeSha256(fixture.runtime.resolve("legal/java.base/LICENSE")), license.text("sha256"))
        assertFalse(Files.readString(fixture.request.output.resolve("manifest.json")).contains(fixture.root.toString()))
        assertEquals("false", report.getValue("redistributionApproved").toString())
    }

    @Test
    fun `runtime legal directories are found in all native layouts`() {
        for (layout in listOf("Visual Agent.app/Contents/app", "Visual Agent/app", "Visual Agent/lib/app")) {
            val packaged = Files.createTempDirectory(root, "layout-")
            val legal = packaged.resolve("$layout/resources/legal/java-runtime/vendor/readme.txt")
            Files.createDirectories(legal.parent)
            Files.writeString(legal, "vendor offer")
            val output = Files.createTempDirectory(root, "out-")
            val runtime = retainRuntimeNotices(packaged.toRealPath(), output, longArrayOf(0))
            assertEquals("$layout/resources/legal/java-runtime", runtime.text("packagePath"))
            assertEquals("vendor offer", Files.readString(output.resolve("runtime-legal/vendor/readme.txt")))
        }
    }

    @Test
    fun `missing runtime or license metadata never produces a success manifest`() {
        for (failure in listOf("runtime", "project", "metadata", "inventory")) {
            val fixture = NoticeFixture(root)
            when (failure) {
                "runtime" -> {
                    val directory = fixture.root.resolve("without-runtime")
                    Files.createDirectory(directory)
                    Files.copy(fixture.archive, directory.resolve("library.jar"))
                    assertFails { NoticeCollector.collect(fixture.request.copy(packageRoot = directory)) }
                    assertFalse(Files.exists(fixture.request.output.resolve("manifest.json")))
                    continue
                }
                "project" -> Files.delete(fixture.request.projectLicense)
                "metadata" -> Files.writeString(fixture.request.licenseReport, "{\"dependencies\":[]}")
                "inventory" -> Files.writeString(fixture.request.inventory, "packagePath,coordinate,packageSha256\n")
            }
            assertFails(failure) { fixture.collect() }
            assertFalse(Files.exists(fixture.request.output.resolve("manifest.json")))
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS, disabledReason = "Creating symbolic links requires developer privileges")
    fun `linked runtime files are rejected`() {
        val fixture = NoticeFixture(root)
        val outside = Files.writeString(root.resolve("outside"), "external")
        Files.createSymbolicLink(fixture.runtime.resolve("linked.txt"), outside)
        assertFails { fixture.collect() }
    }

    @Test
    fun `classes and missing licenses remain explicit rather than implying clearance`() {
        val fixture = NoticeFixture(root)
        fixture.archive(listOf("internal/license/AuthMethod.class" to byteArrayOf(1), "LICENSE.class" to byteArrayOf(2)))
        val component =
            fixture
                .collect()
                .getValue("components")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("no-detected-notice", component.text("noticeStatus"))
        assertTrue(component.getValue("notices").jsonArray.isEmpty())
    }

    @Test
    fun `oversized entries aggregate limits duplicates and reused output fail`() {
        val oversized = NoticeFixture(root)
        oversized.archive(listOf("LICENSE" to ByteArray(MAX_NOTICE_BYTES + 1)))
        assertFails { oversized.collect() }
        val aggregate = NoticeFixture(root)
        aggregate.archive(listOf("LICENSE" to "license".toByteArray()))
        val output = Files.createDirectory(aggregate.root.resolve("aggregate"))
        assertFails {
            retainJarNotices(
                aggregate.archive,
                output,
                noticeSha256(aggregate.archive),
                longArrayOf(MAX_NOTICE_TOTAL_BYTES.toLong()),
            )
        }
        val duplicate = NoticeFixture(root)
        duplicate.archive(listOf("LICENSE" to byteArrayOf(1), "LICENSE" to byteArrayOf(1)))
        assertFails { duplicate.collect() }
        val repeated = NoticeFixture(root)
        repeated.collect()
        assertFails { repeated.collect() }
    }

    @Test
    fun `unsafe artifact paths unresolved identities and duplicate inventory rows fail`() {
        val fixture = NoticeFixture(root)
        for (path in listOf("../library.jar", "/library.jar", "C:/library.jar", "folder\\library.jar")) {
            assertFails { confinedNoticeFile(fixture.packaged, path) }
        }
        for (coordinate in listOf("UNMATCHED", "AMBIGUOUS", "")) {
            val unresolved = NoticeFixture(root)
            unresolved.archive(listOf("LICENSE" to byteArrayOf(1)), coordinate)
            assertFails { unresolved.collect() }
            assertFalse(Files.exists(unresolved.request.output.resolve("manifest.json")))
        }
        val rows = Files.readAllLines(fixture.request.inventory)
        Files.writeString(fixture.request.inventory, (rows + rows.drop(1)).joinToString("\n", postfix = "\n"))
        assertFails { fixture.collect() }
    }

    @Test
    fun `project artifacts and bundled JDK retain distinct evidence ownership`() {
        val fixture = NoticeFixture(root)
        fixture.archive(listOf("LICENSE" to byteArrayOf(1)), "project ':application'")
        val report = fixture.collect()
        assertTrue(report.getValue("components").jsonArray.isEmpty())
        val project =
            report
                .getValue("projectArtifacts")
                .jsonArray
                .single()
                .jsonObject
        val evidence = project.getValue("licenseEvidence").jsonObject
        assertEquals("project ':application'", project.text("coordinate"))
        assertEquals("repository-license-file", evidence.text("source"))
        assertEquals(report.text("projectLicenseSha256"), evidence.text("sha256"))
        val runtime = NoticeFixture(root)
        runtime.archive(listOf("LICENSE" to byteArrayOf(1)), "bundled-jdk:27:jrt-fs")
        val component =
            runtime
                .collect()
                .getValue("components")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("see-runtimeLegal", component.getValue("licenseMetadata").jsonObject.text("status"))
    }

    @Test
    fun `missing license declarations and null names preserve unreviewed metadata`() {
        val fixture = NoticeFixture(root)
        Files.writeString(
            fixture.request.licenseReport,
            """{"dependencies":[{"moduleName":"example:library",
            "moduleVersion":"1","moduleUrls":[],"moduleLicenses":[]}]}""",
        )
        val metadata =
            fixture
                .collect()
                .getValue("components")
                .jsonArray
                .single()
                .jsonObject
                .getValue("licenseMetadata")
                .jsonObject
        assertEquals("no-license-declaration", metadata.text("status"))
        assertEquals("unreviewed", metadata.text("reviewStatus"))
        val nullable = NoticeFixture(root)
        Files.writeString(
            nullable.request.licenseReport,
            """{"dependencies":[{"moduleName":"example:library","moduleVersion":"1",
            "moduleUrls":[],"moduleLicenses":[{"moduleLicense":null,"moduleLicenseUrl":"https://example.org/license"}]}]}""",
        )
        val licenses =
            nullable
                .collect()
                .getValue("components")
                .jsonArray
                .single()
                .jsonObject
                .getValue("licenseMetadata")
                .jsonObject
                .getValue("declaredLicenses")
                .jsonArray
                .single()
                .jsonObject
        assertEquals(JsonNull, licenses["name"])
        assertEquals("https://example.org/license", licenses.text("url"))
    }
}
