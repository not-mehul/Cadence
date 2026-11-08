package app.cadence.core.domain.cycles

import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.Period
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Where a prediction's cycle length came from. */
enum class PredictionBasis {
    /** The user's own logged cycles. */
    HISTORY,

    /** The typical lengths the user entered, because there isn't enough history yet. */
    TYPICAL,
}

data class CyclePrediction(
    val cycleLength: Int,
    val periodLength: Int,
    val basis: PredictionBasis,
    /** How many logged cycles the cycle length is based on (0 when [basis] is TYPICAL). */
    val cyclesUsed: Int,
    /** Start of the latest period that has begun. */
    val currentCycleStart: LocalDate,
    /** When the next period is expected, before any adjustment for lateness. */
    val expectedNextStart: LocalDate,
    /** Days past [expectedNextStart] with no new period; 0 if not late. */
    val daysLate: Int,
) {
    /** Where upcoming predictions start: today if the period is late, otherwise [expectedNextStart]. */
    val nextStart: LocalDate
        get() = expectedNextStart.plusDays(daysLate.toLong())

    /** The next [count] predicted periods, starting with the upcoming one. */
    fun upcomingPeriods(count: Int): List<Period> =
        (0 until count).map { i ->
            val start = nextStart.plusDays(i.toLong() * cycleLength)
            Period(start, start.plusDays(periodLength - 1L))
        }
}

/**
 * Basic prediction (Phase 1): averages of recent history, falling back to the user's
 * typical lengths. Phase 3 adds recency weighting and confidence ranges (docs/PLAN.md §5).
 */
object CyclePredictor {
    /** Only the most recent cycles count, so predictions follow changes over time. */
    const val HISTORY_WINDOW = 6

    /** Fewer completed cycles than this and the user's typical cycle length is used. */
    const val MIN_CYCLES_FOR_HISTORY = 2

    /** Null until at least one period has started on or before [today]. */
    fun predict(
        history: CycleHistory,
        defaults: CycleDefaults,
        today: LocalDate,
    ): CyclePrediction? = history.latestPeriodStartedBy(today)?.let { predict(history, defaults, today, current = it) }

    /** Predicts from [current], the latest period that has started by [today]. */
    internal fun predict(
        history: CycleHistory,
        defaults: CycleDefaults,
        today: LocalDate,
        current: Period,
    ): CyclePrediction {
        val recentCycles =
            history.cycles
                .map { it.length }
                .filter { it in CycleDefaults.CYCLE_LENGTH_RANGE }
                .takeLast(HISTORY_WINDOW)
        val useHistory = recentCycles.size >= MIN_CYCLES_FOR_HISTORY
        val cycleLength = if (useHistory) recentCycles.average().roundToInt() else defaults.cycleLength

        // A period still in progress (or pre-filled into the future) would skew the average.
        val recentPeriods =
            history.periods
                .filter { it.end.isBefore(today) }
                .map(Period::length)
                // Periods are always at least one day long, so only the upper bound can exclude one.
                .filter { it <= CycleDefaults.PERIOD_LENGTH_RANGE.last }
                .takeLast(HISTORY_WINDOW)
        val periodLength =
            (if (recentPeriods.isEmpty()) defaults.periodLength else recentPeriods.average().roundToInt())
                .coerceAtMost(cycleLength - 1)

        val expectedNextStart = current.start.plusDays(cycleLength.toLong())
        val daysLate =
            ChronoUnit.DAYS
                .between(expectedNextStart, today)
                .toInt()
                .coerceAtLeast(0)

        return CyclePrediction(
            cycleLength = cycleLength,
            periodLength = periodLength,
            basis = if (useHistory) PredictionBasis.HISTORY else PredictionBasis.TYPICAL,
            cyclesUsed = if (useHistory) recentCycles.size else 0,
            currentCycleStart = current.start,
            expectedNextStart = expectedNextStart,
            daysLate = daysLate,
        )
    }
}
