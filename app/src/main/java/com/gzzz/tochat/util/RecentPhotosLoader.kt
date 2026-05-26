package com.gzzz.tochat.util

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RecentPhotosLoader {

    suspend fun loadRecentPhotos(context: Context, limit: Int = 30): List<Uri> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<Uri>()

        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED
            )

            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                var count = 0

                while (cursor.moveToNext() && count < limit) {
                    val id = cursor.getLong(idColumn)
                    val uri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    photos.add(uri)
                    count++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        photos
    }
}
