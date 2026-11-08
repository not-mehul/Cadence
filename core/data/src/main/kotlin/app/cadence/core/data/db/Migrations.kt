package app.cadence.core.data.db

import androidx.room.migration.Migration

/**
 * Hand-written schema migrations, in order. Simple changes use Room auto-migrations
 * (see [CadenceDatabase]). Destructive migration is never enabled: user data is
 * irreplaceable. Every version step needs a test in `MigrationTest`.
 */
val ALL_MIGRATIONS: Array<Migration> = emptyArray()
