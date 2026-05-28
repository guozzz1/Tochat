package com.gzzz.tochat.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.gzzz.tochat.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AiReplyShareImage {
    private const val IMAGE_WIDTH = 1080
    private const val OUTER_PADDING = 48f
    private const val CARD_PADDING = 48f
    private const val CARD_RADIUS = 36f
    private const val BLOCK_RADIUS = 28f
    private const val SECTION_SPACING = 36f
    private const val MAX_QUESTION_CHARS = 800
    private const val MAX_ANSWER_CHARS = 8000
    private const val MAX_IMAGE_HEIGHT = 16000
    private const val MAX_BITMAP_BYTES = 64 * 1024 * 1024
    private const val MAX_SHARE_FILES = 20

    suspend fun createShareImage(
        context: Context,
        question: String?,
        answer: String
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedQuestion = question
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { limitText(normalizeMarkdown(it), MAX_QUESTION_CHARS) }
            val normalizedAnswer = limitText(normalizeMarkdown(answer.trim()), MAX_ANSWER_CHARS)
            if (normalizedAnswer.isBlank()) throw IllegalArgumentException("回复内容为空")

            val file = renderToFile(context, normalizedQuestion, normalizedAnswer)
            cleanupOldFiles(file.parentFile)
            file
        }
    }

    fun shareImage(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "ToChat AI 回复", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享回复图片"))
    }

    private fun renderToFile(context: Context, question: String?, answer: String): File {
        val cardWidth = IMAGE_WIDTH - OUTER_PADDING * 2
        val contentWidth = (cardWidth - CARD_PADDING * 2).toInt()

        val titlePaint = textPaint(46f, Color.rgb(20, 24, 31), true)
        val subtitlePaint = textPaint(28f, Color.rgb(116, 123, 137), false)
        val labelPaint = textPaint(30f, Color.rgb(72, 86, 112), true)
        val questionPaint = textPaint(36f, Color.WHITE, false)
        val answerPaint = textPaint(38f, Color.rgb(30, 35, 45), false)
        val footerPaint = textPaint(26f, Color.rgb(150, 156, 168), false)

        val questionLayout = question?.let { staticLayout(it, questionPaint, contentWidth - 48) }
        var answerLayout = staticLayout(answer, answerPaint, contentWidth)
        val footerLayout = staticLayout("由 ToChat 生成", footerPaint, contentWidth)

        val headerHeight = 86f
        val questionHeight = questionLayout?.let { 30f + 20f + it.height + 48f + SECTION_SPACING } ?: 0f
        val answerHeight = 30f + 20f + answerLayout.height
        val footerHeight = SECTION_SPACING + footerLayout.height
        var cardHeight = CARD_PADDING + headerHeight + SECTION_SPACING + questionHeight + answerHeight + footerHeight + CARD_PADDING
        var imageHeight = (cardHeight + OUTER_PADDING * 2).toInt().coerceAtLeast(900)

        if (imageHeight > MAX_IMAGE_HEIGHT || IMAGE_WIDTH.toLong() * imageHeight * 4L > MAX_BITMAP_BYTES) {
            val availableAnswerHeight = (MAX_IMAGE_HEIGHT - (imageHeight - answerLayout.height) - 400).coerceAtLeast(1200)
            val truncatedAnswer = truncateForHeight(answer, answerPaint, contentWidth, availableAnswerHeight)
            answerLayout = staticLayout(truncatedAnswer, answerPaint, contentWidth)
            val newAnswerHeight = 30f + 20f + answerLayout.height
            cardHeight = CARD_PADDING + headerHeight + SECTION_SPACING + questionHeight + newAnswerHeight + footerHeight + CARD_PADDING
            imageHeight = (cardHeight + OUTER_PADDING * 2).toInt().coerceAtMost(MAX_IMAGE_HEIGHT)
        }

        if (IMAGE_WIDTH.toLong() * imageHeight * 4L > MAX_BITMAP_BYTES) {
            throw OutOfMemoryError("分享图片过大")
        }

        val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, imageHeight, Bitmap.Config.ARGB_8888)
        val appIcon = BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher)
        try {
            val canvas = Canvas(bitmap)
            drawBackground(canvas, imageHeight)
            drawCard(
                canvas = canvas,
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                appIcon = appIcon,
                titlePaint = titlePaint,
                subtitlePaint = subtitlePaint,
                labelPaint = labelPaint,
                questionPaint = questionPaint,
                answerPaint = answerPaint,
                footerPaint = footerPaint,
                questionLayout = questionLayout,
                answerLayout = answerLayout,
                footerLayout = footerLayout
            )

            val dir = File(context.filesDir, "generations/share_cards")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "ai_reply_${System.currentTimeMillis()}.png")
            file.outputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IllegalStateException("分享图片保存失败")
                }
            }
            return file
        } finally {
            bitmap.recycle()
            appIcon?.recycle()
        }
    }

    private fun drawBackground(canvas: Canvas, imageHeight: Int) {
        canvas.drawColor(Color.rgb(242, 246, 252))
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(45, 124, 212) }
        canvas.drawCircle(IMAGE_WIDTH - 110f, 80f, 180f, accentPaint.apply { alpha = 28 })
        canvas.drawCircle(80f, imageHeight - 80f, 220f, accentPaint.apply { alpha = 18 })
    }

    private fun drawCard(
        canvas: Canvas,
        cardWidth: Float,
        cardHeight: Float,
        appIcon: Bitmap?,
        titlePaint: TextPaint,
        subtitlePaint: TextPaint,
        labelPaint: TextPaint,
        questionPaint: TextPaint,
        answerPaint: TextPaint,
        footerPaint: TextPaint,
        questionLayout: StaticLayout?,
        answerLayout: StaticLayout,
        footerLayout: StaticLayout
    ) {
        val cardLeft = OUTER_PADDING
        val cardTop = OUTER_PADDING
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRoundRect(
            RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight),
            CARD_RADIUS,
            CARD_RADIUS,
            cardPaint
        )

        var y = cardTop + CARD_PADDING
        val contentLeft = cardLeft + CARD_PADDING
        drawBrand(canvas, contentLeft, y, appIcon, titlePaint, subtitlePaint)
        y += 86f + SECTION_SPACING

        if (questionLayout != null) {
            canvas.drawText("你的问题", contentLeft, y + 30f, labelPaint)
            y += 50f
            val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(45, 124, 212) }
            val blockRect = RectF(contentLeft, y, contentLeft + questionLayout.width + 48f, y + questionLayout.height + 48f)
            canvas.drawRoundRect(blockRect, BLOCK_RADIUS, BLOCK_RADIUS, blockPaint)
            canvas.save()
            canvas.translate(contentLeft + 24f, y + 24f)
            questionLayout.draw(canvas)
            canvas.restore()
            y = blockRect.bottom + SECTION_SPACING
        }

        canvas.drawText("AI 回复", contentLeft, y + 30f, labelPaint)
        y += 50f
        canvas.save()
        canvas.translate(contentLeft, y)
        answerLayout.draw(canvas)
        canvas.restore()
        y += answerLayout.height + SECTION_SPACING

        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(229, 233, 240) }
        canvas.drawRect(contentLeft, y, contentLeft + answerLayout.width, y + 2f, dividerPaint)
        y += 28f
        canvas.save()
        canvas.translate(contentLeft, y)
        footerLayout.draw(canvas)
        canvas.restore()
    }

    private fun drawBrand(canvas: Canvas, x: Float, y: Float, appIcon: Bitmap?, titlePaint: TextPaint, subtitlePaint: TextPaint) {
        val iconRect = RectF(x, y, x + 72f, y + 72f)
        if (appIcon != null) {
            val clipPath = Path().apply { addRoundRect(iconRect, 20f, 20f, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(clipPath)
            canvas.drawBitmap(appIcon, null, iconRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
        } else {
            val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(45, 124, 212) }
            canvas.drawRoundRect(iconRect, 20f, 20f, iconPaint)
            val iconTextPaint = textPaint(42f, Color.WHITE, true).apply { textAlign = Paint.Align.CENTER }
            canvas.drawText("T", x + 36f, y + 50f, iconTextPaint)
        }
        canvas.drawText("ToChat", x + 94f, y + 36f, titlePaint)
        canvas.drawText("AI 回复分享", x + 94f, y + 72f, subtitlePaint)
    }

    private fun textPaint(size: Float, color: Int, bold: Boolean): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }

    private fun staticLayout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(8f, 1f)
            .setIncludePad(false)
            .build()

    private fun limitText(text: String, maxChars: Int): String =
        if (text.length > maxChars) text.take(maxChars).trimEnd() + "\n\n…内容过长，已截断" else text

    private fun normalizeMarkdown(text: String): String = text
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace(Regex("```[a-zA-Z0-9_-]*\n"), "")
        .replace("```", "")
        .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
        .replace(Regex("\\*\\*(.*?)\\*\\*"), "\$1")
        .replace(Regex("__(.*?)__"), "\$1")
        .replace(Regex("`([^`]+)`"), "\$1")
        .replace(Regex("\n{4,}"), "\n\n\n")
        .trim()

    private fun truncateForHeight(text: String, paint: TextPaint, width: Int, maxHeight: Int): String {
        var low = 0
        var high = text.length
        var best = text.take(1000.coerceAtMost(text.length))
        while (low <= high) {
            val mid = (low + high) / 2
            val candidate = text.take(mid).trimEnd() + "\n\n…内容过长，已截断"
            val height = staticLayout(candidate, paint, width).height
            if (height <= maxHeight) {
                best = candidate
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return best
    }

    private fun cleanupOldFiles(dir: File?) {
        if (dir == null || !dir.exists()) return
        dir.listFiles { file -> file.isFile && file.extension.equals("png", true) }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_SHARE_FILES)
            ?.forEach { it.delete() }
    }
}
