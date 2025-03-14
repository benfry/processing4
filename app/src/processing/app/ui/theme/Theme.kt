package processing.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import processing.app.LocalPreferences
import processing.app.PreferencesProvider
import java.io.InputStream
import java.util.Properties


class Theme(themeFile: String? = "") : Properties() {
    init {
        load(ClassLoader.getSystemResourceAsStream("theme.txt"))
        load(ClassLoader.getSystemResourceAsStream(themeFile) ?: InputStream.nullInputStream())
    }
    fun getColor(key: String): Color {
        return Color(getProperty(key).toColorInt())
    }
}

val LocalTheme = compositionLocalOf<Theme> { error("No theme provided") }

@Composable
fun ProcessingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable() () -> Unit
) {
    PreferencesProvider {
        val preferences = LocalPreferences.current
        val theme = Theme(preferences.getProperty("theme"))
        val colors = Colors(
            primary = theme.getColor("editor.gradient.top"),
            primaryVariant = theme.getColor("toolbar.button.pressed.field"),
            secondary = theme.getColor("editor.gradient.bottom"),
            secondaryVariant = theme.getColor("editor.scrollbar.thumb.pressed.color"),
            background = theme.getColor("editor.bgcolor"),
            surface = theme.getColor("editor.bgcolor"),
            error = theme.getColor("status.error.bgcolor"),
            onPrimary = theme.getColor("toolbar.button.enabled.field"),
            onSecondary = theme.getColor("toolbar.button.enabled.field"),
            onBackground = theme.getColor("editor.fgcolor"),
            onSurface = theme.getColor("editor.fgcolor"),
            onError = theme.getColor("status.error.fgcolor"),
            isLight = theme.getProperty("laf.mode").equals("light")
        )

        CompositionLocalProvider(LocalTheme provides theme) {
            LocaleProvider {
                MaterialTheme(
                    colors = colors,
                    typography = Typography,
                    content = content
                )
            }
        }
    }
}

fun String.toColorInt(): Int {
    if (this[0] == '#') {
        var color = substring(1).toLong(16)
        if (length == 7) {
            color = color or 0x00000000ff000000L
        } else if (length != 9) {
            throw IllegalArgumentException("Unknown color")
        }
        return color.toInt()
    }
    throw IllegalArgumentException("Unknown color")
}