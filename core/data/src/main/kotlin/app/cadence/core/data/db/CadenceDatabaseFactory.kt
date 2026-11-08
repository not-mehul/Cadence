package app.cadence.core.data.db

import android.content.Context
import androidx.room.Room
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** Builds the encrypted (SQLCipher) Room database. */
object CadenceDatabaseFactory {
    private val loadNativeLibrary by lazy { System.loadLibrary("sqlcipher") }

    /**
     * @param passphrase SQLCipher key material from [app.cadence.core.data.security.DatabasePassphraseStore].
     * @param fileName override only in tests.
     */
    fun create(
        context: Context,
        passphrase: ByteArray,
        fileName: String = CadenceDatabase.FILE_NAME,
    ): CadenceDatabase {
        loadNativeLibrary
        return Room
            .databaseBuilder(context.applicationContext, CadenceDatabase::class.java, fileName)
            // Copy, so the caller can wipe its array without affecting later reopens.
            .openHelperFactory(SupportOpenHelperFactory(passphrase.copyOf()))
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }
}
