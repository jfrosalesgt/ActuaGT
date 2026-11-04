package com.actuagt.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ActuaGT visual identity: deep petrol green (civic trust) with an amber
// action accent. Warm neutral surfaces in light mode, soft graphite in dark.
object ActuaGTColors {
    val Petrol = Color(0xFF0B6B57)
    val PetrolDeep = Color(0xFF084C3E)
    val PetrolSoft = Color(0xFFD9ECE5)
    val Amber = Color(0xFFD97706)
    val AmberSoft = Color(0xFFFBEED3)
    val Paper = Color(0xFFDCE9E3)
    val CardLight = Color(0xFFFFFFFF)
    val InkLight = Color(0xFF1A1C1B)
    val InkSoftLight = Color(0xFF4A4F4D)
    val Graphite = Color(0xFF0C1A16)
    val CardDark = Color(0xFF16211D)
    val InkDark = Color(0xFFE8ECE9)
    val InkSoftDark = Color(0xFFAEB6B1)
    val PetrolBright = Color(0xFF6FC7A6)
    val AmberBright = Color(0xFFFBBF24)
}

private val LightScheme = lightColorScheme(
    primary = ActuaGTColors.Petrol,
    onPrimary = Color.White,
    primaryContainer = ActuaGTColors.PetrolSoft,
    onPrimaryContainer = ActuaGTColors.PetrolDeep,
    secondary = ActuaGTColors.Amber,
    onSecondary = Color.White,
    secondaryContainer = ActuaGTColors.AmberSoft,
    onSecondaryContainer = Color(0xFF7C4A03),
    background = ActuaGTColors.Paper,
    onBackground = ActuaGTColors.InkLight,
    surface = ActuaGTColors.CardLight,
    onSurface = ActuaGTColors.InkLight,
    surfaceVariant = Color(0xFFC9DCD2),
    onSurfaceVariant = ActuaGTColors.InkSoftLight,
    error = Color(0xFFB3261E),
)

private val DarkScheme = darkColorScheme(
    primary = ActuaGTColors.PetrolBright,
    onPrimary = ActuaGTColors.PetrolDeep,
    primaryContainer = Color(0xFF0E3B31),
    onPrimaryContainer = ActuaGTColors.PetrolSoft,
    secondary = ActuaGTColors.AmberBright,
    onSecondary = Color(0xFF3A2703),
    secondaryContainer = Color(0xFF4A3205),
    onSecondaryContainer = ActuaGTColors.AmberSoft,
    background = ActuaGTColors.Graphite,
    onBackground = ActuaGTColors.InkDark,
    surface = ActuaGTColors.CardDark,
    onSurface = ActuaGTColors.InkDark,
    surfaceVariant = Color(0xFF1E2E28),
    onSurfaceVariant = ActuaGTColors.InkSoftDark,
    error = Color(0xFFF2B8B5),
)

private val ActuaGTTypography = Typography(
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 19.sp,
    ),
)

private val ActuaGTShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

@Composable
fun ActuaGTTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = ActuaGTTypography,
        shapes = ActuaGTShapes,
        content = content,
    )
}
