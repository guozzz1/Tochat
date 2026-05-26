package com.gzzz.tochat.util

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class FileAttachment(
    val fileName: String,
    val text: String,
    val isTruncated: Boolean,
    val originalLength: Int
)

data class ExtractedFileText(
    val fileName: String,
    val mimeType: String?,
    val text: String,
    val isTruncated: Boolean,
    val originalLength: Int
)

object FileAttachmentFormatter {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(attachment: FileAttachment): String =
        json.encodeToString(attachment)

    fun decode(value: String?): FileAttachment? {
        if (value.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<FileAttachment>(value) }.getOrNull()
    }

    fun toModelPrompt(attachment: FileAttachment, userText: String): String {
        val truncNote = if (attachment.isTruncated) {
            "（注：文件过长，已截取前${attachment.text.length}字）\n"
        } else {
            ""
        }
        val prompt = userText.ifBlank { "请阅读附件内容并等待我的下一步指令。" }
        return "[附件: ${attachment.fileName}]\n$truncNote${attachment.text}\n\n用户消息: $prompt"
    }

    fun summary(attachment: FileAttachment): String =
        if (attachment.isTruncated) {
            "${attachment.text.length} / ${attachment.originalLength} 字（已截取）"
        } else {
            "${attachment.text.length} 字"
        }
}

object FileTextExtractor {

    private const val MAX_CHARS = 8000

    fun extract(context: Context, uri: Uri): Result<FileAttachment> {
        return extractText(context, uri, MAX_CHARS).map { extracted ->
            FileAttachment(
                fileName = extracted.fileName,
                text = extracted.text,
                isTruncated = extracted.isTruncated,
                originalLength = extracted.originalLength
            )
        }
    }

    fun extractText(context: Context, uri: Uri, maxChars: Int): Result<ExtractedFileText> {
        return try {
            val fileName = getFileName(context, uri)
            val mimeType = context.contentResolver.getType(uri) ?: ""

            val rawText = when {
                mimeType == "text/plain" || mimeType == "text/markdown" || mimeType == "text/x-markdown" ||
                    fileName.endsWith(".txt", true) || fileName.endsWith(".md", true) || fileName.endsWith(".markdown", true) ->
                    extractTxt(context, uri)
                mimeType == "application/pdf" || fileName.endsWith(".pdf", true) ->
                    extractPdf(context, uri)
                mimeType.contains("wordprocessingml") || fileName.endsWith(".docx", true) ->
                    extractDocx(context, uri)
                else ->
                    return Result.failure(IllegalArgumentException("不支持的文件格式：$fileName"))
            }

            if (rawText.isBlank()) {
                return Result.failure(IllegalStateException("文件内容为空"))
            }

            val isTruncated = rawText.length > maxChars
            val text = if (isTruncated) rawText.take(maxChars) else rawText

            Result.success(
                ExtractedFileText(
                    fileName = fileName,
                    mimeType = mimeType.ifBlank { null },
                    text = text,
                    isTruncated = isTruncated,
                    originalLength = rawText.length
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    private fun extractTxt(context: Context, uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法读取文件")
        return BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { it.readText() }
    }

    private fun extractPdf(context: Context, uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法读取文件")
        return inputStream.use { stream ->
            val document = PDDocument.load(stream)
            document.use { doc ->
                val stripper = PDFTextStripper()
                stripper.getText(doc)
            }
        }
    }

    private fun extractDocx(context: Context, uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法读取文件")
        val sb = StringBuilder()
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = zip.bufferedReader().readText()
                    val textPattern = Regex("<w:t[^>]*>(.*?)</w:t>")
                    val paragraphEnd = Regex("</w:p>")
                    var lastEnd = 0
                    val combined = StringBuilder()
                    paragraphEnd.findAll(xml).forEach { match ->
                        val segment = xml.substring(lastEnd, match.range.last + 1)
                        val texts = textPattern.findAll(segment).map { it.groupValues[1] }.joinToString("")
                        if (texts.isNotEmpty()) {
                            combined.append(texts).append("\n")
                        }
                        lastEnd = match.range.last + 1
                    }
                    sb.append(combined)
                    break
                }
                entry = zip.nextEntry
            }
        }
        return sb.toString()
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "unknown"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(idx) ?: "unknown"
            }
        }
        return name
    }
}
