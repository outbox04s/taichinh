package vn.personalfinance.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object LiquidGlassColors {
    val Background = Color(0xFFF8FBFF)
    val BackgroundSecondary = Color(0xFFEAF3FF)
    val Blue = Color(0xFF1769E0)
    val BlueBright = Color(0xFF4F9CFF)
    val BlueDeep = Color(0xFF092C62)
    val Mint = Blue
    val Violet = Color(0xFF386FC7)
    val Coral = Color(0xFF255EA8)
    val Amber = Color(0xFF5B88C9)
    val TextPrimary = BlueDeep
    val TextSecondary = BlueDeep.copy(alpha = .62f)
    val GlassPrimary = Color.White.copy(alpha = .72f)
    val GlassSecondary = Color.White.copy(alpha = .62f)
    val GlassTertiary = Color.White.copy(alpha = .52f)
    val Border = Blue.copy(alpha = .22f)
    val Highlight = Color.White.copy(alpha = .96f)
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
