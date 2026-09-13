package com.eraherm.hermchat.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
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
 * 聊天附件：图片压缩（HEIC 走 ImageDecoder）；txt/md/json/csv 拷贝；
 * docx/xlsx/pptx 抽取文字存 .txt（无 vision 也能读）；PDF 渲染首页为图（当 vision 用）。
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
            isOfficeFile(mime, lowerName) ->
                importOffice(uri, displayName, mime)
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
        val compressed = compressToJpeg(uri, dest)
        if (compressed != null) {
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
        // 解码兜底：本机解不开（少见格式 / 个别 ROM 的 provider）就原样拷贝字节，
        // 让服务端去解——总比甩「无法读取图片」强。
        dest.delete()
        return copyRawImage(uri, displayName)
    }

    /** 解码失败时的兜底：原样拷贝原图（保留真实 mime，超限报「图片过大」）。 */
    private fun copyRawImage(uri: Uri, displayName: String): ChatAttachment {
        val mime = appContext.contentResolver.getType(uri).orEmpty().ifBlank { "image/jpeg" }
        val ext = mime.substringAfter('/', "jpeg").substringBefore('+').take(8)
        val dest = File(root, "img_${UUID.randomUUID()}.$ext")
        var size = 0L
        val stream = appContext.contentResolver.openInputStream(uri) ?: error("无法读取图片")
        stream.use { input ->
            dest.outputStream().use { output ->
                val buf = ByteArray(8_192)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    size += n
                    if (size > MAX_IMAGE_BYTES) {
                        dest.delete()
                        error("图片过大")
                    }
                    output.write(buf, 0, n)
                }
            }
        }
        if (dest.length() < 1L) {
            dest.delete()
            error("无法读取图片")
        }
        return ChatAttachment(
            path = dest.absolutePath,
            mime = mime,
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

    /**
     * Office 文档（docx/xlsx/pptx）：App 端直接抽取文字存为 .txt，
     * 按文本附件发（无 vision 模型也能读懂内容），界面仍按原名显示。
     */
    private fun importOffice(uri: Uri, displayName: String, mime: String): ChatAttachment {
        val lowerName = displayName.lowercase()
        val extracted = when {
            lowerName.endsWith(".docx") -> extractDocx(uri)
            lowerName.endsWith(".xlsx") || lowerName.endsWith(".xlsm") -> extractXlsx(uri)
            lowerName.endsWith(".pptx") -> extractPptx(uri)
            else -> error("暂不支持此文件类型")
        }
        if (extracted.isBlank()) error("文档内没有可提取的文字")
        val dest = File(root, "txt_${UUID.randomUUID()}.txt")
        val truncated = if (extracted.length > MAX_TEXT_CHARS) {
            extracted.take(MAX_TEXT_CHARS) + "\n…（已截断）"
        } else {
            extracted
        }
        dest.writeText(truncated)
        return ChatAttachment(
            path = dest.absolutePath,
            mime = mime.ifBlank { "application/octet-stream" },
            name = displayName,
            kind = AttachmentKind.TEXT,
        )
    }

    /** 读 OOXML(zip) 内全部 xml 条目（office 文件一般 <10MB，防御上限防异常大文件）。 */
    private fun readOoxmlEntries(uri: Uri): List<Pair<String, String>>? {
        val entries = ArrayList<Pair<String, String>>()
        var total = 0L
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            java.util.zip.ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".xml")) {
                        total += entry.size
                        if (total > MAX_OOXML_TOTAL_BYTES) error("文档过大")
                        val bytes = zip.readBytes()
                        if (bytes.size <= MAX_OOXML_ENTRY_BYTES) {
                            entries.add(entry.name to String(bytes, Charsets.UTF_8))
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: return null
        return entries
    }

    private fun extractDocx(uri: Uri): String {
        val entries = readOoxmlEntries(uri) ?: return ""
        val xml = entries.firstOrNull { it.first == "word/document.xml" }?.second ?: return ""
        // 按段落切（表格内段落同样以 </w:p> 结尾），段内取 <w:t> 文本
        val sb = StringBuilder()
        for (para in xml.split("</w:p>")) {
            val seg = StringBuilder()
            for (m in W_T_REGEX.findAll(para)) seg.append(m.groupValues[1])
            val text = unescapeXml(seg.toString()).trim()
            if (text.isNotEmpty()) {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(text)
            }
        }
        return sb.toString()
    }

    private fun extractPptx(uri: Uri): String {
        val entries = readOoxmlEntries(uri) ?: return ""
        val sb = StringBuilder()
        val slides = entries
            .filter { PPTX_SLIDE_REGEX.matches(it.first) }
            .mapNotNull { e ->
                NUM_IN_NAME.find(e.first)?.groupValues?.get(1)?.toIntOrNull()?.let { it to e.second }
            }
            .sortedBy { it.first }
        if (slides.isEmpty()) return ""
        for ((_, xml) in slides) {
            val slideText = StringBuilder()
            for (para in xml.split("</a:p>")) {
                val seg = StringBuilder()
                for (m in A_T_REGEX.findAll(para)) seg.append(m.groupValues[1])
                val text = unescapeXml(seg.toString()).trim()
                if (text.isNotEmpty()) {
                    if (slideText.isNotEmpty()) slideText.append('\n')
                    slideText.append(text)
                }
            }
            if (slideText.isNotBlank()) {
                if (sb.isNotEmpty()) sb.append("\n\n")
                sb.append(slideText)
            }
        }
        return sb.toString()
    }

    private fun extractXlsx(uri: Uri): String {
        val entries = readOoxmlEntries(uri) ?: return ""
        // 共享字符串表：<t> 文本按出现顺序编号
        val shared = ArrayList<String>()
        entries.firstOrNull { it.first == "xl/sharedStrings.xml" }?.second?.let { xml ->
            for (m in X_T_REGEX.findAll(xml)) shared.add(m.groupValues[1])
        }
        val sb = StringBuilder()
        val sheets = entries
            .filter { XLSX_SHEET_REGEX.matches(it.first) }
            .mapNotNull { e ->
                NUM_IN_NAME.find(e.first)?.groupValues?.get(1)?.toIntOrNull()?.let { it to e.second }
            }
            .sortedBy { it.first }
        for ((_, xml) in sheets) {
            var sheetDirty = false
            val sheetSb = StringBuilder()
            for (rowM in ROW_REGEX.findAll(xml)) {
                val rowText = StringBuilder()
                var cellCount = 0
                for (cellM in CELL_REGEX.findAll(rowM.value)) {
                    val cell = cellM.value
                    val isShared = cell.contains("t=\"s\"")
                    val v = V_REGEX.find(cell)?.groupValues?.get(1)
                    val inline = INLINE_T_REGEX.find(cell)?.groupValues?.get(1)
                    val value = when {
                        inline != null -> inline
                        isShared && v != null -> v.toIntOrNull()?.let { idx ->
                            shared.getOrNull(idx)
                        } ?: v
                        v != null -> v
                        else -> null
                    }
                    if (value != null) {
                        val text = unescapeXml(value).trim()
                        if (text.isNotEmpty()) {
                            if (cellCount > 0) rowText.append('\t')
                            rowText.append(text)
                            cellCount++
                        }
                    }
                }
                if (rowText.isNotBlank()) {
                    if (sheetSb.isNotEmpty()) sheetSb.append('\n')
                    sheetSb.append(rowText)
                    sheetDirty = true
                }
            }
            if (sheetDirty) {
                if (sb.isNotEmpty()) sb.append("\n\n")
                sb.append(sheetSb)
            }
        }
        return sb.toString()
    }

    private fun unescapeXml(raw: String): String {
        var s = raw
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
        s = s.replace(NUM_ENTITY_REGEX) { m ->
            m.groupValues[1].toIntOrNull()?.let { code ->
                if (code in 0..0x10FFFF) String(Character.toChars(code)) else ""
            } ?: ""
        }
        return s.replace("&amp;", "&")
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
        val bitmap = decodeBitmap(uri) ?: return null
        FileOutputStream(dest).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        bitmap.recycle()
        return dest
    }

    /** 统一解码：常规图走 BitmapFactory（bounds→采样）；HEIF/HEIC 等走 ImageDecoder。 */
    private fun decodeBitmap(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null
        var bitmap: Bitmap? = null
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            val maxSide = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            var sample = 1
            while (maxSide / sample > MAX_EDGE) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            bitmap = appContext.contentResolver.openInputStream(uri)?.use {
                runCatching { BitmapFactory.decodeStream(it, null, opts) }.getOrNull()
            }
        }
        if (bitmap == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            bitmap = runCatching {
                val source = ImageDecoder.createSource(appContext.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val maxSide = maxOf(info.size.width, info.size.height)
                    if (maxSide > MAX_EDGE) {
                        decoder.setTargetSampleSize((maxSide + MAX_EDGE - 1) / MAX_EDGE)
                    }
                }
            }.getOrNull()
        }
        if (bitmap == null) return null
        val scaled = scaleDown(bitmap, MAX_EDGE)
        if (scaled != bitmap) bitmap.recycle()
        return scaled
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
        private const val MAX_OOXML_TOTAL_BYTES = 12_000_000L
        private const val MAX_OOXML_ENTRY_BYTES = 8_000_000L

        private val IMAGE_EXT = listOf(
            ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".heic", ".heif",
        )
        private val OFFICE_EXT = listOf(".docx", ".xlsx", ".xlsm", ".pptx")
        private val OFFICE_MIME_PREFIX = "application/vnd.openxmlformats-officedocument"

        // docx / pptx / xlsx 文本标签正则（run 属性如 xml:space 由 (?: [^>]*)? 吸收）
        private val W_T_REGEX = Regex("<w:t(?: [^>]*)?>([^<]*)</w:t>")
        private val A_T_REGEX = Regex("<a:t(?: [^>]*)?>([^<]*)</a:t>")
        private val X_T_REGEX = Regex("<t(?: [^>]*)?>([^<]*)</t>")
        private val ROW_REGEX = Regex("<row\\b[^>]*>.*?</row>", RegexOption.DOT_MATCHES_ALL)
        private val CELL_REGEX = Regex("<c\\b.*?</c>", RegexOption.DOT_MATCHES_ALL)
        private val V_REGEX = Regex("<v>([^<]*)</v>")
        private val INLINE_T_REGEX = Regex(
            "<is>.*?<t(?: [^>]*)?>([^<]*)</t>.*?</is>",
            RegexOption.DOT_MATCHES_ALL,
        )
        private val NUM_ENTITY_REGEX = Regex("&#(\\d+);")
        private val NUM_IN_NAME = Regex("(\\d+)\\.xml$")
        private val PPTX_SLIDE_REGEX = Regex("ppt/slides/slide\\d+\\.xml")
        private val XLSX_SHEET_REGEX = Regex("xl/worksheets/sheet\\d+\\.xml")

        /** 文档类型靠前，减少部分机型一打开像「选相册」。 */
        private val DOC_MIME_TYPES = arrayOf(
            "application/pdf",
            "text/plain",
            "text/markdown",
            "text/csv",
            "application/json",
            "text/*",
            "image/*",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/octet-stream",
            "*/*",
        )

        fun openDocumentMimeTypes(): Array<String> = DOC_MIME_TYPES

        fun isTextMime(mime: String, nameLower: String = ""): Boolean {
            if (mime.startsWith("text/")) return true
            if (mime == "application/json") return true
            return TEXT_EXT.any { nameLower.endsWith(it) }
        }

        fun isOfficeFile(mime: String, nameLower: String = ""): Boolean =
            mime.startsWith(OFFICE_MIME_PREFIX, ignoreCase = true) ||
                OFFICE_EXT.any { nameLower.endsWith(it) }

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
