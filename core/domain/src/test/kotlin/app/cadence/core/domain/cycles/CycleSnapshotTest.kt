package app.cadence.core.domain.cycles

import app.cadence.core.domain.d
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.CyclePhase
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.Period
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.domain.periodDays
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CycleSnapshotTest {
    private val track = UserSettings(cycleDefaults = CycleDefaults(28, 5), onboardingComplete = true)
    private val birthControl = track.copy(goal = Goal.BIRTH_CONTROL)

    // One 5-day period starting 1 March 2026; typical 28-day cycle → next period 29 March.
    private val march = periodDays(d(2026, 3, 1), 5)

    private fun snapshot(
        today: LocalDate,
        days: Map<LocalDate, MenstrualFlow> = march,
        settings: UserSettings = track,
    ) = CycleSnapshot.compute(days, settings, today)

    @Test
    fun `no data`() {
        val s = snapshot(d(2026, 3, 1), days = emptyMap())

        assertEquals(CycleStatus.NoData, s.status)
        assertNull(s.prediction)
        assertNull(s.currentPeriod)
        assertNull(s.cycleDay)
        assertNull(s.phase)
        assertNull(s.timeline)
        assertFalse(s.canExtendPeriod)
    }

    @Test
    fun `on the period`() {
        val s = snapshot(d(2026, 3, 3))

        assertEquals(CycleStatus.OnPeriod(dayOfPeriod = 3), s.status)
        assertEquals(Period(d(2026, 3, 1), d(2026, 3, 5)), s.currentPeriod)
        assertEquals(3, s.cycleDay)
        assertEquals(CyclePhase.MENSTRUAL, s.phase)
        assertFalse(s.canExtendPeriod)
    }

    @Test
    fun `between periods counts down to the expected start`() {
        val s = snapshot(d(2026, 3, 20))

        assertEquals(CycleStatus.BetweenPeriods(daysUntil = 9), s.status)
        assertEquals(20, s.cycleDay)
        assertNull(s.currentPeriod)
    }

    @Test
    fun `expected today`() {
        assertEquals(CycleStatus.BetweenPeriods(daysUntil = 0), snapshot(d(2026, 3, 29)).status)
    }

    @Test
    fun `due within the likely range`() {
        // Expected 29 March, typical range +/- 3 days.
        val s = snapshot(d(2026, 3, 31))
        assertEquals(CycleStatus.Due(daysPastExpected = 2), s.status)
        assertNull(s.phase)
    }

    @Test
    fun `fertile window is the current one, then the next`() {
        assertEquals(d(2026, 3, 15), snapshot(d(2026, 3, 8)).fertileWindow?.ovulation)
        assertEquals(d(2026, 3, 15), snapshot(d(2026, 3, 16)).fertileWindow?.ovulation, "still in it on its last day")
        assertEquals(d(2026, 4, 12), snapshot(d(2026, 3, 17)).fertileWindow?.ovulation)
        assertNull(snapshot(d(2026, 3, 8), settings = birthControl).fertileWindow)
    }

    @Test
    fun `excluded cycles are passed through to predictions`() {
        val days = march + periodDays(d(2026, 2, 1), 5)
        val withAll = snapshot(d(2026, 3, 10), days = days)
        val excluded = CycleSnapshot.compute(days, track, d(2026, 3, 10), excludedCycleStarts = setOf(d(2026, 2, 1)))
        assertEquals(PredictionBasis.TYPICAL, withAll.prediction?.basis)
        assertEquals(withAll.prediction?.cycleLength, excluded.prediction?.cycleLength)
    }

    @Test
    fun `late`() {
        val s = snapshot(d(2026, 4, 2))

        assertEquals(CycleStatus.Late(daysLate = 4), s.status)
        assertEquals(33, s.cycleDay)
        assertNull(s.phase, "phase is unknown once the period is late")
    }

    // 28-day cycle, 14-day luteal phase → ovulation on cycle day 15, fertile window days 10–16.
    @ParameterizedTest(name = "cycle day {0} is {1}")
    @CsvSource(
        "5, MENSTRUAL",
        "6, FOLLICULAR",
        "9, FOLLICULAR",
        "10, OVULATORY",
        "16, OVULATORY",
        "17, LUTEAL",
        "28, LUTEAL",
    )
    fun `phases when tracking`(
        cycleDay: Long,
        phase: CyclePhase,
    ) {
        assertEquals(phase, snapshot(d(2026, 3, 1).plusDays(cycleDay - 1)).phase)
    }

    @Test
    fun `timeline when tracking`() {
        assertEquals(
            CycleTimeline(cycleLength = 28, periodEndDay = 5, fertileWindowDays = 10..16),
            snapshot(d(2026, 3, 8)).timeline,
        )
    }

    @Test
    fun `birth control shows the period but no other phases or fertile window`() {
        assertEquals(CyclePhase.MENSTRUAL, snapshot(d(2026, 3, 2), settings = birthControl).phase)

        val s = snapshot(d(2026, 3, 12), settings = birthControl)
        assertNull(s.phase)
        assertEquals(CycleTimeline(28, 5, null), s.timeline)
        assertEquals(CycleStatus.BetweenPeriods(17), s.status)
    }

    @Test
    fun `a period can be extended for two days after its last logged day`() {
        assertTrue(snapshot(d(2026, 3, 6)).canExtendPeriod)
        assertTrue(snapshot(d(2026, 3, 7)).canExtendPeriod)
        assertFalse(snapshot(d(2026, 3, 8)).canExtendPeriod)
    }

    @Test
    fun `a pre-filled period counts as ongoing through its last day`() {
        val s = snapshot(d(2026, 3, 2))
        assertEquals(CycleStatus.OnPeriod(2), s.status)
        assertEquals(d(2026, 3, 5), s.currentPeriod?.end)
    }
}
