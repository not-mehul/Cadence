package app.cadence.core.data.db

import androidx.room.migration.Migration

/**
 * Every schema migration, in order. Destructive migration is never enabled: user data
 * is irreplaceable. Each new entry needs a test in `MigrationTest`.
 */
val ALL_MIGRATIONS: Array<Migration> = emptyArray()
