package app.cadence.core.domain.patterns

import app.cadence.core.domain.cycles.CycleHistory
import app.cadence.core.domain.cycles.CyclePrediction
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A pattern that's due: something the user tends to log at this point in the cycle. */
data class HeadsUp(
    val pattern: SymptomPattern,
    /** 0: around now (from yesterday to today); 1: tomorrow. */
    val inDays: Int,
)

/**
 * Turns the user's own [SymptomPattern]s into heads-ups for today and tomorrow, such as "cramps
 * tend to come around now". Observations from their logs, not predictions of health.
 */
object SymptomForecast {
    /** At most this many heads-ups, strongest patterns first. */
    const val MAX = 4

    /**
     * Needs a prediction (tracking mode). While a period is late, "before your period" and the
     * predicted period can't be placed, so only patterns tied to the current period or cycle day apply.
     */
    fun upcoming(
        patterns: List<SymptomPattern>,
        history: CycleHistory,
        prediction: CyclePrediction?,
        today: LocalDate,
    ): List<HeadsUp> {
        prediction ?: return emptyList()
        val onPeriod = history.isPeriodDay(today)
        val nextStart = prediction.expectedNextStart.takeIf { prediction.daysLate == 0 }
        return patterns
            .mapNotNull { pattern ->
                val due =
                    when (pattern.timing) {
                        PatternTiming.DURING_PERIOD -> {
                            if (onPeriod) today else nextStart
                        }

                        PatternTiming.BEFORE_PERIOD -> {
                            nextStart?.minusDays((pattern.day ?: 0).toLong())
                        }

                        PatternTiming.AROUND_CYCLE_DAY -> {
                            prediction.currentCycleStart.plusDays((pattern.day ?: 1) - 1L)
                        }
                    } ?: return@mapNotNull null
                when (ChronoUnit.DAYS.between(today, due)) {
                    -1L, 0L -> HeadsUp(pattern, inDays = 0)
                    1L -> HeadsUp(pattern, inDays = 1)
                    else -> null
                }
            }
            // One heads-up per item: the soonest, then the strongest.
            .sortedWith(compareBy<HeadsUp> { it.inDays }.thenByDescending { it.pattern.cycles })
            .distinctBy { it.pattern.itemId }
            .take(MAX)
    }
}
