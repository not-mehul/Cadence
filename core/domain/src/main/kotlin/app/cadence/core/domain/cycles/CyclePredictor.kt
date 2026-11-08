package app.cadence.core.domain.cycles

import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.Period
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Where a prediction's cycle length came from. */
enum class PredictionBasis {
    /** The user's own logged cycles. */
    HISTORY,

    /** The typical lengths the user entered, because there isn't enough history yet. */
    TYPICAL,
}

/** How much to trust a prediction (docs/PLAN.md §5.1). */
enum class Confidence { HIGH, MEDIUM, LOW }

/** The estimated fertile window before one predicted period, and its estimated ovulation day. */
data class FertileWindow(
    val window: Period,
    val ovulation: LocalDate,
)

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
    /** Days past [expectedNextStart] with no new period; 0 if not past it. */
    val daysLate: Int,
    /** The next period is likely within this many days either side of [expectedNextStart]. */
    val rangeDays: Int = CyclePredictor.TYPICAL_RANGE_DAYS,
    val confidence: Confidence = Confidence.LOW,
) {
    /** Where upcoming predictions start: today once the expected day has passed, otherwise [expectedNextStart]. */
    val nextStart: LocalDate
        get() = expectedNextStart.plusDays(daysLate.toLong())

    val earliestNextStart: LocalDate
        get() = expectedNextStart.minusDays(rangeDays.toLong())

    val latestNextStart: LocalDate
        get() = expectedNextStart.plusDays(rangeDays.toLong())

    /** Past the end of the likely range, not just past the single expected day (docs/PLAN.md §5.5). */
    val isLate: Boolean
        get() = daysLate > rangeDays

    /** The next [count] predicted periods, starting with the upcoming one. */
    fun upcomingPeriods(count: Int): List<Period> =
        (0 until count).map { i ->
            val start = nextStart.plusDays(i.toLong() * cycleLength)
            Period(start, start.plusDays(periodLength - 1L))
        }

    /**
     * Estimated fertile windows before each of the next [count] predicted periods (docs/PLAN.md §5.3):
     * ovulation is [lutealPhaseLength] days before a period; the window is the 5 days before
     * ovulation, ovulation, and the day after. Once the expected day has passed, the current
     * cycle's ovulation can't be placed, so its window is left out.
     */
    fun fertileWindows(
        count: Int,
        lutealPhaseLength: Int,
    ): List<FertileWindow> =
        upcomingPeriods(count)
            .drop(if (daysLate > 0) 1 else 0)
            .map { period ->
                val ovulation = period.start.minusDays(lutealPhaseLength.toLong())
                FertileWindow(
                    window =
                        Period(
                            ovulation.minusDays(FERTILE_DAYS_BEFORE_OVULATION),
                            ovulation.plusDays(FERTILE_DAYS_AFTER_OVULATION),
                        ),
                    ovulation = ovulation,
                )
            }

    companion object {
        const val FERTILE_DAYS_BEFORE_OVULATION = 5L
        const val FERTILE_DAYS_AFTER_OVULATION = 1L
    }
}

/**
 * Predicts the next period from the user's own history (docs/PLAN.md §5): a recency-weighted
 * average of the last six plausible, non-excluded cycles, with a confidence range from how
 * much those cycles vary. Falls back to the user's typical lengths until two cycles exist.
 */
object CyclePredictor {
    /** Only the most recent cycles count, so predictions follow changes over time. */
    const val HISTORY_WINDOW = 6

    /** Fewer completed cycles than this and the user's typical cycle length is used. */
    const val MIN_CYCLES_FOR_HISTORY = 2

    /** High confidence needs at least this many cycles... */
    const val MIN_CYCLES_FOR_HIGH_CONFIDENCE = 3

    /** ...varying by at most this standard deviation (days). */
    const val HIGH_CONFIDENCE_MAX_SD = 2.0
    const val MEDIUM_CONFIDENCE_MAX_SD = 4.0

    /** Range shown when predicting from typical lengths. */
    const val TYPICAL_RANGE_DAYS = 3
    const val MIN_RANGE_DAYS = 1
    const val MAX_RANGE_DAYS = 7

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
            history.includedCycles
                .map { it.length }
                .filter { it in CycleDefaults.CYCLE_LENGTH_RANGE }
                .takeLast(HISTORY_WINDOW)
        val useHistory = recentCycles.size >= MIN_CYCLES_FOR_HISTORY
        val cycleLength = if (useHistory) weightedAverage(recentCycles).roundToInt() else defaults.cycleLength

        // A period still in progress (or pre-filled into the future) would skew the average.
        val recentPeriods =
            history.periods
                .filter { it.end.isBefore(today) }
                .map(Period::length)
                // Periods are always at least one day long, so only the upper bound can exclude one.
                .filter { it <= CycleDefaults.PERIOD_LENGTH_RANGE.last }
                .takeLast(HISTORY_WINDOW)
        val periodLength =
            (if (recentPeriods.isEmpty()) defaults.periodLength else weightedAverage(recentPeriods).roundToInt())
                .coerceAtMost(cycleLength - 1)

        val expectedNextStart = current.start.plusDays(cycleLength.toLong())
        val daysLate =
            ChronoUnit.DAYS
                .between(expectedNextStart, today)
                .toInt()
                .coerceAtLeast(0)

        val spread = if (useHistory) standardDeviation(recentCycles) else null
        return CyclePrediction(
            cycleLength = cycleLength,
            periodLength = periodLength,
            basis = if (useHistory) PredictionBasis.HISTORY else PredictionBasis.TYPICAL,
            cyclesUsed = if (useHistory) recentCycles.size else 0,
            currentCycleStart = current.start,
            expectedNextStart = expectedNextStart,
            daysLate = daysLate,
            rangeDays = spread?.let { it.roundToInt().coerceIn(MIN_RANGE_DAYS, MAX_RANGE_DAYS) } ?: TYPICAL_RANGE_DAYS,
            confidence = confidence(recentCycles.size, spread),
        )
    }

    private fun confidence(
        cycles: Int,
        spread: Double?,
    ): Confidence =
        when {
            spread == null -> Confidence.LOW
            cycles >= MIN_CYCLES_FOR_HIGH_CONFIDENCE && spread <= HIGH_CONFIDENCE_MAX_SD -> Confidence.HIGH
            spread <= MEDIUM_CONFIDENCE_MAX_SD -> Confidence.MEDIUM
            else -> Confidence.LOW
        }

    /** Weights 1, 2, 3... from oldest to newest, so recent cycles count most. */
    internal fun weightedAverage(values: List<Int>): Double {
        val weights = values.indices.map { it + 1.0 }
        return values.zip(weights).sumOf { (v, w) -> v * w } / weights.sum()
    }

    /** Sample standard deviation; needs at least two values. */
    internal fun standardDeviation(values: List<Int>): Double {
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1))
    }
}
