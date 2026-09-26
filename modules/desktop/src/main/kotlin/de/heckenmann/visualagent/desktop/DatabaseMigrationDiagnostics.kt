package de.heckenmann.visualagent.desktop

import de.heckenmann.visualagent.knowledge.DatabaseMigrationFailure

/** Finds a migration failure wrapped by Spring startup exceptions. */
internal fun Throwable.databaseMigrationFailure(): DatabaseMigrationFailure? =
    generateSequence(this) { it.cause }.filterIsInstance<DatabaseMigrationFailure>().firstOrNull()
