package de.heckenmann.visualagent.agent.tools

import org.springframework.stereotype.Component
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration

/** Implements bounded redirects and normalized HTTP diagnostic outcomes. */
@Component
class JvmHttpDiagnosticProbe(
    private val transport: HttpDiagnosticTransport,
) : HttpDiagnosticProbe {
    override fun inspect(
        uri: URI,
        method: String,
        timeoutSeconds: Int,
    ): HttpDiagnosticResult {
        val startedAt = System.nanoTime()
        val redirects = mutableListOf<HttpDiagnosticRedirect>()
        var current = uri
        var lastStatus: Int? = null
        var contentType: String? = null
        var contentLength: Long? = null
        repeat(MAX_REDIRECTS + 1) { redirectNumber ->
            val remainingMillis = timeoutSeconds * MILLIS_PER_SECOND - elapsedMillis(startedAt)
            if (remainingMillis <= 0) return result("deadline_exceeded", lastStatus, contentType, contentLength, redirects, startedAt)
            val response =
                try {
                    transport.request(current, method, remainingMillis)
                } catch (_: HttpTimeoutException) {
                    return result("deadline_exceeded", lastStatus, contentType, contentLength, redirects, startedAt)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return result("cancelled", lastStatus, contentType, contentLength, redirects, startedAt)
                } catch (_: ConnectException) {
                    return result("connection_failed", lastStatus, contentType, contentLength, redirects, startedAt)
                } catch (_: IOException) {
                    return result("request_failed", lastStatus, contentType, contentLength, redirects, startedAt)
                } catch (_: IllegalArgumentException) {
                    return result("request_failed", lastStatus, contentType, contentLength, redirects, startedAt)
                }
            lastStatus = response.status
            contentType = response.contentType?.substringBefore(';')?.take(MAX_HEADER_VALUE_LENGTH)
            contentLength = response.contentLength
            val location = response.location
            if (lastStatus !in REDIRECT_STATUSES || location == null) {
                return result("completed", lastStatus, contentType, contentLength, redirects, startedAt)
            }
            if (redirectNumber ==
                MAX_REDIRECTS
            ) {
                return result("redirect_limit", lastStatus, contentType, contentLength, redirects, startedAt)
            }
            val next = runCatching { parseHttpDiagnosticUri(current.resolve(location).toString()) }.getOrNull()
            if (next == null ||
                isHttpsDowngrade(current, next)
            ) {
                return result("redirect_rejected", lastStatus, contentType, contentLength, redirects, startedAt)
            }
            redirects += HttpDiagnosticRedirect(lastStatus, safeAuthority(current), safeAuthority(next))
            current = next
        }
        return result("redirect_limit", lastStatus, contentType, contentLength, redirects, startedAt)
    }

    private fun isHttpsDowngrade(
        from: URI,
        to: URI,
    ): Boolean = from.scheme.equals("https", ignoreCase = true) && to.scheme.equals("http", ignoreCase = true)

    private fun safeAuthority(uri: URI): String =
        buildString {
            append(uri.scheme.lowercase())
            append("://")
            val host = uri.host.removeSurrounding("[", "]")
            if (':' in host) append("[$host]") else append(host)
            if (uri.port >= 0) append(":${uri.port}")
        }

    private fun result(
        status: String,
        httpStatus: Int?,
        contentType: String?,
        contentLength: Long?,
        redirects: List<HttpDiagnosticRedirect>,
        startedAt: Long,
    ) = HttpDiagnosticResult(status, httpStatus, contentType, contentLength, redirects.toList(), elapsedMillis(startedAt))

    private fun elapsedMillis(startedAt: Long): Long = ((System.nanoTime() - startedAt) / NANOS_PER_MILLI).coerceAtLeast(0)

    private companion object {
        val REDIRECT_STATUSES = setOf(HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, 303, 307, 308)
        const val MAX_REDIRECTS = 5
        const val MAX_HEADER_VALUE_LENGTH = 256
        const val MILLIS_PER_SECOND = 1_000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

/** JDK HTTP client transport that never follows redirects or exposes response bodies. */
@Component
class JvmHttpDiagnosticTransport : HttpDiagnosticTransport {
    private val client =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

    override fun request(
        uri: URI,
        method: String,
        timeoutMillis: Long,
    ): HttpDiagnosticResponse {
        val request =
            HttpRequest
                .newBuilder(uri)
                .timeout(Duration.ofMillis(timeoutMillis))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        runCatching { response.body().close() }
        return HttpDiagnosticResponse(
            status = response.statusCode(),
            contentType = response.headers().firstValue("content-type").orElse(null),
            contentLength =
                response
                    .headers()
                    .firstValueAsLong("content-length")
                    .orElse(-1L)
                    .takeIf { it >= 0 },
            location = response.headers().firstValue("location").orElse(null),
        )
    }

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 30L
    }
}
