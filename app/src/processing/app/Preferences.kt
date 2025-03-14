package processing.app

import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.nio.file.*
import java.util.Properties


const val PREFERENCES_FILE_NAME = "preferences.txt"
const val DEFAULTS_FILE_NAME = "defaults.txt"

fun PlatformStart(){
    Platform.inst ?: Platform.init()
}

@Composable
fun loadPreferences(): Properties{
    PlatformStart()

    val settingsFolder = Platform.getSettingsFolder()
    val preferencesFile = settingsFolder.resolve(PREFERENCES_FILE_NAME)

    if(!preferencesFile.exists()){
        preferencesFile.createNewFile()
    }
    watchFile(preferencesFile)

    return Properties().apply {
        load(ClassLoader.getSystemResourceAsStream(DEFAULTS_FILE_NAME) ?: InputStream.nullInputStream())
        load(preferencesFile.inputStream())
    }
}

@Composable
fun watchFile(file: File): Any? {
    val scope = rememberCoroutineScope()
    var event by remember(file) {  mutableStateOf<WatchEvent<*>?> (null) }

    DisposableEffect(file){
        val fileSystem = FileSystems.getDefault()
        val watcher = fileSystem.newWatchService()
        var active = true

        val path = file.toPath()
        val parent = path.parent
        val key = parent.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY)
        scope.launch(Dispatchers.IO) {
            while (active) {
                for (modified in key.pollEvents()) {
                    if (modified.context() != path.fileName) continue
                    event = modified
                }
            }
        }
        onDispose {
            active = false
            key.cancel()
            watcher.close()
        }
    }
    return event
}
val LocalPreferences = compositionLocalOf<Properties> { error("No preferences provided") }
@Composable
fun PreferencesProvider(content: @Composable () -> Unit){
    val preferences = loadPreferences()
    CompositionLocalProvider(LocalPreferences provides preferences){
        content()
    }
}