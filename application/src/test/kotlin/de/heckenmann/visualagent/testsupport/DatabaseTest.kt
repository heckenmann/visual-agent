package de.heckenmann.visualagent.testsupport

import org.junit.jupiter.api.Tag

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

/**
 * JUnit 4 category used by Compose tests that still rely on the Vintage engine.
 *
 * The Vintage engine exposes this category as a JUnit Platform tag.
 */
class DatabaseTestCategory
