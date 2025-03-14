package processing.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

class ExamplesLibrary{
    companion object{
        @JvmStatic
        fun main(args: Array<String>) = application {
            val active = remember { mutableStateOf(true) }
            if(!active.value){
                Window(onCloseRequest = ::exitApplication) {

                }
                return@application
            }
            Window(
                onCloseRequest = { active.value = false },
            ) {
                ExamplesLibrary()
            }
        }
    }

}

