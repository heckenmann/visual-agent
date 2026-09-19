package de.heckenmann.visualagent.update

import io.netty.channel.ChannelOption
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriUtils
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.Duration

/** Release metadata consumed from the GitHub Releases API. */
internal data class GitHubRelease(
    val tagName: String,
    val name: String?,
    val body: String?,
    val htmlUrl: String?,
    val publishedAt: String?,
    val createdAt: String?,
    val draft: Boolean,
    val prerelease: Boolean,
    val assets: List<GitHubReleaseAsset>,
)

/** Release asset metadata returned by GitHub. */
internal data class GitHubReleaseAsset(
    val name: String,
    val sizeBytes: Long,
    val digest: String?,
    val downloadUrl: String = "",
)

/** Boundary for retrieving release metadata without coupling update selection to HTTP. */
internal interface GitHubReleaseClient {
    fun latestRelease(): Mono<GitHubRelease>

    fun releases(): Mono<List<GitHubRelease>>

    /** Retrieves one release by its exact Git tag. */
    fun releaseByTag(tagName: String): Mono<GitHubRelease> =
        releases()
            .flatMapMany { candidates -> Flux.fromIterable(candidates) }
            .filter { it.tagName == tagName }
            .next()

    /** Streams a release asset without buffering the complete file in memory. */
    fun download(asset: GitHubReleaseAsset): Flux<DataBuffer> =
        Flux.error(UnsupportedOperationException("Asset download is not configured"))
}

/** Public unauthenticated GitHub Releases API client for the Visual Agent repository. */
@Component
internal class GitHubReleaseHttpClient(
    private val webClient: WebClient = defaultWebClient(),
) : GitHubReleaseClient {
    private val json = Json { ignoreUnknownKeys = true }

    override fun latestRelease(): Mono<GitHubRelease> =
        request("/releases/latest")
            .map { json.decodeFromString<GitHubReleasePayload>(it).toDomain() }

    override fun releases(): Mono<List<GitHubRelease>> =
        request("/releases?per_page=$MAX_RELEASES")
            .map { json.decodeFromString<List<GitHubReleasePayload>>(it).map(GitHubReleasePayload::toDomain) }

    override fun releaseByTag(tagName: String): Mono<GitHubRelease> =
        request("/releases/tags/${UriUtils.encodePathSegment(tagName, StandardCharsets.UTF_8)}")
            .map { json.decodeFromString<GitHubReleasePayload>(it).toDomain() }

    override fun download(asset: GitHubReleaseAsset): Flux<DataBuffer> =
        webClient
            .get()
            .uri(asset.downloadUrl)
            .accept(MediaType.APPLICATION_OCTET_STREAM)
            .header("User-Agent", USER_AGENT)
            .exchangeToFlux { response ->
                if (response.statusCode().is2xxSuccessful) {
                    response.bodyToFlux(DataBuffer::class.java)
                } else {
                    response
                        .releaseBody()
                        .thenMany(Flux.error(IOException("GitHub asset download failed")))
                }
            }

    private fun request(path: String): Mono<String> =
        webClient
            .get()
            .uri("$API_BASE_URL$path")
            .accept(MediaType.APPLICATION_JSON)
            .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
            .header("User-Agent", USER_AGENT)
            .exchangeToMono { response ->
                when {
                    response.statusCode().value() == 404 -> response.releaseBody().then(Mono.empty())
                    response.statusCode().is2xxSuccessful -> response.bodyToMono(String::class.java)
                    else ->
                        response
                            .releaseBody()
                            .then(Mono.error(IOException("GitHub release lookup failed")))
                }
            }

    private companion object {
        const val API_BASE_URL = "https://api.github.com/repos/heckenmann/visual-agent"
        const val GITHUB_API_VERSION = "2026-03-10"
        const val MAX_RELEASES = 100
        const val USER_AGENT = "VisualAgentUpdateCheck/1.0"

        fun defaultWebClient(): WebClient {
            val httpClient =
                HttpClient
                    .create()
                    .followRedirect(true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                    .responseTimeout(Duration.ofSeconds(10))
            return WebClient
                .builder()
                .clientConnector(ReactorClientHttpConnector(httpClient))
                .build()
        }

        const val CONNECT_TIMEOUT_MILLIS = 5_000
    }
}

@Serializable
private data class GitHubReleasePayload(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubReleaseAssetPayload> = emptyList(),
) {
    fun toDomain() =
        GitHubRelease(
            tagName,
            name,
            body,
            htmlUrl,
            publishedAt,
            createdAt,
            draft,
            prerelease,
            assets.map(GitHubReleaseAssetPayload::toDomain),
        )
}

@Serializable
private data class GitHubReleaseAssetPayload(
    val name: String,
    val size: Long = 0,
    val digest: String? = null,
    @SerialName("browser_download_url") val downloadUrl: String = "",
) {
    fun toDomain() = GitHubReleaseAsset(name, size, digest, downloadUrl)
}
