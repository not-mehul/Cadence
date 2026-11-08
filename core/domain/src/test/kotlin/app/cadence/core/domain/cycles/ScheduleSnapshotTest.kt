package app.cadence.core.domain.cycles

import app.cadence.core.domain.contraception.ContraceptiveMethod
import app.cadence.core.domain.contraception.ContraceptiveSchedule
import app.cadence.core.domain.contraception.DoseAction
import app.cadence.core.domain.d
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.CyclePhase
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.Period
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.domain.periodDays
import org.junit.jupiter.api.Test
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Birth control mode with a pill, patch or ring schedule (docs/PLAN.md Phase 6). */
class ScheduleSnapshotTest {
    // Packs from 2 March 2026: active 2–22 March, break 23–29 March.
    private val pill = ContraceptiveSchedule.typical(ContraceptiveMethod.PILL, d(2026, 3, 2))
    private val onPill =
        UserSettings(
            goal = Goal.BIRTH_CONTROL,
            cycleDefaults = CycleDefaults(28, 5),
            onboardingComplete = true,
            contraception = pill,
        )
    private val bleed = periodDays(d(2026, 3, 24), 4)

    @Test
    fun `the schedule is only followed in birth control mode`() {
        assertEquals(pill, onPill.activeSchedule)
        assertNull(onPill.copy(goal = Goal.TRACK).activeSchedule)
        assertNull(onPill.copy(contraception = null).activeSchedule)
    }

    @Test
    fun `active weeks count down to the break, with no history-based prediction`() {
        val s = CycleSnapshot.compute(emptyMap(), onPill, d(2026, 3, 11))

        assertEquals(CycleStatus.PackActive(dayOfPack = 10, daysUntilBreak = 12), s.status)
        assertNull(s.prediction)
        assertNull(s.phase)
        assertNull(s.fertileWindow)
        assertNull(s.timeline)
        assertEquals(DoseAction.ACTIVE_PILL, s.pack?.action)
        assertFalse(s.canExtendPeriod)
    }

    @Test
    fun `the break, then a logged withdrawal bleed`() {
        assertEquals(
            CycleStatus.PackBreak(dayOfBreak = 1, breakDays = 7),
            CycleSnapshot.compute(emptyMap(), onPill, d(2026, 3, 23)).status,
        )

        val bleeding = CycleSnapshot.compute(bleed, onPill, d(2026, 3, 25))
        assertEquals(CycleStatus.OnPeriod(2), bleeding.status)
        assertEquals(CyclePhase.MENSTRUAL, bleeding.phase)
        assertEquals(Period(d(2026, 3, 24), d(2026, 3, 27)), bleeding.currentPeriod)
    }

    @Test
    fun `the day after a bleed it can still be extended`() {
        val s = CycleSnapshot.compute(bleed, onPill, d(2026, 3, 28))
        assertEquals(CycleStatus.PackBreak(6, 7), s.status)
        assertTrue(s.canExtendPeriod)
        assertFalse(CycleSnapshot.compute(bleed, onPill, d(2026, 4, 2)).canExtendPeriod)
    }

    @Test
    fun `continuous use has no break`() {
        val continuous = onPill.copy(contraception = pill.copy(activeDays = 28, breakDays = 0))
        val s = CycleSnapshot.compute(emptyMap(), continuous, d(2026, 3, 25))

        assertEquals(CycleStatus.PackActive(24, null), s.status)
        assertNull(s.pack?.currentOrNextBreak)
    }

    @Test
    fun `calendar shows the coming breaks as expected bleeds`() {
        val marks = CalendarMarks.forSchedule(YearMonth.of(2026, 3), bleed, pill, today = d(2026, 3, 11))

        assertEquals(DayMark.NONE, marks[d(2026, 3, 22)])
        assertEquals(DayMark.PREDICTED_PERIOD, marks[d(2026, 3, 23)])
        assertEquals(DayMark.LOGGED_PERIOD, marks[d(2026, 3, 24)])
        assertEquals(DayMark.PREDICTED_PERIOD, marks[d(2026, 3, 29)])
        assertEquals(DayMark.NONE, marks[d(2026, 3, 30)])
        assertFalse(marks.containsValue(DayMark.FERTILE))

        val april = CalendarMarks.forSchedule(YearMonth.of(2026, 4), emptyMap(), pill, today = d(2026, 3, 11))
        assertEquals(DayMark.PREDICTED_PERIOD, april[d(2026, 4, 20)])
        val farAhead = CalendarMarks.forSchedule(YearMonth.of(2027, 12), emptyMap(), pill, today = d(2026, 3, 11))
        assertNotNull(farAhead.values.firstOrNull { it == DayMark.PREDICTED_PERIOD })
    }
}
