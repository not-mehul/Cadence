package app.cadence.feature.backup

import app.cadence.core.data.backup.BackupData
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.log.Severity
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.UserSettings
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportContentTest {
    private val today = LocalDate.of(2026, 3, 20)
    private val firstStart = LocalDate.of(2025, 11, 1)

    /** Five-day periods every 28 days: 1 Nov, 29 Nov, 27 Dec, 24 Jan, 21 Feb. */
    private fun periods(count: Int = 5) =
        (0 until count).flatMap { cycle ->
            (0L until 5L).map { day -> DayEntry(firstStart.plusDays(cycle * 28L + day), flow = MenstrualFlow.MEDIUM) }
        }

    private fun build(
        entries: List<DayEntry>,
        excluded: Set<LocalDate> = emptySet(),
    ) = ReportContent.build(BackupData(UserSettings(onboardingComplete = true), entries, excluded), today)

    @Test
    fun `lists completed cycles newest first with their period length`() {
        val report = build(periods(), excluded = setOf(LocalDate.of(2025, 11, 29)))

        assertEquals(
            listOf(
                ReportContent.ReportCycle(LocalDate.of(2026, 1, 24), 28, 5, excluded = false),
                ReportContent.ReportCycle(LocalDate.of(2025, 12, 27), 28, 5, excluded = false),
                ReportContent.ReportCycle(LocalDate.of(2025, 11, 29), 28, 5, excluded = true),
                ReportContent.ReportCycle(LocalDate.of(2025, 11, 1), 28, 5, excluded = false),
            ),
            report.cycles,
        )
        assertEquals(today, report.generatedOn)
        assertEquals(firstStart, report.firstLogged)
        assertNotNull(report.prediction)
        assertEquals(emptyList(), report.flags)
    }

    @Test
    fun `keeps at most twelve cycles`() {
        val report = build(periods(count = 16))

        assertEquals(ReportContent.MAX_CYCLES, report.cycles.size)
        assertEquals(firstStart.plusDays(14 * 28L), report.cycles.first().start)
    }

    @Test
    fun `counts symptoms over the last six months, most frequent first`() {
        val old = LocalDate.of(2025, 9, 1)
        val entries =
            listOf(
                DayEntry(old, symptoms = mapOf("bloating" to Severity.SEVERE, "headache" to Severity.MILD)),
                DayEntry(today.minusDays(3), symptoms = mapOf("cramps" to Severity.MILD, "headache" to Severity.MILD)),
                DayEntry(today.minusDays(2), symptoms = mapOf("cramps" to Severity.MODERATE)),
                DayEntry(today.minusDays(1), symptoms = mapOf("cramps" to Severity.SEVERE, "acne" to Severity.MILD)),
            )

        val report = build(entries)

        assertEquals(listOf("cramps" to 3, "acne" to 1, "headache" to 1), report.topSymptoms)
        assertEquals(old, report.firstLogged)
    }

    @Test
    fun `averages pain on period days only`() {
        val entries =
            periods().map {
                when (it.date) {
                    LocalDate.of(2026, 2, 21) -> it.copy(painLevel = 6)
                    LocalDate.of(2026, 2, 22) -> it.copy(painLevel = 3)
                    else -> it
                }
            } + DayEntry(LocalDate.of(2026, 3, 1), painLevel = 9)

        val report = build(entries)

        assertEquals(4.5, report.averagePainOnPeriodDays)
        assertEquals(2, report.painDays)
    }

    @Test
    fun `an empty log still builds a report`() {
        val report = build(emptyList())

        assertNull(report.firstLogged)
        assertNull(report.averagePainOnPeriodDays)
        assertEquals(0, report.painDays)
        assertTrue(report.cycles.isEmpty())
        assertTrue(report.topSymptoms.isEmpty())
    }
}
