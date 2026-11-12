package app.cadence.feature.healthconnect

import app.cadence.core.domain.cycles.CycleHistory
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.Period
import java.time.LocalDate

/** What to write to and delete from Health Connect so it mirrors Cadence's period days. */
data class MirrorPlan(
    val flows: Map<LocalDate, MenstrualFlow>,
    val periods: List<Period>,
    val staleFlowIds: Set<String>,
    val stalePeriodIds: Set<String>,
)

/**
 * The pure part of the Health Connect mirror. Every record Cadence
 * writes has a client record id derived from its date, so writing again updates it in place,
 * and a record whose day was cleared in Cadence can be found and deleted.
 */
object HealthMirror {
    /** Health Connect rejects menstruation periods longer than this. */
    const val MAX_PERIOD_DAYS = 31

    fun flowId(date: LocalDate) = "cadence-flow-$date"

    fun periodId(start: LocalDate) = "cadence-period-$start"

    /**
     * Spotting isn't mirrored: Health Connect keeps it as intermenstrual bleeding, which would
     * need a third permission. [existingFlowIds] and [existingPeriodIds] are the client record
     * ids Cadence wrote before.
     */
    fun plan(
        flowByDay: Map<LocalDate, MenstrualFlow>,
        existingFlowIds: Set<String>,
        existingPeriodIds: Set<String>,
    ): MirrorPlan {
        val flows = flowByDay.filterValues { it != MenstrualFlow.SPOTTING }.toSortedMap()
        val periods = CycleHistory.from(flowByDay).periods.filter { it.length <= MAX_PERIOD_DAYS }
        return MirrorPlan(
            flows = flows,
            periods = periods,
            staleFlowIds = existingFlowIds - flows.keys.map(::flowId).toSet(),
            stalePeriodIds = existingPeriodIds - periods.map { periodId(it.start) }.toSet(),
        )
    }

    /** Days other apps logged that Cadence has no flow for; Cadence's own logs always win. */
    fun importable(
        theirs: Map<LocalDate, MenstrualFlow>,
        ours: Map<LocalDate, MenstrualFlow>,
    ): Map<LocalDate, MenstrualFlow> = theirs.filterKeys { it !in ours }
}
