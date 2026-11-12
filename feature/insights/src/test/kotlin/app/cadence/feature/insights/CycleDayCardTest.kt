package app.cadence.feature.insights

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.cadence.core.domain.patterns.CycleDayHeatmap
import app.cadence.core.domain.patterns.HeatmapRow
import app.cadence.core.ui.theme.CadenceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rGB-w411dp-h1600dp")
class CycleDayCardTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `day ranges read naturally`() {
        assertEquals("1–3, 21", dayRanges(listOf(21, 2, 1, 3)))
        assertEquals("5", dayRanges(listOf(5)))
        assertEquals("", dayRanges(emptyList()))
    }

    @Test
    fun `each row reads out when it usually comes`() {
        val heatmap =
            CycleDayHeatmap(
                cycles = 4,
                days = 28,
                rows =
                    listOf(
                        HeatmapRow("cramps", listOf(4, 3, 1) + List(25) { 0 }),
                        HeatmapRow("tag:2", List(20) { 0 } + listOf(2) + List(7) { 0 }),
                    ),
                periodDays = 5,
            )
        compose.setContent { CadenceTheme { CycleDayCard(heatmap, mapOf(2L to "Travel")) } }

        compose.onNodeWithText("By cycle day").assertExists()
        compose.onNodeWithText("What you logged on each day of your last 4 cycles.").assertExists()
        compose.onNode(hasContentDescription("Cramps: most often on cycle days 1–2")).assertExists()
        compose.onNode(hasContentDescription("Travel: most often on cycle days 21")).assertExists()
    }
}
