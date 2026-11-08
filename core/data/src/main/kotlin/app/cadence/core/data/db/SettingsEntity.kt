package app.cadence.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.cadence.core.domain.log.LogCategory
import app.cadence.core.domain.model.CycleDefaults
import app.cadence.core.domain.model.Goal
import app.cadence.core.domain.model.ThemePreference
import app.cadence.core.domain.model.UserSettings

/**
 * Single-row table holding [UserSettings]. Settings live in the encrypted database (not
 * plain DataStore) because some, like using hormonal birth control, are sensitive.
 */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "goal")
    val goal: Goal,
    @ColumnInfo(name = "cycle_length")
    val cycleLength: Int,
    @ColumnInfo(name = "period_length")
    val periodLength: Int,
    @ColumnInfo(name = "luteal_phase_length")
    val lutealPhaseLength: Int,
    @ColumnInfo(name = "theme")
    val theme: ThemePreference,
    @ColumnInfo(name = "onboarding_complete")
    val onboardingComplete: Boolean,
    /** Comma-separated [LogCategory] names. Added in schema v3. */
    @ColumnInfo(name = "hidden_categories", defaultValue = "")
    val hiddenCategories: String = "",
) {
    fun toModel(): UserSettings =
        UserSettings(
            goal = goal,
            cycleDefaults = CycleDefaults(cycleLength, periodLength),
            lutealPhaseLength = lutealPhaseLength,
            theme = theme,
            onboardingComplete = onboardingComplete,
            hiddenCategories = LogCategory.parseSet(hiddenCategories),
        )

    companion object {
        const val SINGLETON_ID = 0

        fun from(settings: UserSettings) =
            SettingsEntity(
                goal = settings.goal,
                cycleLength = settings.cycleDefaults.cycleLength,
                periodLength = settings.cycleDefaults.periodLength,
                lutealPhaseLength = settings.lutealPhaseLength,
                theme = settings.theme,
                onboardingComplete = settings.onboardingComplete,
                hiddenCategories = LogCategory.formatSet(settings.hiddenCategories),
            )
    }
}
