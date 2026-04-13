package com.github.oxyethylene.cliper

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import com.github.oxyethylene.cliper.service.settings.ThemeMode
import com.github.oxyethylene.cliper.ui.VideoProcessingScreen
import com.github.oxyethylene.cliper.ui.VideoProcessingViewModel

@Composable
@Preview
fun App() {
    val viewModel = remember { VideoProcessingViewModel() }
    val state by viewModel.state.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (state.themeMode) {
        ThemeMode.FOLLOW_SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (useDarkTheme) darkColorScheme() else lightColorScheme(),
    ) {
        VideoProcessingScreen(viewModel = viewModel)
    }
}