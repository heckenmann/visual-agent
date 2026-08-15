package de.heckenmann.visualagent.testsupport

import org.junit.jupiter.api.Tag

/** JUnit Vintage category translated to a JUnit Platform tag for Compose database tests. */
interface DatabaseTestCategory

/**
 * Marks a Jupiter test class that starts an SQLite-backed persistence context.
 *
 * Database tests run in a dedicated Gradle task so they cannot initialize or tear down
 * persistence contexts concurrently with another module's database tests.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Tag("database")
annotation class DatabaseTest
