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
    fun `v2 to v3 keeps settings and logs and adds the daily log fields`() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL("INSERT INTO day_log (date, flow, note, updated_at) VALUES (20500, 'LIGHT', NULL, 1000)")
            db.execSQL(
                "INSERT INTO settings (id, goal, cycle_length, period_length, luteal_phase_length, theme, " +
                    "onboarding_complete) " +
                    "VALUES (0, 'BIRTH_CONTROL', 30, 4, 14, 'DARK', 1)",
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, *ALL_MIGRATIONS).use { db ->
            db.query("SELECT flow, pain_level, sex FROM day_log").use { c ->
                c.moveToFirst()
                assertEquals("LIGHT", c.getString(0))
                assertEquals(true, c.isNull(1))
                assertEquals(true, c.isNull(2))
            }
            db.query("SELECT goal, hidden_categories FROM settings").use { c ->
                c.moveToFirst()
                assertEquals("BIRTH_CONTROL", c.getString(0))
                assertEquals("", c.getString(1))
            }
            db.query("SELECT count(*) FROM day_tag").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
            }
        }
    }

    @Test
    fun `v3 to v4 adds an empty cycle annotation table`() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL("INSERT INTO day_tag (date, tag_id, kind, severity) VALUES (20500, 'cramps', 'SYMPTOM', 2)")
        }

        helper.runMigrationsAndValidate(TEST_DB, 4, true, *ALL_MIGRATIONS).use { db ->
            db.query("SELECT tag_id FROM day_tag").use { c ->
                c.moveToFirst()
                assertEquals("cramps", c.getString(0))
            }
            db.query("SELECT count(*) FROM cycle_annotation").use { c ->
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
