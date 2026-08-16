package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.protocol.ConversationImageResolution
import de.heckenmann.visualagent.protocol.ConversationImageSources
import de.heckenmann.visualagent.protocol.MAX_MARKDOWN_IMAGE_BYTES
import de.heckenmann.visualagent.protocol.MAX_MARKDOWN_IMAGE_DIMENSION
import de.heckenmann.visualagent.protocol.MAX_MARKDOWN_IMAGE_PIXELS
import de.heckenmann.visualagent.workspace.ImageHeaderReader
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.tika.Tika
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.net.InetAddress
import java.net.Proxy
import java.net.URI
import java.time.Duration
import java.util.Base64
import kotlin.io.path.fileSize
import kotlin.io.path.readBytes

/** Fetches a remote image without following redirects or forwarding application credentials. */
fun interface ConversationImageFetcher {
    /** Fetches one already-validated HTTP(S) URI. */
    fun fetch(uri: URI): ConversationImageFetchResult
}

/** Bounded result returned by [ConversationImageFetcher]. */
data class ConversationImageFetchResult(
    /** HTTP status code, or zero when the request could not be completed. */
    val status: Int,
    /** Response content type without parameters. */
    val contentType: String?,
    /** At most [MAX_MARKDOWN_IMAGE_BYTES] plus one byte of response data. */
    val bytes: ByteArray,
)

/** OkHttp adapter used by the server-side Markdown media resolver. */
@Component
class OkHttpConversationImageFetcher(
    private val client: OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(15))
            .callTimeout(Duration.ofSeconds(15))
            .followRedirects(false)
            .followSslRedirects(false)
            .proxy(Proxy.NO_PROXY)
            .dns(PublicOnlyDns)
            .build(),
) : ConversationImageFetcher {
    override fun fetch(uri: URI): ConversationImageFetchResult =
        runCatching {
            val request =
                Request
                    .Builder()
                    .url(uri.toString())
                    .header("Accept", IMAGE_ACCEPT_HEADER)
                    .header("User-Agent", IMAGE_USER_AGENT)
                    .get()
                    .build()
            client.newCall(request).execute().use { response ->
                ConversationImageFetchResult(
                    status = response.code,
                    contentType = response.header("Content-Type")?.substringBefore(';')?.trim(),
                    bytes = response.body.byteStream().use { it.readNBytes(MAX_MARKDOWN_IMAGE_BYTES.toInt() + 1) },
                )
            }
        }.getOrElse {
            ConversationImageFetchResult(status = 0, contentType = null, bytes = ByteArray(0))
        }

    private companion object {
        const val IMAGE_ACCEPT_HEADER = "image/png,image/jpeg,image/gif"
        const val IMAGE_USER_AGENT = "VisualAgent/0.1 (https://github.com/heckenmann/visual-agent)"
    }
}

/** Resolves managed-workspace and remote Markdown images on the server side. */
@Service
class ConversationMediaResolver(
    private val workspaceFiles: WorkspaceFileService,
    private val remoteFetcher: ConversationImageFetcher,
    private val mimeDetector: Tika,
) {
    /** Resolves one source while keeping all filesystem and network access server-owned. */
    fun resolve(source: String): ConversationImageResolution {
        val normalized = source.trim()
        if (normalized.isBlank()) return rejected("Image source is empty")
        if (ConversationImageSources.isClientFile(normalized)) {
            return rejected("Client image sources must be resolved by the presentation client")
        }
        return when {
            normalized.startsWith("http://", ignoreCase = true) ||
                normalized.startsWith("https://", ignoreCase = true) -> resolveRemote(normalized)
            normalized.startsWith("data:", ignoreCase = true) -> resolveEmbedded(normalized)
            normalized.startsWith(ConversationImageSources.SERVER_FILE_PREFIX, ignoreCase = true) ->
                resolveWorkspace(normalized.substring(ConversationImageSources.SERVER_FILE_PREFIX.length))
            normalized.startsWith(ConversationImageSources.WORKSPACE_PREFIX, ignoreCase = true) ->
                resolveWorkspace(normalized)
            hasUriScheme(normalized) -> rejected("Image source scheme is not supported")
            else -> resolveWorkspace(normalized)
        }
    }

    /** Validates an embedded image data URL produced by a canvas capture or model response. */
    fun resolveEmbedded(source: String): ConversationImageResolution {
        val match = EMBEDDED_IMAGE_PATTERN.matchEntire(source.trim()) ?: return rejected("Embedded image is invalid")
        val contentType = match.groupValues[1].lowercase()
        val encoded = match.groupValues[2]
        if (encoded.length > MAX_MARKDOWN_IMAGE_BASE64_CHARS) return rejected("Image is too large")
        val bytes = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull() ?: return rejected("Embedded image is invalid")
        return validatePayload(contentType, bytes)
    }

    private fun resolveRemote(source: String): ConversationImageResolution {
        val uri = runCatching { URI(source) }.getOrNull() ?: return rejected("Image URL is invalid")
        if (uri.host.isNullOrBlank() || uri.userInfo != null) return rejected("Image URL is invalid")
        if (!PublicImageAddressPolicy.isAllowed(uri)) return rejected("Image URL points to a non-public address")
        val response = remoteFetcher.fetch(uri)
        if (response.status != HTTP_OK) return rejected("Image request failed")
        val contentType = response.contentType?.lowercase()
        return validatePayload(contentType, response.bytes)
    }

    private fun resolveWorkspace(source: String): ConversationImageResolution {
        val path = source.removePrefix(ConversationImageSources.WORKSPACE_PREFIX).removePrefix("./").removePrefix("/")
        if (path.isBlank() || path.split('/', '\\').any { it == ".." }) return rejected("Workspace image path is invalid")
        val record =
            runCatching { workspaceFiles.requireFile(null, path) }
                .getOrNull()
                ?: return rejected("Workspace image was not found")
        if (record.sizeBytes > MAX_MARKDOWN_IMAGE_BYTES) return rejected("Image is too large")
        val file =
            runCatching { workspaceFiles.resolveManagedPath(record.relativePath) }
                .getOrNull()
                ?: return rejected("Workspace image was not found")
        if (runCatching { file.fileSize() }.getOrDefault(Long.MAX_VALUE) > MAX_MARKDOWN_IMAGE_BYTES) {
            return rejected("Image is too large")
        }
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return rejected("Workspace image could not be read")
        return validatePayload(record.mimeType.lowercase(), bytes)
    }

    private fun validatePayload(
        contentType: String?,
        bytes: ByteArray,
    ): ConversationImageResolution {
        if (contentType == null || contentType !in SUPPORTED_IMAGE_TYPES) return rejected("Image type is not supported")
        if (bytes.isEmpty() || bytes.size.toLong() > MAX_MARKDOWN_IMAGE_BYTES) return rejected("Image is too large or empty")
        val detectedType = runCatching { mimeDetector.detect(bytes).lowercase() }.getOrNull()
        if (detectedType != contentType) return rejected("Image content does not match its type")
        val dimensions = runCatching { ImageHeaderReader.dimensions(bytes) }.getOrNull() ?: return rejected("Image dimensions are invalid")
        if (
            dimensions.width <= 0 ||
            dimensions.height <= 0 ||
            dimensions.width > MAX_MARKDOWN_IMAGE_DIMENSION ||
            dimensions.height > MAX_MARKDOWN_IMAGE_DIMENSION ||
            dimensions.width.toLong() * dimensions.height.toLong() > MAX_MARKDOWN_IMAGE_PIXELS
        ) {
            return rejected("Image dimensions are too large")
        }
        return ConversationImageResolution.Loaded(contentType, bytes, dimensions.width, dimensions.height)
    }

    private fun hasUriScheme(source: String): Boolean = source.substringBefore('/', source).contains(':')

    private fun rejected(reason: String): ConversationImageResolution.Rejected = ConversationImageResolution.Rejected(reason)

    private companion object {
        const val HTTP_OK = 200
        const val MAX_MARKDOWN_IMAGE_BASE64_CHARS = ((MAX_MARKDOWN_IMAGE_BYTES * 4L) / 3L + 4L).toInt()
        val SUPPORTED_IMAGE_TYPES = setOf("image/png", "image/jpeg", "image/gif")
        val EMBEDDED_IMAGE_PATTERN = Regex("data:(image/(?:png|jpeg|gif));base64,([A-Za-z0-9+/=]+)", RegexOption.IGNORE_CASE)
    }
}

/** Validates all DNS answers immediately before a remote image fetch. */
private object PublicImageAddressPolicy {
    fun isAllowed(uri: URI): Boolean {
        val host = uri.host ?: return false
        val addresses = runCatching { InetAddress.getAllByName(host) }.getOrNull() ?: return false
        return addresses.isNotEmpty() && addresses.all(::isPublicAddress)
    }

    fun isPublicAddress(address: InetAddress): Boolean {
        if (
            address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return false
        }
        val bytes = address.address
        if (bytes.size == 4) return isPublicIpv4(bytes)
        if (bytes.size == 16) {
            if (
                (bytes[0].toInt() and 0xfe) == 0xfc ||
                ((bytes[0].toInt() and 0xff) == 0xfe && (bytes[1].toInt() and 0xc0) == 0x80)
            ) {
                return false
            }
            if (
                bytes.copyOfRange(0, 10).all { it == 0.toByte() } &&
                bytes[10] == 0xff.toByte() &&
                bytes[11] == 0xff.toByte()
            ) {
                return isPublicIpv4(bytes.copyOfRange(12, 16))
            }
        }
        return true
    }

    private fun isPublicIpv4(bytes: ByteArray): Boolean {
        val first = bytes[0].toInt() and 0xff
        val second = bytes[1].toInt() and 0xff
        return when {
            first == 0 || first == 10 || first == 127 -> false
            first == 100 && second in 64..127 -> false
            first == 169 && second == 254 -> false
            first == 172 && second in 16..31 -> false
            first == 192 && second == 168 -> false
            first == 192 && second == 0 -> false
            first == 198 && second in 18..19 -> false
            first >= 224 -> false
            else -> true
        }
    }
}

/** Resolves only public addresses so OkHttp connects to the same validated DNS result. */
private object PublicOnlyDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = InetAddress.getAllByName(hostname).toList()
        require(addresses.isNotEmpty() && addresses.all(PublicImageAddressPolicy::isPublicAddress)) {
            "Image host resolves to a non-public address"
        }
        return addresses
    }
}
