package com.github.oxyethylene.cliper.domain

import java.nio.file.Path

data class MediaMetadata(
    val durationSeconds: Double,
)

data class TimelineThumbnail(
    val timestampSeconds: Double,
    val imagePath: Path,
)

data class VideoProcessingRequest(
    val ffmpegPath: String,
    val inputPath: Path,
    val outputPath: Path,
    val startSeconds: Double,
    val endSeconds: Double,
    val targetBitrateKbps: Int,
    val overwriteOutput: Boolean,
)

sealed interface ProcessingResult {
    data class Success(val outputPath: Path) : ProcessingResult
    data class Failure(val message: String, val details: String? = null) : ProcessingResult
}

sealed interface ProcessingState {
    data object Idle : ProcessingState
    data class Running(val progress: Float, val statusText: String) : ProcessingState
    data class Done(val outputPath: Path) : ProcessingState
    data class Error(val message: String) : ProcessingState
    data object Cancelled : ProcessingState
}
