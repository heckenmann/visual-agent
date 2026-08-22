package de.heckenmann.visualagent.workspace

import org.apache.sshd.client.SshClient
import org.apache.sshd.client.keyverifier.DefaultKnownHostsServerKeyVerifier
import org.apache.sshd.client.keyverifier.RejectAllServerKeyVerifier
import org.apache.sshd.scp.client.ScpClientCreator
import org.springframework.stereotype.Component
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.isRegularFile

/** Performs authenticated-key SCP downloads with strict known-host verification. */
@Component
class WorkspaceScpTransport {
    /** Downloads one SCP source into an already-created temporary destination. */
    fun download(
        source: URI,
        destination: Path,
        control: WorkspaceDownloadControl,
    ) {
        val user =
            source.userInfo?.substringBefore(':')?.ifBlank { null }
                ?: throw IOException("SCP source must include a username")
        require(source.userInfo?.contains(':') != true) { "SCP password credentials are not accepted in a tool source" }
        val knownHosts = Path.of(System.getProperty("user.home"), ".ssh", "known_hosts")
        require(knownHosts.isRegularFile()) { "SCP requires a trusted ~/.ssh/known_hosts entry" }
        val client = SshClient.setUpDefaultClient()
        client.serverKeyVerifier =
            DefaultKnownHostsServerKeyVerifier(
                RejectAllServerKeyVerifier.INSTANCE,
                true,
                knownHosts,
            )
        client.start()
        try {
            val session =
                client
                    .connect(user, source.host, source.port.takeIf { it > 0 } ?: DEFAULT_SSH_PORT)
                    .verify(CONNECTION_TIMEOUT)
                    .clientSession
            session.auth().verify(CONNECTION_TIMEOUT)
            try {
                Files.newOutputStream(destination).use { output ->
                    ScpClientCreator
                        .instance()
                        .createScpClient(session)
                        .download(source.path, WorkspaceDownloadOutputStream(output, control))
                }
            } finally {
                session.close()
            }
        } finally {
            client.stop()
        }
    }

    private companion object {
        const val DEFAULT_SSH_PORT = 22
        val CONNECTION_TIMEOUT: Duration = Duration.ofSeconds(15)
    }
}
