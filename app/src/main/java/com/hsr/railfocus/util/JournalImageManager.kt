package com.hsr.railfocus.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object JournalImageManager {

    suspend fun saveImageFromUri(context: Context, journeyId: String, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "journals/$journeyId").apply { mkdirs() }
            val fileName = "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
            val targetFile = File(dir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                val boundsOnly = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, boundsOnly)
                
                val maxDim = 1600
                var inSampleSize = 1
                val w = boundsOnly.outWidth
                val h = boundsOnly.outHeight
                if (w > maxDim || h > maxDim) {
                    val halfW = w / 2
                    val halfH = h / 2
                    while ((halfW / inSampleSize) >= maxDim && (halfH / inSampleSize) >= maxDim) {
                        inSampleSize *= 2
                    }
                }

                context.contentResolver.openInputStream(uri)?.use { stream2 ->
                    val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
                    val bitmap = BitmapFactory.decodeStream(stream2, null, decodeOptions) ?: return@withContext null
                    FileOutputStream(targetFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    }
                    bitmap.recycle()
                }
            }
            targetFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun deleteJournalImages(context: Context, journeyId: String) {
        try {
            val dir = File(context.filesDir, "journals/$journeyId")
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        } catch (_: Exception) {}
    }
}

