package com.qi.smbshare.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * 验证文件类型判断与格式化工具的边界行为。
 */
class FileTypeHelperTest {

    @Test
    fun `isApkFile 对 apk 扩展名（大小写均可）返回 true`() {
        assertTrue(FileTypeHelper.isApkFile("app.apk"))
        assertTrue(FileTypeHelper.isApkFile("App.APK"))
        assertTrue(FileTypeHelper.isApkFile("release-v1.0.apk"))
    }

    @Test
    fun `isApkFile 对非 apk 文件返回 false`() {
        assertFalse(FileTypeHelper.isApkFile("archive.zip"))
        assertFalse(FileTypeHelper.isApkFile("apk"))
        assertFalse(FileTypeHelper.isApkFile("app.apk.bak"))
    }

    @Test
    fun `isImageFile 对常见图片扩展名返回 true`() {
        listOf("photo.jpg", "photo.JPEG", "image.png", "anim.gif", "bitmap.bmp", "picture.webp")
            .forEach { name -> assertTrue("$name 应被识别为图片", FileTypeHelper.isImageFile(name)) }
    }

    @Test
    fun `isImageFile 对非图片文件返回 false`() {
        assertFalse(FileTypeHelper.isImageFile("document.pdf"))
        assertFalse(FileTypeHelper.isImageFile("video.mp4"))
        assertFalse(FileTypeHelper.isImageFile("app.apk"))
    }

    @Test
    fun `isDocumentFile 对常见文档扩展名返回 true`() {
        listOf("report.pdf", "word.doc", "word.docx", "excel.xls", "excel.xlsx",
            "slide.ppt", "slide.pptx", "notes.txt")
            .forEach { name -> assertTrue("$name 应被识别为文档", FileTypeHelper.isDocumentFile(name)) }
    }

    @Test
    fun `isDocumentFile 对非文档文件返回 false`() {
        assertFalse(FileTypeHelper.isDocumentFile("photo.jpg"))
        assertFalse(FileTypeHelper.isDocumentFile("app.apk"))
        assertFalse(FileTypeHelper.isDocumentFile("video.mp4"))
    }

    @Test
    fun `formatFileSize 小于 1KB 时显示字节`() {
        assertEquals("512 B", FileTypeHelper.formatFileSize(512))
        assertEquals("0 B", FileTypeHelper.formatFileSize(0))
    }

    @Test
    fun `formatFileSize KB 级别格式化`() {
        assertEquals("1.00 KB", FileTypeHelper.formatFileSize(1024))
        assertEquals("1.50 KB", FileTypeHelper.formatFileSize(1536))
    }

    @Test
    fun `formatFileSize MB 级别格式化`() {
        assertEquals("1.00 MB", FileTypeHelper.formatFileSize(1024L * 1024))
        assertEquals("2.50 MB", FileTypeHelper.formatFileSize(1024L * 1024 * 5 / 2))
    }

    @Test
    fun `formatFileSize GB 级别格式化`() {
        assertEquals("1.00 GB", FileTypeHelper.formatFileSize(1024L * 1024 * 1024))
    }

    @Test
    fun `formatTimestamp 返回 yyyy-MM-dd HH 格式字符串`() {
        val result = FileTypeHelper.formatTimestamp(1700000000000L)
        assertTrue("格式应为 yyyy-MM-dd HH:mm", result.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")))
    }

    @Test
    fun `formatDate 返回 yyyy-MM-dd HH 格式字符串`() {
        val result = FileTypeHelper.formatDate(Date(1700000000000L))
        assertTrue("格式应为 yyyy-MM-dd HH:mm", result.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")))
    }
}
