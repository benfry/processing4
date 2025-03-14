package processing.app.ui.theme

import androidx.compose.material.MaterialTheme.typography
import androidx.compose.material.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp

val processingFont = FontFamily(
    Font(
        resource = "ProcessingSans-Regular.ttf",
        weight = FontWeight.Normal,
        style = FontStyle.Normal
    ),
    Font(
        resource = "ProcessingSans-Bold.ttf",
        weight = FontWeight.Bold,
        style = FontStyle.Normal
    )
)

val Typography = Typography(
    body1 = TextStyle(
        fontFamily = processingFont,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 16.sp
    ),
    subtitle1 = TextStyle(
        fontFamily = processingFont,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp
    )
)