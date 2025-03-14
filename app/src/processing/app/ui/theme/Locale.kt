package processing.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import processing.app.LocalPreferences
import processing.app.Messages
import processing.app.Platform
import processing.app.PlatformStart
import processing.app.watchFile
import java.io.File
import java.io.InputStream
import java.util.*

class Locale(language: String = "") : Properties() {
    init {
        val locale = java.util.Locale.getDefault()
        load(ClassLoader.getSystemResourceAsStream("PDE.properties"))
        load(ClassLoader.getSystemResourceAsStream("PDE_${locale.language}.properties") ?: InputStream.nullInputStream())
        load(ClassLoader.getSystemResourceAsStream("PDE_${locale.toLanguageTag()}.properties") ?: InputStream.nullInputStream())
        load(ClassLoader.getSystemResourceAsStream("PDE_${language}.properties") ?: InputStream.nullInputStream())
    }

    @Deprecated("Use get instead", ReplaceWith("get(key)"))
    override fun getProperty(key: String?, default: String): String {
        val value = super.getProperty(key, default)
        if(value == default) Messages.log("Missing translation for $key")
        return value
    }
    operator fun get(key: String): String = getProperty(key, key)
}
val LocalLocale = compositionLocalOf { Locale() }
@Composable
fun LocaleProvider(content: @Composable () -> Unit) {
    PlatformStart()

    val settingsFolder = Platform.getSettingsFolder()
    val languageFile = File(settingsFolder, "language.txt")
    watchFile(languageFile)

    val locale = Locale(languageFile.readText().substring(0, 2))
    CompositionLocalProvider(LocalLocale provides locale) {
        content()
    }
}