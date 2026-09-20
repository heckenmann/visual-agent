package de.heckenmann.visualagent

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Spring Boot application root used to assemble persistence, provider, tool, and server beans.
 */
@SpringBootApplication
class VisualAgentApplication {
    init {
        if (System.getProperty(BOUNDED_ELASTIC_VIRTUAL_THREADS) == null) {
            System.setProperty(BOUNDED_ELASTIC_VIRTUAL_THREADS, "true")
        }
    }

    private companion object {
        private const val BOUNDED_ELASTIC_VIRTUAL_THREADS = "reactor.schedulers.defaultBoundedElasticOnVirtualThreads"
    }
}

/**
 * Starts the non-UI Spring Boot entry point used by tests and tooling.
 */
fun main(args: Array<String>) {
    runApplication<VisualAgentApplication>(*args)
}
