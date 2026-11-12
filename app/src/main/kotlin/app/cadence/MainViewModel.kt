package app.cadence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cadence.core.data.repository.SettingsRepository
import app.cadence.core.data.security.AppLock
import app.cadence.core.data.security.DatabaseRecovery
import app.cadence.core.domain.model.ThemePreference
import app.cadence.core.domain.security.SecuritySettings
import app.cadence.core.ui.quick.QuickAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MainUiState {
    data object Loading : MainUiState

    /** The database can't be opened on this phone (e.g. its key was lost). */
    data object DataUnavailable : MainUiState

    /**
     * The unreadable data was erased. The app must restart so the database (a process-wide
     * singleton that may hold the old key) is opened afresh with a new key.
     */
    data object RestartRequired : MainUiState

    data class Ready(
        val theme: ThemePreference,
        val onboardingComplete: Boolean,
        val security: SecuritySettings,
        /** Locked: show the lock screen and nothing else. */
        val locked: Boolean,
    ) : MainUiState
}

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        settings: SettingsRepository,
        private val appLock: AppLock,
        private val recovery: DatabaseRecovery,
    ) : ViewModel() {
        private val erased = MutableStateFlow(false)

        @OptIn(ExperimentalCoroutinesApi::class)
        val uiState: StateFlow<MainUiState> =
            erased
                .flatMapLatest { done ->
                    if (done) return@flatMapLatest flowOf(MainUiState.RestartRequired)
                    combine(settings.settings, appLock.locked) { s, locked ->
                        MainUiState.Ready(
                            s.theme,
                            s.onboardingComplete,
                            s.security,
                            locked =
                                s.security.lockEnabled && locked,
                        )
                    }.map<MainUiState.Ready, MainUiState> { it }
                        .catch { e ->
                            if (DatabaseRecovery.isUnreadableData(e)) emit(MainUiState.DataUnavailable) else throw e
                        }
                }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)

        private val pendingQuickAction = MutableStateFlow<QuickAction?>(null)

        /** From a shortcut, the Quick Settings tile or the widget; waits here until the app is unlocked. */
        val quickAction: StateFlow<QuickAction?> = pendingQuickAction

        fun onQuickAction(action: QuickAction) {
            pendingQuickAction.value = action
        }

        fun quickActionHandled() {
            pendingQuickAction.value = null
        }

        fun unlock() = appLock.unlock()

        /** Erases the unreadable data; the UI then restarts the app with a fresh database. */
        fun startOver() {
            viewModelScope.launch {
                recovery.startOver()
                erased.value = true
            }
        }
    }
