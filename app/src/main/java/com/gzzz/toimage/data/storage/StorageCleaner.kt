package com.gzzz.toimage.data.storage

import android.content.Context
import com.gzzz.toimage.data.local.ChatMessageDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageCleaner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageDao: ChatMessageDao
) {
    companion object {
        private const val DEFAULT_MAX_IMAGES = 200
        private const val GENERATIONS_DIR = "generations"
        private const val THUMBNAILS_DIR = "thumbnails"
        private const val INPUT_IMAGES_DIR = "input_images"
    }

    suspend fun cleanIfNeeded(maxImages: Int = DEFAULT_MAX_IMAGES) = withContext(Dispatchers.IO) {
        val count = messageDao.getResultImageCount()
        if (count <= maxImages) return@withContext

        val toDelete = count - maxImages
        val oldestMessages = messageDao.getOldestResultMessages(toDelete)
        for (msg in oldestMessages) {
            // 删除图片文件
            msg.imageResultPath?.let { File(it).delete() }
            msg.thumbnailPath?.let { File(it).delete() }
            // 删除数据库记录
            messageDao.delete(msg)
        }
    }

    suspend fun getStorageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        val generationsDir = File(context.filesDir, GENERATIONS_DIR)
        val thumbnailsDir = File(context.filesDir, THUMBNAILS_DIR)

        val generationsSize = if (generationsDir.exists()) generationsDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() } else 0L

        val thumbnailsSize = if (thumbnailsDir.exists()) thumbnailsDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() } else 0L

        val imageCount = messageDao.getResultImageCount()

        StorageInfo(
            totalSize = generationsSize + thumbnailsSize,
            generationsSize = generationsSize,
            thumbnailsSize = thumbnailsSize,
            imageCount = imageCount
        )
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        val generationsDir = File(context.filesDir, GENERATIONS_DIR)
        val thumbnailsDir = File(context.filesDir, THUMBNAILS_DIR)
        val inputDir = File(context.filesDir, INPUT_IMAGES_DIR)
        if (generationsDir.exists()) generationsDir.deleteRecursively()
        if (thumbnailsDir.exists()) thumbnailsDir.deleteRecursively()
        if (inputDir.exists()) inputDir.deleteRecursively()
    }
}

data class StorageInfo(
    val totalSize: Long,
    val generationsSize: Long,
    val thumbnailsSize: Long,
    val imageCount: Int
) {
    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        bytes < 1024 * 1024 * 1024 -> String.format("%.1fMB", bytes / 1024.0 / 1024.0)
        else -> String.format("%.2fGB", bytes / 1024.0 / 1024.0 / 1024.0)
    }

    val totalSizeFormatted: String get() = formatSize(totalSize)
    val generationsSizeFormatted: String get() = formatSize(generationsSize)
    val thumbnailsSizeFormatted: String get() = formatSize(thumbnailsSize)
}
