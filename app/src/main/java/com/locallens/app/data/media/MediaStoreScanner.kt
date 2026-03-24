package com.locallens.app.data.media

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.locallens.app.data.db.entities.MediaFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val ALLOWED_MIME_TYPES = setOf(
            "image/jpeg", "image/png", "image/webp", "image/heic",
            "video/mp4", "video/quicktime", "video/x-matroska"
        )
        private val EXCLUDED_PATH_SEGMENTS = listOf("/Screenshots/", "/Screen recordings/")
        private const val MIN_FILE_SIZE = 10 * 1024L // 10KB
    }

    private val projection = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.MIME_TYPE,
        MediaStore.MediaColumns.DATA,
        MediaStore.MediaColumns.DATE_ADDED,
        MediaStore.MediaColumns.DATE_MODIFIED,
        MediaStore.MediaColumns.WIDTH,
        MediaStore.MediaColumns.HEIGHT,
        MediaStore.MediaColumns.SIZE,
        MediaStore.Video.Media.DURATION
    )

    fun scanAll(): Flow<MediaFile> = flow {
        val collections = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )

        for (uri in collections) {
            val cursor = context.contentResolver.query(
                uri, projection, null, null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} ASC"
            ) ?: continue

            cursor.use {
                while (it.moveToNext()) {
                    val mediaFile = cursorToMediaFile(it, uri) ?: continue
                    emit(mediaFile)
                }
            }
        }
    }

    fun scanSince(sinceEpochMillis: Long): Flow<MediaFile> = flow {
        val collections = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
        val selection = "${MediaStore.MediaColumns.DATE_MODIFIED} > ?"
        val selectionArgs = arrayOf((sinceEpochMillis / 1000).toString())

        for (uri in collections) {
            val cursor = context.contentResolver.query(
                uri, projection, selection, selectionArgs,
                "${MediaStore.MediaColumns.DATE_MODIFIED} ASC"
            ) ?: continue

            cursor.use {
                while (it.moveToNext()) {
                    val mediaFile = cursorToMediaFile(it, uri) ?: continue
                    emit(mediaFile)
                }
            }
        }
    }

    private fun cursorToMediaFile(cursor: Cursor, collectionUri: Uri): MediaFile? {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
        val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)) ?: return null
        val filePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)) ?: return null
        val width = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH))
        val height = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT))
        val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))

        // Apply filtering rules
        if (mimeType !in ALLOWED_MIME_TYPES) return null
        if (width == 0 || height == 0) return null
        if (size < MIN_FILE_SIZE) return null
        if (EXCLUDED_PATH_SEGMENTS.any { filePath.contains(it) }) return null

        val contentUri = ContentUris.withAppendedId(collectionUri, id)
        val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)) * 1000
        val dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)) * 1000

        val durationIndex = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
        val durationMs = if (durationIndex >= 0 && !cursor.isNull(durationIndex)) {
            cursor.getLong(durationIndex)
        } else {
            0L
        }

        return MediaFile(
            mediaStoreId = id,
            uri = contentUri.toString(),
            mimeType = mimeType,
            filePath = filePath,
            dateAdded = dateAdded,
            dateModified = dateModified,
            durationMs = durationMs,
            width = width,
            height = height
        )
    }
}
