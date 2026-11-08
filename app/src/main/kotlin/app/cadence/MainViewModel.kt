package app.cadence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cadence.core.data.repository.SettingsRepository
import app.cadence.core.domain.model.ThemePreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface MainUiState {
    data object Loading : MainUiState

    data class Ready(
        val theme: ThemePreference,
        val onboardingComplete: Boolean,
    ) : MainUiState
}

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        settings: SettingsRepository,
    ) : ViewModel() {
        val uiState: StateFlow<MainUiState> =
            settings.settings
                .map { MainUiState.Ready(it.theme, it.onboardingComplete) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)
    }
