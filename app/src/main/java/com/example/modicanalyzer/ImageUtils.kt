package com.example.modicanalyzer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

object ImageUtils {
    
    fun getBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                    // Force software bitmap to avoid HARDWARE config issues
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            
            // Ensure bitmap is in a format that supports pixel access
            ensureSoftwareBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun ensureSoftwareBitmap(bitmap: Bitmap): Bitmap {
        return if (bitmap.config == Bitmap.Config.HARDWARE) {
            // Convert HARDWARE bitmap to ARGB_8888 for pixel access
            val softwareBitmap = Bitmap.createBitmap(
                bitmap.width,
                bitmap.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(softwareBitmap)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            bitmap.recycle() // Free the hardware bitmap
            softwareBitmap
        } else {
            bitmap
        }
    }
}