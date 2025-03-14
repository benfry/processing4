package processing.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.MaterialTheme.colors
import androidx.compose.material.MaterialTheme.typography
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.formdev.flatlaf.util.SystemInfo
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.m2.markdownColor
import com.mikepenz.markdown.m2.markdownTypography
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownTypography
import processing.app.Base.getRevision
import processing.app.Base.getVersionName
import processing.app.ui.theme.LocalLocale
import processing.app.ui.theme.LocalTheme
import processing.app.ui.theme.Locale
import processing.app.ui.theme.ProcessingTheme
import java.awt.Cursor
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.io.InputStream
import java.util.Properties
import javax.swing.JFrame
import javax.swing.SwingUtilities


class WelcomeToBeta {
    companion object{
        val windowSize = Dimension(400, 200)
        val windowTitle = Locale()["beta.window.title"]

        @JvmStatic
        fun showWelcomeToBeta() {
            val mac = SystemInfo.isMacFullWindowContentSupported
            SwingUtilities.invokeLater {
                JFrame(windowTitle).apply {
                    val close = { dispose() }
                    rootPane.putClientProperty("apple.awt.transparentTitleBar", mac)
                    rootPane.putClientProperty("apple.awt.fullWindowContent", mac)
                    defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
                    contentPane.add(ComposePanel().apply {
                        size = windowSize
                        setContent {
                            ProcessingTheme {
                                Box(modifier = Modifier.padding(top = if (mac) 22.dp else 0.dp)) {
                                    welcomeToBeta(close)
                                }
                            }
                        }
                    })
                    pack()
                    background = java.awt.Color.white
                    setLocationRelativeTo(null)
                    addKeyListener(object : KeyAdapter() {
                        override fun keyPressed(e: KeyEvent) {
                            if (e.keyCode == KeyEvent.VK_ESCAPE) close()
                        }
                    })
                    isResizable = false
                    isVisible = true
                    requestFocus()
                }
            }
        }

        @Composable
        fun welcomeToBeta(close: () -> Unit = {}) {
            Row(
                modifier = Modifier
                    .padding(20.dp, 10.dp)
                    .size(windowSize.width.dp, windowSize.height.dp),
                horizontalArrangement = Arrangement
                    .spacedBy(20.dp)
            ){
                val locale = LocalLocale.current
                Image(
                    painter = painterResource("bird.svg"),
                    contentDescription = locale["beta.logo"],
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .size(100.dp, 100.dp)
                        .offset(0.dp, (-25).dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement
                        .spacedBy(
                            10.dp,
                            alignment = Alignment.CenterVertically
                        )
                ) {
                    Text(
                        text = locale["beta.title"],
                        style = typography.subtitle1,
                    )
                    val text = locale["beta.message"]
                        .replace('$' + "version", getVersionName())
                        .replace('$' + "revision", getRevision().toString())
                    Markdown(
                        text,
                        colors = markdownColor(),
                        typography = markdownTypography(text = typography.body1, link = typography.body1.copy(color = colors.primary)),
                        modifier = Modifier.background(Color.Transparent).padding(bottom = 10.dp)
                    )
                    Row {
                        Spacer(modifier = Modifier.weight(1f))
                        PDEButton(onClick = {
                            close()
                        }) {
                            Text(
                                text = locale["beta.button"],
                                color = colors.onPrimary
                            )
                        }
                    }
                }
            }
        }
        @OptIn(ExperimentalComposeUiApi::class)
        @Composable
        fun PDEButton(onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
            val theme = LocalTheme.current

            var hover by remember { mutableStateOf(false) }
            var clicked by remember { mutableStateOf(false) }
            val offset by animateFloatAsState(if (hover) -5f else 5f)
            val color by animateColorAsState(if(clicked) colors.primaryVariant else colors.primary)

            Box(modifier = Modifier.padding(end = 5.dp, top = 5.dp)) {
                Box(
                    modifier = Modifier
                        .offset((-offset).dp, (offset).dp)
                        .background(theme.getColor("toolbar.button.pressed.field"))
                        .matchParentSize()
                )
                Box(
                    modifier = Modifier
                        .onPointerEvent(PointerEventType.Press) {
                            clicked = true
                        }
                        .onPointerEvent(PointerEventType.Release) {
                            clicked = false
                            onClick()
                        }
                        .onPointerEvent(PointerEventType.Enter) {
                            hover = true
                        }
                        .onPointerEvent(PointerEventType.Exit) {
                            hover = false
                        }
                        .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
                        .background(color)
                        .padding(10.dp)
                        .sizeIn(minWidth = 100.dp),
                    contentAlignment = Alignment.Center,
                    content = content
                )
            }
        }


        @JvmStatic
        fun main(args: Array<String>) {
            application {
                val windowState = rememberWindowState(
                    size = DpSize.Unspecified,
                    position = WindowPosition(Alignment.Center)
                )

                Window(onCloseRequest = ::exitApplication, state = windowState, title = windowTitle) {
                    ProcessingTheme {
                        Surface(color = colors.background) {
                            welcomeToBeta {
                                exitApplication()
                            }
                        }
                    }
                }
            }
        }
    }
}

