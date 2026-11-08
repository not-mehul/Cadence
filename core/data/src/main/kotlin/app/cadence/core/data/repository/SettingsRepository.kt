package app.cadence.core.data.repository

import app.cadence.core.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    /** Current settings; defaults (with onboarding incomplete) until first saved. */
    val settings: Flow<UserSettings>

    suspend fun update(transform: (UserSettings) -> UserSettings)
}
