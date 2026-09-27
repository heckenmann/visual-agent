package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.EffectiveServerConfigurationDiagnostic
import de.heckenmann.visualagent.agent.tools.api.ServerConfigurationDiagnosticsPort
import de.heckenmann.visualagent.agent.tools.api.ServerDatabaseDiagnostic
import de.heckenmann.visualagent.agent.tools.api.ServerDatabaseDiagnosticsPort
import de.heckenmann.visualagent.agent.tools.api.ServerHealthDiagnostic
import de.heckenmann.visualagent.agent.tools.api.ServerHealthDiagnosticsPort
import de.heckenmann.visualagent.agent.tools.api.ServerProviderDiagnostic
import de.heckenmann.visualagent.agent.tools.api.ServerProviderDiagnosticsPort
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/** Combines safe database, provider, and configuration status without exposing their details. */
@Component
class ServerHealthDiagnosticsPortAdapter(
    private val database: ServerDatabaseDiagnosticsPort,
    private val provider: ServerProviderDiagnosticsPort,
    private val configuration: ServerConfigurationDiagnosticsPort,
) : ServerHealthDiagnosticsPort {
    override fun snapshot(): Mono<ServerHealthDiagnostic> =
        Mono
            .fromCallable(configuration::snapshot)
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap { config ->
                Mono.zip(database.snapshot(), provider.check()) { db, providerStatus -> aggregate(db, providerStatus, config) }
            }

    private fun aggregate(
        database: ServerDatabaseDiagnostic,
        provider: ServerProviderDiagnostic,
        configuration: EffectiveServerConfigurationDiagnostic,
    ): ServerHealthDiagnostic {
        val databaseStatus = databaseStatus(database)
        val providerStatus = providerStatus(provider)
        val configurationStatus =
            if (configuration.warningCodes.isEmpty() && configuration.warnings.isEmpty()) HEALTHY else DEGRADED
        val databaseReasons = databaseReasons(database)
        val providerReasons = providerReasons(provider)
        val configurationReasons =
            when {
                configuration.warningCodes.isNotEmpty() -> configuration.warningCodes
                configuration.warnings.isNotEmpty() -> listOf("configuration_validation_warning")
                else -> emptyList()
            }
        val overallStatus =
            when {
                databaseStatus == UNAVAILABLE -> UNAVAILABLE
                databaseStatus != HEALTHY || providerStatus != HEALTHY || configurationStatus != HEALTHY -> DEGRADED
                else -> HEALTHY
            }
        return ServerHealthDiagnostic(
            status = overallStatus,
            database = databaseStatus,
            provider = providerStatus,
            configuration = configurationStatus,
            databaseReasons = databaseReasons,
            providerReasons = providerReasons,
            configurationReasons = configurationReasons,
        )
    }

    private fun databaseReasons(snapshot: ServerDatabaseDiagnostic): List<String> =
        buildList {
            if (!snapshot.reachable) {
                add("database_unreachable")
            } else {
                if (!snapshot.schemaHistoryAvailable) add("migration_history_unavailable")
                if ((snapshot.failedMigrationCount ?: 0) > 0) add("failed_migrations_present")
            }
        }

    private fun providerReasons(snapshot: ServerProviderDiagnostic): List<String> {
        val failureKind = snapshot.failureKind
        return when {
            !snapshot.enabled -> listOf("provider_not_enabled")
            failureKind != null -> listOf(providerFailureReason(failureKind))
            snapshot.selectedModelAvailable == false -> listOf("selected_model_not_discovered")
            snapshot.selectedModelAvailable == null -> listOf("selected_model_availability_unknown")
            else -> emptyList()
        }
    }

    private fun providerFailureReason(failureKind: String): String =
        when (failureKind) {
            "Provider timeout" -> "provider_timeout"
            "Provider unreachable" -> "provider_unreachable"
            "Authentication failed" -> "provider_authentication_failed"
            "Provider quota exhausted" -> "provider_quota_exhausted"
            "Model not available" -> "provider_model_unavailable"
            "Model not available for this account" -> "provider_model_access_denied"
            else -> "provider_request_failed"
        }

    private fun databaseStatus(snapshot: ServerDatabaseDiagnostic): String =
        when {
            !snapshot.reachable -> UNAVAILABLE
            !snapshot.schemaHistoryAvailable || (snapshot.failedMigrationCount ?: 0) > 0 -> DEGRADED
            else -> HEALTHY
        }

    private fun providerStatus(snapshot: ServerProviderDiagnostic): String =
        when {
            !snapshot.enabled || snapshot.failureKind != null -> UNAVAILABLE
            snapshot.selectedModelAvailable != true -> DEGRADED
            else -> HEALTHY
        }

    private companion object {
        const val HEALTHY = "healthy"
        const val DEGRADED = "degraded"
        const val UNAVAILABLE = "unavailable"
    }
}
