package com.vfxsal.filemanager.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
)

/** Held at the activity level (not per-screen) so a theme change made from the Settings
 *  screen re-themes the whole app - including screens outside the Files tab - immediately. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            themeMode = SettingsStore.getThemeMode(application),
            dynamicColor = SettingsStore.getDynamicColor(application),
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        SettingsStore.setThemeMode(getApplication(), mode)
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        SettingsStore.setDynamicColor(getApplication(), enabled)
        _uiState.update { it.copy(dynamicColor = enabled) }
    }
}
