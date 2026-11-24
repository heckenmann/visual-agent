package de.heckenmann.visualagent.release.registry

import de.heckenmann.visualagent.release.text
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Keeps the exact historical JVM source/material identities used by the original registry tests. */
class HistoricalJvmRegistryTest {
    @Test
    fun `historical licenses Jakarta ICU UPL and ANTLR retain exact identity`() {
        val fixture = RegistryFixture()
        val components = fixture.components
        assertEquals(107, components.size)
        for ((coordinate, license) in mapOf(
            "com.h2database:h2:2.5.252" to "MPL-2.0",
            "ch.qos.logback:logback-core:1.6.6" to "EPL-2.0",
            "net.java.dev.jna:jna:5.19.1" to "Apache-2.0",
        )) {
            assertEquals(
                license,
                components
                    .getValue(coordinate)
                    .proposedLicense.jsonPrimitive.content,
            )
        }
        val jakarta = components.getValue("jakarta.annotation:jakarta.annotation-api:3.0.0")
        assertEquals("EPL-2.0 OR GPL-2.0-only WITH Classpath-exception-2.0", jakarta.proposedLicense.jsonPrimitive.content)
        assertEquals("d456606ef104b5602700d7a2414f812e48a8a694", jakarta.upstreamCommit)
        assertEquals(
            listOf("jakarta-annotation-api-3.0.0/LICENSE.md", "jakarta-annotation-api-3.0.0/NOTICE.md"),
            jakarta.materials.map {
                it.metadata.text("path")
            },
        )
        assertEquals(
            listOf(
                "6e1f002892b81cbe0647019b150c8a056efc1add565671a4f8af629b6cd2cc7b",
                "20bf986a153cc3095639379c99ef0a5a3bf99ac4895429920d380414a3eecf08",
            ),
            jakarta.materials.map { it.metadata.text("sha256") },
        )
        assertTrue(jakarta.remaining.jsonArray.isNotEmpty())
        val icu = components.getValue("org.graalvm.shadowed:icu4j:25.4.4.1.1")
        assertEquals("Unicode/ICU License", icu.proposedLicense.jsonPrimitive.content)
        assertEquals("f1b3db8ecd39d5b3a6eff4d5641b176c7f914dfb", icu.upstreamCommit)
        assertEquals("02f9e8a76ed2f8fa80d53054ca4632c7058354010df27c8f4e7e7443543f7e1e", icu.sourceArtifactSha256)
        assertTrue(
            icu.materials
                .first()
                .content
                .toString(Charsets.UTF_8)
                .contains("UNICODE LICENSE V3"),
        )
        val upl = components.getValue("org.graalvm.polyglot:polyglot:25.4.4.1.1")
        assertEquals("UPL-1.0", upl.proposedLicense.jsonPrimitive.content)
        assertEquals("95ce1499c8c96ab7d5a6697c5b4bf42160f3b68b", upl.upstreamCommit)
        assertTrue(
            upl.materials
                .first()
                .content
                .toString(Charsets.UTF_8)
                .contains("The Universal Permissive License (UPL), Version 1.0"),
        )
        assertTrue(upl.remaining.jsonArray.isNotEmpty())
        val record = fixture.record("org.graalvm.polyglot:polyglot:25.4.4.1.1")
        assertEquals(12, record.getValue("sourceArtifacts").jsonObject.size)
        assertEquals("unreviewed", record.text("reviewStatus"))
        assertEquals("unreviewed", fixture.record("org.graalvm.shadowed:icu4j:25.4.4.1.1").text("reviewStatus"))
        assertTrue(icu.remaining.jsonArray.isNotEmpty())
        val antlr =
            components
                .getValue("org.antlr:antlr-runtime:3.5.3")
                .materials
                .single { it.metadata.text("path").endsWith("RUNTIME-SOURCE-NOTICES.txt") }
                .content
                .toString(Charsets.UTF_8)
        assertTrue(antlr.contains("2005-2009 Terence Parr"))
        assertTrue(antlr.contains("2012 Sam Harwell"))
    }

    @Test
    fun `current Logback evidence preserves historical licenses and exact binary identities`() {
        val fixture = RegistryFixture()
        for ((module, hash) in mapOf(
            "classic" to "23c22387c0cec7ef3a8fe75fc9a443b87a9c88a57b30d441f92f20a843626ad8",
            "core" to "1762e3b6cb3af6931ae6a9fcec05fb1176c044993e7cb423a365d842fd49e4f8",
        )) {
            val coordinate = "ch.qos.logback:logback-$module:1.6.7"
            val current = fixture.components.getValue(coordinate)
            val historical = fixture.components.getValue("ch.qos.logback:logback-$module:1.6.6")
            assertEquals(hash, current.sourceArtifactSha256)
            assertEquals("8b97c2fe6dd0cfa298ed3c3dd4f500b942edc63a", current.upstreamCommit)
            assertEquals("EPL-2.0", current.proposedLicense.jsonPrimitive.content)
            assertEquals("unreviewed", fixture.record(coordinate).text("reviewStatus"))
            assertContentEquals(historical.materials.first().content, current.materials.first().content)
            val notice = current.materials.single { it.metadata.text("path").endsWith("SOURCE-AVAILABILITY.txt") }
            assertTrue(notice.content.toString(Charsets.UTF_8).contains("logback-$module-1.6.7-sources.jar"))
            assertTrue(current.remaining.jsonArray.isNotEmpty())
        }
    }

    @Test
    fun `newly mapped JVM coordinates retain explicit remaining review`() {
        val components = RegistryFixture().components
        val expected =
            listOf(
                "org.antlr:ST4:4.3.4",
                "org.codehaus.mojo:animal-sniffer-annotations:1.27",
                "org.antlr:antlr-runtime:3.5.3",
                "org.antlr:antlr4-runtime:4.13.1",
                "org.ow2.asm:asm:9.11.1",
                "org.nibor.autolink:autolink:0.12.0",
                "com.github.hypfvieh:dbus-java-core:5.2.2",
                "com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.2.2",
                "io.github.vinceglb:filekit-core-jvm:0.17.0",
                "io.github.vinceglb:filekit-dialogs-compose-jvm:0.17.0",
                "io.github.vinceglb:filekit-dialogs-jvm:0.17.0",
                "com.knuddels:jtokkit:1.1.0",
                "com.google.protobuf:protobuf-java:4.36.2",
                "org.reactivestreams:reactive-streams:1.0.4",
                "org.jetbrains.kotlinx:kotlinx-io-bytestring-jvm:0.9.1",
                "org.jetbrains.kotlinx:kotlinx-io-core-jvm:0.9.1",
                "org.jetbrains.kotlinx:atomicfu-jvm:0.28.0",
            ) +
                listOf("core-jvm", "reactive", "reactor", "swing").map { "org.jetbrains.kotlinx:kotlinx-coroutines-$it:1.11.0" } +
                listOf("core-jvm", "json-jvm").map { "org.jetbrains.kotlinx:kotlinx-serialization-$it:1.12.0" }
        expected.forEach {
            assertTrue(
                components
                    .getValue(it)
                    .remaining.jsonArray
                    .isNotEmpty(),
                it,
            )
        }
        val io =
            components
                .getValue("org.jetbrains.kotlinx:kotlinx-io-core-jvm:0.9.1")
                .materials
                .first()
                .content
                .toString(Charsets.UTF_8)
        assertTrue(io.contains("NOTICE file corresponding to the section 4 d"))
        assertTrue(io.contains("Copyright 2017-2023 JetBrains s.r.o"))
    }

    @Test
    fun `Kotlin library source hashes and shared notices stay pinned`() {
        val components = RegistryFixture().components
        val families =
            listOf(
                Triple(
                    "coroutines",
                    "1.11.0",
                    mapOf(
                        "core-jvm" to "3e0847c0b463965404d5b6fe5d6868000a220b050180dec68501801e9439976a",
                        "reactive" to "44f2180d726095371e55413ef3e649e292e97f8c3742096ced8dc83b112f4d79",
                        "reactor" to "3c77a98b5d521e9c5bbb03ac6188fecd90fbb0c926e8b79c96eda8bb885a5b8c",
                        "swing" to "b1c9feab47fe4bbd9840aced64a896ebdf1e3c1287f6a400a263cd1f6046338d",
                    ),
                ),
                Triple(
                    "serialization",
                    "1.12.0",
                    mapOf(
                        "core-jvm" to "b62fb8059123c43a72ddbe4aeaa8c92c8313d822c128ca2f861e3d91e35e5070",
                        "json-jvm" to "af584f8e83f23e62a3a06f58f79a49e0c84e0011eb6c3526afc8ea1f3ace7db1",
                    ),
                ),
            )
        for ((family, version, hashes) in families) {
            val notice = components.getValue("org.jetbrains.kotlinx:kotlinx-$family-core-jvm:$version").materials.first()
            val expectedHash =
                if (family ==
                    "coroutines"
                ) {
                    "1ab6de67ef738bffae63f9aa6a8798a35e87341c83ea28d09c8dc8ca6993232a"
                } else {
                    "ec77bba5c852830abfdefcdefbf4a22577ff4df79ec323a898d5100ca1d6f8d6"
                }
            assertEquals(expectedHash, notice.metadata.text("sha256"))
            assertTrue(notice.content.toString(Charsets.UTF_8).contains("kotlinx.$family library"))
            for ((module, hash) in hashes) {
                val component = components.getValue("org.jetbrains.kotlinx:kotlinx-$family-$module:$version")
                assertEquals(hash, component.sourceArtifactSha256)
                assertContentEquals(notice.content, component.materials.first().content)
                assertEquals(
                    if (family ==
                        "coroutines"
                    ) {
                        "8564f65764d3d05893cec026c6e94250e2b23874"
                    } else {
                        "4c28c3e33c145da536f1f4a671f70e5eb5b62b9c"
                    },
                    component.upstreamCommit,
                )
            }
        }
        val atomicfu = components.getValue("org.jetbrains.kotlinx:atomicfu-jvm:0.28.0")
        assertEquals(
            "2b1db3aa8302414ea2bb1be5391110268b4611a11c9c3d28e34bf3acfddc9ef4",
            atomicfu.materials
                .first()
                .metadata
                .text("sha256"),
        )
        assertTrue(
            atomicfu.materials
                .first()
                .content
                .toString(Charsets.UTF_8)
                .contains("for the kotlix.atomicfu library"),
        )
        assertEquals("750bd5f9686abceca5ee6cdcab53d7abc20a2c1bebe462a23dba9932eeb9744c", atomicfu.sourceArtifactSha256)
    }
}
