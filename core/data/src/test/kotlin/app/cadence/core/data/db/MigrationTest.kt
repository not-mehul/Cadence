package app.cadence.core.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * Guards the committed schema files and the migration chain. Runs on the JVM with
 * Robolectric's SQLite (no encryption); the schema is identical either way.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            CadenceDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun `every exported schema migrates to the current version`() {
        helper.createDatabase(TEST_DB, 1).close()

        helper.runMigrationsAndValidate(TEST_DB, CadenceDatabase.VERSION, true, *ALL_MIGRATIONS).close()
    }

    @Test
    fun `v1 to v2 keeps logged days and adds an empty settings table`() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("INSERT INTO day_log (date, flow, note, updated_at) VALUES (20500, 'HEAVY', 'kept', 1000)")
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, *ALL_MIGRATIONS).use { db ->
            db.query("SELECT date, flow, note FROM day_log").use { c ->
                c.moveToFirst()
                assertEquals(20500, c.getLong(0))
                assertEquals("HEAVY", c.getString(1))
                assertEquals("kept", c.getString(2))
            }
            db.query("SELECT count(*) FROM settings").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
            }
        }
    }

    @Test
    fun `room opens a database created from the oldest schema`() {
        helper.createDatabase(TEST_DB, 1).close()

        val db =
            Room
                .databaseBuilder(ApplicationProvider.getApplicationContext(), CadenceDatabase::class.java, TEST_DB)
                .addMigrations(*ALL_MIGRATIONS)
                .allowMainThreadQueries()
                .build()
        // Opening validates Room's identity hash against the migrated schema.
        db.openHelper.writableDatabase
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
