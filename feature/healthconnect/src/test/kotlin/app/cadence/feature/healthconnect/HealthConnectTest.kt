package app.cadence.feature.healthconnect

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.test.core.app.ApplicationProvider
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.Period
import app.cadence.core.testing.FakeDayEntryRepository
import app.cadence.core.testing.FakePeriodRepository
import app.cadence.core.ui.theme.CadenceTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun d(day: Int) = LocalDate.of(2026, 3, day)

class HealthMirrorTest {
    @Test
    fun `plans flows and periods, skipping spotting, and deletes what's gone`() {
        val flows =
            mapOf(
                d(1) to MenstrualFlow.HEAVY,
                d(2) to MenstrualFlow.MEDIUM,
                d(3) to MenstrualFlow.LIGHT,
                d(10) to MenstrualFlow.SPOTTING,
            )
        val plan =
            HealthMirror.plan(
                flows,
                existingFlowIds = setOf("cadence-flow-2026-03-02", "cadence-flow-2026-02-27"),
                existingPeriodIds = setOf("cadence-period-2026-03-01", "cadence-period-2026-02-27"),
            )

        assertEquals(setOf(d(1), d(2), d(3)), plan.flows.keys)
        assertEquals(listOf(Period(d(1), d(3))), plan.periods)
        assertEquals(setOf("cadence-flow-2026-02-27"), plan.staleFlowIds)
        assertEquals(setOf("cadence-period-2026-02-27"), plan.stalePeriodIds)
    }

    @Test
    fun `periods too long for Health Connect are left out`() {
        val long = (0L until 40L).associate { LocalDate.of(2026, 1, 1).plusDays(it) to MenstrualFlow.MEDIUM }
        assertEquals(emptyList(), HealthMirror.plan(long, emptySet(), emptySet()).periods)
    }

    @Test
    fun `imports only days Cadence has nothing for`() {
        val theirs = mapOf(d(1) to MenstrualFlow.HEAVY, d(2) to MenstrualFlow.LIGHT)
        val ours = mapOf(d(1) to MenstrualFlow.MEDIUM)
        assertEquals(mapOf(d(2) to MenstrualFlow.LIGHT), HealthMirror.importable(theirs, ours))
    }

    @Test
    fun `flow levels map both ways, and unknown flow counts as medium`() {
        listOf(MenstrualFlow.LIGHT, MenstrualFlow.MEDIUM, MenstrualFlow.HEAVY).forEach {
            assertEquals(it, HealthConnectStore.fromHealth(HealthConnectStore.toHealth(it)))
        }
        assertEquals(MenstrualFlow.MEDIUM, HealthConnectStore.fromHealth(MenstruationFlowRecord.FLOW_UNKNOWN))
    }
}

/** An in-memory Health Connect for the sync logic. */
private class FakeHealthStore : HealthStore {
    val flows = mutableMapOf<String, Pair<LocalDate, MenstrualFlow>>()
    val periods = mutableMapOf<String, Period>()
    var others = emptyMap<LocalDate, MenstrualFlow>()
    var writes = 0

    override suspend fun ownRecordIds() = flows.keys.toSet() to periods.keys.toSet()

    override suspend fun write(
        flows: Map<LocalDate, MenstrualFlow>,
        periods: List<Period>,
        version: Long,
    ) {
        writes++
        flows.forEach { (date, flow) -> this.flows[HealthMirror.flowId(date)] = date to flow }
        periods.forEach { this.periods[HealthMirror.periodId(it.start)] = it }
    }

    override suspend fun delete(
        flowIds: Set<String>,
        periodIds: Set<String>,
    ) {
        flowIds.forEach(flows::remove)
        periodIds.forEach(periods::remove)
    }

    override suspend fun otherAppsFlows() = others
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class HealthConnectSyncTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val periods = FakePeriodRepository(mapOf(d(1) to MenstrualFlow.HEAVY, d(2) to MenstrualFlow.MEDIUM))
    private val entries = FakeDayEntryRepository(periods)
    private val store = FakeHealthStore()
    private var permitted = true
    private val sync =
        HealthConnectSync(context, periods, entries, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)).also {
            it.storeFactory = { store }
            it.permissionsGranted = { permitted }
        }

    @Test
    fun `mirrors period days and follows edits`() =
        runTest {
            sync.mirror()
            assertEquals(setOf("cadence-flow-2026-03-01", "cadence-flow-2026-03-02"), store.flows.keys)
            assertEquals(Period(d(1), d(2)), store.periods.values.single())

            // The second day is cleared in Cadence; Health Connect follows.
            periods.flowByDay.value = mapOf(d(1) to MenstrualFlow.HEAVY)
            sync.mirror()
            assertEquals(setOf("cadence-flow-2026-03-01"), store.flows.keys)
            assertEquals(Period(d(1), d(1)), store.periods.values.single())
        }

    @Test
    fun `nothing happens without permission`() =
        runTest {
            permitted = false
            sync.mirror()
            assertEquals(0, store.writes)
            assertEquals(0, sync.importFromOtherApps())
        }

    @Test
    fun `copies other apps' days into empty days only`() =
        runTest {
            store.others = mapOf(d(2) to MenstrualFlow.LIGHT, d(3) to MenstrualFlow.LIGHT, d(4) to MenstrualFlow.HEAVY)

            assertEquals(2, sync.importFromOtherApps())

            val flows = periods.flowByDay.first()
            assertEquals(MenstrualFlow.MEDIUM, flows[d(2)])
            assertEquals(MenstrualFlow.LIGHT, flows[d(3)])
            assertEquals(MenstrualFlow.HEAVY, flows[d(4)])
        }

    @Test
    fun `health connect isn't installed under robolectric`() {
        assertEquals(HealthConnectAvailability.UNAVAILABLE, sync.availability())
        assertTrue(HealthConnectSync.PERMISSIONS.all { it.startsWith("android.permission.health.") })
        assertEquals(2, HealthConnectSync.PERMISSIONS.size)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class HealthConnectRowsTest {
    @get:Rule
    val compose = createComposeRule()

    private val changes = mutableListOf<Boolean>()
    private var imports = 0
    private var dismissed = 0

    private fun show(state: HealthConnectUiState) =
        compose.setContent {
            CadenceTheme {
                HealthConnectRows(state, { changes += it }, { imports++ }, { dismissed++ })
            }
        }

    @Test
    fun `hidden on phones without Health Connect`() {
        show(HealthConnectUiState(HealthConnectAvailability.UNAVAILABLE))
        compose.onNodeWithText("Health Connect").assertDoesNotExist()
    }

    @Test
    fun `asks for an update when Health Connect is too old`() {
        show(HealthConnectUiState(HealthConnectAvailability.NEEDS_UPDATE))
        compose.onNodeWithText("Update the Health Connect app", substring = true).assertExists()
        compose.onNodeWithText("Share with Health Connect").assertDoesNotExist()
    }

    @Test
    fun `turning sharing on, then copying periods once connected`() {
        show(HealthConnectUiState(HealthConnectAvailability.AVAILABLE))
        compose.onNodeWithText("Copy periods from other apps").assertDoesNotExist()
        compose.onNodeWithText("Share with Health Connect").performClick()
        assertEquals(listOf(true), changes)
    }

    @Test
    fun `connected rows and the result message`() {
        show(
            HealthConnectUiState(
                HealthConnectAvailability.AVAILABLE,
                connected = true,
                message = HealthConnectMessage.Imported(3),
            ),
        )
        compose.onNodeWithText("Copied 3 period days from Health Connect.").assertExists()
        compose.onNodeWithText("OK").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun `the rationale explains what's shared`() {
        var closed = false
        compose.setContent { CadenceTheme { HealthPermissionsRationale(onClose = { closed = true }) } }
        compose.onNodeWithText("Cadence itself has no internet access", substring = true).assertExists()
        compose.onNodeWithText("OK").performClick()
        assertTrue(closed)
    }
}
