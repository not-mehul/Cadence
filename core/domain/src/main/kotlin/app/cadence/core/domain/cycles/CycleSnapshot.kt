package app.cadence.core.domain.cycles

import app.cadence.core.domain.model.CyclePhase
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.Period
import app.cadence.core.domain.model.UserSettings
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The headline state for the Today screen. */
sealed interface CycleStatus {
    /** Nothing logged yet. */
    data object NoData : CycleStatus

    data class OnPeriod(
        val dayOfPeriod: Int,
    ) : CycleStatus

    /** [daysUntil] is 0 when the period is expected today. */
    data class BetweenPeriods(
        val daysUntil: Int,
    ) : CycleStatus

    /** Past the expected day but still within the likely range: due any day. */
    data class Due(
        val daysPastExpected: Int,
    ) : CycleStatus

    data class Late(
        val daysLate: Int,
    ) : CycleStatus
}

/** Phase boundaries as cycle days (day 1 = first day of the period), for drawing the cycle ring. */
data class CycleTimeline(
    val cycleLength: Int,
    /** Last day of the menstrual phase. */
    val periodEndDay: Int,
    /** Estimated fertile window, or null when ovulation estimates don't apply (birth control). */
    val fertileWindowDays: IntRange?,
)

/** Everything the Today screen needs, computed in one pure step. */
data class CycleSnapshot(
    val today: LocalDate,
    val status: CycleStatus,
    val prediction: CyclePrediction?,
    /** The period [today] falls in, if any. */
    val currentPeriod: Period?,
    /** Day of the current cycle (1-based), or null with no data. */
    val cycleDay: Int?,
    /** Null with no data, on birth control outside a period, or once the expected day has passed. */
    val phase: CyclePhase?,
    val timeline: CycleTimeline?,
    /** True the day or two after a period's last logged day, when it may simply not be over yet. */
    val canExtendPeriod: Boolean,
    /** The current or next estimated fertile window; null on birth control or without data. */
    val fertileWindow: FertileWindow? = null,
) {
    companion object {
        fun compute(
            flowByDay: Map<LocalDate, MenstrualFlow>,
            settings: UserSettings,
            today: LocalDate,
            excludedCycleStarts: Set<LocalDate> = emptySet(),
        ): CycleSnapshot {
            val history = CycleHistory.from(flowByDay, excludedCycleStarts)
            val latest =
                history.latestPeriodStartedBy(today)
                    ?: return CycleSnapshot(today, CycleStatus.NoData, null, null, null, null, null, false)
            val prediction = CyclePredictor.predict(history, settings.cycleDefaults, today, latest)

            val currentPeriod = latest.takeIf { today in it }
            val cycleDay = ChronoUnit.DAYS.between(latest.start, today).toInt() + 1
            val status =
                when {
                    currentPeriod != null -> {
                        CycleStatus.OnPeriod(dayOfPeriod = cycleDay)
                    }

                    prediction.isLate -> {
                        CycleStatus.Late(prediction.daysLate)
                    }

                    prediction.daysLate > 0 -> {
                        CycleStatus.Due(prediction.daysLate)
                    }

                    else -> {
                        CycleStatus.BetweenPeriods(
                            ChronoUnit.DAYS.between(today, prediction.expectedNextStart).toInt(),
                        )
                    }
                }

            val fertileDays =
                if (settings.goal.showsOvulationEstimates) {
                    val ovulationDay = prediction.cycleLength - settings.lutealPhaseLength + 1
                    val before = CyclePrediction.FERTILE_DAYS_BEFORE_OVULATION.toInt()
                    val after = CyclePrediction.FERTILE_DAYS_AFTER_OVULATION.toInt()
                    (ovulationDay - before)..(ovulationDay + after)
                } else {
                    null
                }
            val timeline = CycleTimeline(prediction.cycleLength, latest.length, fertileDays)

            val phase =
                when {
                    currentPeriod != null -> CyclePhase.MENSTRUAL

                    // No fertile window means ovulation estimates don't apply (birth control);
                    // past the expected day, where the cycle stands is unknown.
                    fertileDays == null || prediction.daysLate > 0 -> null

                    cycleDay < fertileDays.first -> CyclePhase.FOLLICULAR

                    cycleDay <= fertileDays.last -> CyclePhase.OVULATORY

                    else -> CyclePhase.LUTEAL
                }

            val daysSincePeriodEnded = ChronoUnit.DAYS.between(latest.end, today)
            val canExtend = currentPeriod == null && daysSincePeriodEnded <= CycleHistory.MAX_BRIDGED_GAP_DAYS + 1

            val nextFertileWindow =
                if (settings.goal.showsOvulationEstimates) {
                    prediction
                        .fertileWindows(count = 2, lutealPhaseLength = settings.lutealPhaseLength)
                        .firstOrNull { !it.window.end.isBefore(today) }
                } else {
                    null
                }

            return CycleSnapshot(
                today,
                status,
                prediction,
                currentPeriod,
                cycleDay,
                phase,
                timeline,
                canExtend,
                nextFertileWindow,
            )
        }
    }
}
