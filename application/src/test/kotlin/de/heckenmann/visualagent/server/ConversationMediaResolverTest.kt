package de.heckenmann.visualagent.server

import com.sun.net.httpserver.HttpServer
import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import de.heckenmann.visualagent.protocol.ConversationImageResolution
import de.heckenmann.visualagent.protocol.MAX_MARKDOWN_IMAGE_BYTES
import de.heckenmann.visualagent.testsupport.TestPng
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import io.mockk.every
import io.mockk.mockk
import okhttp3.Dns
import okhttp3.OkHttpClient
import org.apache.tika.Tika
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Verifies server-owned Markdown media validation and source restrictions. */
class ConversationMediaResolverTest {
    private val workspace = mockk<WorkspaceFileService>()

    @Test
    fun `remote fetcher identifies the application to strict image hosts`() {
        val bytes = pngBytes()
        val userAgent = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/image.png") { exchange ->
            userAgent.set(exchange.requestHeaders.getFirst("User-Agent"))
            exchange.responseHeaders.add("Content-Type", "image/png")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client =
                OkHttpClient
                    .Builder()
                    .dns(
                        Dns { hostname ->
                            listOf(InetAddress.getByName(hostname))
                        },
                    ).build()
            val result =
                OkHttpConversationImageFetcher(client)
                    .fetch(URI("http://127.0.0.1:${server.address.port}/image.png"))
            assertEquals(200, result.status)
            assertEquals("image/png", result.contentType)
            assertTrue(result.bytes.contentEquals(bytes))
            assertEquals("VisualAgent/0.1 (https://github.com/heckenmann/visual-agent)", userAgent.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `remote fetcher aborts a response body that stalls`() {
        val release = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/stalled.png") { exchange ->
            exchange.responseHeaders.add("Content-Type", "image/png")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.write(byteArrayOf(0x89.toByte()))
            exchange.responseBody.flush()
            release.await(2, TimeUnit.SECONDS)
            exchange.close()
        }
        server.start()
        try {
            val client =
                OkHttpClient
                    .Builder()
                    .callTimeout(Duration.ofMillis(100))
                    .readTimeout(Duration.ofMillis(100))
                    .dns(Dns { hostname -> listOf(InetAddress.getByName(hostname)) })
                    .build()
            val result =
                OkHttpConversationImageFetcher(client)
                    .fetch(URI("http://127.0.0.1:${server.address.port}/stalled.png"))

            assertEquals(0, result.status)
        } finally {
            release.countDown()
            server.stop(0)
        }
    }

    @Test
    fun `accepts a validated remote png without following redirects`() {
        val bytes = pngBytes()
        val fetcher =
            ConversationImageFetcher { uri ->
                assertEquals("https", uri.scheme)
                ConversationImageFetchResult(200, "image/png", bytes)
            }

        val result = newResolver(fetcher).resolve("https://93.184.216.34/chart.png")

        val loaded = assertIs<ConversationImageResolution.Loaded>(result)
        assertEquals("image/png", loaded.mimeType)
        assertTrue(loaded.bytes.contentEquals(bytes))
    }

    @Test
    fun `rejects unsafe schemes before invoking the remote fetcher`() {
        var fetchCount = 0
        val resolver =
            newResolver {
                fetchCount++
                ConversationImageFetchResult(200, "image/png", pngBytes())
            }

        val result = resolver.resolve("file:///etc/passwd")

        assertIs<ConversationImageResolution.Rejected>(result)
        assertEquals(0, fetchCount)
    }

    @Test
    fun `rejects private and loopback remote targets before invoking the fetcher`() {
        var fetchCount = 0
        val resolver =
            newResolver {
                fetchCount++
                ConversationImageFetchResult(200, "image/png", pngBytes())
            }

        assertIs<ConversationImageResolution.Rejected>(resolver.resolve("http://127.0.0.1/image.png"))
        assertIs<ConversationImageResolution.Rejected>(resolver.resolve("http://192.168.1.10/image.png"))
        assertEquals(0, fetchCount)
    }

    @Test
    fun `rejects client-file sources at the server boundary`() {
        val result =
            newResolver { error("client files must not reach the server fetcher") }
                .resolve("client-file:/tmp/client-image.png")

        assertIs<ConversationImageResolution.Rejected>(result)
    }

    @Test
    fun `rejects redirects mismatched media and oversized payloads`() {
        val bytes = pngBytes()
        val resolver =
            newResolver { uri ->
                when (uri.path) {
                    "/redirect" -> ConversationImageFetchResult(302, "image/png", bytes)
                    "/wrong-type" -> ConversationImageFetchResult(200, "application/octet-stream", bytes)
                    "/declared-jpeg" -> ConversationImageFetchResult(200, "image/jpeg", bytes)
                    else -> ConversationImageFetchResult(200, "image/png", ByteArray(MAX_MARKDOWN_IMAGE_BYTES.toInt() + 1))
                }
            }

        assertIs<ConversationImageResolution.Rejected>(resolver.resolve("https://93.184.216.34/redirect"))
        assertIs<ConversationImageResolution.Rejected>(resolver.resolve("https://93.184.216.34/wrong-type"))
        assertIs<ConversationImageResolution.Rejected>(resolver.resolve("https://93.184.216.34/declared-jpeg"))
        assertIs<ConversationImageResolution.Rejected>(resolver.resolve("https://93.184.216.34/oversized"))
    }

    @Test
    fun `accepts bounded model-generated image data urls`() {
        val bytes = pngBytes()
        val source = "data:image/png;base64,${java.util.Base64.getEncoder().encodeToString(bytes)}"

        val result = newResolver { error("data urls must not invoke the remote fetcher") }.resolve(source)

        val loaded = assertIs<ConversationImageResolution.Loaded>(result)
        assertEquals("image/png", loaded.mimeType)
        assertTrue(loaded.bytes.contentEquals(bytes))
    }

    @Test
    fun `rejects an embedded image whose declared type differs from Tika`() {
        val bytes = pngBytes()
        val source = "data:image/jpeg;base64,${java.util.Base64.getEncoder().encodeToString(bytes)}"

        val result = newResolver { error("data urls must not invoke the remote fetcher") }.resolve(source)

        assertIs<ConversationImageResolution.Rejected>(result)
    }

    @Test
    fun `rejects images with unsafe decoded dimensions`() {
        val oversized =
            pngBytes().also {
                it[16] = 0x00
                it[17] = 0x00
                it[18] = 0x40
                it[19] = 0x00
                it[20] = 0x00
                it[21] = 0x00
                it[22] = 0x40
                it[23] = 0x00
            }
        val resolver =
            newResolver {
                ConversationImageFetchResult(200, "image/png", oversized)
            }

        assertIs<ConversationImageResolution.Rejected>(resolver.resolve("https://93.184.216.34/huge.png"))
    }

    @Test
    fun `loads only registered managed workspace images`() {
        val path = Files.createTempFile("conversation-image", ".png")
        TestPng.write(path, 2, 2)
        val record =
            WorkspaceFileRecord(
                id = "image-1",
                relativePath = "imports/plot..final.png",
                originalName = "plot..final.png",
                mimeType = "image/png",
                sizeBytes = Files.size(path),
                sha256 = "hash",
                extractedText = null,
                importedAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            )
        every { workspace.requireFile(null, "imports/plot..final.png") } returns record
        every { workspace.resolveManagedPath("imports/plot..final.png") } returns path

        val result = newResolver { error("remote fetch must not be called") }

        assertIs<ConversationImageResolution.Loaded>(result.resolve("workspace:imports/plot..final.png"))
        Files.deleteIfExists(path)
    }

    @Test
    fun `loads server-file sources only from registered managed workspace images`() {
        val path = Files.createTempFile("conversation-server-image", ".png")
        TestPng.write(path, 2, 2)
        val record =
            WorkspaceFileRecord(
                id = "image-2",
                relativePath = "generated/chart.png",
                originalName = "chart.png",
                mimeType = "image/png",
                sizeBytes = Files.size(path),
                sha256 = "hash",
                extractedText = null,
                importedAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            )
        every { workspace.requireFile(null, "generated/chart.png") } returns record
        every { workspace.resolveManagedPath("generated/chart.png") } returns path

        val result = newResolver { error("remote fetch must not be called") }

        assertIs<ConversationImageResolution.Loaded>(result.resolve("server-file:generated/chart.png"))
        Files.deleteIfExists(path)
    }

    private fun pngBytes(): ByteArray {
        val path = Files.createTempFile("conversation-image-fixture", ".png")
        return try {
            TestPng.write(path, 2, 2)
            Files.readAllBytes(path)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    private fun newResolver(fetcher: ConversationImageFetcher): ConversationMediaResolver =
        ConversationMediaResolver(workspace, fetcher, Tika())
}
