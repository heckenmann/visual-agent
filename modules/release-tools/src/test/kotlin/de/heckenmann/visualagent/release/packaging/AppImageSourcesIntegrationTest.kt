package de.heckenmann.visualagent.release.packaging

import de.heckenmann.visualagent.release.noticeSha256
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Actual pinned source contents and relink recipes retained for AppImage and embedded fonts. */
@Tag("release-integration")
class AppImageSourcesIntegrationTest {
    private val repository = Path.of(System.getProperty("release.tools.repository"))
    private val output = repository.resolve("modules/desktop/build/reports/dependency-license/library-source-materials/archives")
    private val document get() = Files.readString(repository.resolve("docs/licensing/source-delivery.md"))

    @Test
    fun `all eleven AppImage source archives match documented hashes and recipe versions`() {
        val section = document.substringAfter("| AppImage type2-runtime commit").substringBefore("\n\n")
        val expected =
            Regex("\\[source archive]\\(([^)]+)\\).*?`([0-9a-f]{64})`")
                .findAll(section)
                .associate { it.groupValues[1] to it.groupValues[2] }
        assertEquals(11, expected.size)
        val root = output.resolve("appimage-runtime")
        assertEquals(11L, Files.list(root).use { it.count() })
        val aliases =
            mapOf(
                "dd6cebedcbddde9c82f89b011e8e1d40b6e43868" to "type2-runtime-dd6cebed.tar.gz",
                "0.5.2.tar.gz" to "squashfuse-0.5.2.tar.gz",
                "v2.1.7.tar.gz" to "mimalloc-2.1.7.tar.gz",
                "v1.5.6.tar.gz" to "zstd-1.5.6.tar.gz",
                "5245849e14d213abe18d589b4b80d65793327940.tar.gz" to "alpine-musl-package.tar.gz",
                "5a1a5dc3216a69e3cd66a57ee07cb5ef01b8be39.tar.gz" to "alpine-mimalloc-package.tar.gz",
                "5c2ddf18f193dafdd25ae7391d96c7e30766eecd.tar.gz" to "alpine-zstd-package.tar.gz",
                "d3332621d44c2c4cf52e52217b16674db190212c.tar.gz" to "alpine-zlib-package.tar.gz",
            )
        val recipes =
            mapOf(
                "alpine-musl-package.tar.gz" to Triple("/main/musl/APKBUILD", "1.2.5", "12"),
                "alpine-mimalloc-package.tar.gz" to Triple("/community/mimalloc2/APKBUILD", "2.1.7", "0"),
                "alpine-zstd-package.tar.gz" to Triple("/main/zstd/APKBUILD", "1.5.6", "2"),
                "alpine-zlib-package.tar.gz" to Triple("/main/zlib/APKBUILD", "1.4", "0"),
            )
        expected.forEach { (url, hash) ->
            val basename = URI(url).path.substringAfterLast('/')
            val name = aliases[basename] ?: basename
            val path = root.resolve(name)
            assertEquals(hash, noticeSha256(path))
            val entries = sourceTarEntries(path) { it.endsWith("/APKBUILD") }
            val recipe = recipes[name]
            if (recipe == null) {
                assertTrue(entries.keys.any { it.endsWith("LICENSE") || it.endsWith("COPYING") || it.endsWith("COPYRIGHT") })
            } else {
                val text = entries.entries.single { it.key.endsWith(recipe.first) }.value
                assertTrue(text.contains("pkgver=${recipe.second}"))
                assertTrue(text.contains("pkgrel=${recipe.third}"))
                val license =
                    when (name) {
                        "alpine-zstd-package.tar.gz" -> "BSD-3-Clause OR GPL-2.0-or-later"
                        "alpine-zlib-package.tar.gz" -> "Zlib"
                        else -> "MIT"
                    }
                assertTrue(text.contains("license=\"$license\""))
                if (name.contains("mimalloc")) assertTrue(entries.keys.any { it.endsWith("/cmake-add-insecure-suffix.patch") })
                if (name.contains("musl")) assertTrue(entries.keys.any { it.endsWith("/CVE-2025-26519.patch") })
            }
        }
    }

    @Test
    fun `libfuse relink path preserves build scripts patches and static linking`() {
        val prefix = "type2-runtime-dd6cebedcbddde9c82f89b011e8e1d40b6e43868/"
        val entries =
            sourceTarEntries(output.resolve("appimage-runtime/type2-runtime-dd6cebed.tar.gz")) { name ->
                name.removePrefix(prefix) in
                    setOf(
                        "BUILD.md",
                        "src/runtime/runtime.c",
                        "src/runtime/Makefile",
                        "scripts/common/install-dependencies.sh",
                        "patches/libfuse/mount.c.diff",
                        "scripts/docker/Dockerfile",
                    )
            }

        fun source(name: String) = entries.getValue(prefix + name)
        for (name in listOf(
            "BUILD.md",
            "patches/libfuse/mount.c.diff",
            "scripts/docker/build-with-docker.sh",
            "src/runtime/runtime.c",
            "src/runtime/Makefile",
        )) {
            assertTrue(prefix + name in entries)
        }
        assertTrue(source("BUILD.md").contains("build-with-docker.sh"))
        assertTrue(source("src/runtime/runtime.c").contains("int main"))
        assertTrue(source("src/runtime/Makefile").contains("-static-pie"))
        assertTrue(source("src/runtime/Makefile").contains("-lfuse3"))
        val installer = source("scripts/common/install-dependencies.sh")
        for (text in listOf(
            "70589cfd5e1cff7ccd6ac91c86c01be340b227285c5e200baa284e401eea2ca0",
            "patch -p1",
            "--default-library static",
        )) {
            assertTrue(installer.contains(text))
        }
        assertTrue(source("scripts/docker/Dockerfile").contains("COPY patches/ /tmp/patches/"))
        assertTrue(source("patches/libfuse/mount.c.diff").contains("mount"))
        val guide = Files.readString(repository.resolve("docs/licensing/third-party/libfuse-3.15.0/SOURCE-AND-RELINK.txt"))
        for (text in listOf(
            "LGPL-2.1 section 6(a)",
            "fuse-3.15.0.tar.xz",
            "type2-runtime-dd6cebed.tar.gz",
            "mount.c.diff",
            "--runtime-file",
        )) {
            assertTrue(guide.contains(text))
        }
        val delivery = document.replace(Regex("\\s+"), " ")
        for (text in listOf(
            "lgpl-2.1.en.html",
            "the complete libfuse source and changes",
            "runtime source (or suitable object code)",
            "instructions verified against a working build",
        )) {
            assertTrue(delivery.contains(text))
        }
    }

    @Test
    fun `Liberation archive contains pinned editable sources and its original font license`() {
        val path = output.resolve("liberation-fonts/liberation-fonts-2.1.5.tar.gz")
        assertEquals("fb22c2109dcbb25202aeb6202c0a58fd2e536dbf7c72879c4bbfa2b8a9a75d60", noticeSha256(path))
        assertTrue(document.contains("liberation-fonts-2.1.5.tar.gz"))
        val entries = sourceTarEntries(path) { it == "liberation-fonts-2.1.5/LICENSE" }
        assertTrue(entries.keys.any { it.endsWith("/src/LiberationSans-Regular.sfd") })
        assertTrue(entries.getValue("liberation-fonts-2.1.5/LICENSE").contains("SIL OPEN FONT LICENSE Version 1.1"))
        assertFalse(entries.keys.any { it.endsWith("/LiberationSans-Regular.ttf") })
    }
}
