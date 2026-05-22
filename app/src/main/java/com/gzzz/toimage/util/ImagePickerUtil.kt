package com.gzzz.toimage.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID

object ImagePickerUtil {

    private const val MAX_FILE_SIZE = 4 * 1024 * 1024 // 4MB
    private const val MAX_DIMENSION = 1536

    /**
     * 将 Uri 持久化为本地文件，压缩到目标大小以内。
     * 成功返回文件绝对路径，失败返回 null 并通过 onError 回调中文提示。
     */
    fun processPickedImage(
        context: Context,
        uri: Uri,
        onError: (String) -> Unit
    ): String? {
        try {
            // 读取原始图片
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                onError("无法读取图片，请重新选择")
                return null
            }

            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap == null) {
                onError("图片解码失败，请选择其他图片")
                return null
            }

            // 压缩：限制尺寸
            val scaled = if (bitmap.width > MAX_DIMENSION || bitmap.height > MAX_DIMENSION) {
                val ratio = minOf(
                    MAX_DIMENSION.toFloat() / bitmap.width,
                    MAX_DIMENSION.toFloat() / bitmap.height
                )
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt(),
                    (bitmap.height * ratio).toInt(),
                    true
                )
            } else {
                bitmap
            }

            // 保存到 app 私有目录
            val dir = File(context.filesDir, "input_images")
            if (!dir.exists()) dir.mkdirs()

            val fileName = "input_${UUID.randomUUID()}.jpg"
            val file = File(dir, fileName)

            // 先用 85% 质量保存
            var quality = 85
            file.outputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }

            // 如果超过目标大小，降低质量重试
            while (file.length() > MAX_FILE_SIZE && quality > 30) {
                quality -= 10
                file.outputStream().use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                }
            }

            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()

            if (file.length() > MAX_FILE_SIZE) {
                file.delete()
                onError("图片过大，即使压缩后仍超过 4MB，请选择更小的图片")
                return null
            }

            return file.absolutePath
        } catch (e: SecurityException) {
            onError("权限被拒绝，请在系统设置中允许访问存储")
            return null
        } catch (e: Exception) {
            onError("图片处理失败：${e.localizedMessage}")
            return null
        }
    }

    /**
     * 将拍照得到的文件路径压缩到目标大小。
     * 拍照返回的已经是本地文件，但仍需压缩。
     */
    fun processCapturedImage(
        context: Context,
        photoPath: String,
        onError: (String) -> Unit
    ): String? {
        try {
            val file = File(photoPath)
            if (!file.exists()) {
                onError("照片文件丢失，请重新拍摄")
                return null
            }

            val bitmap = BitmapFactory.decodeFile(photoPath)
            if (bitmap == null) {
                onError("照片解码失败，请重新拍摄")
                return null
            }

            // 压缩尺寸
            val scaled = if (bitmap.width > MAX_DIMENSION || bitmap.height > MAX_DIMENSION) {
                val ratio = minOf(
                    MAX_DIMENSION.toFloat() / bitmap.width,
                    MAX_DIMENSION.toFloat() / bitmap.height
                )
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt(),
                    (bitmap.height * ratio).toInt(),
                    true
                )
            } else {
                bitmap
            }

            val dir = File(context.filesDir, "input_images")
            if (!dir.exists()) dir.mkdirs()

            val fileName = "input_${UUID.randomUUID()}.jpg"
            val outFile = File(dir, fileName)

            var quality = 85
            outFile.outputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }

            while (outFile.length() > MAX_FILE_SIZE && quality > 30) {
                quality -= 10
                outFile.outputStream().use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                }
            }

            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()

            // 删除原始拍照文件（如果不同于输出文件）
            if (file.absolutePath != outFile.absolutePath) {
                file.delete()
            }

            if (outFile.length() > MAX_FILE_SIZE) {
                outFile.delete()
                onError("照片过大，即使压缩后仍超过 4MB")
                return null
            }

            return outFile.absolutePath
        } catch (e: Exception) {
            onError("照片处理失败：${e.localizedMessage}")
            return null
        }
    }
}
