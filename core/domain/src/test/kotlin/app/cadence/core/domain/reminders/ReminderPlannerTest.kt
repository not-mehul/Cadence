package app.cadence.core.domain.reminders

import app.cadence.core.domain.d
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.UserSettings
import app.cadence.core.domain.periodDays
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderPlannerTest {
    // Period 1–5 March, typical 28-day cycle: next expected 29 March, likely 26 March to 1 April.
    private val days = periodDays(d(2026, 3, 1), 5)
    private val all =
        ReminderSettings(periodEnabled = true, lateEnabled = true, dailyEnabled = true, dailyTime = LocalTime.of(20, 0))

    private fun plan(
        now: LocalDateTime,
        reminders: ReminderSettings = all,
        flow: Map<java.time.LocalDate, app.cadence.core.domain.model.MenstrualFlow> = days,
    ) = ReminderPlanner
        .plan(
            flow,
            UserSettings(cycleDefaults = CycleDefaults(28, 5), reminders = reminders),
            emptySet(),
            now,
        ).associateBy { it.kind }

    @Test
    fun `nothing planned when everything is off`() {
        assertEquals(emptyMap(), plan(LocalDateTime.of(2026, 3, 10, 12, 0), ReminderSettings()))
    }

    @Test
    fun `upcoming period, late period and daily check-in`() {
        val p = plan(LocalDateTime.of(2026, 3, 10, 12, 0))

        assertEquals(
            PlannedReminder(ReminderKind.UPCOMING_PERIOD, LocalDateTime.of(2026, 3, 27, 9, 0), 2),
            p[ReminderKind.UPCOMING_PERIOD],
        )
        assertEquals(LocalDateTime.of(2026, 4, 2, 9, 0), p[ReminderKind.LATE_PERIOD]?.at)
        assertEquals(LocalDateTime.of(2026, 3, 10, 20, 0), p[ReminderKind.DAILY_LOG]?.at)
    }

    @Test
    fun `daily check-in moves to tomorrow once today's time has passed`() {
        assertEquals(
            LocalDateTime.of(2026, 3, 11, 20, 0),
            plan(LocalDateTime.of(2026, 3, 10, 20, 0))[ReminderKind.DAILY_LOG]?.at,
        )
    }

    @Test
    fun `days before and time of day are respected`() {
        val reminders = all.copy(periodDaysBefore = 5, periodTime = LocalTime.of(7, 30))
        assertEquals(
            LocalDateTime.of(2026, 3, 24, 7, 30),
            plan(LocalDateTime.of(2026, 3, 10, 12, 0), reminders)[ReminderKind.UPCOMING_PERIOD]?.at,
        )
    }

    @Test
    fun `a reminder whose moment has passed is not planned`() {
        val p = plan(LocalDateTime.of(2026, 3, 27, 9, 0))
        assertFalse(ReminderKind.UPCOMING_PERIOD in p)
        assertTrue(ReminderKind.LATE_PERIOD in p)
    }

    @Test
    fun `no period reminders while on a period or without data`() {
        val onPeriod = plan(LocalDateTime.of(2026, 3, 3, 12, 0))
        assertEquals(setOf(ReminderKind.DAILY_LOG), onPeriod.keys)

        val noData = plan(LocalDateTime.of(2026, 3, 3, 12, 0), flow = emptyMap())
        assertEquals(setOf(ReminderKind.DAILY_LOG), noData.keys)
    }

    @Test
    fun `past the expected day only the late reminder is left, and only once`() {
        val due = plan(LocalDateTime.of(2026, 3, 30, 12, 0))
        assertFalse(ReminderKind.UPCOMING_PERIOD in due)
        assertEquals(LocalDateTime.of(2026, 4, 2, 9, 0), due[ReminderKind.LATE_PERIOD]?.at)

        val alreadyLate = plan(LocalDateTime.of(2026, 4, 2, 10, 0))
        assertFalse(ReminderKind.LATE_PERIOD in alreadyLate)
    }

    @Test
    fun `settings defaults and validation`() {
        val s = ReminderSettings()
        assertFalse(s.anyEnabled)
        assertTrue(s.discreet, "discreet by default")
        assertEquals(2, s.periodDaysBefore)
        assertEquals(LocalTime.of(9, 0), ReminderSettings.DEFAULT_PERIOD_TIME)
        assertEquals(LocalTime.of(20, 0), ReminderSettings.DEFAULT_DAILY_TIME)
        assertEquals(1..7, ReminderSettings.DAYS_BEFORE_RANGE)
        assertTrue(ReminderSettings(lateEnabled = true).anyEnabled)
        assertTrue(ReminderSettings(dailyEnabled = true).anyEnabled)
        assertTrue(ReminderSettings(periodEnabled = true).anyEnabled)
        assertThrows<IllegalArgumentException> { ReminderSettings(periodDaysBefore = 0) }
        assertThrows<IllegalArgumentException> { ReminderSettings(periodDaysBefore = 8) }
    }
}
