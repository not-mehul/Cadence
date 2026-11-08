package app.cadence.core.testing

import app.cadence.core.data.backup.BackupData
import app.cadence.core.data.backup.BackupRepository
import app.cadence.core.data.backup.ImportMode
import app.cadence.core.data.repository.ContraceptionRepository
import app.cadence.core.data.repository.CustomTagRepository
import app.cadence.core.data.repository.CycleAnnotationRepository
import app.cadence.core.data.repository.DataEraser
import app.cadence.core.data.repository.DayEntryRepository
import app.cadence.core.data.repository.MedicationRepository
import app.cadence.core.data.repository.PeriodRepository
import app.cadence.core.data.repository.SettingsRepository
import app.cadence.core.data.time.DateSource
import app.cadence.core.domain.contraception.DoseStatus
import app.cadence.core.domain.log.CustomTag
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.medication.Medication
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

    override val patternItemsByDay: Flow<Map<LocalDate, Set<String>>> =
        entries.map { all -> all.values.associate { it.date to it.patternItems() }.filterValues { it.isNotEmpty() } }

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

/** Holds one in-memory snapshot; [restore] records what it was given. */
class FakeBackupRepository(
    var data: BackupData = BackupData(UserSettings(onboardingComplete = true), emptyList(), emptySet()),
) : BackupRepository {
    val restored = mutableListOf<Pair<BackupData, ImportMode>>()

    /** When set, [restore] throws it, like a database failure. */
    var failure: RuntimeException? = null

    override suspend fun snapshot(): BackupData = data

    override suspend fun restore(
        data: BackupData,
        mode: ImportMode,
    ) {
        failure?.let { throw it }
        restored += data to mode
        this.data = data
    }
}

class FakeContraceptionRepository : ContraceptionRepository {
    override val doses = MutableStateFlow<Map<LocalDate, DoseStatus>>(emptyMap())

    override suspend fun record(
        date: LocalDate,
        status: DoseStatus?,
    ) {
        doses.update { if (status == null) it - date else it + (date to status) }
    }
}

class FakeMedicationRepository : MedicationRepository {
    override val medications = MutableStateFlow<List<Medication>>(emptyList())
    override val doses = MutableStateFlow<Map<LocalDate, Set<Long>>>(emptyMap())
    private var nextId = 1L

    override suspend fun save(medication: Medication): Long {
        val saved = if (medication.id == 0L) medication.copy(id = nextId++) else medication
        medications.update { list -> (list.filterNot { it.id == saved.id } + saved).sortedBy { it.name.lowercase() } }
        return saved.id
    }

    override suspend fun delete(id: Long) {
        medications.update { list -> list.filterNot { it.id == id } }
        doses.update { all -> all.mapValues { it.value - id }.filterValues { it.isNotEmpty() } }
    }

    override suspend fun setTaken(
        date: LocalDate,
        medicationId: Long,
        taken: Boolean,
    ) {
        doses.update { all ->
            val day = all[date].orEmpty().let { if (taken) it + medicationId else it - medicationId }
            if (day.isEmpty()) all - date else all + (date to day)
        }
    }
}

/** Tags only; removing a deleted tag from days is left to the caller's [FakeDayEntryRepository]. */
class FakeCustomTagRepository(
    private val entries: FakeDayEntryRepository? = null,
) : CustomTagRepository {
    override val tags = MutableStateFlow<List<CustomTag>>(emptyList())
    private var nextId = 1L

    override suspend fun create(label: String): Long {
        val clean = CustomTag(0, label.trim()).label
        tags.value.firstOrNull { it.label.equals(clean, ignoreCase = true) }?.let { return it.id }
        val tag = CustomTag(nextId++, clean)
        tags.update { (it + tag).sortedBy { t -> t.label.lowercase() } }
        return tag.id
    }

    override suspend fun rename(
        id: Long,
        label: String,
    ) {
        tags.update { list ->
            list
                .map {
                    if (it.id ==
                        id
                    ) {
                        CustomTag(id, label.trim())
                    } else {
                        it
                    }
                }.sortedBy { it.label.lowercase() }
        }
    }

    override suspend fun delete(id: Long) {
        tags.update { list -> list.filterNot { it.id == id } }
        entries?.entries?.update { all -> all.mapValues { (_, e) -> e.copy(tags = e.tags - id) } }
    }
}
