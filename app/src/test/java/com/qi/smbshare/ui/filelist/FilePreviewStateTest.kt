package com.qi.smbshare.ui.filelist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证预览相关状态转换的正确性：
 * - [PreviewState] 各子类携带的数据
 * - [FileListState] 预览字段的初始值和更新行为
 */
class FilePreviewStateTest {

    // ── PreviewState ──────────────────────────────────────────────────────────

    @Test
    fun `PreviewState 初始为 Idle`() {
        val state = FileListState()
        assertTrue(state.previewState is PreviewState.Idle)
    }

    @Test
    fun `PreviewState Loading 无附带数据`() {
        val state = FileListState(previewState = PreviewState.Loading)
        assertTrue(state.previewState is PreviewState.Loading)
    }

    @Test
    fun `PreviewState ImageReady 持有字节数组`() {
        val bytes = byteArrayOf(1, 2, 3)
        val previewState = PreviewState.ImageReady(bytes)
        assertEquals(3, previewState.bytes.size)
    }

    @Test
    fun `PreviewState TextReady 默认不截断`() {
        val textState = PreviewState.TextReady("hello")
        assertEquals("hello", textState.content)
        assertFalse(textState.isTruncated)
    }

    @Test
    fun `PreviewState TextReady 可标记截断`() {
        val textState = PreviewState.TextReady("partial content", isTruncated = true)
        assertTrue(textState.isTruncated)
    }

    @Test
    fun `PreviewState Error 持有错误消息`() {
        val errorState = PreviewState.Error("网络中断")
        assertEquals("网络中断", errorState.message)
    }

    // ── FileListState 预览字段 ────────────────────────────────────────────────

    @Test
    fun `默认状态下预览字段为空`() {
        val state = FileListState()
        assertNull(state.previewFileName)
        assertTrue(state.previewState is PreviewState.Idle)
    }

    @Test
    fun `设置 previewFileName 后状态正确`() {
        val state = FileListState(
            previewFileName = "photo.jpg",
            previewState = PreviewState.Loading
        )
        assertEquals("photo.jpg", state.previewFileName)
        assertTrue(state.previewState is PreviewState.Loading)
    }

    @Test
    fun `关闭预览后 previewFileName 恢复 null`() {
        val state = FileListState(
            previewFileName = "photo.jpg",
            previewState = PreviewState.ImageReady(byteArrayOf())
        ).copy(
            previewFileName = null,
            previewState = PreviewState.Idle
        )
        assertNull(state.previewFileName)
        assertTrue(state.previewState is PreviewState.Idle)
    }

    @Test
    fun `预览状态转换到 TextReady 时列表状态不受影响`() {
        val originalFiles = listOf(
            com.qi.smbshare.data.model.FileItem("readme.txt", "path", false, 100)
        )
        val state = FileListState(files = originalFiles).copy(
            previewFileName = "readme.txt",
            previewState = PreviewState.TextReady("内容", false)
        )
        // 文件列表数据不变
        assertEquals(1, state.files.size)
        assertEquals("readme.txt", state.files.first().name)
        // 预览状态已更新
        val text = state.previewState as PreviewState.TextReady
        assertEquals("内容", text.content)
    }
}
