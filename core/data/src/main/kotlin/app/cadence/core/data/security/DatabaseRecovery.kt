package app.cadence.core.data.security

import android.content.Context
import android.database.SQLException
import app.cadence.core.data.db.CadenceDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.inject.Inject

/**
 * When the database can't be opened, usually because the Keystore key was lost in a
 * device reset, the data is unrecoverable on this phone. This starts over
 * with an empty database; the user can then restore from a backup file.
 */
class DatabaseRecovery
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        suspend fun startOver() =
            withContext(Dispatchers.IO) {
                context.deleteDatabase(CadenceDatabase.FILE_NAME)
                File(context.noBackupFilesDir, KEY_FILE_NAME).delete()
                runCatching {
                    KeyStore
                        .getInstance(
                            "AndroidKeyStore",
                        ).apply { load(null) }
                        .deleteEntry(KeystoreKeyWrapper.DEFAULT_ALIAS)
                }
            }

        companion object {
            const val KEY_FILE_NAME = "cadence_db.key"

            /** Whether [error] means the stored data can't be read (as opposed to a bug). */
            fun isUnreadableData(error: Throwable): Boolean =
                generateSequence(error) { it.cause }.any { it is KeyUnavailableException || it is SQLException }
        }
    }
