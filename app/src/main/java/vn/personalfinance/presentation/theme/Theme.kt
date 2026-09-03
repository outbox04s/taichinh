package vn.personalfinance.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary=Color(0xFF1769E0),onPrimary=Color.White,primaryContainer=Color(0xFFDCEBFF),onPrimaryContainer=Color(0xFF092C62),
    secondary=Color(0xFF386FC7),background=Color(0xFFF8FBFF),onBackground=Color(0xFF092C62),surface=Color(0xFFF8FBFF),onSurface=Color(0xFF092C62),
    surfaceVariant=Color(0xFFEAF3FF),onSurfaceVariant=Color(0xFF426188),outline=Color(0xFF90A9C9),
)
private val DarkColors = darkColorScheme(primary = Color(0xFF75ADFF), secondary = Color(0xFF9EC5FF))

@Composable
fun FinanceTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
