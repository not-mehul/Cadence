package app.cadence.feature.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.Period
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass

/** The few Health Connect operations Cadence needs; a fake stands in for it in tests. */
interface HealthStore {
    /** Client record ids of the flow and period records Cadence wrote. */
    suspend fun ownRecordIds(): Pair<Set<String>, Set<String>>

    suspend fun write(
        flows: Map<LocalDate, MenstrualFlow>,
        periods: List<Period>,
        version: Long,
    )

    suspend fun delete(
        flowIds: Set<String>,
        periodIds: Set<String>,
    )

    /** Flow days logged by other apps. */
    suspend fun otherAppsFlows(): Map<LocalDate, MenstrualFlow>
}

/** [HealthStore] on the real Health Connect, in the phone's current time zone. */
class HealthConnectStore(
    private val context: Context,
    private val client: HealthConnectClient,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : HealthStore {
    private val ownOrigin = DataOrigin(context.packageName)

    override suspend fun ownRecordIds(): Pair<Set<String>, Set<String>> =
        readAll(MenstruationFlowRecord::class, setOf(ownOrigin)).mapNotNull { it.metadata.clientRecordId }.toSet() to
            readAll(MenstruationPeriodRecord::class, setOf(ownOrigin)).mapNotNull { it.metadata.clientRecordId }.toSet()

    override suspend fun write(
        flows: Map<LocalDate, MenstrualFlow>,
        periods: List<Period>,
        version: Long,
    ) {
        val zone = zone()
        val records =
            flows.map { (date, flow) ->
                val time = date.atStartOfDay(zone).toInstant()
                MenstruationFlowRecord(
                    time = time,
                    zoneOffset = zone.rules.getOffset(time),
                    metadata = Metadata.manualEntry(HealthMirror.flowId(date), version),
                    flow = toHealth(flow),
                )
            } +
                periods.map { period ->
                    val start = period.start.atStartOfDay(zone).toInstant()
                    val end =
                        period.end
                            .plusDays(1)
                            .atStartOfDay(zone)
                            .toInstant()
                    MenstruationPeriodRecord(
                        startTime = start,
                        startZoneOffset = zone.rules.getOffset(start),
                        endTime = end,
                        endZoneOffset = zone.rules.getOffset(end),
                        metadata = Metadata.manualEntry(HealthMirror.periodId(period.start), version),
                    )
                }
        // Health Connect takes up to 1000 records per call.
        records.chunked(BATCH).forEach { client.insertRecords(it) }
    }

    override suspend fun delete(
        flowIds: Set<String>,
        periodIds: Set<String>,
    ) {
        if (flowIds.isNotEmpty()) client.deleteRecords(MenstruationFlowRecord::class, emptyList(), flowIds.toList())
        if (periodIds.isNotEmpty()) {
            client.deleteRecords(
                MenstruationPeriodRecord::class,
                emptyList(),
                periodIds.toList(),
            )
        }
    }

    override suspend fun otherAppsFlows(): Map<LocalDate, MenstrualFlow> {
        val zone = zone()
        return readAll(MenstruationFlowRecord::class, emptySet())
            .filter { it.metadata.dataOrigin != ownOrigin }
            .associate { record ->
                val offset = record.zoneOffset
                val date =
                    if (offset !=
                        null
                    ) {
                        record.time.atOffset(offset).toLocalDate()
                    } else {
                        record.time.atZone(zone).toLocalDate()
                    }
                date to fromHealth(record.flow)
            }
    }

    private suspend fun <T : Record> readAll(
        type: KClass<T>,
        origins: Set<DataOrigin>,
    ): List<T> {
        val all = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val response =
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = type,
                        timeRangeFilter = TimeRangeFilter.after(Instant.EPOCH),
                        dataOriginFilter = origins,
                        pageToken = pageToken,
                    ),
                )
            all += response.records
            pageToken = response.pageToken
        } while (pageToken != null)
        return all
    }

    internal companion object {
        const val BATCH = 1000

        fun toHealth(flow: MenstrualFlow): Int =
            when (flow) {
                MenstrualFlow.LIGHT -> MenstruationFlowRecord.FLOW_LIGHT

                MenstrualFlow.MEDIUM -> MenstruationFlowRecord.FLOW_MEDIUM

                MenstrualFlow.HEAVY -> MenstruationFlowRecord.FLOW_HEAVY

                // Not mirrored (see HealthMirror.plan); kept total for safety.
                MenstrualFlow.SPOTTING -> MenstruationFlowRecord.FLOW_LIGHT
            }

        fun fromHealth(flow: Int): MenstrualFlow =
            when (flow) {
                MenstruationFlowRecord.FLOW_LIGHT -> MenstrualFlow.LIGHT

                MenstruationFlowRecord.FLOW_HEAVY -> MenstrualFlow.HEAVY

                // Medium, and "unknown" flow from apps that don't record how heavy.
                else -> MenstrualFlow.MEDIUM
            }
    }
}
