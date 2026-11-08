package app.cadence.feature.insights

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.cadence.core.domain.cycles.CycleHistory
import app.cadence.core.domain.cycles.CycleInsights
import app.cadence.core.domain.cycles.CyclePredictor
import app.cadence.core.domain.cycles.HealthFlag
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.ui.theme.CadenceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rGB-w411dp-h1600dp")
class InsightsContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 4, 10)
    private var toggled: Pair<LocalDate, Boolean>? = null

    private fun state(
        starts: List<LocalDate>,
        flags: List<HealthFlag> = emptyList(),
    ): InsightsUiState.Ready {
        val days =
            starts.fold(emptyMap<LocalDate, MenstrualFlow>()) { acc, s ->
                acc +
                    (0L until 5L).associate { s.plusDays(it) to MenstrualFlow.MEDIUM }
            }
        val history = CycleHistory.from(days)
        return InsightsUiState.Ready(
            stats = CycleInsights.stats(history, today),
            prediction = CyclePredictor.predict(history, CycleDefaults(), today),
            cycles = history.cycles.map { CycleRow(it.start, it.length, 5, it.excluded) },
            currentCycle = starts.lastOrNull()?.let { CurrentCycle(it, 3) },
            flags = flags,
        )
    }

    private fun show(state: InsightsUiState.Ready) {
        compose.setContent {
            CadenceTheme {
                InsightsContent(state, onIncludeChange = {
                    d,
                    inc,
                    ->
                    toggled = d to inc
                })
            }
        }
    }

    @Test
    fun `empty with nothing logged`() {
        show(state(emptyList()))
        compose.onNodeWithText("Nothing to show yet").assertExists()
    }

    @Test
    fun `first cycle in progress`() {
        show(state(listOf(LocalDate.of(2026, 4, 8))))
        compose.onNodeWithText("Your first cycle is in progress").assertExists()
        compose.onNodeWithText("Current cycle").performScrollTo().assertExists()
    }

    @Test
    fun `many cycles show tiles, chart and history`() {
        show(
            state(
                listOf(
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 1, 29),
                    LocalDate.of(2026, 2, 28),
                    LocalDate.of(2026, 3, 28),
                ),
            ),
        )

        compose.onNodeWithText("Average cycle").assertExists()
        compose.onNodeWithText("29 days").assertExists()
        compose.onNodeWithText("Prediction confidence").assertExists()
        compose.onNodeWithText("High").assertExists()
        compose.onNode(hasContentDescription("Cycle lengths, oldest to newest: 28, 30, 28.")).assertExists()
        compose.onNodeWithText("Cycle starting 28 Feb: 28 days").assertExists()
        compose.onNodeWithText("28 Feb to 27 Mar").performScrollTo().assertExists()
    }

    @Test
    fun `turning a cycle off reports it`() {
        show(state(listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 29), LocalDate.of(2026, 2, 28))))
        compose.onNodeWithText("1 Jan to 28 Jan").performScrollTo().performClick()
        assertEquals(LocalDate.of(2026, 1, 1) to false, toggled)
    }

    @Test
    fun `health flags with their footer`() {
        show(
            state(
                listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 29)),
                flags = listOf(HealthFlag.LongPeriod(10, LocalDate.of(2026, 1, 1)), HealthFlag.NoRecentPeriod(95)),
            ),
        )
        compose.onNodeWithText("Worth mentioning to a clinician").performScrollTo().assertExists()
        compose.onNodeWithText("lasted 10 days", substring = true).assertExists()
        compose.onNodeWithText("It's been 95 days", substring = true).assertExists()
        compose.onNodeWithText("not a diagnosis", substring = true).assertExists()
    }
}
