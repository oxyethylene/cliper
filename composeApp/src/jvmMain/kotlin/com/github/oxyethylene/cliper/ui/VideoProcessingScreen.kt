package com.github.oxyethylene.cliper.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.dp
import com.github.oxyethylene.cliper.domain.ProcessingState
import com.github.oxyethylene.cliper.service.settings.ThemeMode
import org.jetbrains.skia.Image
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
fun VideoProcessingScreen(
    viewModel: VideoProcessingViewModel,
) {
    val state by viewModel.state.collectAsState()

    if (state.askOverwriteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.clearOverwritePrompt() },
            title = { Text("Output already exists") },
            text = { Text("Do you want to overwrite the existing output file?") },
            confirmButton = {
                TextButton(onClick = { viewModel.startProcessing(overwriteConfirmed = true) }) {
                    Text("Overwrite")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearOverwritePrompt() }) {
                    Text("Cancel")
                }
            },
        )
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Sidebar(
            selected = state.selectedTab,
            onSelect = viewModel::selectTab,
        )

        Spacer(modifier = Modifier.width(16.dp))

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (state.selectedTab) {
                    SidebarTab.Video -> VideoTabContent(
                        state = state,
                        viewModel = viewModel,
                    )
                    SidebarTab.Settings -> SettingsTabContent(
                        state = state,
                        viewModel = viewModel,
                    )
                }

                if (!state.errorMessage.isNullOrBlank()) {
                    Text(state.errorMessage ?: "", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun Sidebar(
    selected: SidebarTab,
    onSelect: (SidebarTab) -> Unit,
) {
    Card(
        modifier = Modifier
            .width(220.dp)
            .fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("cliper", style = MaterialTheme.typography.headlineSmall)
            Text("Workspace", style = MaterialTheme.typography.labelMedium)

            SidebarItem(
                title = "Video",
                selected = selected == SidebarTab.Video,
                onClick = { onSelect(SidebarTab.Video) },
            )
            SidebarItem(
                title = "Settings",
                selected = selected == SidebarTab.Settings,
                onClick = { onSelect(SidebarTab.Settings) },
            )
        }
    }
}

@Composable
private fun SidebarItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) Color(0xFF1E6A5D) else Color.Transparent
    val foreground = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = MaterialTheme.shapes.small,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = foreground, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun VideoTabContent(
    state: VideoProcessingUiState,
    viewModel: VideoProcessingViewModel,
) {
    Text("Video", style = MaterialTheme.typography.headlineMedium)
    Text("Trim your clip and export with your saved settings.")

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.inputPath,
            onValueChange = viewModel::updateInputPath,
            label = { Text("Input video") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        Button(onClick = {
            chooseFile(title = "Select Input Video")?.let {
                viewModel.updateInputPath(it)
                viewModel.loadMetadataAndThumbnails()
            }
        }) {
            Text("Pick")
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.outputPath,
            onValueChange = viewModel::updateOutputPath,
            label = { Text("Output video") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        Button(onClick = {
            chooseSaveFile(title = "Select Output Video")?.let(viewModel::updateOutputPath)
        }) {
            Text("Save As")
        }
    }

    Text("Target bitrate: ${state.bitrateKbps} kbps")

    if (state.maxDurationSeconds > 0f) {
        Text("Timeline preview")
        ThumbnailStrip(state)
        Text("Start: ${formatSeconds(state.startSeconds)}")
        Slider(
            value = state.startSeconds,
            onValueChange = viewModel::updateStart,
            valueRange = 0f..state.endSeconds,
        )
        Text("End: ${formatSeconds(state.endSeconds)}")
        Slider(
            value = state.endSeconds,
            onValueChange = viewModel::updateEnd,
            valueRange = state.startSeconds..state.maxDurationSeconds,
        )
    }

    when (val processing = state.processingState) {
        is ProcessingState.Running -> {
            LinearProgressIndicator(progress = { processing.progress }, modifier = Modifier.fillMaxWidth())
            Text(processing.statusText)
        }
        is ProcessingState.Done -> Text("Done: ${processing.outputPath}")
        is ProcessingState.Error -> Text("Error: ${processing.message}", color = MaterialTheme.colorScheme.error)
        is ProcessingState.Cancelled -> Text("Processing cancelled")
        ProcessingState.Idle -> Unit
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { viewModel.loadMetadataAndThumbnails() }, enabled = state.inputPath.isNotBlank()) {
            Text("Refresh Preview")
        }
        Button(onClick = { viewModel.startProcessing() }, enabled = state.canProcess) {
            Text("Export")
        }
        Button(onClick = { viewModel.cancelProcessing() }) {
            Text("Cancel")
        }
    }
}

@Composable
private fun SettingsTabContent(
    state: VideoProcessingUiState,
    viewModel: VideoProcessingViewModel,
) {
    Text("Settings", style = MaterialTheme.typography.headlineMedium)
    Text("These values are stored on disk and reused automatically.")

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.ffmpegPath,
            onValueChange = viewModel::updateFfmpegPath,
            label = { Text("FFmpeg binary") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        Button(onClick = {
            chooseFile(title = "Select FFmpeg Binary")?.let(viewModel::updateFfmpegPath)
        }) {
            Text("Browse")
        }
    }

    OutlinedTextField(
        value = state.bitrateKbps.toString(),
        onValueChange = viewModel::updateBitrate,
        label = { Text("Default target video bitrate (kbps)") },
        singleLine = true,
    )

    Text("Theme", style = MaterialTheme.typography.titleMedium)
    Text("Choose Light, Dark, or follow the OS appearance.")

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeModeChip(
            label = "Follow OS",
            selected = state.themeMode == ThemeMode.FOLLOW_SYSTEM,
            onClick = { viewModel.updateThemeMode(ThemeMode.FOLLOW_SYSTEM) },
        )
        ThemeModeChip(
            label = "Light",
            selected = state.themeMode == ThemeMode.LIGHT,
            onClick = { viewModel.updateThemeMode(ThemeMode.LIGHT) },
        )
        ThemeModeChip(
            label = "Dark",
            selected = state.themeMode == ThemeMode.DARK,
            onClick = { viewModel.updateThemeMode(ThemeMode.DARK) },
        )
    }
}

@Composable
private fun ThemeModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .background(container, shape = MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, color = content)
    }
}

@Composable
private fun ThumbnailStrip(state: VideoProcessingUiState) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.thumbnails.forEach { item ->
            val painter = remember(item.imagePath) {
                runCatching {
                    val bytes = item.imagePath.toFile().readBytes()
                    BitmapPainter(Image.makeFromEncoded(bytes).asImageBitmap())
                }.getOrNull()
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (painter != null) {
                    Image(
                        painter = painter,
                        contentDescription = "Thumbnail ${formatSeconds(item.timestampSeconds.toFloat())}",
                        modifier = Modifier
                            .size(width = 120.dp, height = 68.dp)
                            .border(1.dp, Color(0xFFB69E7B)),
                    )
                } else {
                    Spacer(
                        modifier = Modifier
                            .size(width = 120.dp, height = 68.dp)
                            .background(Color(0xFFE0D5C4)),
                    )
                }
                Text(formatSeconds(item.timestampSeconds.toFloat()), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun chooseFile(title: String): String? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    dialog.isVisible = true
    val fileName = dialog.file ?: return null
    val directory = dialog.directory ?: ""
    return File(directory, fileName).absolutePath
}

private fun chooseSaveFile(title: String): String? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
    dialog.isVisible = true
    val fileName = dialog.file ?: return null
    val directory = dialog.directory ?: ""
    return File(directory, fileName).absolutePath
}

private fun formatSeconds(value: Float): String {
    val total = value.toInt().coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    return "%02d:%02d".format(minutes, seconds)
}
