package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.agent.provider.ServerTrustManagerProvider
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.springframework.core.io.FileSystemResource
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.io.IOException
import java.net.InetAddress
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
    httpClientOverride: RestClient? = null,
    serverTrustManagerProvider: ServerTrustManagerProvider? = null,
) : WorkspaceDownloadTransfer,
    AutoCloseable {
    private val managedHttpClient =
        if (httpClientOverride == null) {
            SpringHttpClientFactory.create(
                serverTrustManagerProvider,
                PublicDownloadAddressPolicy::resolve,
                HTTP_CONNECT_TIMEOUT_MILLIS,
                TRANSFER_TIMEOUT_MILLIS,
            )
        } else {
            null
        }
    private val httpClient = httpClientOverride ?: checkNotNull(managedHttpClient).client

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
        httpClient
            .get()
            .uri(source)
            .header("User-Agent", USER_AGENT)
            .exchange { _, response ->
                require(response.statusCode.is2xxSuccessful) { "Remote HTTP request failed" }
                control.setTotalBytes(response.headers.contentLength.takeIf { it >= 0 })
                response.body.use { input ->
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

    override fun close() {
        managedHttpClient?.close()
    }

    private companion object {
        const val DEFAULT_FTP_PORT = 21
        const val DEFAULT_SSH_PORT = 22
        const val HTTP_CONNECT_TIMEOUT_MILLIS = 5_000
        const val TRANSFER_TIMEOUT_MILLIS = 15_000
        const val USER_AGENT = "VisualAgent/0.1 (https://github.com/heckenmann/visual-agent)"
    }
}

internal object PublicDownloadAddressPolicy {
    fun isAllowed(host: String): Boolean = runCatching { resolve(host) }.isSuccess

    fun resolve(host: String): Array<InetAddress> {
        val addresses = InetAddress.getAllByName(host)
        require(addresses.isNotEmpty()) { "Remote source host did not resolve" }
        require(
            addresses.all { address ->
                !address.isAnyLocalAddress &&
                    !address.isLoopbackAddress &&
                    !address.isLinkLocalAddress &&
                    !address.isSiteLocalAddress &&
                    !address.isMulticastAddress &&
                    !address.isPrivateOrReserved()
            },
        ) { "Remote source host is not public" }
        return addresses
    }

    private fun InetAddress.isPrivateOrReserved(): Boolean {
        val bytes = address
        return when (bytes.size) {
            4 -> toUnsigned(bytes[0]) == 100 && toUnsigned(bytes[1]) in 64..127
            16 -> {
                val first = toUnsigned(bytes[0])
                first and 0xfe == 0xfc ||
                    (first == 0x20 && toUnsigned(bytes[1]) == 0x01 && toUnsigned(bytes[2]) == 0x0d && toUnsigned(bytes[3]) == 0xb8)
            }
            else -> true
        }
    }

    private fun toUnsigned(value: Byte): Int = value.toInt() and 0xff
}
