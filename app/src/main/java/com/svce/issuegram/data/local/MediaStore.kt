package com.svce.issuegram.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaStore(private val context: Context) {
    suspend fun prepare(uri: Uri, requestId: String): File = withContext(Dispatchers.IO) {
        val raw = File(context.cacheDir, "$requestId.source")
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> raw.outputStream().use { out ->
                val buffer = ByteArray(8192); var total = 0
                while (true) { val count = input.read(buffer); if (count < 0) break; total += count
                    require(total <= 30 * 1024 * 1024) { "Choose a photo smaller than 30 MB." }; out.write(buffer, 0, count) }
            } } ?: error("Could not read this photo.")
            compress(raw, requestId)
        } finally { raw.delete() }
    }

    suspend fun prepare(file: File, requestId: String): File = withContext(Dispatchers.IO) {
        require(file.exists() && file.length() in 1..30L * 1024 * 1024) { "Take or select a valid photo." }
        compress(file, requestId)
    }

    private fun compress(raw: File, requestId: String): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(raw.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "This file is not a supported photo." }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2880) sample *= 2
        val bitmap = BitmapFactory.decodeFile(raw.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("Could not decode photo.")
        val orientation = ExifInterface(raw).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix().apply { when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
        } }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        val ratio = minOf(1440f / rotated.width, 1440f / rotated.height, 1f)
        val scaled = rotated.scale((rotated.width * ratio).toInt().coerceAtLeast(1), (rotated.height * ratio).toInt().coerceAtLeast(1), true)
        val destination = File(context.noBackupFilesDir, "uploads/$requestId.jpg").apply { parentFile?.mkdirs() }
        try { destination.outputStream().use { check(scaled.compress(Bitmap.CompressFormat.JPEG, 84, it)) } }
        finally { if (scaled !== rotated) scaled.recycle(); if (rotated !== bitmap) rotated.recycle(); bitmap.recycle() }
        return destination
    }
}
