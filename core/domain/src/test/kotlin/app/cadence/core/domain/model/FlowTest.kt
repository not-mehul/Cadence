package app.cadence.core.domain.model

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals

class FlowTest {
    @ParameterizedTest(name = "{0} counts as period = {1}")
    @CsvSource(
        "SPOTTING, false",
        "LIGHT, true",
        "MEDIUM, true",
        "HEAVY, true",
    )
    fun `only light, medium and heavy flow count as period days`(
        flow: Flow,
        expected: Boolean,
    ) {
        assertEquals(expected, flow.countsAsPeriod)
    }
}
