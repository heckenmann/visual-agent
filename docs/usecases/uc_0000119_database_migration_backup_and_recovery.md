# UC-0000119: Preserve Data During Database Schema Upgrades

## Goal

When a server starts with an existing H2 database that requires a newer schema, preserve a recoverable copy of the database and managed workspace before any migration is applied.

## Preconditions

- The server data root and the database path are resolved before Spring initializes Flyway.
- No server runtime is accepting writes during startup migration.

## Main Flow

1. Flyway inspects the applied and pending schema migrations.
2. If a future schema version is present, startup stops without changing the database.
3. For a file-backed database with pending migrations, the server creates a private staged backup directory.
4. H2 writes a consistent `database.zip` archive using `BACKUP TO`; the server copies the managed workspace, including canvas documents, and writes the version manifest.
5. The complete staged snapshot is published under `migration-backups/` before Flyway applies migrations.
6. On success, the application starts normally. A restart at the same schema version does not create another snapshot.

## Failure And Recovery

- A backup failure prevents Flyway from migrating. The user sees a sanitized startup error and should check disk space and server data permissions.
- A migration failure stops startup and reports the snapshot directory. The original database and snapshot must both be retained until a restore is verified.
- A newer-schema database is never automatically downgraded.
- To recover, stop the server, restore the H2 archive into a separate empty directory, verify it, then restore the database and workspace together as documented in `docs/database.md`.
- Automatic pre-migration snapshots do not replace regular user backups.

## Tool Calls

- None.

## Code Entry Points

- `knowledge/DatabaseMigrationConfiguration.kt`: guarded Spring Boot Flyway migration strategy.
- `knowledge/DatabaseMigrationBackup.kt`: H2 archive and workspace snapshot.
- `knowledge/DatabaseMigrationFailure.kt`: sanitized failure categories and user messages.
- `desktop/ComposeStartupHost.kt`: desktop startup error display.

## Tests

- `DatabaseMigrationBackupTest` covers populated V2 upgrade, H2 restore, workspace and canvas preservation, and no snapshot on a fresh or unchanged database.
- `DatabaseMigrationFailureTest` covers backup failure, checksum failure, and future-schema rejection.
