package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = CyanBright,
    onPrimary = ObsidianBg,
    primaryContainer = CyanContainerDark,
    onPrimaryContainer = CyanBright,
    secondary = IndigoBright,
    onSecondary = ObsidianBg,
    secondaryContainer = IndigoContainerDark,
    onSecondaryContainer = IndigoBright,
    tertiary = TealBright,
    onTertiary = ObsidianBg,
    tertiaryContainer = TealContainerDark,
    onTertiaryContainer = TealBright,
    background = ObsidianBg,
    onBackground = TextLight,
    surface = ObsidianSurface,
    onSurface = TextLight,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextLightMuted,
    surfaceContainerLow = ObsidianCard,
    outline = OutlineDark,
    error = CrimsonError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = CyanDeep,
    onPrimary = Color.White,
    primaryContainer = CyanContainerLight,
    onPrimaryContainer = CyanDeep,
    secondary = IndigoStudio,
    onSecondary = Color.White,
    secondaryContainer = IndigoContainerLight,
    onSecondaryContainer = IndigoStudio,
    tertiary = TealEmerald,
    onTertiary = Color.White,
    tertiaryContainer = TealContainerLight,
    onTertiaryContainer = TealEmerald,
    background = PorcelainBg,
    onBackground = TextDark,
    surface = PorcelainSurface,
    onSurface = TextDark,
    surfaceVariant = PorcelainSurfaceVariant,
    onSurfaceVariant = TextDarkMuted,
    surfaceContainerLow = PorcelainSurface,
    outline = OutlineLight,
    error = CrimsonError,
    onError = Color.White
)

val ScrollShotShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ScrollShotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ScrollShotTypography,
        shapes = ScrollShotShapes,
        content = content
    )
}
