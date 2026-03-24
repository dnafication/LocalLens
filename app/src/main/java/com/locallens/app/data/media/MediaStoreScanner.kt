package com.locallens.app.data.media

import android.content.Context
import android.net.Uri
import android.os.Build
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

    // Folder names that indicate screenshots or screen recordings (case-insensitive)
    private val excludedFolderPatterns = listOf("screenshot", "screen record")

    fun scanAll(): Flow<MediaFile> = scanWithSelection(null, null)

    fun scanSince(sinceEpochMillis: Long): Flow<MediaFile> {
        val selection = "${MediaStore.MediaColumns.DATE_MODIFIED} > ?"
        val selectionArgs = arrayOf((sinceEpochMillis / 1000).toString())
        return scanWithSelection(selection, selectionArgs)
    }

    private fun scanWithSelection(selection: String?, selectionArgs: Array<String>?): Flow<MediaFile> = flow {
        val collections = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.DATA)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(MediaStore.Video.Media.DURATION)
            // RELATIVE_PATH is available on API 29+ and is locale-independent
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
            }
        }.toTypedArray()

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
                val relativePathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    it.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                } else -1

                while (it.moveToNext()) {
                    val mimeType = it.getString(mimeCol) ?: continue
                    if (mimeType !in allowedMimeTypes) continue

                    val width = it.getInt(widthCol)
                    val height = it.getInt(heightCol)
                    if (width == 0 || height == 0) continue

                    // Use RELATIVE_PATH (API 29+) for locale-independent folder filtering;
                    // fall back to DATA path on older APIs
                    val pathForFiltering = if (relativePathCol >= 0) {
                        it.getString(relativePathCol) ?: ""
                    } else {
                        it.getString(dataCol) ?: ""
                    }
                    if (isExcludedPath(pathForFiltering)) continue

                    val filePath = it.getString(dataCol) ?: continue
                    val mediaStoreId = it.getLong(idCol)
                    val contentUri = Uri.withAppendedPath(uri, mediaStoreId.toString())

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

    /**
     * Returns true if the given path belongs to an excluded folder (e.g. Screenshots, Screen recordings).
     * Matching is case-insensitive to handle locale differences.
     */
    private fun isExcludedPath(path: String): Boolean {
        val lowerPath = path.lowercase()
        return excludedFolderPatterns.any { pattern -> lowerPath.contains(pattern) }
    }
}
