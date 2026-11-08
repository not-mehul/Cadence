package app.cadence.core.testing

import app.cadence.core.data.repository.DataEraser
import app.cadence.core.data.repository.PeriodRepository
import app.cadence.core.data.repository.SettingsRepository
import app.cadence.core.data.time.DateSource
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

class FakePeriodRepository(
    initial: Map<LocalDate, MenstrualFlow> = emptyMap(),
) : PeriodRepository {
    override val flowByDay = MutableStateFlow(initial)

    override suspend fun logPeriodDays(days: Collection<LocalDate>): List<LocalDate> {
        val added = days.distinct().filter { it !in flowByDay.value }.sorted()
        flowByDay.update { current -> current + added.associateWith { MenstrualFlow.MEDIUM } }
        return added
    }

    override suspend fun clearPeriodDays(days: Collection<LocalDate>) {
        flowByDay.update { it - days.toSet() }
    }
}

class FakeSettingsRepository(
    initial: UserSettings = UserSettings(onboardingComplete = true),
) : SettingsRepository {
    override val settings = MutableStateFlow(initial)

    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        settings.update(transform)
    }
}

class FakeDateSource(
    today: LocalDate,
) : DateSource {
    override val today = MutableStateFlow(today)
}

/** Resets the given fakes the way the real eraser resets the database. */
class FakeDataEraser(
    private val periods: FakePeriodRepository,
    private val settings: FakeSettingsRepository,
) : DataEraser {
    var erased = false
        private set

    override suspend fun eraseAll() {
        periods.flowByDay.value = emptyMap()
        settings.settings.value = UserSettings()
        erased = true
    }
}
