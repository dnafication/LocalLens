package com.locallens.app.data.media

import android.content.Context
import android.provider.MediaStore
import com.locallens.app.data.db.entities.MediaFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class MediaStoreScanner @Inject constructor(private val context: Context) {

    private val allowedMimeTypes = setOf(
        "image/jpeg", "image/png", "image/webp", "image/heic",
        "video/mp4", "video/quicktime", "video/x-matroska"
    )

    fun scanAll(): Flow<MediaFile> = flow {
        val collections = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.Video.Media.DURATION
        )

        for (uri in collections) {
            val cursor = context.contentResolver.query(
                uri, projection, null, null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} ASC"
            ) ?: continue

            cursor.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val mimeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val dateAddedCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val dateModifiedCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val widthCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val heightCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val durationCol = it.getColumnIndex(MediaStore.Video.Media.DURATION)

                while (it.moveToNext()) {
                    val mimeType = it.getString(mimeCol) ?: continue
                    if (mimeType !in allowedMimeTypes) continue

                    val width = it.getInt(widthCol)
                    val height = it.getInt(heightCol)
                    if (width == 0 || height == 0) continue

                    val filePath = it.getString(dataCol) ?: continue
                    if (filePath.contains("/Screenshots/") || filePath.contains("/Screen recordings/")) continue

                    val mediaStoreId = it.getLong(idCol)
                    val contentUri = android.net.Uri.withAppendedPath(uri, mediaStoreId.toString())

                    emit(
                        MediaFile(
                            mediaStoreId = mediaStoreId,
                            uri = contentUri.toString(),
                            mimeType = mimeType,
                            filePath = filePath,
                            dateAdded = it.getLong(dateAddedCol) * 1000L,
                            dateModified = it.getLong(dateModifiedCol) * 1000L,
                            width = width,
                            height = height,
                            durationMs = if (durationCol >= 0) it.getLong(durationCol) else 0L
                        )
                    )
                }
            }
        }
    }

    fun scanSince(sinceEpochMillis: Long): Flow<MediaFile> = flow {
        val collections = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.Video.Media.DURATION
        )
        val selection = "${MediaStore.MediaColumns.DATE_MODIFIED} > ?"
        val selectionArgs = arrayOf((sinceEpochMillis / 1000).toString())

        for (uri in collections) {
            val cursor = context.contentResolver.query(
                uri, projection, selection, selectionArgs,
                "${MediaStore.MediaColumns.DATE_MODIFIED} ASC"
            ) ?: continue

            cursor.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val mimeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val dateAddedCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val dateModifiedCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val widthCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val heightCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val durationCol = it.getColumnIndex(MediaStore.Video.Media.DURATION)

                while (it.moveToNext()) {
                    val mimeType = it.getString(mimeCol) ?: continue
                    if (mimeType !in allowedMimeTypes) continue

                    val width = it.getInt(widthCol)
                    val height = it.getInt(heightCol)
                    if (width == 0 || height == 0) continue

                    val filePath = it.getString(dataCol) ?: continue
                    if (filePath.contains("/Screenshots/") || filePath.contains("/Screen recordings/")) continue

                    val mediaStoreId = it.getLong(idCol)
                    val contentUri = android.net.Uri.withAppendedPath(uri, mediaStoreId.toString())

                    emit(
                        MediaFile(
                            mediaStoreId = mediaStoreId,
                            uri = contentUri.toString(),
                            mimeType = mimeType,
                            filePath = filePath,
                            dateAdded = it.getLong(dateAddedCol) * 1000L,
                            dateModified = it.getLong(dateModifiedCol) * 1000L,
                            width = width,
                            height = height,
                            durationMs = if (durationCol >= 0) it.getLong(durationCol) else 0L
                        )
                    )
                }
            }
        }
    }
}
