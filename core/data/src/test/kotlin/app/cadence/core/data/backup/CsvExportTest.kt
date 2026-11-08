package app.cadence.core.data.backup

import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.log.Severity
import app.cadence.core.domain.model.MenstrualFlow
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class CsvExportTest {
    @Test
    fun `header, ordering and quoting`() {
        val csv =
            CsvExport.write(
                listOf(
                    DayEntry(LocalDate.of(2026, 3, 3), painLevel = 4, note = "said \"ouch\", then\nslept"),
                    DayEntry(
                        LocalDate.of(2026, 3, 2),
                        flow = MenstrualFlow.HEAVY,
                        symptoms =
                            mapOf("headache" to Severity.MILD, "acne" to Severity.SEVERE),
                        moods = setOf("sad", "calm"),
                    ),
                    DayEntry(LocalDate.of(2026, 3, 4)),
                ),
            )

        assertEquals(
            "date,flow,symptoms,moods,pain,energy,sleep,stress,sex,note\r\n" +
                "2026-03-02,heavy,acne:severe;headache:mild,calm;sad,,,,,,\r\n" +
                "2026-03-03,,,,4,,,,,\"said \"\"ouch\"\", then\nslept\"\r\n",
            csv,
        )
    }
}
