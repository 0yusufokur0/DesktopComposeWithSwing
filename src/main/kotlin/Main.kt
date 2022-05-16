// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.sun.javafx.application.PlatformImpl
import com.sun.javafx.application.PlatformImpl.runLater
import javafx.application.Platform
import javafx.application.Platform.runLater
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView
import netscape.javascript.JSObject
import java.awt.BorderLayout
import javax.swing.JPanel


@Composable
@Preview
fun App() {
    var text by remember { mutableStateOf("Hello, World!") }

    MaterialTheme {
        Button(onClick = {
            text = "Hello, Desktop!"
        }) {
            Text(text)
        }
    }
}

fun main() = application(exitProcessOnExit = true) {
    // Required to make sure the JavaFx event loop doesn't finish (can happen when java fx panels in app are shown/hidden)
    val finishListener = object : PlatformImpl.FinishListener {
        override fun idle(implicitExit: Boolean) {}
        override fun exitCalled() {}
    }
    PlatformImpl.addListener(finishListener)

    Window(
        title = "WebView Test",
        resizable = false,
        state = WindowState(
            placement = WindowPlacement.Floating,
            size = DpSize(1200.dp, 800.dp)
        ),
        onCloseRequest = {
            PlatformImpl.removeListener(finishListener)
            exitApplication()
        },
        content = {
            Scaffold {
                WebViewScreen(window)
            }
        })
}

@Composable
fun WebViewScreen(window: ComposeWindow) {

    val jfxPanel = remember { JFXPanel() }
    var jsObject = remember<JSObject?> { null }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {

        ComposeJFXPanel(
            composeWindow = window,
            jfxPanel = jfxPanel,
            onCreate = {
                Platform.runLater {
                    val root = WebView()
                    val engine = root.engine
                    val scene = Scene(root)
                    engine.loadWorker.stateProperty().addListener { _, _, newState ->
                        if (newState === Worker.State.SUCCEEDED) {
                            jsObject = root.engine.executeScript("window") as JSObject
                            // execute other javascript / setup js callbacks fields etc..
                        }
                    }
                    engine.loadWorker.exceptionProperty().addListener { _, _, newError ->
                        println("page load error : $newError")
                    }
                    jfxPanel.scene = scene
                    engine.load("http://youtube.com") // can be a html document from resources ..
                    engine.setOnError { error -> println("onError : $error") }
                }
            }, onDestroy = {
                Platform.runLater {
                    jsObject?.let { jsObj ->
                        // clean up code for more complex implementations i.e. removing javascript callbacks etc..
                    }
                }
            })
    }
}

@Composable
fun ComposeJFXPanel(
    composeWindow: ComposeWindow,
    jfxPanel: JFXPanel,
    onCreate: () -> Unit,
    onDestroy: () -> Unit = {}
) {
    val jPanel = remember { JPanel() }
    val density = LocalDensity.current.density

    Layout(
        content = {},
        modifier = Modifier.onGloballyPositioned { childCoordinates ->
            val coordinates = childCoordinates.parentCoordinates!!
            val location = coordinates.localToWindow(Offset.Zero).round()
            val size = coordinates.size
            jPanel.setBounds(
                (location.x / density).toInt(),
                (location.y / density).toInt(),
                (size.width / density).toInt(),
                (size.height / density).toInt()
            )
            jPanel.validate()
            jPanel.repaint()
        },
        measurePolicy = { _, _ -> layout(0, 0) {} })

    DisposableEffect(jPanel) {
        composeWindow.add(jPanel)
        jPanel.layout = BorderLayout(0, 0)
        jPanel.add(jfxPanel)
        onCreate()
        onDispose {
            onDestroy()
            composeWindow.remove(jPanel)
        }
    }
}