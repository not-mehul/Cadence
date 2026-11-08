package app.cadence.core.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cadence.core.data.db.CadenceDatabaseFactory
import app.cadence.core.data.db.DayLogEntity
import app.cadence.core.data.security.DatabasePassphraseStore
import app.cadence.core.data.security.KeystoreKeyWrapper
import app.cadence.core.domain.model.MenstrualFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** Verifies on a real device that the database file is encrypted at rest. */
@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "encryption-test.db"
    private val alias = "cadence_test_db_kek"
    private lateinit var keyFile: File

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
        keyFile = File(context.noBackupFilesDir, "encryption-test.key").apply { delete() }
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
        keyFile.delete()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    private fun passphrase() = DatabasePassphraseStore(keyFile, KeystoreKeyWrapper(alias)).getOrCreate()

    @Test
    fun databaseIsUnreadableWithoutTheKeyAndReadableWithIt() {
        val day = LocalDate.of(2026, 11, 8)
        val entity = DayLogEntity(day, MenstrualFlow.MEDIUM, "test", updatedAt = Instant.ofEpochMilli(1_000))

        CadenceDatabaseFactory.create(context, passphrase(), dbName).run {
            runBlocking { dayLogDao().upsert(entity) }
            close()
        }

        val file = context.getDatabasePath(dbName)
        val header = ByteArray(16).also { buf -> file.inputStream().use { it.read(buf) } }
        assertFalse(String(header, Charsets.US_ASCII).startsWith("SQLite format 3"), "file header is plaintext")

        assertFailsWith<SQLiteException> {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use {
                it.rawQuery("SELECT count(*) FROM day_log", null).use { c -> c.moveToFirst() }
            }
        }

        CadenceDatabaseFactory.create(context, passphrase(), dbName).run {
            assertEquals(entity, runBlocking { dayLogDao().get(day) })
            close()
        }
    }
}
