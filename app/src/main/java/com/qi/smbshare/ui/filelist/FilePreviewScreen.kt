package com.qi.smbshare.ui.filelist

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.qi.smbshare.R

/**
 * 文件全屏在线预览页（图片 / 文本 / 视频）。
 * 通过 [previewState] 驱动显示加载中、图片、文本或错误提示。
 * 图片支持双指缩放；文本支持垂直滚动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePreviewScreen(
    fileName: String,
    previewState: PreviewState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = fileName,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.preview_close)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            when (previewState) {
                is PreviewState.Loading, PreviewState.Idle -> {
                    CircularProgressIndicator()
                }

                is PreviewState.ImageReady -> {
                    ZoomableImage(cacheFile = previewState.cacheFile)
                }

                is PreviewState.TextReady -> {
                    TextPreview(
                        content = previewState.content,
                        isTruncated = previewState.isTruncated
                    )
                }

                is PreviewState.VideoDownloading -> {
                    VideoDownloadingProgress(progress = previewState.progress)
                }

                is PreviewState.VideoReady -> {
                    VideoPreview(cacheFile = previewState.cacheFile)
                }

                is PreviewState.Error -> {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = previewState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

/**
 * 视频内容正在从 SMB 流式写入本地缓存时显示的进度提示。
 * progress 为 0.0 ~ 1.0；文件大小未知时为 -1，展示无确定进度条。
 */
@Composable
private fun VideoDownloadingProgress(progress: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (progress >= 0f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(
                    R.string.preview_video_downloading,
                    (progress * 100).toInt()
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.preview_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 使用 ExoPlayer 播放本地缓存视频文件的播放器。
 * 自动播放并展示内置播放控制栏（播放/暂停/快进/快退/时间轴）。
 * Composable 离开时自动释放播放器资源。
 */
@Composable
private fun VideoPreview(cacheFile: java.io.File) {
    val context = LocalContext.current
    val exoPlayer = remember(cacheFile) {
        ExoPlayer.Builder(context).build().also { player ->
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(cacheFile)))
            player.playWhenReady = true
            player.prepare()
        }
    }
    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).also { playerView ->
                playerView.player = exoPlayer
                playerView.useController = true
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * 支持双指缩放的图片展示区域。
 * 使用 Coil 从本地缓存文件解码，避免大图一次性读入 JVM 内存。
 */
@Composable
private fun ZoomableImage(cacheFile: java.io.File) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    // 限制缩放范围在 0.5x 到 5x 之间
                    scale = (scale * zoom).coerceIn(0.5f, 5f)
                    // 仅在放大时允许平移，还原比例时重置偏移
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(cacheFile)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}

/**
 * 可垂直滚动的文本预览区域。
 * 超出 1 MB 截断时在顶部显示提示横幅。
 */
@Composable
private fun TextPreview(content: String, isTruncated: Boolean) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (isTruncated) {
            Text(
                text = stringResource(R.string.preview_text_truncated),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        )
    }
}
