package de.heckenmann.visualagent.testsupport

import org.junit.jupiter.api.Tag

/**
 * Marks a Jupiter test class that starts an H2-backed persistence context.
 *
 * Each test context must use an isolated database because the application test task
 * runs database and non-database classes across multiple Gradle forks.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Tag("database")
annotation class DatabaseTest
