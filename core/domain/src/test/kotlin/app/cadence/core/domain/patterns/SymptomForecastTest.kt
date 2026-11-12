package app.cadence.core.domain.patterns

import app.cadence.core.domain.cycles.CycleHistory
import app.cadence.core.domain.cycles.CyclePredictor
import app.cadence.core.domain.d
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.periodDays
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class SymptomForecastTest {
    // Periods every 28 days from 1 Jan 2026, five days each; the next is expected on 26 March.
    private val flows =
        (0L until 3L).fold(emptyMap<LocalDate, app.cadence.core.domain.model.MenstrualFlow>()) { acc, c ->
            acc + periodDays(d(2026, 1, 1).plusDays(c * 28), 5)
        }
    private val history = CycleHistory.from(flows)
    private val cramps = SymptomPattern("cramps", PatternTiming.DURING_PERIOD, null, 3, 3)
    private val headache = SymptomPattern("headache", PatternTiming.BEFORE_PERIOD, 2, 4, 4)
    private val acne = SymptomPattern("acne", PatternTiming.AROUND_CYCLE_DAY, 21, 3, 4)

    private fun upcoming(today: LocalDate) =
        SymptomForecast.upcoming(
            listOf(cramps, headache, acne),
            history,
            CyclePredictor.predict(history, CycleDefaults(28, 5), today),
            today,
        )

    @Test
    fun `before the period, tomorrow and around now`() {
        // 23 March: headaches tend to come 2 days before 26 March, so tomorrow.
        assertEquals(listOf(HeadsUp(headache, 1)), upcoming(d(2026, 3, 23)))
        assertEquals(listOf(HeadsUp(headache, 0)), upcoming(d(2026, 3, 24)))
        assertEquals(listOf(HeadsUp(cramps, 1), HeadsUp(headache, 0)).sortedBy { it.inDays }, upcoming(d(2026, 3, 25)))
    }

    @Test
    fun `during a period and around a cycle day`() {
        assertEquals(listOf(HeadsUp(cramps, 0)), upcoming(d(2026, 2, 27)))
        // Cycle day 21 of the cycle from 26 Feb is 18 March.
        assertEquals(listOf(HeadsUp(acne, 1)), upcoming(d(2026, 3, 17)))
        assertEquals(emptyList(), upcoming(d(2026, 3, 10)))
    }

    @Test
    fun `a late period keeps only what can still be placed, and nothing without a prediction`() {
        // 2 April: late, so "before your period" and the predicted period are left out.
        assertEquals(emptyList(), upcoming(d(2026, 4, 2)))
        assertEquals(emptyList(), SymptomForecast.upcoming(listOf(cramps), history, null, d(2026, 3, 25)))
    }

    @Test
    fun `a pattern without a typical day falls on the period start or the first cycle day`() {
        val before = SymptomPattern("bloating", PatternTiming.BEFORE_PERIOD, null, 3, 3)
        val around = SymptomPattern("calm", PatternTiming.AROUND_CYCLE_DAY, null, 3, 3)
        val prediction = CyclePredictor.predict(history, CycleDefaults(28, 5), d(2026, 3, 25))
        assertEquals(
            listOf(HeadsUp(before, 1)),
            SymptomForecast.upcoming(listOf(before, around), history, prediction, d(2026, 3, 25)),
        )
        val early = CyclePredictor.predict(history, CycleDefaults(28, 5), d(2026, 2, 26))
        assertEquals(
            listOf(HeadsUp(around, 0)),
            SymptomForecast.upcoming(listOf(around), history, early, d(2026, 2, 26)),
        )
    }

    @Test
    fun `at most a few, one per item, soonest first`() {
        val many =
            (1..6).map { SymptomPattern("item$it", PatternTiming.AROUND_CYCLE_DAY, 21, 3 + it, 10) } +
                SymptomPattern("item1", PatternTiming.BEFORE_PERIOD, 2, 3, 10)
        val result =
            SymptomForecast.upcoming(
                many,
                history,
                CyclePredictor.predict(history, CycleDefaults(28, 5), d(2026, 3, 18)),
                d(2026, 3, 18),
            )
        assertEquals(SymptomForecast.MAX, result.size)
        assertEquals(listOf("item6", "item5", "item4", "item3"), result.map { it.pattern.itemId })
    }
}
