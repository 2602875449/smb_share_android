package com.qi.smbshare.ui.filelist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 验证视频预览相关状态的数据正确性与生命周期转换行为。
 * 覆盖 VideoDownloading / VideoReady 两种新增状态以及
 * FileListState 在视频预览场景下的字段变化。
 */
class VideoPreviewStateTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // ── VideoDownloading ──────────────────────────────────────────────────────

    @Test
    fun `VideoDownloading 持有正确的进度值`() {
        val state = PreviewState.VideoDownloading(0.42f)
        assertEquals(0.42f, state.progress, 0.001f)
    }

    @Test
    fun `VideoDownloading 初始进度为 0`() {
        val state = PreviewState.VideoDownloading(0f)
        assertEquals(0f, state.progress, 0.001f)
    }

    @Test
    fun `VideoDownloading 进度 100 百分比`() {
        val state = PreviewState.VideoDownloading(1f)
        assertEquals(1f, state.progress, 0.001f)
    }

    @Test
    fun `VideoDownloading 进度 -1 表示文件大小未知`() {
        // progress = -1 时 UI 应展示无确定进度（spinner）
        val state = PreviewState.VideoDownloading(-1f)
        assertTrue(state.progress < 0f)
    }

    // ── VideoReady ────────────────────────────────────────────────────────────

    @Test
    fun `VideoReady 持有正确的缓存文件引用`() {
        val file = tempFolder.newFile("video_preview_test.mp4")
        val state = PreviewState.VideoReady(file)
        assertEquals(file.absolutePath, state.cacheFile.absolutePath)
        assertTrue(state.cacheFile.exists())
    }

    @Test
    fun `VideoReady 是 PreviewState 的子类型`() {
        val file = tempFolder.newFile("video.mp4")
        val state: PreviewState = PreviewState.VideoReady(file)
        assertTrue(state is PreviewState.VideoReady)
    }

    // ── FileListState 视频预览字段 ─────────────────────────────────────────────

    @Test
    fun `视频缓存中状态 fileListState 包含正确的 previewFileName`() {
        val state = FileListState(
            previewFileName = "movie.mkv",
            previewState = PreviewState.VideoDownloading(0.25f)
        )
        assertEquals("movie.mkv", state.previewFileName)
        val downloading = state.previewState as PreviewState.VideoDownloading
        assertEquals(0.25f, downloading.progress, 0.001f)
    }

    @Test
    fun `视频就绪状态 fileListState 包含正确的缓存文件`() {
        val file = tempFolder.newFile("cached_video.mp4")
        val state = FileListState(
            previewFileName = "cached_video.mp4",
            previewState = PreviewState.VideoReady(file)
        )
        val ready = state.previewState as PreviewState.VideoReady
        assertEquals(file.absolutePath, ready.cacheFile.absolutePath)
    }

    @Test
    fun `关闭视频预览后 previewFileName 恢复 null 且状态重置为 Idle`() {
        val file = tempFolder.newFile("video.mp4")
        val state = FileListState(
            previewFileName = "video.mp4",
            previewState = PreviewState.VideoReady(file)
        ).copy(
            previewFileName = null,
            previewState = PreviewState.Idle
        )
        assertNull(state.previewFileName)
        assertTrue(state.previewState is PreviewState.Idle)
    }

    @Test
    fun `视频预览期间文件列表数据不受影响`() {
        val files = listOf(
            com.qi.smbshare.data.model.FileItem("movie.mkv", "root/movie.mkv", false, 1024L * 1024 * 500)
        )
        val state = FileListState(files = files).copy(
            previewFileName = "movie.mkv",
            previewState = PreviewState.VideoDownloading(0.5f)
        )
        assertEquals(1, state.files.size)
        assertEquals("movie.mkv", state.files.first().name)
        assertTrue(state.previewState is PreviewState.VideoDownloading)
    }

    @Test
    fun `从 VideoDownloading 转换到 VideoReady 时 previewFileName 不变`() {
        val file = tempFolder.newFile("film.mp4")
        val initial = FileListState(
            previewFileName = "film.mp4",
            previewState = PreviewState.VideoDownloading(0.99f)
        )
        val ready = initial.copy(previewState = PreviewState.VideoReady(file))
        // previewFileName 在整个预览生命周期内应保持不变
        assertEquals(initial.previewFileName, ready.previewFileName)
        assertTrue(ready.previewState is PreviewState.VideoReady)
    }

    @Test
    fun `视频预览错误时状态为 Error 且持有错误消息`() {
        val state = FileListState(
            previewFileName = "corrupt.avi",
            previewState = PreviewState.Error("视频文件损坏，无法播放")
        )
        val error = state.previewState as PreviewState.Error
        assertEquals("视频文件损坏，无法播放", error.message)
        // previewFileName 在错误状态下仍保留，方便 UI 显示文件名
        assertEquals("corrupt.avi", state.previewFileName)
    }

    @Test
    fun `createVideoPreviewCacheFile 会清理远端文件名中的路径分隔符`() {
        val file = createVideoPreviewCacheFile(
            cacheDir = tempFolder.root,
            fileName = "../unsafe/folder/movie.mp4",
            nowMillis = 123L
        )

        assertEquals(tempFolder.root.absolutePath, file.parentFile?.absolutePath)
        assertFalse(file.name.contains("/"))
        assertFalse(file.name.contains("\\"))
        assertTrue(file.name.startsWith("video_preview_123_"))
    }

    @Test
    fun `deleteReadyVideoCache 只删除 VideoReady 持有的缓存文件`() {
        val file = tempFolder.newFile("cached_video.mp4")

        assertTrue(deleteReadyVideoCache(PreviewState.VideoReady(file)))
        assertFalse(file.exists())
        assertFalse(deleteReadyVideoCache(PreviewState.VideoDownloading(0.5f)))
    }
}
