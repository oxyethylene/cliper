package com.github.oxyethylene.cliper.service.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.io.path.Path

class FfmpegCommandBuilderTest {
    @Test
    fun `build export command with overwrite enabled`() {
        val command = FfmpegCommandBuilder.exportTrimmedWithBitrate(
            ffmpegPath = "/usr/local/bin/ffmpeg",
            inputPath = Path("/tmp/in.mp4"),
            outputPath = Path("/tmp/out.mp4"),
            startSeconds = 2.5,
            endSeconds = 7.5,
            targetBitrateKbps = 1800,
            overwriteOutput = true,
        )

        assertEquals("/usr/local/bin/ffmpeg", command.first())
        assertTrue(command.contains("-ss"))
        assertTrue(command.contains("2.5"))
        assertTrue(command.contains("-to"))
        assertTrue(command.contains("7.5"))
        assertTrue(command.contains("-b:v"))
        assertTrue(command.contains("1800k"))
        assertTrue(command.contains("-y"))
        assertEquals("/tmp/out.mp4", command.last())
    }

    @Test
    fun `build export command with overwrite disabled`() {
        val command = FfmpegCommandBuilder.exportTrimmedWithBitrate(
            ffmpegPath = "ffmpeg",
            inputPath = Path("input.mp4"),
            outputPath = Path("output.mp4"),
            startSeconds = 0.0,
            endSeconds = 10.0,
            targetBitrateKbps = 1200,
            overwriteOutput = false,
        )

        assertTrue(command.contains("-n"))
        assertTrue(!command.contains("-y"))
    }
}
