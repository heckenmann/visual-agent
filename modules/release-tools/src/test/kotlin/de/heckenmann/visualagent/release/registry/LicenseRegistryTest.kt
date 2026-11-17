package de.heckenmann.visualagent.release.registry

import de.heckenmann.visualagent.release.text
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Retains provider/native license qualifications and the reviewed embedded-font boundary. */
class LicenseRegistryTest {
    @Test
    fun `current Graal catalog version has hash bound UPL ICU and source notices`() {
        val fixture = RegistryFixture()
        val catalog = Files.readString(fixture.root.resolve("gradle/libs.versions.toml"))
        val version = Regex("^graaljs\\s*=\\s*\"([^\"]+)\"\\s*$", RegexOption.MULTILINE).find(catalog)!!.groupValues[1]
        for (module in listOf(
            "org.graalvm.js:js-language",
            "org.graalvm.polyglot:polyglot",
            "org.graalvm.regex:regex",
            "org.graalvm.sdk:collections",
            "org.graalvm.sdk:jniutils",
            "org.graalvm.sdk:nativeimage",
            "org.graalvm.sdk:word",
            "org.graalvm.shadowed:xz",
            "org.graalvm.truffle:truffle-api",
            "org.graalvm.truffle:truffle-compiler",
            "org.graalvm.truffle:truffle-runtime",
        )) {
            val component = fixture.components.getValue("$module:$version")
            assertEquals("UPL-1.0", component.proposedLicense.jsonPrimitive.content)
            assertTrue(
                component.materials
                    .first()
                    .content
                    .toString(Charsets.UTF_8)
                    .contains("Universal Permissive License"),
            )
        }
        assertEquals(
            "Unicode/ICU License",
            fixture.components
                .getValue("org.graalvm.shadowed:icu4j:$version")
                .proposedLicense.jsonPrimitive.content,
        )
        val language = fixture.components.getValue("org.graalvm.js:js-language:$version")
        val record = fixture.record("org.graalvm.js:js-language:$version")
        assertEquals("unreviewed", record.text("reviewStatus"))
        for (text in listOf(
            "attributing MIT to the JONI regexp engine",
            "fresh inspection of the exact Maven binary and source JARs found no JONI",
            "removed in version 20.1.0",
        )) {
            assertTrue(record.text("reviewNotes").contains(text))
        }
        assertTrue(
            record.getValue("remaining").jsonArray.any {
                it.jsonPrimitive.content.contains("competent reviewer determine whether the remaining MIT POM declaration is stale")
            },
        )
        assertTrue(
            language.materials
                .first()
                .content
                .toString(Charsets.UTF_8)
                .contains("Copyright (c) 2010, 2022, Oracle"),
        )
        val thirdParty =
            language.materials
                .single {
                    it.metadata.text("path").endsWith("THIRD_PARTY_LICENSE_GRAALJS.txt")
                }.content
                .toString(Charsets.UTF_8)
        for (notice in listOf("Lucent Technologies", "V8 project authors", "Mozilla Public")) assertTrue(thirdParty.contains(notice))
        val source =
            language.materials
                .single {
                    it.metadata
                        .text(
                            "path",
                        ).endsWith("SOURCE-AVAILABILITY.txt")
                }.content
                .toString(Charsets.UTF_8)
        assertTrue(source.contains("js-language-$version-sources.jar"))
        assertFalse(Files.readString(fixture.root.resolve("modules/tool-javascript/build.gradle.kts")).contains("js-isolate-"))
    }

    @Test
    fun `Skiko original notice does not clear its native dependencies`() {
        val component = RegistryFixture().components.getValue("org.jetbrains.skiko:skiko-awt:0.150.2")
        val notice = component.materials.single { it.metadata.text("path") == "skiko-0.150.2/NOTICE" }
        assertTrue(notice.content.toString(Charsets.UTF_8).contains("The Android Open Source Project"))
        assertTrue(notice.metadata.text("url").contains(component.upstreamCommit))
        for (library in listOf("skia_ganesh_ext", "skunicode_icu", "harfbuzz", "webp_sse41", "piex", "dng_sdk", "d3d12allocator")) {
            assertTrue(component.remaining.toString().contains(library))
        }
    }

    @Test
    fun `JNA retains libffi provenance without granting native clearance`() {
        val fixture = RegistryFixture()
        val component = fixture.components.getValue("net.java.dev.jna:jna:5.19.1")
        val material = component.materials.single { it.metadata.text("path") == "jna-5.19.1/libffi-LICENSE" }
        assertEquals("2c9c2acb9743e6b007b91350475308aee44691d96aa20eacef8e199988c8c388", material.metadata.text("sha256"))
        assertTrue(material.metadata.text("url").contains(component.upstreamCommit + "/native/libffi/LICENSE"))
        assertTrue(material.content.toString(Charsets.UTF_8).startsWith("libffi - Copyright"))
        assertEquals(2, component.remaining.jsonArray.size)
        assertTrue(
            component.remaining.jsonArray[0]
                .jsonPrimitive.content
                .contains("section 4(b)"),
        )
        assertTrue(
            component.remaining.jsonArray[1]
                .jsonPrimitive.content
                .contains("source-file attribution"),
        )
        assertTrue(
            fixture
                .record(
                    "net.java.dev.jna:jna:5.19.1",
                ).text("reviewNotes")
                .contains("do not require a corresponding-source or relink archive"),
        )
    }

    @Test
    fun `PDFBox embedded font review does not approve the release package`() {
        val fixture = RegistryFixture()
        val font =
            fixture.data.getValue("embeddedAssets").jsonArray.map { it.jsonObject }.single {
                it.text("name") ==
                    "LiberationSans-Regular.ttf"
            }
        assertEquals("org.apache.pdfbox:pdfbox:3.0.8", font.text("containerCoordinate"))
        assertEquals("76d04c18ea243f426b7de1f3ad208e927008f961dc5945e5aad352d0dfde8ee8", font.text("assetSha256"))
        assertEquals("OFL-1.1", font.text("proposedLicense"))
        assertEquals("reviewed", font.text("reviewStatus"))
        assertTrue(font.getValue("remaining").jsonArray.isEmpty())
        for (text in listOf(
            "permits bundling the font with software",
            "prohibits selling the font by itself",
            "does not approve the complete release package",
        )) {
            assertTrue(font.text("reviewNotes").contains(text))
        }
        assertEquals(103, fixture.components.size)
        assertEquals("liberation-fonts-2.1.5/LICENSE", font.text("licenseMaterialPath"))
        assertTrue(
            Files.readString(fixture.path.parent.resolve(font.text("licenseMaterialPath"))).contains("SIL OPEN FONT LICENSE Version 1.1"),
        )
        assertEquals("false", fixture.data.getValue("redistributionApproved").toString())
    }

    @Test
    fun `license report keeps all declared alternatives`() {
        val fixture = RegistryFixture()
        assertTrue(
            Files.readString(fixture.root.resolve("build.gradle.kts")).contains("ExtendedJsonReportRenderer(\"licenses-all.json\", false)"),
        )
        assertTrue(
            Files
                .readString(
                    fixture.root.resolve(".github/workflows/license-inventory.yml"),
                ).contains("build/reports/dependency-license/licenses-all.json"),
        )
    }
}
