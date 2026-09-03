package vn.personalfinance.presentation.components.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.personalfinance.presentation.theme.LiquidGlassColors
import vn.personalfinance.presentation.theme.LiquidGlassMotion
import vn.personalfinance.presentation.theme.LiquidGlassShapes

enum class GlassLevel { Primary, Secondary, Tertiary }

@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassLevel.Secondary,
    reduceTransparency: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val base = when (level) {
        GlassLevel.Primary -> LiquidGlassColors.GlassPrimary
        GlassLevel.Secondary -> LiquidGlassColors.GlassSecondary
        GlassLevel.Tertiary -> LiquidGlassColors.GlassTertiary
    }
    val color = if (reduceTransparency) LiquidGlassColors.BackgroundSecondary else base
    val radius = if (level == GlassLevel.Primary) LiquidGlassShapes.PrimaryRadius else LiquidGlassShapes.CardRadius
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .shadow(if (level == GlassLevel.Primary) 22.dp else 14.dp, shape, ambientColor = LiquidGlassColors.Blue.copy(alpha = .16f), spotColor = LiquidGlassColors.Blue.copy(alpha = .20f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(LiquidGlassColors.Highlight.copy(alpha = .78f), color, LiquidGlassColors.BackgroundSecondary.copy(alpha = .42f))))
            .border(BorderStroke(1.25.dp, Brush.linearGradient(listOf(LiquidGlassColors.Highlight, LiquidGlassColors.Border, LiquidGlassColors.Blue.copy(alpha = .34f)))), shape),
        content = content,
    )
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, level: GlassLevel = GlassLevel.Secondary, content: @Composable ColumnScope.() -> Unit) {
    LiquidGlassSurface(modifier, level) { Column(Modifier.fillMaxWidth().padding(18.dp), content = content) }
}

@Composable
fun GlassIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.size(48.dp), shape = CircleShape, color = LiquidGlassColors.GlassPrimary, border = BorderStroke(1.dp, LiquidGlassColors.Border)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, tint = LiquidGlassColors.TextPrimary) }
    }
}

@Composable
fun GlassChip(text: String, selected: Boolean = false, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color by animateColorAsState(if (selected) LiquidGlassColors.Blue.copy(alpha = .14f) else LiquidGlassColors.GlassTertiary, tween(LiquidGlassMotion.Fast), label = "chip")
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(LiquidGlassShapes.ChipRadius), color = color, border = BorderStroke(1.dp, if (selected) LiquidGlassColors.Mint.copy(alpha = .45f) else LiquidGlassColors.Border)) {
        Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) { Text(text, maxLines = 1, color = if(selected) LiquidGlassColors.Blue else LiquidGlassColors.TextPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = if(selected) FontWeight.Bold else FontWeight.Medium) }
    }
}

@Composable
fun <T> GlassSegmentedControl(items: List<Pair<T, String>>, selected: T, onSelected: (T) -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { (value, label) -> GlassChip(label, selected == value, { onSelected(value) }) }
        trailing?.invoke()
    }
}

@Composable
fun GlassStatusPill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(LiquidGlassShapes.ChipRadius), color = color.copy(alpha = .16f), border = BorderStroke(1.dp, color.copy(alpha = .35f))) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GlassEmptyState(icon: ImageVector, title: String, message: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = CircleShape, color = LiquidGlassColors.Blue.copy(alpha = .10f)) { Icon(icon, null, Modifier.padding(12.dp).size(26.dp), tint = LiquidGlassColors.Blue) }
        Text(title.uppercase(), color = LiquidGlassColors.TextPrimary, fontWeight = FontWeight.Bold)
        Text(message, color = LiquidGlassColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        action?.let { TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(it, color = LiquidGlassColors.Blue) } }
    }
}

@Composable
fun GlassPressButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .975f else 1f, tween(120), label = "press")
    Button(onClick, modifier.scale(scale).heightIn(min = 48.dp), interactionSource = source, colors = ButtonDefaults.buttonColors(containerColor = LiquidGlassColors.Blue, contentColor = Color.White)) { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) }
}
