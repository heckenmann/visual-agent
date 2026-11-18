package de.heckenmann.visualagent.release.registry

import de.heckenmann.visualagent.release.noticeSha256
import de.heckenmann.visualagent.release.text
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Keeps root-notice provenance and coverage for every published gRPC and Netty module. */
class NetworkRegistryTest {
    @Test
    fun `gRPC root notice covers all published modules`() {
        checkModules(
            "io.grpc",
            "1.85.1",
            listOf(
                "grpc-api",
                "grpc-context",
                "grpc-core",
                "grpc-inprocess",
                "grpc-netty-shaded",
                "grpc-protobuf",
                "grpc-protobuf-lite",
                "grpc-stub",
                "grpc-util",
            ),
            "grpc-java-1.85.1/NOTICE.txt",
            "54c634aed4b5c78a29d0fa51d9282e9538722543",
            listOf("OkHttp", "Envoy", "protoc-gen-validate", "udpa"),
        )
    }

    @Test
    fun `Netty root notice covers all modules and preserves Apple DNS terms`() {
        checkModules(
            "io.netty",
            "4.2.20.Final",
            listOf(
                "netty-buffer",
                "netty-codec-base",
                "netty-codec-classes-quic",
                "netty-codec-compression",
                "netty-codec-dns",
                "netty-codec-http",
                "netty-codec-http2",
                "netty-codec-http3",
                "netty-codec-socks",
                "netty-common",
                "netty-handler",
                "netty-handler-proxy",
                "netty-resolver",
                "netty-resolver-dns",
                "netty-resolver-dns-classes-macos",
                "netty-resolver-dns-native-macos",
                "netty-transport",
                "netty-transport-classes-epoll",
                "netty-transport-native-epoll",
                "netty-transport-native-unix-common",
            ),
            "netty-4.2.20.Final/NOTICE.txt",
            "4fb6a5f308382b4bee1bff3ef80b45ea8b35166d",
            listOf("The Netty Project", "dnsinfo.h", "Quiche"),
        )
        val resolver = RegistryFixture().components.getValue("io.netty:netty-resolver-dns-native-macos:4.2.20.Final")
        assertEquals(3, resolver.materials.size)
        assertTrue(
            resolver.materials[1]
                .content
                .toString(Charsets.UTF_8)
                .contains("Apple Public Source License"),
        )
        assertTrue(
            resolver.materials[2]
                .content
                .toString(Charsets.UTF_8)
                .contains("APPLE PUBLIC SOURCE LICENSE"),
        )
        assertEquals(1, resolver.remaining.jsonArray.size)
        assertTrue(
            resolver.remaining.jsonArray
                .single()
                .jsonPrimitive.content
                .contains("continue to exclude"),
        )
    }

    private fun checkModules(
        group: String,
        version: String,
        modules: List<String>,
        path: String,
        commit: String,
        credits: List<String>,
    ) {
        val fixture = RegistryFixture()
        val material = fixture.material(path)
        val expected = noticeSha256(fixture.path.parent.resolve(path))
        assertEquals(expected, material.text("sha256"))
        assertTrue(material.text("url").contains(commit))
        modules.forEach { name ->
            val component = fixture.components.getValue("$group:$name:$version")
            val first = component.materials.first()
            assertEquals(expected, first.metadata.text("sha256"))
            assertTrue(Files.readAllBytes(fixture.path.parent.resolve(path)).contentEquals(first.content))
            credits.forEach { assertTrue(first.content.toString(Charsets.UTF_8).contains(it), it) }
            assertTrue(component.remaining.jsonArray.isNotEmpty())
        }
        assertEquals("false", fixture.data.getValue("redistributionApproved").toString())
    }
}
