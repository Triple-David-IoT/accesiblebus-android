
package dte.masteriot.accessiblebus.ui.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dte.masteriot.accessiblebus.core.config.AccessibilityPreferences
import dte.masteriot.accessiblebus.core.config.VoiceMode
import dte.masteriot.accessiblebus.core.config.AppLanguage
import dte.masteriot.accessiblebus.core.config.AppConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PreferencesUiState(
    val accessibilityPreferences: AccessibilityPreferences =
        AccessibilityPreferences(),
    val isLoaded: Boolean = false
)

class PreferencesViewModel(
    private val repository: AppConfigRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PreferencesUiState())

    val uiState: StateFlow<PreferencesUiState> =
        _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.accessibilityPreferences().collect { preferences ->
                _uiState.update {
                    it.copy(
                        accessibilityPreferences = preferences,
                        isLoaded = true
                    )
                }
            }
        }
    }

    fun updateVoiceMode(mode: VoiceMode) {
        _uiState.update { current ->
            current.copy(
                accessibilityPreferences =
                    current.accessibilityPreferences.copy(
                        voiceMode = mode
                    )
            )
        }
    }

    fun updateVibration(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(
                accessibilityPreferences =
                    current.accessibilityPreferences.copy(
                        vibrationEnabled = enabled
                    )
            )
        }
    }

    fun updateHighContrast(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(
                accessibilityPreferences =
                    current.accessibilityPreferences.copy(
                        highContrastEnabled = enabled
                    )
            )
        }
    }

    fun updateLanguage(language: AppLanguage) {
        _uiState.update { current ->
            current.copy(
                accessibilityPreferences =
                    current.accessibilityPreferences.copy(
                        language = language
                    )
            )
        }
    }

    suspend fun saveAccessibilityPreferences() {
        repository.saveAccessibilityPreferences(
            _uiState.value.accessibilityPreferences
        )
    }
}
