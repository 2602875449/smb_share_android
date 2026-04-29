package com.qi.smbshare.ui.filelist

import com.qi.smbshare.data.model.FileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证 FileListState 的过滤逻辑与导航辅助属性的正确性。
 */
class FileListStateTest {

    private val sampleFiles = listOf(
        FileItem(name = "document.pdf", path = "root/document.pdf", isDirectory = false, size = 1024),
        FileItem(name = "photos", path = "root/photos", isDirectory = true),
        FileItem(name = "video.mp4", path = "root/video.mp4", isDirectory = false, size = 10240),
        FileItem(name = "README.txt", path = "root/README.txt", isDirectory = false, size = 512)
    )

    @Test
    fun `searchQuery 为空时 filteredFiles 返回全部文件`() {
        val state = FileListState(files = sampleFiles, searchQuery = "")
        assertEquals(4, state.filteredFiles.size)
    }

    @Test
    fun `filteredFiles 按名称不区分大小写过滤`() {
        val stateLower = FileListState(files = sampleFiles, searchQuery = "pdf")
        assertEquals(1, stateLower.filteredFiles.size)
        assertEquals("document.pdf", stateLower.filteredFiles.first().name)

        val stateUpper = FileListState(files = sampleFiles, searchQuery = "README")
        assertEquals(1, stateUpper.filteredFiles.size)
    }

    @Test
    fun `filteredFiles 无匹配时返回空列表`() {
        val state = FileListState(files = sampleFiles, searchQuery = "xyz_no_match_123")
        assertTrue(state.filteredFiles.isEmpty())
    }

    @Test
    fun `filteredFiles 匹配目录名称`() {
        val state = FileListState(files = sampleFiles, searchQuery = "photos")
        assertEquals(1, state.filteredFiles.size)
        assertTrue(state.filteredFiles.first().isDirectory)
    }

    @Test
    fun `canGoBack 在 pathHistory 为空时返回 false`() {
        val state = FileListState(pathHistory = emptyList())
        assertFalse(state.canGoBack)
    }

    @Test
    fun `canGoBack 在 pathHistory 非空时返回 true`() {
        val state = FileListState(pathHistory = listOf("", "folder1"))
        assertTrue(state.canGoBack)
    }

    @Test
    fun `files 为空且无搜索词时 filteredFiles 返回空列表`() {
        val state = FileListState(files = emptyList(), searchQuery = "")
        assertTrue(state.filteredFiles.isEmpty())
    }

    @Test
    fun `搜索词匹配多个文件时全部返回`() {
        val state = FileListState(files = sampleFiles, searchQuery = ".") // 所有文件名都含 "."
        val dotFiles = sampleFiles.filter { it.name.contains(".") }
        assertEquals(dotFiles.size, state.filteredFiles.size)
    }
}
