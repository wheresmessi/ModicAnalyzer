package com.example.modicanalyzer

import android.graphics.Bitmap

interface ModelHandler {
    fun analyzeImage(bitmap: Bitmap): Pair<String, Float>
    fun analyzeDualImages(t1Image: Bitmap, t2Image: Bitmap): Pair<String, Float>
    fun isModelLoaded(): Boolean
    fun close()
}