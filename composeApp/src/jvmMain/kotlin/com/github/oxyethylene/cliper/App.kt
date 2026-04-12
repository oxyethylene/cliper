package com.github.oxyethylene.cliper

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import com.github.oxyethylene.cliper.ui.VideoProcessingScreen
import com.github.oxyethylene.cliper.ui.VideoProcessingViewModel

@Composable
@Preview
fun App() {
    MaterialTheme {
        val viewModel = remember { VideoProcessingViewModel() }
        VideoProcessingScreen(viewModel = viewModel)
    }
}