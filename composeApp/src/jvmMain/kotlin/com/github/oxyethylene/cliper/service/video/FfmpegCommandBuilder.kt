package com.github.oxyethylene.cliper.service.video

import java.nio.file.Path

import com.github.oxyethylene.cliper.domain.ExportAccelerationMode
import java.util.Locale

object FfmpegCommandBuilder {
    fun probeDuration(
        ffmpegPath: String,
        inputPath: Path,
    ): List<String> = listOf(
        ffmpegPath,
        "-i",
        inputPath.toString(),
    )

    fun exportTrimmedWithBitrate(
        ffmpegPath: String,
        inputPath: Path,
        outputPath: Path,
        startSeconds: Double,
        endSeconds: Double,
        targetBitrateKbps: Int,
        overwriteOutput: Boolean,
        accelerationMode: ExportAccelerationMode = ExportAccelerationMode.AUTO,
    ): List<String> {
        val encoder = chooseEncoder(accelerationMode)
        val command = mutableListOf(
            ffmpegPath,
            "-ss", startSeconds.toString(),
            "-to", endSeconds.toString(),
            "-i", inputPath.toString(),
            "-c:v", encoder,
            "-b:v", "${targetBitrateKbps}k",
            "-preset", if (encoder == "libx264") "medium" else "medium",
            "-c:a", "aac",
            "-b:a", "128k",
        )

        command += if (overwriteOutput) "-y" else "-n"
        command += outputPath.toString()
        return command
    }

    fun extractThumbnail(
        ffmpegPath: String,
        inputPath: Path,
        outputPath: Path,
        timestampSeconds: Double,
    ): List<String> = listOf(
        ffmpegPath,
        "-ss", timestampSeconds.toString(),
        "-i", inputPath.toString(),
        "-frames:v", "1",
        "-q:v", "3",
        "-y",
        outputPath.toString(),
    )

    private fun chooseEncoder(accelerationMode: ExportAccelerationMode): String = when (accelerationMode) {
        ExportAccelerationMode.CPU -> "libx264"
        ExportAccelerationMode.GPU -> platformPreferredGpuEncoder()
        ExportAccelerationMode.AUTO -> platformPreferredGpuEncoder().takeIf { it != "libx264" } ?: "libx264"
    }

    private fun platformPreferredGpuEncoder(): String {
        val os = System.getProperty("os.name", "").lowercase(Locale.getDefault())
        return when {
            os.contains("mac") -> "h264_videotoolbox"
            os.contains("win") -> "h264_nvenc"
            else -> "libx264"
        }
    }
}
