package de.heckenmann.visualagent.server

import io.grpc.BindableService
import io.grpc.Server
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

/** Owns the local in-process and optional remote gRPC server lifecycle. */
@Component
class VisualAgentGrpcServer(
    private val sessionService: VisualAgentGrpcSessionService,
    @Value("\${visualagent.server.in-process-name:visual-agent-local}") private val inProcessName: String,
    @Value("\${visualagent.server.port:0}") private val port: Int,
    @Value("\${visualagent.server.tls.certificate-chain:}") private val certificateChain: String,
    @Value("\${visualagent.server.tls.private-key:}") private val privateKey: String,
    @Value("\${visualagent.server.bind-address:127.0.0.1}") private val bindAddress: String = LOOPBACK_ADDRESS,
) : AutoCloseable {
    private var inProcessServer: Server? = null
    private var remoteServer: Server? = null

    /** Starts configured endpoints after Spring has created all application services. */
    @jakarta.annotation.PostConstruct
    fun start() {
        if (inProcessServer != null) return
        if (port > 0) {
            require(certificateChain.isNotBlank() && privateKey.isNotBlank()) {
                "TLS certificate-chain and private-key are required when visualagent.server.port is enabled"
            }
            require(InetAddress.getByName(bindAddress).isLoopbackAddress) {
                "Non-loopback server binding is disabled until authentication is configured"
            }
        }
        val service: BindableService = sessionService
        inProcessServer =
            InProcessServerBuilder
                .forName(inProcessName)
                .directExecutor()
                .addService(service)
                .build()
                .start()
        if (port > 0) {
            val builder =
                NettyServerBuilder
                    .forAddress(InetSocketAddress(bindAddress, port))
                    .addService(service)
            remoteServer =
                builder
                    .useTransportSecurity(File(certificateChain), File(privateKey))
                    .build()
                    .start()
        }
    }

    /** Returns the configured in-process transport name for a local desktop client. */
    fun inProcessServerName(): String = inProcessName

    /** Returns the configured remote port, or zero when no network endpoint is enabled. */
    fun remotePort(): Int = port

    /** Returns true when the local server has completed endpoint startup. */
    fun isReady(): Boolean = inProcessServer?.let { !it.isShutdown && !it.isTerminated } == true

    override fun close() {
        listOfNotNull(remoteServer, inProcessServer).forEach { server ->
            server.shutdown()
            runCatching { server.awaitTermination(2, TimeUnit.SECONDS) }
        }
        remoteServer = null
        inProcessServer = null
    }

    /** Stops all configured gRPC endpoints during Spring shutdown. */
    @jakarta.annotation.PreDestroy
    fun stop() = close()

    private companion object {
        const val LOOPBACK_ADDRESS = "127.0.0.1"
    }
}
