package de.heckenmann.visualagent.release.registry

import de.heckenmann.visualagent.release.text
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Retains historical AppImage and Graal native evidence without treating it as current clearance. */
class HistoricalNativeRegistryTest {
    @Test
    fun `AppImage static components keep original licenses and build provenance`() {
        val fixture = RegistryFixture()
        val cases =
            listOf(
                Triple("mimalloc2:2.1.7-r0", "MIT", "MIT License\n"),
                Triple("zstd:1.5.6", "BSD-3-Clause", "BSD License\n"),
                Triple("zlib:1.4", "Zlib", ""),
                Triple("AppImage:type2-runtime:20251108", "MIT", ""),
                Triple("AppImage:libfuse:3.15.0", "LGPL-2.1-only", ""),
                Triple("AppImage:squashfuse:0.5.2", "BSD-2-Clause", ""),
                Triple("AppImage:musl:1.2.5", "MIT", ""),
            )
        cases.forEach { (coordinate, license, prefix) ->
            val component = fixture.components.getValue(coordinate)
            assertEquals(license, component.proposedLicense.jsonPrimitive.content)
            assertTrue(
                component.materials
                    .first()
                    .content
                    .toString(Charsets.UTF_8)
                    .startsWith(prefix),
            )
        }
        val mimalloc = fixture.components.getValue("mimalloc2:2.1.7-r0")
        assertEquals("0eed39319f139afde8515010ff59baf24de9e47ea316a315398e8027d198202d", mimalloc.sourceArtifactSha256)
        val mimallocRecord = fixture.record("mimalloc2:2.1.7-r0")
        assertTrue(mimallocRecord.text("reviewNotes").contains("links -lmimalloc"))
        assertFalse(mimallocRecord.getValue("remaining").toString().contains("original build log expired"))
        val zstd = fixture.record("zstd:1.5.6")
        for (text in listOf("zstd-static 1.5.6-r2", "BSD-3-Clause OR GPLv2")) assertTrue(zstd.text("reviewNotes").contains(text))
        assertTrue(zstd.getValue("remaining").toString().contains("final release artifacts"))
        assertEquals(
            "e32ff4e00d9d94930537635291da39e7e612703334bf6fde8c7f1686fe8a45a2",
            fixture.components
                .getValue("zlib:1.4")
                .materials
                .first()
                .metadata
                .text("sha256"),
        )
        val zlib = fixture.record("zlib:1.4")
        assertTrue(zlib.text("reviewNotes").contains("zlib-static 1.4-r0"))
        assertTrue(zlib.text("reviewNotes").contains("050415c464375727d855a14abb0cdf2d549bda3b32ddc3c0017256e552b3ec09"))
        assertFalse(zlib.getValue("remaining").toString().contains("zlib-static 1.4-r0"))
        val runtime = fixture.components.getValue("AppImage:type2-runtime:20251108")
        assertEquals("f5fec23be76e50e2445ed2d018bac49b367490fc483c62c4637e99ec705d27ba", runtime.sourceArtifactSha256)
        assertTrue(
            runtime.materials
                .first()
                .content
                .toString(Charsets.UTF_8)
                .contains("statically linked code"),
        )
        val runtimeRecord = fixture.record("AppImage:type2-runtime:20251108")
        assertTrue(runtimeRecord.text("reviewNotes").contains("-lzstd -lz -lfuse3 -lmimalloc"))
        assertEquals(1, runtimeRecord.getValue("remaining").jsonArray.size)
        val fuse = fixture.components.getValue("AppImage:libfuse:3.15.0")
        assertTrue(
            fuse.materials[1]
                .content
                .toString(Charsets.UTF_8)
                .contains("GNU LESSER GENERAL PUBLIC LICENSE"),
        )
        assertTrue(fuse.remaining.toString().contains("final AppImage"))
        assertTrue(fuse.remaining.toString().contains("source manifest"))
        assertTrue(fixture.record("AppImage:libfuse:3.15.0").text("reviewNotes").contains("technical evidence"))
        assertTrue(
            fixture.components
                .getValue("AppImage:squashfuse:0.5.2")
                .materials
                .first()
                .content
                .toString(Charsets.UTF_8)
                .contains("Redistribution and use in source and binary forms"),
        )
        val musl = fixture.components.getValue("AppImage:musl:1.2.5")
        assertTrue(
            musl.materials
                .first()
                .content
                .toString(Charsets.UTF_8)
                .contains("musl as a whole is licensed"),
        )
        val muslRecord = fixture.record("AppImage:musl:1.2.5")
        assertTrue(muslRecord.text("reviewNotes").contains("musl-dev 1.2.5-r12"))
        assertTrue(muslRecord.text("reviewNotes").contains("e63fe34178270d2d8cc74f1bab303ec1868150606e66f6da2c16221e6ec86034"))
        assertFalse(muslRecord.getValue("remaining").toString().contains("musl-dev 1.2.5-r12"))
        for (coordinate in listOf("mimalloc2:2.1.7-r0", "zlib:1.4", "AppImage:type2-runtime:20251108", "AppImage:musl:1.2.5")) {
            val record = fixture.record(coordinate)
            assertTrue(record.text("reviewNotes").contains("workflow 43305484301"))
            assertEquals("unreviewed", record.text("reviewStatus"))
        }
        assertEquals("unreviewed", zstd.text("reviewStatus"))
        assertEquals("unreviewed", fixture.record("AppImage:libfuse:3.15.0").text("reviewStatus"))
    }

    @Test
    fun `historical Graal isolate findings remain separate from active package clearance`() {
        val fixture = RegistryFixture()
        val coordinate = "org.graalvm.js:js-isolate-linux-amd64:25.4.4.1.1"
        val component = fixture.components.getValue(coordinate)
        assertEquals("GraalVM Free Terms and Conditions (GFTC)", component.proposedLicense.jsonPrimitive.content)
        assertEquals("58f1795f7d12d12260d0c0423811e846c7632649", component.upstreamCommit)
        assertEquals(
            "81c71eea20f3c272c69d9d6ba3ed392f3651562150f3a4f34401004fcb403821",
            fixture.components.getValue("org.graalvm.js:js-isolate-darwin-aarch64:25.4.4.1.1").sourceArtifactSha256,
        )
        val content =
            component.materials
                .first()
                .content
                .toString(Charsets.UTF_8)
        for (text in listOf(
            "re-signing an embedded macOS library",
            "without charging recipients",
            "public GitHub Releases",
        )) {
            assertTrue(content.contains(text))
        }
        assertTrue(component.remaining.toString().contains("six-platform final release artifacts"))
        for (removed in listOf(
            "unmodified-Program condition",
            "no-fee redistribution condition",
        )) {
            assertFalse(component.remaining.toString().contains(removed))
        }
        val record = fixture.record(coordinate)
        for (text in listOf(
            "workflow 43393143407",
            "current PR-head inventory",
            "not an active component finding",
        )) {
            assertTrue(record.text("reviewNotes").contains(text))
        }
        assertEquals("unreviewed", record.text("reviewStatus"))
        assertEquals("false", fixture.data.getValue("redistributionApproved").toString())
    }
}
