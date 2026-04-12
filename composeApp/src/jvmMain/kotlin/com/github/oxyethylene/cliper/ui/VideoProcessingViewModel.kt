package com.github.oxyethylene.cliper.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.oxyethylene.cliper.domain.MediaMetadata
import com.github.oxyethylene.cliper.domain.ProcessingResult
import com.github.oxyethylene.cliper.domain.ProcessingState
import com.github.oxyethylene.cliper.domain.TimelineThumbnail
import com.github.oxyethylene.cliper.domain.VideoProcessingRequest
import com.github.oxyethylene.cliper.service.process.DefaultProcessRunner
import com.github.oxyethylene.cliper.service.video.DefaultFfmpegService
import com.github.oxyethylene.cliper.service.video.FfmpegService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.math.min

data class VideoProcessingUiState(
    val ffmpegPath: String = "ffmpeg",
    val inputPath: String = "",
    val outputPath: String = "",
    val startSeconds: Float = 0f,
    val endSeconds: Float = 0f,
    val maxDurationSeconds: Float = 0f,
    val bitrateKbps: Int = 2000,
    val metadata: MediaMetadata? = null,
    val thumbnails: List<TimelineThumbnail> = emptyList(),
    val processingState: ProcessingState = ProcessingState.Idle,
    val canProcess: Boolean = false,
    val errorMessage: String? = null,
    val askOverwriteConfirmation: Boolean = false,
)

class VideoProcessingViewModel(
    private val ffmpegService: FfmpegService = DefaultFfmpegService(DefaultProcessRunner()),
) : ViewModel() {
    private val _state = MutableStateFlow(VideoProcessingUiState())
    val state: StateFlow<VideoProcessingUiState> = _state.asStateFlow()

    private var processingJob: Job? = null

    fun updateFfmpegPath(value: String) {
        _state.value = _state.value.copy(ffmpegPath = value.trim())
    }

    fun updateInputPath(value: String) {
        _state.value = _state.value.copy(inputPath = value.trim())
    }

    fun updateOutputPath(value: String) {
        _state.value = _state.value.copy(outputPath = value.trim())
        recalculateCanProcess()
    }

    fun updateBitrate(value: String) {
        val bitrate = value.toIntOrNull() ?: return
        _state.value = _state.value.copy(bitrateKbps = bitrate)
    }

    fun updateStart(seconds: Float) {
        val clamped = seconds.coerceIn(0f, _state.value.endSeconds)
        _state.value = _state.value.copy(startSeconds = clamped)
        recalculateCanProcess()
    }

    fun updateEnd(seconds: Float) {
        val maxValue = _state.value.maxDurationSeconds
        val clamped = seconds.coerceIn(_state.value.startSeconds, maxValue)
        _state.value = _state.value.copy(endSeconds = clamped)
        recalculateCanProcess()
    }

    fun clearOverwritePrompt() {
        _state.value = _state.value.copy(askOverwriteConfirmation = false)
    }

    fun loadMetadataAndThumbnails() {
        val current = _state.value
        if (current.inputPath.isBlank()) {
            _state.value = current.copy(errorMessage = "Select an input file first.")
            return
        }

        val inputPath = Path(current.inputPath)
        viewModelScope.launch {
            _state.value = _state.value.copy(
                errorMessage = null,
                processingState = ProcessingState.Running(progress = 0f, statusText = "Reading metadata..."),
            )

            val metadataResult = ffmpegService.probeMetadata(
                ffmpegPath = _state.value.ffmpegPath,
                inputPath = inputPath,
            )

            val metadata = metadataResult.getOrElse {
                _state.value = _state.value.copy(
                    processingState = ProcessingState.Error("Could not read metadata: ${it.message}"),
                    errorMessage = it.message ?: "Unknown metadata error.",
                )
                return@launch
            }

            val thumbnailsResult = ffmpegService.generateTimelineThumbnails(
                ffmpegPath = _state.value.ffmpegPath,
                inputPath = inputPath,
                durationSeconds = metadata.durationSeconds,
                count = 12,
            )

            val thumbnails = thumbnailsResult.getOrElse {
                _state.value = _state.value.copy(
                    processingState = ProcessingState.Error("Could not generate thumbnails: ${it.message}"),
                    errorMessage = it.message ?: "Unknown thumbnail error.",
                )
                return@launch
            }

            val suggestedOutput = if (_state.value.outputPath.isBlank()) {
                suggestOutputPath(inputPath)
            } else {
                _state.value.outputPath
            }

            val maxDuration = metadata.durationSeconds.toFloat().coerceAtLeast(0f)
            _state.value = _state.value.copy(
                metadata = metadata,
                thumbnails = thumbnails,
                maxDurationSeconds = maxDuration,
                startSeconds = 0f,
                endSeconds = maxDuration,
                outputPath = suggestedOutput,
                processingState = ProcessingState.Idle,
            )
            recalculateCanProcess()
        }
    }

    fun startProcessing(overwriteConfirmed: Boolean = false) {
        val current = _state.value
        if (!current.canProcess) {
            _state.value = current.copy(errorMessage = "Please provide valid input/output and trim range.")
            return
        }

        val output = Path(current.outputPath)
        if (Files.exists(output) && !overwriteConfirmed) {
            _state.value = current.copy(askOverwriteConfirmation = true)
            return
        }

        val request = VideoProcessingRequest(
            ffmpegPath = current.ffmpegPath,
            inputPath = Path(current.inputPath),
            outputPath = output,
            startSeconds = current.startSeconds.toDouble(),
            endSeconds = current.endSeconds.toDouble(),
            targetBitrateKbps = current.bitrateKbps,
            overwriteOutput = overwriteConfirmed,
        )

        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                askOverwriteConfirmation = false,
                errorMessage = null,
                processingState = ProcessingState.Running(0f, "Starting FFmpeg..."),
            )

            when (val result = ffmpegService.processVideo(request) { progress, status ->
                _state.value = _state.value.copy(
                    processingState = ProcessingState.Running(progress, status),
                )
            }) {
                is ProcessingResult.Success -> {
                    _state.value = _state.value.copy(processingState = ProcessingState.Done(result.outputPath))
                }
                is ProcessingResult.Failure -> {
                    val detail = listOfNotNull(result.message, result.details).joinToString("\n")
                    _state.value = _state.value.copy(
                        processingState = ProcessingState.Error(result.message),
                        errorMessage = detail,
                    )
                }
            }
        }
    }

    fun cancelProcessing() {
        processingJob?.cancel()
        ffmpegService.cancelActiveProcess()
        _state.value = _state.value.copy(processingState = ProcessingState.Cancelled)
    }

    private fun suggestOutputPath(inputPath: Path): String {
        val fileName = inputPath.fileName.toString()
        val dot = fileName.lastIndexOf('.')
        val stem = if (dot >= 0) fileName.substring(0, dot) else fileName
        val ext = if (dot >= 0) fileName.substring(dot) else ".mp4"
        val parent = inputPath.parent ?: Path(".")
        return parent.resolve("${stem}_clipped${ext}").toString()
    }

    private fun recalculateCanProcess() {
        val current = _state.value
        val hasInput = current.inputPath.isNotBlank()
        val hasOutput = current.outputPath.isNotBlank()
        val validTimes = current.endSeconds > current.startSeconds
        val validBitrate = current.bitrateKbps > 0
        val hasDuration = current.maxDurationSeconds > 0f
        _state.value = current.copy(canProcess = hasInput && hasOutput && validTimes && validBitrate && hasDuration)
    }

    override fun onCleared() {
        ffmpegService.cancelActiveProcess()
        super.onCleared()
    }
}
