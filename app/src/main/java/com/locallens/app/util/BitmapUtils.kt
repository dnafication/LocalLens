package com.locallens.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max
import kotlin.math.min

object BitmapUtils {

    fun loadAndOrient(filePath: String): Bitmap? {
        val bitmap = BitmapFactory.decodeFile(filePath) ?: return null
        return try {
            val exif = ExifInterface(filePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            rotateBitmap(bitmap, orientation)
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun cropFace(bitmap: Bitmap, boundingBox: Rect, padding: Float): Bitmap? {
        val width = bitmap.width
        val height = bitmap.height
        val boxWidth = boundingBox.width()
        val boxHeight = boundingBox.height()
        val padX = (boxWidth * padding).toInt()
        val padY = (boxHeight * padding).toInt()

        val left = max(0, boundingBox.left - padX)
        val top = max(0, boundingBox.top - padY)
        val right = min(width, boundingBox.right + padX)
        val bottom = min(height, boundingBox.bottom + padY)

        val cropWidth = right - left
        val cropHeight = bottom - top
        if (cropWidth <= 0 || cropHeight <= 0) return null

        return Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)
    }
}
