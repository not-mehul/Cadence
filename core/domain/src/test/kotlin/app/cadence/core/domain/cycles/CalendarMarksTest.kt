package app.cadence.core.domain.cycles

import app.cadence.core.domain.d
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.periodDays
import org.junit.jupiter.api.Test
import java.time.YearMonth
import kotlin.test.assertEquals

class CalendarMarksTest {
    private val days = periodDays(d(2026, 3, 1), 5)
    private val prediction = CyclePredictor.predict(CycleHistory.from(days), CycleDefaults(28, 5), d(2026, 3, 10))

    @Test
    fun `logged and predicted days in the same month`() {
        val marks = CalendarMarks.forMonth(YearMonth.of(2026, 3), days, prediction)

        assertEquals(31, marks.size)
        assertEquals((1..5).map { d(2026, 3, it) }, marks.filterValues { it == DayMark.LOGGED_PERIOD }.keys.sorted())
        assertEquals(
            (29..31).map { d(2026, 3, it) },
            marks.filterValues { it == DayMark.PREDICTED_PERIOD }.keys.sorted(),
        )
        assertEquals(DayMark.NONE, marks[d(2026, 3, 15)])
    }

    @Test
    fun `predictions continue into later months`() {
        val april = CalendarMarks.forMonth(YearMonth.of(2026, 4), days, prediction)
        assertEquals(
            listOf(
                d(2026, 4, 1),
                d(2026, 4, 2),
                d(2026, 4, 26),
                d(2026, 4, 27),
                d(2026, 4, 28),
                d(2026, 4, 29),
                d(2026, 4, 30),
            ),
            april.filterValues { it == DayMark.PREDICTED_PERIOD }.keys.sorted(),
        )
    }

    @Test
    fun `logged days take precedence over predictions`() {
        val withEarly = days + periodDays(d(2026, 3, 29), 2)
        val p = CyclePredictor.predict(CycleHistory.from(withEarly), CycleDefaults(28, 5), d(2026, 3, 30))
        val marks = CalendarMarks.forMonth(YearMonth.of(2026, 3), withEarly, p)

        assertEquals(DayMark.LOGGED_PERIOD, marks[d(2026, 3, 29)])
    }

    @Test
    fun `nothing predicted without data`() {
        val marks = CalendarMarks.forMonth(YearMonth.of(2026, 3), emptyMap(), null)
        assertEquals(setOf(DayMark.NONE), marks.values.toSet())
    }

    @Test
    fun `fertile window and ovulation when a luteal length is given`() {
        val marks = CalendarMarks.forMonth(YearMonth.of(2026, 3), days, prediction, lutealPhaseLength = 14)

        assertEquals(DayMark.OVULATION, marks[d(2026, 3, 15)])
        assertEquals(
            (10..16).filter { it != 15 }.map { d(2026, 3, it) },
            marks.filterValues { it == DayMark.FERTILE }.keys.sorted(),
        )
    }

    @Test
    fun `no fertile marks without a luteal length (birth control)`() {
        val marks = CalendarMarks.forMonth(YearMonth.of(2026, 3), days, prediction, lutealPhaseLength = null)
        assertEquals(setOf(DayMark.NONE, DayMark.LOGGED_PERIOD, DayMark.PREDICTED_PERIOD), marks.values.toSet())
    }

    @Test
    fun `period marks win over fertile marks`() {
        // A 15-day typical cycle puts the fertile window over the period.
        val shortPrediction = CyclePredictor.predict(CycleHistory.from(days), CycleDefaults(15, 5), d(2026, 3, 3))
        val marks = CalendarMarks.forMonth(YearMonth.of(2026, 3), days, shortPrediction, lutealPhaseLength = 14)
        assertEquals(DayMark.LOGGED_PERIOD, marks[d(2026, 3, 2)])
    }
}
