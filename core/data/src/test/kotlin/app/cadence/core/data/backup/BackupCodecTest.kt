package app.cadence.core.data.backup

import app.cadence.core.domain.contraception.ContraceptiveMethod
import app.cadence.core.domain.contraception.ContraceptiveSchedule
import app.cadence.core.domain.contraception.DoseStatus
import app.cadence.core.domain.contraception.PackSupply
import app.cadence.core.domain.log.CustomTag
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.log.LogCategory
import app.cadence.core.domain.log.Severity
import app.cadence.core.domain.log.SexActivity
import app.cadence.core.domain.medication.Medication
import app.cadence.core.domain.medication.MedicationUse
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.ThemePreference
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.domain.reminders.ReminderSettings
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupCodecTest {
    private val day = LocalDate.of(2026, 3, 2)
    private val data =
        BackupData(
            settings =
                UserSettings(
                    goal = Goal.BIRTH_CONTROL,
                    cycleDefaults = CycleDefaults(30, 4),
                    theme = ThemePreference.DARK,
                    onboardingComplete = true,
                    hiddenCategories = setOf(LogCategory.SEX),
                    reminders =
                        ReminderSettings(
                            dailyEnabled = true,
                            dailyTime = LocalTime.of(21, 15),
                            discreet = false,
                        ),
                ),
            entries =
                listOf(
                    DayEntry(
                        day,
                        flow = MenstrualFlow.HEAVY,
                        symptoms = mapOf("cramps" to Severity.SEVERE),
                        moods = setOf("calm", "sad"),
                        painLevel = 8,
                        energy = 2,
                        sleep = 3,
                        stress = 4,
                        sex = SexActivity.PROTECTED,
                        note = "Line one\nline \"two\", with comma",
                    ),
                    DayEntry(day.plusDays(1), flow = MenstrualFlow.LIGHT),
                ),
            excludedCycleStarts = setOf(LocalDate.of(2026, 1, 1)),
        )
    private val at = Instant.parse("2026-11-08T10:00:00Z")
    private val password = "correct horse".toCharArray()

    private fun encode(password: CharArray? = null) = BackupCodec.encode(data, at, password, iterations = 1_000)

    private fun reason(block: () -> Unit) = assertFailsWith<BackupException> { block() }.reason

    @Test
    fun `plain backups round trip`() {
        assertEquals(data, BackupCodec.decode(encode()))
    }

    @Test
    fun `plain backups are readable JSON with a format and version`() {
        val text = encode().toString(Charsets.UTF_8)
        assertTrue("\"format\": \"cadence-backup\"" in text)
        assertTrue("\"version\": 2" in text)
        assertFalse(BackupCodec.isEncrypted(encode()))
    }

    @Test
    fun `encrypted backups round trip and hide their contents`() {
        val bytes = encode(password)

        assertTrue(BackupCodec.isEncrypted(bytes))
        assertFalse("Line one" in bytes.toString(Charsets.UTF_8))
        assertEquals(data, BackupCodec.decode(bytes, password))
    }

    @Test
    fun `an empty password means no encryption`() {
        assertFalse(BackupCodec.isEncrypted(encode(CharArray(0))))
    }

    @Test
    fun `encrypted backups need the right password`() {
        val bytes = encode(password)
        assertEquals(BackupException.Reason.PASSWORD_REQUIRED, reason { BackupCodec.decode(bytes) })
        assertEquals(BackupException.Reason.WRONG_PASSWORD, reason { BackupCodec.decode(bytes, "wrong".toCharArray()) })
    }

    @Test
    fun `other files are rejected`() {
        assertEquals(BackupException.Reason.NOT_A_BACKUP, reason { BackupCodec.decode("hello".toByteArray()) })
        assertEquals(
            BackupException.Reason.NOT_A_BACKUP,
            reason { BackupCodec.decode("""{"name": "x"}""".toByteArray()) },
        )
        assertEquals(BackupException.Reason.NOT_A_BACKUP, reason { BackupCodec.decode("[1, 2]".toByteArray()) })
        assertFalse(BackupCodec.isEncrypted("garbage".toByteArray()))
    }

    private val phase6 =
        data.copy(
            settings =
                data.settings.copy(
                    contraception =
                        ContraceptiveSchedule(
                            ContraceptiveMethod.RING,
                            LocalDate.of(2026, 2, 2),
                            activeDays = 21,
                            breakDays = 7,
                            since = LocalDate.of(2026, 2, 10),
                            reminderEnabled = true,
                            supply = PackSupply(3, LocalDate.of(2026, 3, 10)),
                            reminderTime = LocalTime.of(20, 45),
                        ),
                ),
            entries = data.entries.map { if (it.date == day) it.copy(tags = setOf(7)) else it },
            contraceptiveDoses = mapOf(day to DoseStatus.TAKEN, day.plusDays(1) to DoseStatus.MISSED),
            medications =
                listOf(
                    Medication(3, "Iron", MedicationUse.DAILY, LocalTime.of(8, 0)),
                    Medication(4, "Ibuprofen", MedicationUse.AS_NEEDED, active = false),
                ),
            medicationDoses = mapOf(day to setOf(3L, 4L)),
            customTags = listOf(CustomTag(7, "Travel")),
        )

    @Test
    fun `birth control, medications and custom tags round trip`() {
        assertEquals(phase6, BackupCodec.decode(BackupCodec.encode(phase6, at, null)))
    }

    @Test
    fun `version 1 backups from Phase 5 still restore`() {
        val v1 =
            """
            {
              "format": "cadence-backup",
              "version": 1,
              "exportedAt": "2026-11-01T10:00:00Z",
              "settings": {"goal": "TRACK", "cycleLength": 28, "periodLength": 5, "lutealPhaseLength": 14, "theme": "SYSTEM"},
              "days": [{"date": "2026-03-02", "flow": "MEDIUM", "moods": ["calm"]}]
            }
            """.trimIndent()

        val restored = BackupCodec.decode(v1.toByteArray())
        assertEquals(listOf(DayEntry(day, flow = MenstrualFlow.MEDIUM, moods = setOf("calm"))), restored.entries)
        assertEquals(null, restored.settings.contraception)
        assertEquals(emptyList(), restored.medications)
        assertEquals(emptyList(), restored.customTags)
    }

    @Test
    fun `references to tags or medications that aren't in the backup are invalid`() {
        val text = BackupCodec.encode(phase6, at, null).toString(Charsets.UTF_8)
        listOf(
            text.replace("\"tags\": [\n                7\n            ]", "\"tags\": [8]"),
            text.replace("\"medicationId\": 4", "\"medicationId\": 9"),
            text.replace("\"id\": 4", "\"id\": 3"),
            text.replace("\"MISSED\"", "\"FORGOT\""),
            text.replace("\"method\": \"RING\"", "\"method\": \"IMPLANT\""),
        ).forEach { bad ->
            assertTrue(bad != text, "replacement didn't apply: $bad")
            assertEquals(BackupException.Reason.INVALID, reason { BackupCodec.decode(bad.toByteArray()) })
        }
    }

    @Test
    fun `backups from a newer app version are refused`() {
        val newer = encode().toString(Charsets.UTF_8).replace("\"version\": 2", "\"version\": 99")
        assertEquals(BackupException.Reason.NEWER_VERSION, reason { BackupCodec.decode(newer.toByteArray()) })
    }

    @Test
    fun `invalid contents are rejected`() {
        val text = encode().toString(Charsets.UTF_8)
        listOf(
            text.replace("\"painLevel\": 8", "\"painLevel\": 11"),
            text.replace("\"2026-03-02\"", "\"not a date\""),
            text.replace("\"HEAVY\"", "\"GUSHING\""),
            text.replace("\"2026-03-03\"", "\"2026-03-02\""),
            text.replace("\"days\"", "\"days\": 5, \"x\""),
        ).forEach { bad ->
            assertEquals(BackupException.Reason.INVALID, reason { BackupCodec.decode(bad.toByteArray()) }, bad.take(80))
        }
    }

    @Test
    fun `a damaged encrypted file is invalid`() {
        val damaged = encode(password).toString(Charsets.UTF_8).replace("\"salt\"", "\"salty\"")
        assertEquals(BackupException.Reason.INVALID, reason { BackupCodec.decode(damaged.toByteArray(), password) })
    }

    @Test
    fun `unknown fields from future versions are ignored`() {
        val withExtra = encode().toString(Charsets.UTF_8).replace("\"days\"", "\"newThing\": true, \"days\"")
        assertEquals(data, BackupCodec.decode(withExtra.toByteArray()))
    }

    @Test
    fun `security settings are not part of a backup`() {
        val locked =
            data.copy(
                settings = data.settings.copy(security = data.settings.security.copy(lockEnabled = true)),
            )
        val restored = BackupCodec.decode(BackupCodec.encode(locked, at))
        assertFalse(restored.settings.security.lockEnabled)
    }
}
