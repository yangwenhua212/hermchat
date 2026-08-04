package com.eraherm.hermchat.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.UUID

enum class AttachmentKind {
    /** 压缩后的图片，走 vision */
    IMAGE,
    /** 纯文本，注入 prompt */
    TEXT,
    /**
     * PDF：磁盘上存首页 JPEG 供 vision，元数据仍是文件（界面按附件名显示，不当相册图）。
     */
    PDF,
}

data class ChatAttachment(
    val path: String,
    val mime: String,
    val name: String,
    val kind: AttachmentKind,
)

/**
 * 聊天附件：图片压缩；txt/md/json/csv 拷贝；PDF 渲染首页为图（当 vision 用）。
 */
class ChatAttachmentStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "chat_attachments").also { it.mkdirs() }

    fun importUri(uri: Uri): Result<ChatAttachment> = runCatching {
        val displayName = queryDisplayName(uri) ?: "附件"
        val mime = appContext.contentResolver.getType(uri).orEmpty()
        val lowerName = displayName.lowercase()
        when {
            mime.startsWith("image/") || IMAGE_EXT.any { lowerName.endsWith(it) } ->
                importImage(uri, displayName)
            mime == "application/pdf" || lowerName.endsWith(".pdf") ->
                importPdfAsImage(uri, displayName)
            isTextMime(mime, lowerName) ->
                importText(uri, displayName, mime.ifBlank { guessTextMime(lowerName) })
            else -> error("暂不支持此文件类型")
        }
    }

    fun importImage(uri: Uri): Result<ChatAttachment> = runCatching {
        importImage(uri, queryDisplayName(uri) ?: "图片.jpg")
    }

    fun toDataUrl(path: String, mime: String = "image/jpeg"): String? {
        val file = File(path)
        if (!file.exists() || file.length() < MIN_IMAGE_BYTES) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        val encoded = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val safeMime = mime.ifBlank { "image/jpeg" }
        return "data:$safeMime;base64,$encoded"
    }

    fun readTextLimited(path: String, maxChars: Int = MAX_TEXT_CHARS): String? {
        val file = File(path)
        if (!file.exists()) return null
        return runCatching {
            file.bufferedReader(Charset.forName("UTF-8")).use { reader ->
                buildString {
                    var count = 0
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (count > 0) append('\n')
                        append(line)
                        count += line.length + 1
                        if (count >= maxChars) {
                            append("\n…（已截断）")
                            break
                        }
                    }
                }
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun importImage(uri: Uri, displayName: String): ChatAttachment {
        val dest = File(root, "img_${UUID.randomUUID()}.jpg")
        val compressed = compressToJpeg(uri, dest) ?: error("无法读取图片")
        if (compressed.length() < MIN_IMAGE_BYTES) {
            compressed.delete()
            error("图片无效")
        }
        if (compressed.length() > MAX_IMAGE_BYTES) {
            compressed.delete()
            error("图片过大")
        }
        return ChatAttachment(
            path = compressed.absolutePath,
            mime = "image/jpeg",
            name = displayName,
            kind = AttachmentKind.IMAGE,
        )
    }

    private fun importText(uri: Uri, displayName: String, mime: String): ChatAttachment {
        val dest = File(root, "txt_${UUID.randomUUID()}.txt")
        var size = 0L
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output ->
                val buf = ByteArray(8_192)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    size += n
                    if (size > MAX_TEXT_FILE_BYTES) {
                        dest.delete()
                        error("文本过大")
                    }
                    output.write(buf, 0, n)
                }
            }
        } ?: error("无法读取文件")
        if (dest.length() < 1L) {
            dest.delete()
            error("文件为空")
        }
        val preview = readTextLimited(dest.absolutePath, 64)
            ?: run {
                dest.delete()
                error("无法解码文本")
            }
        if (preview.isBlank()) {
            dest.delete()
            error("文本为空")
        }
        return ChatAttachment(
            path = dest.absolutePath,
            mime = mime,
            name = displayName,
            kind = AttachmentKind.TEXT,
        )
    }

    private fun importPdfAsImage(uri: Uri, displayName: String): ChatAttachment {
        val pdfFile = File(root, "pdf_${UUID.randomUUID()}.pdf")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            pdfFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("无法读取 PDF")
        if (pdfFile.length() < 64L) {
            pdfFile.delete()
            error("PDF 无效")
        }
        if (pdfFile.length() > MAX_PDF_BYTES) {
            pdfFile.delete()
            error("PDF 过大")
        }
        val dest = File(root, "img_${UUID.randomUUID()}.jpg")
        try {
            renderPdfFirstPage(pdfFile, dest)
        } finally {
            pdfFile.delete()
        }
        if (!dest.exists() || dest.length() < MIN_IMAGE_BYTES) {
            dest.delete()
            error("无法预览 PDF")
        }
        return ChatAttachment(
            path = dest.absolutePath,
            mime = "application/pdf",
            name = displayName,
            kind = AttachmentKind.PDF,
        )
    }

    private fun renderPdfFirstPage(pdfFile: File, dest: File) {
        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount < 1) error("PDF 无页面")
                renderer.openPage(0).use { page ->
                    val w = page.width.coerceAtLeast(1)
                    val h = page.height.coerceAtLeast(1)
                    val scale = (MAX_EDGE.toFloat() / maxOf(w, h)).coerceAtMost(2f)
                    val bw = (w * scale).toInt().coerceAtLeast(1)
                    val bh = (h * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    FileOutputStream(dest).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                    }
                    bitmap.recycle()
                }
            }
        }
    }

    private fun compressToJpeg(uri: Uri, dest: File): File? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null
        var sample = 1
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        while (maxSide / sample > MAX_EDGE) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = appContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        val scaled = scaleDown(bitmap, MAX_EDGE)
        if (scaled != bitmap) bitmap.recycle()
        FileOutputStream(dest).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        scaled.recycle()
        return dest
    }

    private fun scaleDown(src: Bitmap, maxEdge: Int): Bitmap {
        val w = src.width
        val h = src.height
        val longest = maxOf(w, h)
        if (longest <= maxEdge) return src
        val scale = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            src,
            (w * scale).toInt().coerceAtLeast(1),
            (h * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor = appContext.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )
        return cursor?.use {
            if (!it.moveToFirst()) return@use null
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx < 0) null else it.getString(idx)?.takeIf { n -> n.isNotBlank() }
        }
    }

    companion object {
        private const val MIN_IMAGE_BYTES = 2_000L
        private const val MAX_IMAGE_BYTES = 3_500_000L
        private const val MAX_PDF_BYTES = 8_000_000L
        private const val MAX_TEXT_FILE_BYTES = 512_000L
        private const val MAX_TEXT_CHARS = 80_000
        private const val MAX_EDGE = 1280
        private const val JPEG_QUALITY = 82

        private val IMAGE_EXT = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp")

        /** 文档类型靠前，减少部分机型一打开像「选相册」。 */
        private val DOC_MIME_TYPES = arrayOf(
            "application/pdf",
            "text/plain",
            "text/markdown",
            "text/csv",
            "application/json",
            "text/*",
            "image/*",
        )

        fun openDocumentMimeTypes(): Array<String> = DOC_MIME_TYPES

        fun isTextMime(mime: String, nameLower: String = ""): Boolean {
            if (mime.startsWith("text/")) return true
            if (mime == "application/json") return true
            return TEXT_EXT.any { nameLower.endsWith(it) }
        }

        fun isImageMime(mime: String): Boolean = mime.startsWith("image/")

        fun isPdfMime(mime: String, nameLower: String = ""): Boolean =
            mime.equals("application/pdf", ignoreCase = true) ||
                nameLower.endsWith(".pdf")

        private fun guessTextMime(nameLower: String): String = when {
            nameLower.endsWith(".md") -> "text/markdown"
            nameLower.endsWith(".csv") -> "text/csv"
            nameLower.endsWith(".json") -> "application/json"
            else -> "text/plain"
        }

        private val TEXT_EXT = listOf(".txt", ".md", ".markdown", ".csv", ".json", ".log")
    }
}
