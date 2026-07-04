package com.vfxsal.filemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vfxsal.filemanager.feature.settings.SettingsViewModel
import com.vfxsal.filemanager.feature.settings.ThemeMode
import com.vfxsal.filemanager.ui.nav.AppRoot
import com.vfxsal.filemanager.ui.theme.FileManagerTheme

class MainActivity : ComponentActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            val darkTheme = when (settingsState.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            FileManagerTheme(darkTheme = darkTheme, dynamicColor = settingsState.dynamicColor) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(settingsViewModel = settingsViewModel)
                }
            }
        }
    }
}
