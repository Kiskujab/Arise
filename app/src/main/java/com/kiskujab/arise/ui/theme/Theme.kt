package com.kiskujab.arise.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kiskujab.arise.R

object AriseColors {
    val Bg = Color(0xFF04060C)
    val BgElevated = Color(0xFF0A1020)
    val Panel = Color(0xE60A1426)
    val PanelSolid = Color(0xFF0B1528)
    val Blue = Color(0xFF4DB8FF)
    val BlueDeep = Color(0xFF2F6BFF)
    val Cyan = Color(0xFF7DF9FF)
    val Text = Color(0xFFE6F1FF)
    val TextDim = Color(0xFF8AA0BF)
    val TextFaint = Color(0xFF4A5A75)
    val Red = Color(0xFFFF3B5C)
    val Gold = Color(0xFFFFC857)
    val Purple = Color(0xFFB388FF)
    val Green = Color(0xFF4CD964)
}

object AriseFonts {
    val Orbitron = FontFamily(
        Font(R.font.orbitron_bold, FontWeight.Bold),
        Font(R.font.orbitron_black, FontWeight.Black),
    )
    val Rajdhani = FontFamily(
        Font(R.font.rajdhani_medium, FontWeight.Medium),
        Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
        Font(R.font.rajdhani_bold, FontWeight.Bold),
    )
}

object AriseType {
    val display = TextStyle(fontFamily = AriseFonts.Orbitron, fontWeight = FontWeight.Black, fontSize = 44.sp, color = AriseColors.Text, letterSpacing = 2.sp)
    val title = TextStyle(fontFamily = AriseFonts.Orbitron, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AriseColors.Text, letterSpacing = 2.sp)
    val header = TextStyle(fontFamily = AriseFonts.Orbitron, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AriseColors.Blue, letterSpacing = 3.sp)
    val label = TextStyle(fontFamily = AriseFonts.Rajdhani, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AriseColors.TextDim, letterSpacing = 1.5.sp)
    val body = TextStyle(fontFamily = AriseFonts.Rajdhani, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = AriseColors.Text, lineHeight = 22.sp)
    val bodyBold = body.copy(fontWeight = FontWeight.Bold)
    val number = TextStyle(fontFamily = AriseFonts.Orbitron, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AriseColors.Text)
    val small = TextStyle(fontFamily = AriseFonts.Rajdhani, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = AriseColors.TextDim, lineHeight = 18.sp)
}

@Composable
fun AriseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AriseColors.Blue,
            onPrimary = AriseColors.Bg,
            secondary = AriseColors.Cyan,
            background = AriseColors.Bg,
            surface = AriseColors.PanelSolid,
            onSurface = AriseColors.Text,
            onBackground = AriseColors.Text,
            error = AriseColors.Red,
            surfaceVariant = AriseColors.BgElevated,
            onSurfaceVariant = AriseColors.TextDim,
            outline = AriseColors.Blue,
        ),
        typography = Typography(
            bodyLarge = AriseType.body,
            bodyMedium = AriseType.body,
            labelLarge = AriseType.label,
            titleLarge = AriseType.title,
        ),
        content = content,
    )
}
