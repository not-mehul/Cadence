package app.cadence.core.domain.cycles

import app.cadence.core.domain.model.MenstrualFlow
import java.time.LocalDate
import java.time.YearMonth

enum class DayMark {
    NONE,
    LOGGED_PERIOD,
    PREDICTED_PERIOD,
}

/** What to show on each calendar day. */
object CalendarMarks {
    /** Never predict further ahead than this many cycles. */
    const val MAX_PREDICTED_CYCLES = 24

    fun forMonth(
        month: YearMonth,
        flowByDay: Map<LocalDate, MenstrualFlow>,
        prediction: CyclePrediction?,
    ): Map<LocalDate, DayMark> {
        val history = CycleHistory.from(flowByDay)
        val predicted =
            prediction
                ?.let { p ->
                    p.upcomingPeriods(MAX_PREDICTED_CYCLES).takeWhile { !it.start.isAfter(month.atEndOfMonth()) }
                }.orEmpty()
        return (1..month.lengthOfMonth()).associate { dayOfMonth ->
            val date = month.atDay(dayOfMonth)
            date to
                when {
                    history.isPeriodDay(date) -> DayMark.LOGGED_PERIOD
                    predicted.any { date in it } -> DayMark.PREDICTED_PERIOD
                    else -> DayMark.NONE
                }
        }
    }
}
