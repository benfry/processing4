package processing.app.contrib.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window

//--processing-blue-light: #82afff;
//--processing-blue-mid: #0564ff;
//--processing-blue-deep: #1e32aa;
//--processing-blue-dark: #0f195a;
//--processing-blue: #0251c8;

@Composable
fun ContributionPane(contribution: Contribution?, onClose: () -> Unit) {
    if(contribution == null) {
        return
    }
    val typeName = when(contribution.type) {
        Type.library -> "Library"
        Type.tool -> "Tool"
        Type.examples -> "Example"
        Type.mode -> "Mode"
    }
    Window(
        title = "${typeName}: ${contribution.name}",
        onCloseRequest = onClose,
        onKeyEvent = {
            if(it.key == Key.Escape) {
                onClose()
                true
            } else {
                false
            }
        }
    ){
        Box {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(typeName, style = TextStyle(fontSize = 16.sp))
                Text(contribution.name ?: "", style = TextStyle(fontSize = 20.sp))
                Row(modifier = Modifier.padding(0.dp, 10.dp)) {
                    val action = when(contribution.isUpdate) {
                        true -> "Update"
                        false, null -> when(contribution.isInstalled) {
                            true -> "Uninstall"
                            false, null -> "Install"
                        }
                    }
                    Text(action,
                        style = TextStyle(fontSize = 14.sp, color = Color.White),
                        modifier = Modifier
                            .clickable {

                            }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .background(Color(0xff0251c8))
                            .padding(24.dp,12.dp)
                    )
                }
                Text(contribution.paragraph ?: "", style = TextStyle(fontSize = 14.sp))
            }
        }
    }

}