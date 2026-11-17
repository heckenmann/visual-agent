package de.heckenmann.visualagent.release

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** End-to-end retention of supplemental legal evidence for exact packaged artifact identities. */
class SupplementCollectorTest {
    @TempDir
    lateinit var root: Path
    private val repository: Path = Path.of(System.getProperty("release.tools.repository"))

    @Test
    fun `pinned ICU UPL gRPC Netty and Apple materials survive actual collection`() {
        val registry = repository.resolve("docs/licensing/third-party/sources.json")
        val supplements = SupplementalEvidence.load(registry)
        val cases =
            mapOf(
                "org.graalvm.shadowed:icu4j:25.4.4.1.1" to "icu4j-78.2/LICENSE",
                "org.graalvm.polyglot:polyglot:25.4.4.1.1" to "graalvm-25.4.4.1.1/UPL-1.0.txt",
                "io.grpc:grpc-api:1.85.1" to "grpc-java-1.85.1/NOTICE.txt",
                "io.netty:netty-buffer:4.2.20.Final" to "netty-4.2.20.Final/NOTICE.txt",
                "io.netty:netty-resolver-dns-native-macos:4.2.20.Final" to "netty-4.2.20.Final/LICENSE.dnsinfo.txt",
            )
        cases.forEach { (coordinate, expectedPath) ->
            val fixture = NoticeFixture(root)
            fixture.archive(listOf("library.class" to "synthetic bytecode".toByteArray()), coordinate)
            val source = supplements.getValue(coordinate)
            val hash = noticeSha256(fixture.archive)
            Files.writeString(
                fixture.request.inventory,
                "packagePath,coordinate,packageSha256,resolvedArtifactSha256\n" +
                    "library.jar,$coordinate,$hash,${source.sourceArtifactSha256}\n",
            )
            val metadata =
                buildJsonObject {
                    put(
                        "dependencies",
                        JsonArray(
                            listOf(
                                buildJsonObject {
                                    put("moduleName", coordinate.substringBeforeLast(':'))
                                    put("moduleVersion", coordinate.substringAfterLast(':'))
                                    put("moduleUrls", JsonArray(emptyList()))
                                    put("moduleLicenses", JsonArray(emptyList()))
                                },
                            ),
                        ),
                    )
                }
            Files.writeString(fixture.request.licenseReport, metadata.toString())
            val report = NoticeCollector.collect(fixture.request.copy(supplements = registry))
            val component =
                report
                    .getValue("components")
                    .jsonArray
                    .single()
                    .jsonObject
            assertEquals("no-detected-notice", component.text("noticeStatus"))
            assertEquals("unreviewed", component.text("reviewStatus"))
            assertEquals("false", report.getValue("redistributionApproved").toString())
            val evidence = component.getValue("supplement").jsonObject
            assertTrue(evidence.getValue("remaining").jsonArray.isNotEmpty())
            assertEquals(source.proposedLicense, evidence["proposedLicense"])
            val retained = evidence.getValue("materials").jsonArray.map { it.jsonObject }
            assertEquals(source.materials.size, retained.size)
            assertTrue(retained.any { it.text("path").endsWith(expectedPath) }, expectedPath)
            source.materials.forEach { original ->
                val material = retained.single { it.text("path").endsWith(original.metadata.text("path")) }
                assertEquals(original.metadata.text("sha256"), material.text("sha256"))
                assertContentEquals(original.content, Files.readAllBytes(fixture.request.output.resolve(material.text("path"))))
            }
            if (coordinate.contains("native-macos")) {
                assertEquals(3, retained.size)
                assertTrue(retained.any { it.text("path").endsWith("APSL-2.0.txt") })
            }
        }
    }

    @Test
    fun `supplement retains independent source hash and cannot hide missing embedded notice`() {
        val fixture = NoticeFixture(root)
        fixture.archive(listOf("library.class" to byteArrayOf(1)))
        val material = Files.writeString(fixture.root.resolve("original-license"), "Original license\r\n")
        val registry = fixture.root.resolve("sources.json")
        val metadata =
            buildJsonObject {
                put("schemaVersion", 1)
                put("redistributionApproved", false)
                put(
                    "materials",
                    JsonArray(
                        listOf(
                            buildJsonObject {
                                put("path", "original-license")
                                put("sha256", noticeSha256(material))
                                put("url", "https://example.org/LICENSE")
                            },
                        ),
                    ),
                )
                put(
                    "components",
                    JsonArray(
                        listOf(
                            buildJsonObject {
                                put("sourceArtifacts", JsonObject(mapOf("example:library:1" to JsonPrimitive("d".repeat(64)))))
                                put("upstreamCommit", "b".repeat(40))
                                put("proposedLicense", "MIT")
                                put("reviewStatus", "unreviewed")
                                put("remaining", JsonArray(listOf(JsonPrimitive("Source review"))))
                                put("materialPaths", JsonArray(listOf(JsonPrimitive("original-license"))))
                            },
                        ),
                    ),
                )
            }
        Files.writeString(registry, metadata.toString())
        val component =
            NoticeCollector
                .collect(fixture.request.copy(supplements = registry))
                .getValue("components")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("no-detected-notice", component.text("noticeStatus"))
        val evidence = component.getValue("supplement").jsonObject
        assertEquals("d".repeat(64), evidence.text("sourceArtifactSha256"))
        assertNotEquals(component.text("resolvedArtifactSha256"), evidence.text("sourceArtifactSha256"))
        val retained =
            evidence
                .getValue("materials")
                .jsonArray
                .single()
                .jsonObject
        assertContentEquals(Files.readAllBytes(material), Files.readAllBytes(fixture.request.output.resolve(retained.text("path"))))
    }
}
