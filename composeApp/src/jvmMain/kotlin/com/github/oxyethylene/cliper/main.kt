package com.github.oxyethylene.cliper

import cliper.composeapp.generated.resources.Res
import cliper.composeapp.generated.resources.cliper_icon
import androidx.compose.runtime.DisposableEffect
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.awt.Taskbar
import java.awt.Window as AwtWindow

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "cliper",
        icon = painterResource(Res.drawable.cliper_icon),
    ) {
        DisposableEffect(window) {
            setDockIconIfSupported(window)
            onDispose { }
        }
        App()
    }
}

private fun setDockIconIfSupported(window: AwtWindow) {
    if (!System.getProperty("os.name", "").contains("Mac", ignoreCase = true)) {
        return
    }

    runCatching {
        if (!Taskbar.isTaskbarSupported()) {
            return
        }
        val iconImage = window.iconImages.firstOrNull() ?: return
        Taskbar.getTaskbar().iconImage = iconImage
    }
}