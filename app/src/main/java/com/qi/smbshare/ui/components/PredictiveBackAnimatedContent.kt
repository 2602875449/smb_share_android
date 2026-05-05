package com.qi.smbshare.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow

// 缩放到 90%，与系统原生预见式返回视觉接近
private const val PredictiveBackMinScale = 0.9f
private const val PredictiveBackMinAlpha = 0.85f
// X 轴最大偏移量（dp）：跟随手势边缘方向轻微横移
private const val PredictiveBackMaxOffsetXDp = 48f
// Y 轴最大偏移量（dp）：轻微下沉增加层次感
private const val PredictiveBackMaxOffsetYDp = 16f
// 最大圆角（dp）：随进度平滑过渡
private const val PredictiveBackMaxCornerDp = 28f

/**
 * 为手动状态机页面补充预测式返回进度动画，同时保持普通返回键兜底行为。
 * 只做轻量缩放和淡出，避免与系统边缘返回动画叠加后产生明显横向错位。
 */
@Composable
fun PredictiveBackAnimatedContent(
    enabled: Boolean = true,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    var progress by remember { mutableFloatStateOf(0f) }
    // 记录手势边缘：LEFT 从左划入，RIGHT 从右划入
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "predictiveBackProgress"
    )

    PredictiveBackHandler(enabled = enabled) { backEvents: Flow<BackEventCompat> ->
        try {
            backEvents.collect { backEvent ->
                progress = backEvent.progress.coerceIn(0f, 1f)
                swipeEdge = backEvent.swipeEdge
            }
            onBack()
        } catch (_: CancellationException) {
            // 手势取消时回弹到原位，不触发业务返回。
        } finally {
            progress = 0f
        }
    }

    // 提前计算圆角，避免在 graphicsLayer lambda 中反复分配 Dp 对象
    val cornerRadius = (PredictiveBackMaxCornerDp * animatedProgress).dp

    Box(modifier = modifier.fillMaxSize()) {
        val animatedModifier = Modifier.graphicsLayer {
            val scale = 1f - ((1f - PredictiveBackMinScale) * animatedProgress)
            scaleX = scale
            scaleY = scale
            alpha = 1f - ((1f - PredictiveBackMinAlpha) * animatedProgress)

            // 左划向右偏移，右划向左偏移，模拟系统「内容被推向屏幕中心」的视感
            val offsetSign = if (swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
            translationX = offsetSign * PredictiveBackMaxOffsetXDp * animatedProgress * density
            translationY = PredictiveBackMaxOffsetYDp * animatedProgress * density

            shape = RoundedCornerShape(cornerRadius)
            clip = animatedProgress > 0f
        }

        content(animatedModifier)
    }
}
