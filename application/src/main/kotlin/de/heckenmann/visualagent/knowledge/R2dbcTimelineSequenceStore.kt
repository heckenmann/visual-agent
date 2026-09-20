package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.knowledge.R2dbcPersistenceSupport.blockRequired
import org.springframework.context.annotation.DependsOn
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/** Allocates globally ordered timeline identifiers through the H2 database sequence. */
@Component
@DependsOn("flywayInitializer")
internal class R2dbcTimelineSequenceStore(
    private val databaseClient: DatabaseClient,
) {
    /** Allocates the next ordering key atomically. */
    fun nextReactive(): Mono<Long> =
        databaseClient
            .sql("SELECT NEXT VALUE FOR visual_agent_timeline_sequence AS sequence_value")
            .map { row, _ -> R2dbcPersistenceSupport.long(row, "sequence_value") ?: error("Timeline sequence returned no value") }
            .one()

    /** Synchronous compatibility adapter for test and legacy callers. */
    fun next(): Long = nextReactive().blockRequired()
}
