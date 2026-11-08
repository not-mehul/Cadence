package app.cadence.feature.reminders

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cadence.core.domain.contraception.ContraceptiveMethod
import app.cadence.core.domain.contraception.ContraceptiveSchedule
import app.cadence.core.domain.contraception.DoseStatus
import app.cadence.core.domain.medication.Medication
import app.cadence.core.domain.medication.MedicationUse
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.domain.reminders.ReminderKind
import app.cadence.core.domain.reminders.ReminderSettings
import app.cadence.core.testing.FakeContraceptionRepository
import app.cadence.core.testing.FakeCycleAnnotationRepository
import app.cadence.core.testing.FakeMedicationRepository
import app.cadence.core.testing.FakePeriodRepository
import app.cadence.core.testing.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ReminderSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val now = LocalDateTime.of(2026, 3, 10, 12, 0)
    private val periods =
        FakePeriodRepository((1..5).associate { LocalDate.of(2026, 3, it) to MenstrualFlow.MEDIUM })
    private val settings =
        FakeSettingsRepository(UserSettings(cycleDefaults = CycleDefaults(28, 5), onboardingComplete = true))
    private val contraception = FakeContraceptionRepository()
    private val medications = FakeMedicationRepository()
    private val plans = ReminderPlans(periods, settings, FakeCycleAnnotationRepository(), contraception, medications)
    private val scheduler = ReminderScheduler(context, plans) { now }

    private fun millis(at: LocalDateTime) = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `nothing is scheduled while reminders are off`() =
        runTest {
            assertEquals(emptyList(), scheduler.reschedule())
            assertEquals(0, alarms.scheduledAlarms.size)
        }

    @Test
    fun `each enabled reminder gets one inexact alarm at its time`() =
        runTest {
            settings.update {
                it.copy(
                    reminders = ReminderSettings(periodEnabled = true, lateEnabled = true, dailyEnabled = true),
                )
            }

            val planned = scheduler.reschedule()

            assertEquals(
                setOf(
                    ReminderKind.UPCOMING_PERIOD,
                    ReminderKind.LATE_PERIOD,
                    ReminderKind.DAILY_LOG,
                ),
                planned
                    .map {
                        it.kind
                    }.toSet(),
            )
            assertEquals(
                planned.map { millis(it.at) }.sorted(),
                alarms.scheduledAlarms.map { it.triggerAtMs }.sorted(),
            )
            alarms.scheduledAlarms.forEach { assertEquals(AlarmManager.RTC_WAKEUP, it.getType()) }
        }

    @Test
    fun `rescheduling replaces alarms rather than adding more`() =
        runTest {
            settings.update { it.copy(reminders = ReminderSettings(dailyEnabled = true)) }
            scheduler.reschedule()
            scheduler.reschedule()

            assertEquals(1, alarms.scheduledAlarms.size)
        }

    @Test
    fun `turning a reminder off cancels its alarm`() =
        runTest {
            settings.update { it.copy(reminders = ReminderSettings(dailyEnabled = true, lateEnabled = true)) }
            scheduler.reschedule()
            assertEquals(2, alarms.scheduledAlarms.size)

            settings.update { it.copy(reminders = ReminderSettings(lateEnabled = true)) }
            scheduler.reschedule()

            assertEquals(
                listOf(millis(LocalDateTime.of(2026, 4, 2, 9, 0))),
                alarms.scheduledAlarms.map { it.triggerAtMs },
            )
        }

    @Test
    fun `each daily medication gets its own alarm, and stopping one cancels it`() =
        runTest {
            val iron = medications.save(Medication(0, "Iron", MedicationUse.DAILY, LocalTime.of(8, 0)))
            val zinc = medications.save(Medication(0, "Zinc", MedicationUse.DAILY, LocalTime.of(21, 0)))
            medications.save(Medication(0, "Ibuprofen", MedicationUse.AS_NEEDED))

            scheduler.reschedule()
            assertEquals(
                listOf(millis(LocalDateTime.of(2026, 3, 10, 21, 0)), millis(LocalDateTime.of(2026, 3, 11, 8, 0))),
                alarms.scheduledAlarms.map { it.triggerAtMs }.sorted(),
            )

            medications.save(Medication(iron, "Iron", MedicationUse.DAILY, LocalTime.of(8, 0), active = false))
            val planned = scheduler.reschedule()
            assertEquals(listOf(zinc), planned.mapNotNull { it.medicationId })
            assertEquals(
                listOf(millis(LocalDateTime.of(2026, 3, 10, 21, 0))),
                alarms.scheduledAlarms.map { it.triggerAtMs },
            )
        }

    @Test
    fun `birth control reminders follow the schedule and skip recorded doses`() =
        runTest {
            val pill =
                ContraceptiveSchedule.typical(ContraceptiveMethod.PILL, LocalDate.of(2026, 3, 2)).copy(
                    reminderEnabled = true,
                    reminderTime = LocalTime.of(21, 0),
                )
            settings.update { it.copy(goal = Goal.BIRTH_CONTROL, contraception = pill) }
            assertEquals(LocalDateTime.of(2026, 3, 10, 21, 0), scheduler.reschedule().single().at)

            contraception.record(LocalDate.of(2026, 3, 10), DoseStatus.TAKEN)
            assertEquals(LocalDateTime.of(2026, 3, 11, 21, 0), scheduler.reschedule().single().at)
            assertEquals(1, alarms.scheduledAlarms.size)
        }

    @Test
    fun `alarm keys are distinct per kind and per medication`() {
        val keys =
            ReminderKind.entries.filter { it != ReminderKind.MEDICATION }.map { ReminderKey(it, null) } +
                listOf(ReminderKey(ReminderKind.MEDICATION, 1), ReminderKey(ReminderKind.MEDICATION, 2))
        assertEquals(keys.size, keys.map { it.requestCode }.toSet().size)
    }
}
