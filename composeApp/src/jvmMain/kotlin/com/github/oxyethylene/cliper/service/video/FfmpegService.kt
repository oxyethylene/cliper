package com.github.oxyethylene.cliper.service.video

import com.github.oxyethylene.cliper.domain.MediaMetadata
import com.github.oxyethylene.cliper.domain.ProcessingResult
import com.github.oxyethylene.cliper.domain.TimelineThumbnail
import com.github.oxyethylene.cliper.domain.VideoProcessingRequest
import com.github.oxyethylene.cliper.service.process.ProcessRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.max

interface FfmpegService {
    suspend fun probeMetadata(ffmpegPath: String, inputPath: Path): Result<MediaMetadata>

    suspend fun generateTimelineThumbnails(
        ffmpegPath: String,
        inputPath: Path,
        durationSeconds: Double,
        count: Int = 12,
    ): Result<List<TimelineThumbnail>>

    suspend fun processVideo(
        request: VideoProcessingRequest,
        onProgress: (Float, String) -> Unit,
    ): ProcessingResult

    fun cancelActiveProcess()
}

class DefaultFfmpegService(
    private val processRunner: ProcessRunner,
) : FfmpegService {
    private var thumbnailTempDir: Path? = null

    override suspend fun probeMetadata(ffmpegPath: String, inputPath: Path): Result<MediaMetadata> = runCatching {
        require(Files.exists(inputPath)) { "Input file does not exist." }

        val command = FfmpegCommandBuilder.probeDuration(
            ffmpegPath = ffmpegPath,
            inputPath = inputPath,
        )

        val result = processRunner.run(
            command = command,
            acceptedExitCodes = setOf(0, 1),
        )

        val duration = parseDurationSeconds(result.stderr)
            ?: throw IllegalStateException("Could not parse media duration from FFmpeg output.")

        MediaMetadata(durationSeconds = duration)
    }

    override suspend fun generateTimelineThumbnails(
        ffmpegPath: String,
        inputPath: Path,
        durationSeconds: Double,
        count: Int,
    ): Result<List<TimelineThumbnail>> = runCatching {
        cleanupThumbnailTempDir()
        val tempDir = Files.createTempDirectory("cliper-thumbs-")
        thumbnailTempDir = tempDir

        val points = samplingPoints(durationSeconds, count)
        val thumbnails = mutableListOf<TimelineThumbnail>()

        points.forEachIndexed { index, seconds ->
            val outputFile = tempDir.resolve("thumb_${index.toString().padStart(3, '0')}.jpg")
            val command = FfmpegCommandBuilder.extractThumbnail(
                ffmpegPath = ffmpegPath,
                inputPath = inputPath,
                outputPath = outputFile,
                timestampSeconds = seconds,
            )
            processRunner.run(command = command)
            thumbnails += TimelineThumbnail(
                timestampSeconds = seconds,
                imagePath = outputFile,
            )
        }

        thumbnails
    }

    override suspend fun processVideo(
        request: VideoProcessingRequest,
        onProgress: (Float, String) -> Unit,
    ): ProcessingResult = withContext(Dispatchers.IO) {
        if (!Files.exists(request.inputPath)) {
            return@withContext ProcessingResult.Failure("Input file does not exist.")
        }
        if (request.endSeconds <= request.startSeconds) {
            return@withContext ProcessingResult.Failure("End time must be greater than start time.")
        }
        if (request.targetBitrateKbps <= 0) {
            return@withContext ProcessingResult.Failure("Target bitrate must be a positive value.")
        }

        val command = FfmpegCommandBuilder.exportTrimmedWithBitrate(
            ffmpegPath = request.ffmpegPath,
            inputPath = request.inputPath,
            outputPath = request.outputPath,
            startSeconds = request.startSeconds,
            endSeconds = request.endSeconds,
            targetBitrateKbps = request.targetBitrateKbps,
            overwriteOutput = request.overwriteOutput,
        )

        return@withContext try {
            processRunner.run(command = command) { line ->
                val progress = parseProgressFraction(
                    ffmpegLine = line,
                    startSeconds = request.startSeconds,
                    endSeconds = request.endSeconds,
                )
                if (progress != null) {
                    onProgress(progress, "Encoding ${(progress * 100).toInt()}%")
                }
            }
            ProcessingResult.Success(request.outputPath)
        } catch (error: Throwable) {
            ProcessingResult.Failure(
                message = "FFmpeg processing failed.",
                details = error.message,
            )
        }
    }

    override fun cancelActiveProcess() {
        processRunner.cancelActiveProcess()
    }

    private fun parseDurationSeconds(stderr: String): Double? {
        val durationRegex = Regex("Duration: (\\d{2}):(\\d{2}):(\\d{2}(?:\\.\\d+)?)")
        val match = durationRegex.find(stderr) ?: return null
        val hours = match.groupValues[1].toDoubleOrNull() ?: return null
        val minutes = match.groupValues[2].toDoubleOrNull() ?: return null
        val seconds = match.groupValues[3].toDoubleOrNull() ?: return null
        return hours * 3600 + minutes * 60 + seconds
    }

    private fun parseProgressFraction(
        ffmpegLine: String,
        startSeconds: Double,
        endSeconds: Double,
    ): Float? {
        val timeRegex = Regex("time=(\\d{2}):(\\d{2}):(\\d{2}(?:\\.\\d+)?)")
        val match = timeRegex.find(ffmpegLine) ?: return null

        val hours = match.groupValues[1].toDoubleOrNull() ?: return null
        val minutes = match.groupValues[2].toDoubleOrNull() ?: return null
        val seconds = match.groupValues[3].toDoubleOrNull() ?: return null
        val elapsed = hours * 3600 + minutes * 60 + seconds

        val total = max(0.1, endSeconds - startSeconds)
        return ((elapsed - startSeconds) / total).toFloat().coerceIn(0f, 1f)
    }

    private fun samplingPoints(durationSeconds: Double, count: Int): List<Double> {
        if (durationSeconds <= 0.0 || count <= 0) {
            return emptyList()
        }
        if (count == 1) {
            return listOf(0.0)
        }

        val safeDuration = max(1.0, durationSeconds)
        val step = safeDuration / count
        return List(count) { index -> (index * step).coerceAtMost(max(0.0, safeDuration - 0.05)) }
    }

    private fun cleanupThumbnailTempDir() {
        val dir = thumbnailTempDir ?: return
        runCatching {
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(Files::deleteIfExists)
        }
        thumbnailTempDir = null
    }
}
