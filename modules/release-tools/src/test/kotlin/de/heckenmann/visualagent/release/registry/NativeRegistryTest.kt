package de.heckenmann.visualagent.release.registry

import de.heckenmann.visualagent.release.noticeSha256
import de.heckenmann.visualagent.release.text
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Preserves native dependency attribution and the boundaries of historical binary evidence. */
class NativeRegistryTest {
    @Test
    fun `Skia dependency notices and required credits retain exact upstream provenance`() {
        val fixture = RegistryFixture()
        val expected =
            mutableMapOf(
                "expat-6154446f/COPYING" to "6154446fccefbf3ca644894f598969113b0c7bcd",
                "harfbuzz-9cb1fee5/COPYING" to "9cb1fee51069b206effb4736e443b038d230789d",
                "d3d12allocator-169895d5/LICENSE.txt" to "169895d529dfce00390a20e69c2f516066fe7a3b",
                "libwebp-845d5476/COPYING" to "845d5476a866141ba35ac133f856fa62f0b7445f",
                "libwebp-845d5476/PATENTS" to "845d5476a866141ba35ac133f856fa62f0b7445f",
                "libpng-d5515b5b/LICENSE" to "d5515b5b8be3901aac04e5bd8bd5c89f287bcd33",
                "libjpeg-turbo-e14cbfaa/LICENSE.md" to "e14cbfaa85529d47f9f55b0f104a579c1061f9ad",
                "libjpeg-turbo-e14cbfaa/README.ijg" to "e14cbfaa85529d47f9f55b0f104a579c1061f9ad",
                "zlib-646b7f56/LICENSE" to "646b7f569718921d7d4b5b8e22572ff6c76f2596",
                "icu-364118a1/LICENSE" to "364118a1d9da24bb5b770ac3d762ac144d6da5a4",
            )
        for (name in listOf("LICENSE.TXT", "FTL.TXT", "bdf-README", "pcf-README")) {
            expected["freetype-264b5fbf/$name"] = "264b5fbf5b912b39f98d038bf75d39be0a73f21b"
        }
        for (platform in listOf("linux-x64", "macos-arm64", "macos-x64", "windows-x64")) {
            val materials =
                fixture.components
                    .getValue("org.jetbrains.skiko:skiko-awt-runtime-$platform:0.150.2")
                    .materials
                    .associateBy { it.metadata.text("path") }
            expected.forEach { (path, commit) ->
                assertTrue(
                    materials
                        .getValue(path)
                        .metadata
                        .text("url")
                        .contains(commit),
                    path,
                )
            }
            val credits = materials.getValue("skia-m150-a3f509bf52/ATTRIBUTIONS.txt").content.toString(Charsets.UTF_8)
            for (text in listOf(
                "This software is based in part on the work of the Independent JPEG Group.",
                "1996-2026 The FreeType Project",
                "not the alternative GPL option",
            )) {
                assertTrue(credits.contains(text))
            }
        }
    }

    @Test
    fun `ANGLE dependencies preserve distinct upstream licenses`() {
        val materials =
            RegistryFixture()
                .components
                .getValue("org.jetbrains.skiko:skiko-awt-runtime-angle-windows-x64:0.150.2")
                .materials
                .associateBy { it.metadata.text("path") }
        val expected =
            mapOf(
                "angle-ec4d8f8e4d/xxhash-LICENSE" to "ec4d8f8e4d3f1a9e64430abd8b6b436253732adf",
                "abseil-24012838/LICENSE" to "2401283880a451e9ea10787920117fb67695abb4",
                "zlib-e8ff3f35/LICENSE" to "e8ff3f35551275330696606d7fef3a2a0f73a831",
                "chromium-055e258e/LICENSE" to "055e258e91fc310ded1c0071f108c08f0e924a53",
            )
        expected.forEach { (path, commit) ->
            assertTrue(
                materials
                    .getValue(path)
                    .metadata
                    .text("url")
                    .contains(commit),
            )
            assertTrue(materials.getValue(path).content.isNotEmpty())
        }
        assertFalse(
            materials.getValue("chromium-055e258e/LICENSE").content.contentEquals(materials.getValue("zlib-e8ff3f35/LICENSE").content),
        )
    }

    @Test
    fun `embedded components remain pinned unreviewed and hash bound`() {
        val fixture = RegistryFixture()
        val components =
            fixture.data
                .getValue("embeddedComponents")
                .jsonArray
                .map { it.jsonObject }
                .associateBy { it.text("name") }
        val expected =
            mapOf(
                "dng_sdk" to "dbe0a676450d9b8c71bf00688bb306409b779e90",
                "piex" to "bb217acdca1cc0c16b704669dd6f91a1b509c406",
                "wuffs" to "e3f919ccfe3ef542cfc983a82146070258fb57f8",
                "d3d12allocator" to "169895d529dfce00390a20e69c2f516066fe7a3b",
            )
        assertEquals(expected.keys, components.keys)
        expected.forEach { (name, commit) ->
            val component = components.getValue(name)
            assertEquals(commit, component.text("upstreamCommit"))
            assertEquals("unreviewed", component.text("reviewStatus"))
            assertTrue(component.getValue("remaining").jsonArray.isNotEmpty())
            component.getValue("materialPaths").jsonArray.forEach { value ->
                val path = value.jsonPrimitive.content
                assertEquals(fixture.material(path).text("sha256"), noticeSha256(fixture.path.parent.resolve(path)))
            }
        }
        val dng = components.getValue("dng_sdk")
        val dngBinary = dng.getValue("binaryEvidence").jsonObject
        for (text in listOf(
            "Absent from the pinned Skiko 0.150.2 Windows JVM runtime",
            "skia_use_piex = !is_win",
            "eebad490c2828198e86e641cbd44958617867ce70f893b21852f07d0be099c0a",
            "not the stripped-binary string scan",
        )) {
            assertTrue(dngBinary.text("windows-x64").contains(text))
        }
        assertTrue(dngBinary.text("linux-x64").contains("found"))
        val piex =
            components
                .getValue("piex")
                .getValue("binaryEvidence")
                .jsonObject
                .text("windows-x64")
        assertTrue(piex.contains("Absent from the pinned Skiko 0.150.2 Windows JVM runtime"))
        assertTrue(piex.contains("Skia disables Piex on Windows"))
        val allocator = components.getValue("d3d12allocator")
        assertEquals("MIT", allocator.text("proposedLicense"))
        val binary = allocator.getValue("binaryEvidence").jsonObject.text("windows-x64")
        assertTrue(binary.contains("staticSkiaLibs(\"d3d12allocator\")"))
        assertTrue(binary.contains("e90e2e5ae3d48c7694b6cf5948953f6d007fe40478b1040353040acef118dd41"))
        assertFalse(
            fixture
                .record("org.jetbrains.skiko:skiko-awt-runtime-angle-windows-x64:0.150.2")
                .getValue("materialPaths")
                .jsonArray
                .any { it.jsonPrimitive.content == "d3d12allocator-169895d5/LICENSE.txt" },
        )
        val noticePath = "dng-sdk-dbe0a676/DNG-PATENT-NOTICE.txt"
        assertTrue(dng.getValue("materialPaths").jsonArray.any { it.jsonPrimitive.content == noticePath })
        val notice = Files.readString(fixture.path.parent.resolve(noticePath))
        assertEquals("This product includes DNG technology under license by Adobe.\n", notice)
        assertEquals(fixture.material(noticePath).text("sha256"), noticeSha256(fixture.path.parent.resolve(noticePath)))
        for (text in listOf(
            "BY_EXCEPTION_ONLY",
            "Google's internal classification",
            "not an additional Adobe license term",
            "/dbe0a676450d9b8c71bf00688bb306409b779e90/METADATA",
            "No third party was contacted.",
        )) {
            assertTrue(dng.text("reviewNotes").contains(text))
        }
        assertFalse(dng.getValue("remaining").jsonArray.any { it.jsonPrimitive.content.contains("BY_EXCEPTION_ONLY") })
        for (path in listOf(
            "README.md",
            "modules/desktop/build.gradle.kts",
            "modules/ui/src/main/kotlin/de/heckenmann/visualagent/ui/canvas/ComposeCanvasFigureViews.kt",
        )) {
            assertTrue(Files.readString(fixture.root.resolve(path)).contains(notice.trim()))
        }
        val historical =
            fixture.data
                .getValue("packageEvidence")
                .jsonArray
                .first()
                .jsonObject
        assertEquals("42388152203", historical.getValue("workflowRun").jsonPrimitive.content)
        assertEquals("historical-smoke-artifact-not-final-release-candidate", historical.text("status"))
        assertEquals("8596fdd05feffc299febae7ddf8833b85c725fdae66b81d6a03d88e9a91f717a", historical.text("sha256"))
        assertTrue(historical.getValue("remaining").jsonArray.any { it.jsonPrimitive.content.contains("DNG SDK, Piex, Wuffs, or libffi") })
        assertEquals("false", fixture.data.getValue("redistributionApproved").toString())
    }
}
