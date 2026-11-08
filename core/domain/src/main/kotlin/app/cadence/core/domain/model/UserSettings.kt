package app.cadence.core.domain.model

import app.cadence.core.domain.contraception.ContraceptiveSchedule
import app.cadence.core.domain.log.LogCategory
import app.cadence.core.domain.reminders.ReminderSettings
import app.cadence.core.domain.security.SecuritySettings

enum class ThemePreference { SYSTEM, LIGHT, DARK }

/** Everything the user configures. Stored in the encrypted database. */
data class UserSettings(
    val goal: Goal = Goal.TRACK,
    val cycleDefaults: CycleDefaults = CycleDefaults(),
    val lutealPhaseLength: Int = DEFAULT_LUTEAL_PHASE_LENGTH,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val onboardingComplete: Boolean = false,
    /** Log sections the user chose not to see. Flow can't be hidden. */
    val hiddenCategories: Set<LogCategory> = emptySet(),
    val reminders: ReminderSettings = ReminderSettings(),
    val security: SecuritySettings = SecuritySettings(),
    /** Kept when switching to cycle tracking, so switching back loses nothing. */
    val contraception: ContraceptiveSchedule? = null,
) {
    /** The schedule Cadence follows: only in birth control mode. */
    val activeSchedule: ContraceptiveSchedule?
        get() = contraception.takeIf { goal == Goal.BIRTH_CONTROL }

    companion object {
        /** Days from ovulation to the next period. Fairly constant between people, unlike cycle length. */
        const val DEFAULT_LUTEAL_PHASE_LENGTH = 14
    }
}
