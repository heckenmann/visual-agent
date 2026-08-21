package de.heckenmann.visualagent.workspace

import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.springframework.core.io.FileSystemResource
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate
import org.springframework.stereotype.Component
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.isRegularFile

/** Protocol-neutral remote transfer used by the workspace download service. */
fun interface WorkspaceDownloadTransfer {
    /** Transfers a source into an already-created temporary destination. */
    fun download(
        source: URI,
        destination: Path,
        control: WorkspaceDownloadControl,
    )
}

/** Downloads resources using the protocol selected by the source URI. */
@Component
class WorkspaceDownloadTransport(
    private val scp: WorkspaceScpTransport,
    private val httpClient: OkHttpClient = defaultHttpClient(),
) : WorkspaceDownloadTransfer {
    /** Transfers a source into an already-created temporary destination. */
    override fun download(
        source: URI,
        destination: Path,
        control: WorkspaceDownloadControl,
    ) {
        require(source.host.isNullOrBlank().not()) { "Remote source host is missing" }
        require(PublicDownloadAddressPolicy.isAllowed(source.host!!)) { "Remote source host is not public" }
        when (source.scheme.lowercase()) {
            "http", "https" -> downloadHttp(source, destination, control)
            "ftp" -> downloadFtp(source, destination, control)
            "sftp" -> downloadSftp(source, destination, control)
            "scp" -> scp.download(source, destination, control)
            else -> throw IOException("Unsupported download protocol")
        }
    }

    private fun downloadHttp(
        source: URI,
        destination: Path,
        control: WorkspaceDownloadControl,
    ) {
        require(source.userInfo == null) { "HTTP credentials are not accepted in a tool source" }
        val request =
            Request
                .Builder()
                .url(source.toString())
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
        httpClient.newCall(request).execute().use { response ->
            require(response.code in 200..299) { "Remote HTTP request failed" }
            val length = response.header("Content-Length")?.toLongOrNull()
            control.setTotalBytes(length)
            response.body.byteStream().use { input ->
                Files.newOutputStream(destination).use { output ->
                    copyDownload(input, output, control)
                }
            }
        }
    }

    private fun downloadFtp(
        source: URI,
        destination: Path,
        control: WorkspaceDownloadControl,
    ) {
        require(source.userInfo == null) { "FTP credentials are not accepted in a tool source" }
        require(source.path.isNotBlank()) { "FTP source path is missing" }
        val client = FTPClient()
        client.connectTimeout = TRANSFER_TIMEOUT_MILLIS
        client.defaultTimeout = TRANSFER_TIMEOUT_MILLIS
        client.dataTimeout = Duration.ofMillis(TRANSFER_TIMEOUT_MILLIS.toLong())
        try {
            client.connect(source.host, source.port.takeIf { it > 0 } ?: DEFAULT_FTP_PORT)
            require(FTPReply.isPositiveCompletion(client.replyCode)) { "FTP server rejected the connection" }
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)
            require(client.login("anonymous", "anonymous@visual-agent.invalid")) { "FTP authentication failed" }
            val input = client.retrieveFileStream(source.path) ?: throw IOException("FTP file was not found")
            input.use { stream ->
                Files.newOutputStream(destination).use { output ->
                    copyDownload(stream, output, control)
                }
            }
            require(client.completePendingCommand()) { "FTP transfer did not complete" }
            client.logout()
        } finally {
            if (client.isConnected) client.disconnect()
        }
    }

    private fun downloadSftp(
        source: URI,
        destination: Path,
        control: WorkspaceDownloadControl,
    ) {
        val user =
            source.userInfo?.substringBefore(':')?.ifBlank { null }
                ?: throw IOException("SFTP source must include a username")
        require(source.userInfo?.contains(':') != true) { "SFTP password credentials are not accepted in a tool source" }
        require(source.path.isNotBlank()) { "SFTP source path is missing" }
        val knownHosts = Path.of(System.getProperty("user.home"), ".ssh", "known_hosts")
        require(knownHosts.isRegularFile()) { "SFTP requires a trusted ~/.ssh/known_hosts entry" }
        val factory = DefaultSftpSessionFactory()
        factory.setHost(source.host)
        factory.setPort(source.port.takeIf { it > 0 } ?: DEFAULT_SSH_PORT)
        factory.setUser(user)
        factory.setKnownHostsResource(FileSystemResource(knownHosts))
        factory.setAllowUnknownKeys(false)
        factory.setTimeout(TRANSFER_TIMEOUT_MILLIS)
        val template = SftpRemoteFileTemplate(factory)
        template.afterPropertiesSet()
        try {
            require(
                template.get(source.path) { input ->
                    Files.newOutputStream(destination).use { output ->
                        copyDownload(input, output, control)
                    }
                },
            ) { "SFTP file was not found" }
        } finally {
            factory.destroy()
        }
    }

    private companion object {
        const val DEFAULT_FTP_PORT = 21
        const val DEFAULT_SSH_PORT = 22
        const val TRANSFER_TIMEOUT_MILLIS = 15_000
        const val USER_AGENT = "VisualAgent/0.1 (https://github.com/heckenmann/visual-agent)"

        /** Builds an HTTP client that cannot follow redirects or use ambient proxies. */
        fun defaultHttpClient(): OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(15))
                .callTimeout(Duration.ofSeconds(15))
                .followRedirects(false)
                .followSslRedirects(false)
                .proxy(Proxy.NO_PROXY)
                .build()
    }
}

private object PublicDownloadAddressPolicy {
    fun isAllowed(host: String): Boolean =
        runCatching { InetAddress.getAllByName(host).toList() }
            .getOrNull()
            ?.takeIf(List<InetAddress>::isNotEmpty)
            ?.all { address ->
                !address.isAnyLocalAddress &&
                    !address.isLoopbackAddress &&
                    !address.isLinkLocalAddress &&
                    !address.isSiteLocalAddress &&
                    !address.isMulticastAddress
            } == true
}
