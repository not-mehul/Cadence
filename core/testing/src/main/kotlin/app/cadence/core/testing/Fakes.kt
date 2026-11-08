package app.cadence.core.testing

import app.cadence.core.data.repository.CycleAnnotationRepository
import app.cadence.core.data.repository.DataEraser
import app.cadence.core.data.repository.DayEntryRepository
import app.cadence.core.data.repository.PeriodRepository
import app.cadence.core.data.repository.SettingsRepository
import app.cadence.core.data.time.DateSource
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.model.MenstrualFlow
import app.cadence.core.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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

/** Stores entries in memory; flow changes are mirrored into [periods] like the real shared table. */
class FakeDayEntryRepository(
    private val periods: FakePeriodRepository = FakePeriodRepository(),
) : DayEntryRepository {
    val entries = MutableStateFlow<Map<LocalDate, DayEntry>>(emptyMap())

    override fun observe(date: LocalDate): Flow<DayEntry> =
        combine(entries, periods.flowByDay) { all, flows ->
            (all[date] ?: DayEntry(date)).copy(flow = flows[date])
        }

    override suspend fun save(entry: DayEntry) {
        entries.update { if (entry.isEmpty) it - entry.date else it + (entry.date to entry) }
        periods.flowByDay.update { flows ->
            val flow = entry.flow
            if (flow == null) flows - entry.date else flows + (entry.date to flow)
        }
    }

    override val painByDay: Flow<Map<LocalDate, Int>> =
        entries.map { all -> all.values.mapNotNull { e -> e.painLevel?.let { e.date to it } }.toMap() }

    override fun observeDatesWithDetails(
        from: LocalDate,
        to: LocalDate,
    ): Flow<Set<LocalDate>> =
        entries.map { all ->
            all.values
                .filter { it.hasDetails && it.date in from..to }
                .map { it.date }
                .toSet()
        }
}

class FakeCycleAnnotationRepository : CycleAnnotationRepository {
    override val excludedStarts = MutableStateFlow<Set<LocalDate>>(emptySet())

    override suspend fun setExcluded(
        cycleStart: LocalDate,
        excluded: Boolean,
    ) {
        excludedStarts.update { if (excluded) it + cycleStart else it - cycleStart }
    }
}
