package vn.personalfinance.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object LiquidGlassColors {
    val Background = Color(0xFF070A12)
    val BackgroundSecondary = Color(0xFF0E1424)
    val Mint = Color(0xFF54E6C1)
    val Violet = Color(0xFF7C83FF)
    val Blue = Color(0xFF4895FF)
    val Coral = Color(0xFFFF817A)
    val Amber = Color(0xFFFFC56A)
    val TextPrimary = Color(0xFFF5F7FF)
    val TextSecondary = Color(0xFFAEB6C9)
    val GlassPrimary = Color.White.copy(alpha = .12f)
    val GlassSecondary = Color.White.copy(alpha = .085f)
    val GlassTertiary = Color.White.copy(alpha = .06f)
    val Border = Color.White.copy(alpha = .15f)
    val Highlight = Color.White.copy(alpha = .24f)
}

object LiquidGlassShapes {
    val PrimaryRadius = 30.dp
    val CardRadius = 26.dp
    val ChipRadius = 20.dp
    val BottomBarRadius = 34.dp
}

object LiquidGlassMotion {
    const val Standard = 260
    const val Fast = 220
}
