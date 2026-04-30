package com.qi.smbshare.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow

private const val PredictiveBackMinScale = 0.985f
private const val PredictiveBackMinAlpha = 0.96f

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
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(),
        label = "predictiveBackProgress"
    )

    PredictiveBackHandler(enabled = enabled) { backEvents: Flow<BackEventCompat> ->
        try {
            backEvents.collect { backEvent ->
                progress = backEvent.progress.coerceIn(0f, 1f)
            }
            onBack()
        } catch (_: CancellationException) {
            // 手势取消时回弹到原位，不触发业务返回。
        } finally {
            progress = 0f
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val animatedModifier = Modifier.graphicsLayer {
            val scale = 1f - ((1f - PredictiveBackMinScale) * animatedProgress)
            scaleX = scale
            scaleY = scale
            alpha = 1f - ((1f - PredictiveBackMinAlpha) * animatedProgress)
        }

        content(animatedModifier)
    }
}
