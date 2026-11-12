package app.cadence.core.domain.time

import java.time.Clock
import java.time.LocalDate

/**
 * The single source of "today" for all cycle logic. A day is a calendar date in the
 * user's current timezone, never an instant, and tests substitute a
 * fixed clock.
 */
fun interface TodayProvider {
    fun today(): LocalDate

    companion object {
        fun fromClock(clock: Clock): TodayProvider = TodayProvider { LocalDate.now(clock) }

        /** Reads the device's current timezone on every call, so a timezone change is picked up. */
        val System: TodayProvider = TodayProvider { LocalDate.now() }
    }
}
