package app.cadence.feature.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cadence.core.data.repository.DayEntryRepository
import app.cadence.core.data.repository.SettingsRepository
import app.cadence.core.domain.log.DayEntry
import app.cadence.core.domain.log.LogCategory
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
    ) : DayLogUiState {
        fun shows(category: LogCategory) = category !in hiddenCategories
    }
}

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
    ) : ViewModel() {
        private val draft = MutableStateFlow<DayEntry?>(null)
        private var pendingNoteSave: Job? = null

        val uiState: StateFlow<DayLogUiState> =
            combine(draft, settings.settings) { entry, s ->
                if (entry == null) {
                    DayLogUiState.Loading
                } else {
                    DayLogUiState.Ready(entry, entry.date == todayProvider.today(), s.hiddenCategories)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayLogUiState.Loading)

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

        /** Saves an unsaved note immediately. */
        fun flush() {
            val pending = pendingNoteSave ?: return
            if (pending.isActive) {
                pending.cancel()
                draft.value?.let { entry -> viewModelScope.launch { entries.save(entry) } }
            }
            pendingNoteSave = null
        }

        private companion object {
            const val NOTE_SAVE_DELAY_MS = 400L
        }
    }
