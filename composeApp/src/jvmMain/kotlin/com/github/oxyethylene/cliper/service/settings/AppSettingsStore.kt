package com.github.oxyethylene.cliper.service.settings

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Properties
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

private const val DefaultBitrateKbps = 2000
private const val DefaultFfmpegPath = "ffmpeg"

data class AppSettings(
    val ffmpegPath: String,
    val defaultBitrateKbps: Int,
)

class AppSettingsStore(
    private val settingsFile: Path = defaultSettingsPath(),
) {
    fun load(): AppSettings {
        return runCatching {
            if (!settingsFile.exists()) {
                return@runCatching AppSettings(
                    ffmpegPath = DefaultFfmpegPath,
                    defaultBitrateKbps = DefaultBitrateKbps,
                )
            }

            val props = Properties()
            settingsFile.inputStream().use(props::load)

            val ffmpegPath = props.getProperty("ffmpegPath")?.takeIf { it.isNotBlank() } ?: DefaultFfmpegPath
            val bitrate = props.getProperty("defaultBitrateKbps")?.toIntOrNull()?.takeIf { it > 0 } ?: DefaultBitrateKbps

            AppSettings(
                ffmpegPath = ffmpegPath,
                defaultBitrateKbps = bitrate,
            )
        }.getOrDefault(
            AppSettings(
                ffmpegPath = DefaultFfmpegPath,
                defaultBitrateKbps = DefaultBitrateKbps,
            ),
        )
    }

    fun save(settings: AppSettings) {
        runCatching {
            settingsFile.parent?.createDirectories()

            val props = Properties().apply {
                setProperty("ffmpegPath", settings.ffmpegPath)
                setProperty("defaultBitrateKbps", settings.defaultBitrateKbps.toString())
            }

            settingsFile.outputStream(
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            ).use { stream ->
                props.store(stream, "cliper settings")
            }
        }
    }

    private companion object {
        fun defaultSettingsPath(): Path {
            val userHome = System.getProperty("user.home") ?: "."
            val dir = Path.of(userHome, ".cliper")
            if (!Files.exists(dir)) {
                dir.createDirectories()
            }
            return dir.resolve("settings.properties")
        }
    }
}
