package app.cadence.core.domain.time

import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals

class TodayProviderTest {
    private val instant = Instant.parse("2026-11-08T23:30:00Z")

    @Test
    fun `today is the calendar date in the clock's timezone`() {
        val utc = TodayProvider.fromClock(Clock.fixed(instant, ZoneId.of("UTC")))
        assertEquals(LocalDate.of(2026, 11, 8), utc.today())
    }

    @Test
    fun `the same instant can be a different day in another timezone`() {
        val tokyo = TodayProvider.fromClock(Clock.fixed(instant, ZoneId.of("Asia/Tokyo")))
        assertEquals(LocalDate.of(2026, 11, 9), tokyo.today())
    }
}
