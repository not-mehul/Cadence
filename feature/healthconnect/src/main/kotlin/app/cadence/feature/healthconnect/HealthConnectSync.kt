package app.cadence.feature.healthconnect

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import app.cadence.core.data.repository.DayEntryRepository
import app.cadence.core.data.repository.PeriodRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

enum class HealthConnectAvailability { UNAVAILABLE, NEEDS_UPDATE, AVAILABLE }

private const val TAG = "CadenceHealthConnect"

/**
 * Opt-in Health Connect support (docs/PLAN.md Phase 7). While Cadence holds the permissions,
 * it mirrors its period days there; the user can also copy in periods other apps logged.
 * Turning it off revokes the permissions, so "on" simply means "permitted".
 */
@Singleton
class HealthConnectSync
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val periods: PeriodRepository,
        private val entries: DayEntryRepository,
        private val clock: Clock,
    ) {
        /** Replaced in tests. */
        internal var storeFactory: () -> HealthStore? = {
            client()?.let { HealthConnectStore(context, it) }
        }
        internal var permissionsGranted: suspend () -> Boolean = {
            client()?.permissionController?.getGrantedPermissions()?.containsAll(PERMISSIONS) == true
        }

        private val mutex = Mutex()
        private val scope =
            CoroutineScope(
                SupervisorJob() + Dispatchers.Default +
                    CoroutineExceptionHandler { _, e -> Log.w(TAG, "Sync failed", e) },
            )
        private var started = false

        fun availability(): HealthConnectAvailability =
            when (HealthConnectClient.getSdkStatus(context)) {
                HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.NEEDS_UPDATE
                else -> HealthConnectAvailability.UNAVAILABLE
            }

        private fun client(): HealthConnectClient? =
            if (availability() ==
                HealthConnectAvailability.AVAILABLE
            ) {
                HealthConnectClient.getOrCreate(context)
            } else {
                null
            }

        suspend fun isConnected(): Boolean = permissionsGranted()

        /** Stops sharing: Health Connect forgets Cadence's permissions. Records already written stay until deleted there. */
        suspend fun disconnect() {
            client()?.permissionController?.revokeAllPermissions()
        }

        /** Makes Health Connect match Cadence's period days. No-op unless permitted. */
        suspend fun mirror() =
            mutex.withLock {
                if (!permissionsGranted()) return@withLock
                val store = storeFactory() ?: return@withLock
                val (flowIds, periodIds) = store.ownRecordIds()
                val plan = HealthMirror.plan(periods.flowByDay.first(), flowIds, periodIds)
                store.delete(plan.staleFlowIds, plan.stalePeriodIds)
                // A newer version each time, so Health Connect replaces what's there.
                store.write(plan.flows, plan.periods, version = clock.millis())
            }

        /** Copies flow days from other apps into days Cadence has nothing for. Returns how many. */
        suspend fun importFromOtherApps(): Int =
            mutex.withLock {
                if (!permissionsGranted()) return@withLock 0
                val store = storeFactory() ?: return@withLock 0
                val toAdd = HealthMirror.importable(store.otherAppsFlows(), periods.flowByDay.first())
                toAdd.forEach { (date, flow) -> entries.save(entries.observe(date).first().copy(flow = flow)) }
                toAdd.size
            }

        /** Mirrors changes while the app runs. */
        @OptIn(FlowPreview::class)
        @Synchronized
        fun start() {
            if (started) return
            started = true
            scope.launch {
                periods.flowByDay
                    .distinctUntilChanged()
                    .debounce(DEBOUNCE_MS)
                    .collect { mirror() }
            }
        }

        companion object {
            /** Menstruation flow and periods share one read and one write permission. */
            val PERMISSIONS: Set<String> =
                setOf(
                    HealthPermission.getReadPermission(MenstruationFlowRecord::class),
                    HealthPermission.getWritePermission(MenstruationFlowRecord::class),
                    HealthPermission.getReadPermission(MenstruationPeriodRecord::class),
                    HealthPermission.getWritePermission(MenstruationPeriodRecord::class),
                )

            private const val DEBOUNCE_MS = 2_000L
        }
    }
