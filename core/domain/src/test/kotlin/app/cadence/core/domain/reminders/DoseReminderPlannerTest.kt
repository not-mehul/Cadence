package app.cadence.core.domain.reminders

import app.cadence.core.domain.contraception.ContraceptiveMethod
import app.cadence.core.domain.contraception.ContraceptiveSchedule
import app.cadence.core.domain.contraception.DoseAction
import app.cadence.core.domain.contraception.DoseStatus
import app.cadence.core.domain.contraception.PackSupply
import app.cadence.core.domain.d
import app.cadence.core.domain.medication.Medication
import app.cadence.core.domain.medication.MedicationUse
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.domain.periodDays
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DoseReminderPlannerTest {
    // Ring from 2 March 2026: in on 2 March, out on 23 March, new ring 30 March.
    private val ring =
        ContraceptiveSchedule.typical(ContraceptiveMethod.RING, d(2026, 3, 2)).copy(
            reminderEnabled = true,
            reminderTime = LocalTime.of(21, 0),
        )
    private val periodReminders = ReminderSettings(periodEnabled = true, lateEnabled = true)
    private val onRing = UserSettings(goal = Goal.BIRTH_CONTROL, reminders = periodReminders, contraception = ring)

    private fun plan(
        now: LocalDateTime,
        settings: UserSettings = onRing,
        medications: List<Medication> = emptyList(),
        doses: RecordedDoses = RecordedDoses(),
    ) = ReminderPlanner.plan(periodDays(d(2026, 3, 1), 5), settings, emptySet(), now, medications, doses)

    @Test
    fun `the next ring change, and no period reminders on a schedule`() {
        val planned = plan(LocalDateTime.of(2026, 3, 10, 12, 0))

        assertEquals(
            listOf(
                PlannedReminder(
                    ReminderKind.CONTRACEPTIVE,
                    LocalDateTime.of(2026, 3, 23, 21, 0),
                    action = DoseAction.REMOVE_RING,
                ),
            ),
            planned,
        )
        assertEquals(d(2026, 3, 23), planned.single().date)
    }

    @Test
    fun `today's dose once its time has passed or it's recorded moves on to the next`() {
        // On the day: still ahead.
        assertEquals(LocalDateTime.of(2026, 3, 23, 21, 0), plan(LocalDateTime.of(2026, 3, 23, 20, 0)).single().at)
        // Recorded already: skip to the next ring.
        val recorded = RecordedDoses(contraceptive = mapOf(d(2026, 3, 23) to DoseStatus.TAKEN))
        assertEquals(
            LocalDateTime.of(2026, 3, 30, 21, 0),
            plan(LocalDateTime.of(2026, 3, 23, 20, 0), doses = recorded).single().at,
        )
        // Past its time: next ring.
        assertEquals(DoseAction.NEW_RING, plan(LocalDateTime.of(2026, 3, 23, 22, 0)).single().action)
    }

    @Test
    fun `no birth control reminder when it's off, in tracking mode, or when everything ahead is recorded`() {
        assertEquals(
            emptyList(),
            plan(LocalDateTime.of(2026, 3, 10, 12, 0), onRing.copy(contraception = ring.copy(reminderEnabled = false))),
        )

        // Tracking mode: the schedule is kept but ignored, so period reminders are back.
        val tracking = plan(LocalDateTime.of(2026, 3, 10, 12, 0), onRing.copy(goal = Goal.TRACK)).map { it.kind }
        assertEquals(listOf(ReminderKind.UPCOMING_PERIOD, ReminderKind.LATE_PERIOD), tracking)

        val everything = (0L..60L).associate { d(2026, 3, 10).plusDays(it) to DoseStatus.TAKEN }
        assertEquals(
            emptyList(),
            plan(LocalDateTime.of(2026, 3, 10, 12, 0), doses = RecordedDoses(contraceptive = everything)),
        )
    }

    @Test
    fun `daily medications remind until taken, as-needed and stopped ones don't`() {
        val iron = Medication(7, "Iron", MedicationUse.DAILY, LocalTime.of(8, 0))
        val meds =
            listOf(
                iron,
                Medication(8, "Ibuprofen", MedicationUse.AS_NEEDED),
                Medication(9, "Old", MedicationUse.DAILY, LocalTime.of(8, 0), active = false),
                Medication(10, "No reminder", MedicationUse.DAILY),
            )
        val settings = UserSettings()
        val morning = LocalDateTime.of(2026, 3, 10, 7, 0)

        assertEquals(
            listOf(PlannedReminder(ReminderKind.MEDICATION, LocalDateTime.of(2026, 3, 10, 8, 0), medicationId = 7)),
            plan(morning, settings, meds),
        )
        val takenToday = RecordedDoses(medications = mapOf(d(2026, 3, 10) to setOf(7L)))
        assertEquals(LocalDateTime.of(2026, 3, 11, 8, 0), plan(morning, settings, meds, takenToday).single().at)
        assertEquals(
            LocalDateTime.of(2026, 3, 11, 8, 0),
            plan(LocalDateTime.of(2026, 3, 10, 9, 0), settings, meds).single().at,
        )
    }

    @Test
    fun `reminders that aren't about a dose have no action or medication`() {
        val daily = PlannedReminder(ReminderKind.DAILY_LOG, LocalDateTime.of(2026, 3, 10, 20, 0))
        assertNull(daily.action)
        assertNull(daily.medicationId)
        assertEquals(LocalDate.of(2026, 3, 10), daily.date)
    }

    @Test
    fun `refill reminders come five days and one day before the packs run out`() {
        // Two rings counted on 10 March: this one (from 2 March) and one more, so they last until 26 April.
        val tracked =
            onRing.copy(contraception = ring.copy(supply = PackSupply(packs = 2, countedOn = d(2026, 3, 10))))
        val refills = { now: LocalDateTime -> plan(now, tracked).filter { it.kind == ReminderKind.REFILL } }

        assertEquals(
            listOf(
                PlannedReminder(ReminderKind.REFILL, LocalDateTime.of(2026, 4, 22, 21, 0), runsOutOn = d(2026, 4, 27)),
            ),
            refills(LocalDateTime.of(2026, 3, 10, 12, 0)),
        )
        assertEquals(LocalDateTime.of(2026, 4, 26, 21, 0), refills(LocalDateTime.of(2026, 4, 22, 22, 0)).single().at)
        assertEquals(emptyList(), refills(LocalDateTime.of(2026, 4, 26, 22, 0)), "nothing once both have passed")
        assertEquals(emptyList(), plan(LocalDateTime.of(2026, 3, 10, 12, 0)).filter { it.kind == ReminderKind.REFILL })
    }
}
