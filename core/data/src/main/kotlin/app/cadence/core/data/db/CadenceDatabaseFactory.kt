package app.cadence.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
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
            .addCallback(SecureDeleteCallback)
            .build()
    }

    /** Overwrite deleted content with zeros rather than leaving it in free pages. */
    internal object SecureDeleteCallback : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            db.query("PRAGMA secure_delete = ON").close()
        }
    }
}
