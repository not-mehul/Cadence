package app.cadence.feature.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cadence.core.data.repository.ContraceptionRepository
import app.cadence.core.data.repository.CustomTagRepository
import app.cadence.core.data.repository.DayEntryRepository
import app.cadence.core.data.repository.MedicationRepository
import app.cadence.core.data.repository.SettingsRepository
import app.cadence.core.domain.contraception.DoseAction
import app.cadence.core.domain.contraception.DoseStatus
import app.cadence.core.domain.log.CustomTag
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.log.LogCategory
import app.cadence.core.domain.medication.Medication
import app.cadence.core.domain.time.TodayProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed interface DayLogUiState {
    data object Loading : DayLogUiState

    data class Ready(
        val entry: DayEntry,
        val isToday: Boolean,
        val hiddenCategories: Set<LogCategory>,
        /** The birth control dose scheduled for the day, on a pill, patch or ring schedule. */
        val dose: DoseAction? = null,
        val doseStatus: DoseStatus? = null,
        /** Active medications, plus stopped ones taken on this day. */
        val medications: List<LoggedMedication> = emptyList(),
        /** The user's own tags, to pick from. */
        val tags: List<CustomTag> = emptyList(),
    ) : DayLogUiState {
        fun shows(category: LogCategory) = category !in hiddenCategories
    }
}

data class LoggedMedication(
    val medication: Medication,
    val taken: Boolean,
)

/**
 * Edits one day's log. Taps save straight away; typing in the note saves after a short
 * pause, and [flush] saves anything pending when the sheet closes.
 */
@HiltViewModel
class DayLogViewModel
    @Inject
    constructor(
        private val entries: DayEntryRepository,
        settings: SettingsRepository,
        private val todayProvider: TodayProvider,
        private val contraception: ContraceptionRepository,
        private val medications: MedicationRepository,
        private val tags: CustomTagRepository,
    ) : ViewModel() {
        private val draft = MutableStateFlow<DayEntry?>(null)
        private var pendingNoteSave: Job? = null

        private val tracking =
            combine(contraception.doses, medications.medications, medications.doses, tags.tags, ::Tracking)

        val uiState: StateFlow<DayLogUiState> =
            combine(draft, settings.settings, tracking) { entry, s, t ->
                if (entry == null) {
                    DayLogUiState.Loading
                } else {
                    val date = entry.date
                    val taken = t.medicationDoses[date].orEmpty()
                    DayLogUiState.Ready(
                        entry = entry,
                        isToday = date == todayProvider.today(),
                        hiddenCategories = s.hiddenCategories,
                        dose = s.activeSchedule?.actionOn(date),
                        doseStatus = t.doses[date],
                        medications =
                            t.medications
                                .filter { it.active || it.id in taken }
                                .map { LoggedMedication(it, it.id in taken) },
                        tags = t.tags,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayLogUiState.Loading)

        /** Records (or with null, clears) the day's birth control dose. */
        fun recordDose(status: DoseStatus?) {
            val date = draft.value?.date ?: return
            viewModelScope.launch { contraception.record(date, status) }
        }

        fun setMedicationTaken(
            id: Long,
            taken: Boolean,
        ) {
            val date = draft.value?.date ?: return
            viewModelScope.launch { medications.setTaken(date, id, taken) }
        }

        /** Creates a tag (or finds one with the same name) and adds it to the day. */
        fun createTag(label: String) {
            if (label.isBlank()) return
            viewModelScope.launch {
                val id = tags.create(label)
                edit { if (id in it.tags) it else it.toggleTag(id) }
            }
        }

        fun load(date: LocalDate) {
            if (draft.value?.date == date) return
            flush()
            draft.value = null
            viewModelScope.launch { draft.value = entries.observe(date).first() }
        }

        /** Applies a change and saves it. */
        fun edit(transform: (DayEntry) -> DayEntry) {
            val updated = draft.value?.let(transform) ?: return
            draft.value = updated
            pendingNoteSave?.cancel()
            viewModelScope.launch { entries.save(updated) }
        }

        fun editNote(text: String) {
            val current = draft.value ?: return
            val note = text.take(DayEntry.MAX_NOTE_LENGTH)
            draft.value = current.copy(note = note.ifBlank { null })
            pendingNoteSave?.cancel()
            pendingNoteSave =
                viewModelScope.launch {
                    delay(NOTE_SAVE_DELAY_MS)
                    draft.value?.let { entries.save(it) }
                }
        }

        /**
         * The sheet closed: saves an unsaved note and forgets the day, so opening it again reads it
         * afresh. Without this, a day changed elsewhere in between ("Period started", the calendar)
         * would come back stale, and the next tap would write the old log over the new one.
         */
        fun close() {
            flush()
            draft.value = null
        }

        /** Saves an unsaved note immediately. */
        private fun flush() {
            val pending = pendingNoteSave ?: return
            if (pending.isActive) {
                pending.cancel()
                draft.value?.let { entry -> viewModelScope.launch { entries.save(entry) } }
            }
            pendingNoteSave = null
        }

        private class Tracking(
            val doses: Map<LocalDate, DoseStatus>,
            val medications: List<Medication>,
            val medicationDoses: Map<LocalDate, Set<Long>>,
            val tags: List<CustomTag>,
        )

        private companion object {
            const val NOTE_SAVE_DELAY_MS = 400L
        }
    }
