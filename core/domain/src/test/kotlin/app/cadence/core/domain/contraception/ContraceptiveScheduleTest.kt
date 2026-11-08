package app.cadence.core.domain.contraception

import app.cadence.core.domain.contraception.ContraceptiveMethod.PATCH
import app.cadence.core.domain.contraception.ContraceptiveMethod.PILL
import app.cadence.core.domain.contraception.ContraceptiveMethod.RING
import app.cadence.core.domain.d
import app.cadence.core.domain.model.Period
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContraceptiveScheduleTest {
    // Packs start on 2 March 2026: 21 active days (2–22 March), break 23–29 March, next pack 30 March.
    private val start = d(2026, 3, 2)
    private val pill = ContraceptiveSchedule.typical(PILL, start)

    private fun actions(
        schedule: ContraceptiveSchedule,
        from: LocalDate = start,
        days: Int = schedule.packLength,
    ) = (0 until days)
        .associate { from.plusDays(it.toLong()) to schedule.actionOn(from.plusDays(it.toLong())) }
        .filterValues { it != null }

    @Test
    fun `days of the pack repeat forwards and backwards`() {
        assertEquals(1, pill.dayOfPack(start))
        assertEquals(21, pill.dayOfPack(d(2026, 3, 22)))
        assertEquals(28, pill.dayOfPack(d(2026, 3, 29)))
        assertEquals(1, pill.dayOfPack(d(2026, 3, 30)))
        // Before the anchor pack: 1 March is the last day of the previous pack.
        assertEquals(28, pill.dayOfPack(d(2026, 3, 1)))
        assertEquals(d(2026, 2, 2), pill.packStartFor(d(2026, 3, 1)))
        assertEquals(28, pill.packLength)
        assertTrue(pill.inBreak(d(2026, 3, 23)))
        assertFalse(pill.inBreak(d(2026, 3, 22)))
    }

    @Test
    fun `a pill every active day, and placebo pills only when the pack has them`() {
        assertEquals(DoseAction.ACTIVE_PILL, pill.actionOn(d(2026, 3, 22)))
        assertEquals(null, pill.actionOn(d(2026, 3, 23)))

        val placebo = ContraceptiveSchedule(PILL, start, activeDays = 24, breakDays = 4, placebo = true)
        assertEquals(DoseAction.ACTIVE_PILL, placebo.actionOn(d(2026, 3, 25)))
        assertEquals(DoseAction.PLACEBO_PILL, placebo.actionOn(d(2026, 3, 26)))
        assertEquals(28, actions(placebo).size)
    }

    @Test
    fun `patches change weekly and come off for the break`() {
        val patch = ContraceptiveSchedule.typical(PATCH, start)

        assertEquals(
            mapOf(
                start to DoseAction.NEW_PATCH,
                d(2026, 3, 9) to DoseAction.NEW_PATCH,
                d(2026, 3, 16) to DoseAction.NEW_PATCH,
                d(2026, 3, 23) to DoseAction.REMOVE_PATCH,
            ),
            actions(patch),
        )
    }

    @Test
    fun `continuous patches never come off`() {
        val patch = ContraceptiveSchedule(PATCH, start, activeDays = 28, breakDays = 0)

        assertEquals(listOf(DoseAction.NEW_PATCH), actions(patch).values.distinct())
        assertEquals(4, actions(patch).size)
    }

    @Test
    fun `a ring goes in on day 1 and comes out for the break`() {
        val ring = ContraceptiveSchedule.typical(RING, start)
        assertEquals(mapOf(start to DoseAction.NEW_RING, d(2026, 3, 23) to DoseAction.REMOVE_RING), actions(ring))

        // Continuous: a new ring each pack, never a removal.
        val continuous = ContraceptiveSchedule(RING, start, activeDays = 28, breakDays = 0)
        assertEquals(mapOf(start to DoseAction.NEW_RING), actions(continuous))
    }

    @Test
    fun `upcoming breaks start with the current one, clipped to today`() {
        assertEquals(
            listOf(Period(d(2026, 3, 23), d(2026, 3, 29)), Period(d(2026, 4, 20), d(2026, 4, 26))),
            pill.upcomingBreaks(d(2026, 3, 10), 2),
        )
        assertEquals(
            listOf(Period(d(2026, 3, 25), d(2026, 3, 29)), Period(d(2026, 4, 20), d(2026, 4, 26))),
            pill.upcomingBreaks(d(2026, 3, 25), 2),
        )
        assertEquals(emptyList(), ContraceptiveSchedule(PILL, start, 28, 0).upcomingBreaks(start, 3))
    }

    @Test
    fun `pack day during the active weeks and the break`() {
        assertEquals(
            PackDay(
                dayOfPack = 10,
                inBreak = false,
                dayOfBreak = null,
                daysUntilBreak = 12,
                action = DoseAction.ACTIVE_PILL,
                currentOrNextBreak = Period(d(2026, 3, 23), d(2026, 3, 29)),
            ),
            pill.packDay(d(2026, 3, 11)),
        )
        assertEquals(
            PackDay(
                dayOfPack = 24,
                inBreak = true,
                dayOfBreak = 3,
                daysUntilBreak = null,
                action = null,
                currentOrNextBreak = Period(d(2026, 3, 23), d(2026, 3, 29)),
            ),
            pill.packDay(d(2026, 3, 25)),
        )
        val continuous = ContraceptiveSchedule(PILL, start, 28, 0).packDay(d(2026, 3, 11))
        assertEquals(null, continuous.daysUntilBreak)
        assertEquals(null, continuous.currentOrNextBreak)
    }

    @ParameterizedTest
    @CsvSource(
        "PILL, 0, 7, false",
        "PILL, 366, 7, false",
        "PILL, 21, 8, false",
        "PILL, 21, -1, false",
        "PATCH, 20, 7, false",
        "PILL, 28, 0, true",
        "PATCH, 21, 7, true",
        "RING, 21, 7, true",
    )
    fun `invalid layouts are rejected`(
        method: ContraceptiveMethod,
        active: Int,
        breakDays: Int,
        placebo: Boolean,
    ) {
        assertThrows<IllegalArgumentException> { ContraceptiveSchedule(method, start, active, breakDays, placebo) }
    }

    @Test
    fun `placebo and required doses`() {
        assertFalse(DoseAction.PLACEBO_PILL.required)
        assertTrue(DoseAction.entries.filter { it != DoseAction.PLACEBO_PILL }.all { it.required })
        assertEquals(start, pill.since)
        assertFalse(pill.reminderEnabled)
        assertEquals(ContraceptiveSchedule.DEFAULT_REMINDER_TIME, pill.reminderTime)
        assertTrue(pill.hasBreak)
        // The settings UI offers these ranges.
        assertEquals(21, ContraceptiveSchedule.ACTIVE_DAYS_RANGE.first { it == pill.activeDays })
        assertTrue(pill.breakDays in ContraceptiveSchedule.BREAK_DAYS_RANGE)
    }
}

class MissedDosesTest {
    private val pill = ContraceptiveSchedule.typical(PILL, d(2026, 3, 2))

    @Test
    fun `required doses in the last week with nothing recorded, oldest first`() {
        val today = d(2026, 3, 20)
        val recorded =
            mapOf(
                d(2026, 3, 13) to DoseStatus.TAKEN,
                d(2026, 3, 15) to DoseStatus.MISSED,
                d(2026, 3, 16) to DoseStatus.TAKEN,
                d(2026, 3, 17) to DoseStatus.TAKEN,
                d(2026, 3, 18) to DoseStatus.TAKEN,
            )

        assertEquals(
            listOf(
                PendingDose(d(2026, 3, 14), DoseAction.ACTIVE_PILL),
                PendingDose(d(2026, 3, 19), DoseAction.ACTIVE_PILL),
            ),
            MissedDoses.find(pill, recorded, today),
        )
    }

    @Test
    fun `nothing before the schedule was set up, nothing in the break, and never placebo pills`() {
        val setUpYesterday = pill.copy(since = d(2026, 3, 19))
        assertEquals(
            listOf(PendingDose(d(2026, 3, 19), DoseAction.ACTIVE_PILL)),
            MissedDoses.find(setUpYesterday, emptyMap(), d(2026, 3, 20)),
        )

        // 23–29 March is the break; with placebo pills, they still aren't "missed".
        assertEquals(emptyList(), MissedDoses.find(pill, emptyMap(), d(2026, 3, 30)))
        val placebo = ContraceptiveSchedule(PILL, d(2026, 3, 2), 21, 7, placebo = true, since = d(2026, 3, 1))
        assertEquals(emptyList(), MissedDoses.find(placebo, emptyMap(), d(2026, 3, 30)))
    }
}
