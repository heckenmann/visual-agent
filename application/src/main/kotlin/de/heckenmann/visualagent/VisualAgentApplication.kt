package de.heckenmann.visualagent

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Spring Boot application root used to assemble persistence, provider, tool, and UI beans.
 */
@SpringBootApplication
class VisualAgentApplication

/**
 * Starts the non-UI Spring Boot entry point used by tests and tooling.
 */
fun main(args: Array<String>) {
    runApplication<VisualAgentApplication>(*args)
}
