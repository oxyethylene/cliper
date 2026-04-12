package com.github.oxyethylene.cliper.service.video

import java.nio.file.Path

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
    ): List<String> {
        val command = mutableListOf(
            ffmpegPath,
            "-ss", startSeconds.toString(),
            "-to", endSeconds.toString(),
            "-i", inputPath.toString(),
            "-c:v", "libx264",
            "-b:v", "${targetBitrateKbps}k",
            "-preset", "medium",
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
}
