package vn.personalfinance.presentation.components.glass

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import vn.personalfinance.presentation.theme.LiquidGlassColors

data class GlassNavigationItem(val route:String,val label:String,val icon:ImageVector)

@Composable
fun LiquidGlassFloatingBottomBar(
    items:List<GlassNavigationItem>,
    currentRoute:String?,
    onItemSelected:(GlassNavigationItem)->Unit,
    modifier:Modifier=Modifier,
    accent:Color=LiquidGlassColors.Blue,
    reduceMotion:Boolean=false,
    reduceTransparency:Boolean=false,
){
    if(items.isEmpty())return
    val selectedIndex=items.indexOfFirst{it.route==currentRoute}.coerceAtLeast(0)
    BoxWithConstraints(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=16.dp,vertical=10.dp).height(86.dp)){
        val itemWidth=maxWidth/items.size
        val bubbleSize=58.dp
        val targetX=itemWidth*selectedIndex+(itemWidth-bubbleSize)/2
        val density=LocalDensity.current
        val targetXPx=with(density){targetX.toPx()}
        val bubbleX by animateFloatAsState(targetXPx,if(reduceMotion)snap() else tween(210,easing=FastOutSlowInEasing),label="activeBubbleX")
        val capsuleShape=RoundedCornerShape(32.dp)
        val capsuleColor=if(reduceTransparency)Color(0xFFF4F8FF)else Color.White.copy(alpha=.70f)
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(66.dp)
                .shadow(18.dp,capsuleShape,ambientColor=accent.copy(alpha=.13f),spotColor=accent.copy(alpha=.18f))
                .background(Brush.linearGradient(listOf(Color.White.copy(alpha=.92f),capsuleColor,LiquidGlassColors.BackgroundSecondary.copy(alpha=.68f))),capsuleShape)
                .border(BorderStroke(1.dp,Brush.verticalGradient(listOf(Color.White,accent.copy(alpha=.20f)))),capsuleShape)
        ){
            Row(Modifier.fillMaxSize().padding(horizontal=5.dp)){
                items.forEachIndexed{index,item->
                    val active=index==selectedIndex
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clickable(role=Role.Tab,indication=null,interactionSource=remember{MutableInteractionSource()}){
                            if(!active)onItemSelected(item)
                        }.semantics{selected=active;role=Role.Tab;contentDescription=item.label},
                        contentAlignment=Alignment.Center,
                    ){
                        if(active){Text(item.label.uppercase(),Modifier.align(Alignment.BottomCenter).padding(bottom=7.dp),color=accent,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)}
                        else Icon(item.icon,item.label,Modifier.size(24.dp),tint=LiquidGlassColors.TextSecondary.copy(alpha=.70f))
                    }
                }
            }
        }
        Surface(
            modifier=Modifier.graphicsLayer{translationX=bubbleX}.size(bubbleSize).shadow(14.dp,CircleShape,ambientColor=accent.copy(alpha=.22f),spotColor=accent.copy(alpha=.25f)),
            shape=CircleShape,
            color=if(reduceTransparency)Color.White else Color.White.copy(alpha=.82f),
            border=BorderStroke(1.4.dp,Brush.linearGradient(listOf(Color.White,accent.copy(alpha=.46f),Color.White))),
        ){
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(accent.copy(alpha=.12f),Color.Transparent))),contentAlignment=Alignment.Center){
                Icon(items[selectedIndex].icon,items[selectedIndex].label,Modifier.size(25.dp),tint=accent)
            }
        }
    }
}

private val previewItems=listOf(
    GlassNavigationItem("overview","Tổng quan",Icons.Rounded.Home),GlassNavigationItem("transactions","Giao dịch",Icons.Rounded.SwapHoriz),GlassNavigationItem("accounts","Tài khoản",Icons.Rounded.AccountBalanceWallet),GlassNavigationItem("debts","Khoản nợ",Icons.Rounded.CreditCard),GlassNavigationItem("reports","Báo cáo",Icons.Rounded.BarChart),
)
@Preview(name="Bottom bar light",showBackground=true,backgroundColor=0xFFF8FBFF,widthDp=390) @Composable private fun BottomBarLightPreview(){MaterialTheme{LiquidGlassFloatingBottomBar(previewItems,"overview",{})}}
@Preview(name="Bottom bar dark",showBackground=true,backgroundColor=0xFF07142A,widthDp=390) @Composable private fun BottomBarDarkPreview(){MaterialTheme(colorScheme=darkColorScheme()){LiquidGlassFloatingBottomBar(previewItems,"debts",{},accent=Color(0xFF55E6C1))}}
@Preview(name="Bottom bar small",showBackground=true,backgroundColor=0xFFF8FBFF,widthDp=320) @Composable private fun BottomBarSmallPreview(){MaterialTheme{LiquidGlassFloatingBottomBar(previewItems,"transactions",{})}}
