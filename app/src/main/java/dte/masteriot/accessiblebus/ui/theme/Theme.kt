package dte.masteriot.accessiblebus.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val NormalColorScheme = lightColorScheme(
    primary = PrimaryNormal,
    background = BackgroundNormal,
    surface = SurfaceNormal,
    onBackground = TextNormal,
    onSurface = TextNormal
)

private val HighContrastColorScheme = darkColorScheme(
    primary = PrimaryHighContrast,
    background = BackgroundHighContrast,
    surface = SurfaceHighContrast,
    onBackground = TextHighContrast,
    onSurface = TextHighContrast
)

@Composable
fun AppTheme(
    useHighContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (useHighContrast) HighContrastColorScheme else NormalColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}