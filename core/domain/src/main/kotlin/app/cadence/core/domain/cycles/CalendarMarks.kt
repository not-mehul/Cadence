package app.cadence.core.domain.cycles

import app.cadence.core.domain.model.MenstrualFlow
import java.time.LocalDate
import java.time.YearMonth

/** What a calendar day shows, highest priority first. */
enum class DayMark {
    NONE,
    LOGGED_PERIOD,
    PREDICTED_PERIOD,

    /** Estimated ovulation (an estimate only). */
    OVULATION,

    /** Estimated fertile window (an estimate only). */
    FERTILE,
}

/** What to show on each calendar day. */
object CalendarMarks {
    /** Never predict further ahead than this many cycles. */
    const val MAX_PREDICTED_CYCLES = 24

    /**
     * @param lutealPhaseLength when non-null, estimated fertile windows and ovulation are shown;
     *   pass null on hormonal birth control, where they don't apply.
     */
    fun forMonth(
        month: YearMonth,
        flowByDay: Map<LocalDate, MenstrualFlow>,
        prediction: CyclePrediction?,
        lutealPhaseLength: Int? = null,
    ): Map<LocalDate, DayMark> {
        val history = CycleHistory.from(flowByDay)
        val monthEnd = month.atEndOfMonth()
        val predicted =
            prediction
                ?.let { p -> p.upcomingPeriods(MAX_PREDICTED_CYCLES).takeWhile { !it.start.isAfter(monthEnd) } }
                .orEmpty()
        val fertile =
            if (prediction != null && lutealPhaseLength != null) {
                prediction
                    .fertileWindows(MAX_PREDICTED_CYCLES, lutealPhaseLength)
                    .takeWhile { !it.window.start.isAfter(monthEnd) }
            } else {
                emptyList()
            }
        return (1..month.lengthOfMonth()).associate { dayOfMonth ->
            val date = month.atDay(dayOfMonth)
            date to
                when {
                    history.isPeriodDay(date) -> DayMark.LOGGED_PERIOD
                    predicted.any { date in it } -> DayMark.PREDICTED_PERIOD
                    fertile.any { it.ovulation == date } -> DayMark.OVULATION
                    fertile.any { date in it.window } -> DayMark.FERTILE
                    else -> DayMark.NONE
                }
        }
    }
}
