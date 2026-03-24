package com.locallens.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object BitmapUtils {

    /**
     * Load a bitmap from file path, applying EXIF rotation so it's display-ready.
     */
    fun loadAndOrient(filePath: String): Bitmap {
        val bitmap = BitmapFactory.decodeFile(filePath)
            ?: throw IllegalArgumentException("Cannot decode bitmap from: $filePath")

        val exif = ExifInterface(filePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        }

        return if (matrix.isIdentity) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
        }
    }

    /**
     * Crop a face region from a bitmap with padding, clamped to image boundaries.
     * @param padding Fractional padding to add around the bounding box (e.g. 0.20 for 20%)
     */
    fun cropFace(bitmap: Bitmap, boundingBox: Rect, padding: Float = 0.20f): Bitmap {
        val padW = (boundingBox.width() * padding).roundToInt()
        val padH = (boundingBox.height() * padding).roundToInt()

        val left = max(0, boundingBox.left - padW)
        val top = max(0, boundingBox.top - padH)
        val right = min(bitmap.width, boundingBox.right + padW)
        val bottom = min(bitmap.height, boundingBox.bottom + padH)

        val width = right - left
        val height = bottom - top

        if (width <= 0 || height <= 0) {
            throw IllegalArgumentException("Invalid crop dimensions: ${width}x${height}")
        }

        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }
}
