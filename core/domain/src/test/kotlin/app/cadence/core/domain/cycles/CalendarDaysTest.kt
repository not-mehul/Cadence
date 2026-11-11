package app.cadence.core.domain.cycles

import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.Period
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalendarDaysTest {
    private val today = LocalDate.of(2026, 3, 20)

    // Periods on 1 Jan, 29 Jan, 26 Feb (28-day cycles), each 5 days.
    private val flows =
        (0L until 3L)
            .flatMap { cycle -> (0L until 5L).map { LocalDate.of(2026, 1, 1).plusDays(cycle * 28 + it) } }
            .associateWith { MenstrualFlow.MEDIUM }
    private val history = CycleHistory.from(flows)
    private val prediction = CyclePredictor.predict(history, CycleDefaults(28, 5), today)

    private fun cycleDay(date: LocalDate) = CalendarDays.cycleDay(date, today, history, prediction)

    @Test
    fun `days in the past count from the logged start before them`() {
        assertEquals(CycleDayInfo(1, estimated = false), cycleDay(LocalDate.of(2026, 1, 29)))
        assertEquals(CycleDayInfo(10, estimated = false), cycleDay(LocalDate.of(2026, 2, 7)))
        assertEquals(CycleDayInfo(23, estimated = false), cycleDay(today))
    }

    @Test
    fun `future days after a predicted start count from it, as an estimate`() {
        // Next period predicted on 26 Mar.
        assertEquals(CycleDayInfo(28, estimated = false), cycleDay(LocalDate.of(2026, 3, 25)))
        assertEquals(CycleDayInfo(1, estimated = true), cycleDay(LocalDate.of(2026, 3, 26)))
        assertEquals(CycleDayInfo(2, estimated = true), cycleDay(LocalDate.of(2026, 4, 24)))
    }

    @Test
    fun `a predicted start that already passed does not reset the count`() {
        val late = LocalDate.of(2026, 4, 2)
        val latePrediction = CyclePredictor.predict(history, CycleDefaults(28, 5), late)
        assertEquals(
            CycleDayInfo(36, estimated = false),
            CalendarDays.cycleDay(LocalDate.of(2026, 4, 2), late, history, latePrediction),
        )
    }

    @Test
    fun `a period logged ahead counts instead of an earlier prediction`() {
        val ahead = flows + (LocalDate.of(2026, 4, 30) to MenstrualFlow.MEDIUM)
        val aheadHistory = CycleHistory.from(ahead)
        val aheadPrediction = CyclePredictor.predict(aheadHistory, CycleDefaults(28, 5), today)
        // The next period is predicted in April, before the one logged from 30 Apr.
        assertEquals(
            CycleDayInfo(3, estimated = false),
            CalendarDays.cycleDay(LocalDate.of(2026, 5, 2), today, aheadHistory, aheadPrediction),
        )
    }

    @Test
    fun `before the first period, and without a prediction`() {
        assertNull(cycleDay(LocalDate.of(2025, 12, 31)))
        assertEquals(
            CycleDayInfo(30, estimated = false),
            CalendarDays.cycleDay(LocalDate.of(2026, 3, 27), today, history, prediction = null),
        )
    }

    @Test
    fun `the period containing a day`() {
        assertEquals(
            Period(LocalDate.of(2026, 1, 29), LocalDate.of(2026, 2, 2)),
            CalendarDays.periodContaining(history, LocalDate.of(2026, 1, 31)),
        )
        assertNull(CalendarDays.periodContaining(history, LocalDate.of(2026, 2, 3)))
    }
}
