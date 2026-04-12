package com.github.oxyethylene.cliper

import cliper.composeapp.generated.resources.Res
import cliper.composeapp.generated.resources.cliper_icon
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "cliper",
        icon = painterResource(Res.drawable.cliper_icon),
    ) {
        App()
    }
}