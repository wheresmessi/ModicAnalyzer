package com.example.modicanalyzer

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class ModicModelHandler(context: Context) {
    private var interpreter: Interpreter? = null
    private val inputSize = 224 // Adjust based on your model's input size
    private val numClasses = 2 // Assuming binary classification: No Modic, Modic

    init {
        try {
            val model = loadModelFile(context, "modic_model.tflite")
            interpreter = Interpreter(model)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun analyzeImage(bitmap: Bitmap): ModicAnalysisResult {
        return try {
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            val inputBuffer = convertBitmapToByteBuffer(resizedBitmap)
            
            val output = Array(1) { FloatArray(numClasses) }
            interpreter?.run(inputBuffer, output)
            
            val predictions = output[0]
            val noModicScore = predictions[0]
            val modicScore = predictions[1]
            
            val hasModicChange = modicScore > noModicScore
            val confidence = if (hasModicChange) modicScore else noModicScore
            
            ModicAnalysisResult(
                hasModicChange = hasModicChange,
                confidence = confidence,
                modicScore = modicScore,
                noModicScore = noModicScore
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ModicAnalysisResult(
                hasModicChange = false,
                confidence = 0f,
                modicScore = 0f,
                noModicScore = 0f,
                error = "Analysis failed: ${e.message}"
            )
        }
    }

    fun analyzeTwoImages(bitmap1: Bitmap, bitmap2: Bitmap): ModicAnalysisResult {
        return try {
            val result1 = analyzeImage(bitmap1)
            val result2 = analyzeImage(bitmap2)
            
            // Combine results - take the higher Modic score from either image
            val combinedModicScore = maxOf(result1.modicScore, result2.modicScore)
            val combinedNoModicScore = (result1.noModicScore + result2.noModicScore) / 2f
            val hasModicChange = combinedModicScore > combinedNoModicScore
            val confidence = if (hasModicChange) combinedModicScore else combinedNoModicScore
            
            ModicAnalysisResult(
                hasModicChange = hasModicChange,
                confidence = confidence,
                modicScore = combinedModicScore,
                noModicScore = combinedNoModicScore,
                error = result1.error ?: result2.error
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ModicAnalysisResult(
                hasModicChange = false,
                confidence = 0f,
                modicScore = 0f,
                noModicScore = 0f,
                error = "Combined analysis failed: ${e.message}"
            )
        }
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        
        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        var pixelIndex = 0
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val pixel = intValues[pixelIndex++]
                
                // Normalize RGB values to [0, 1] range
                byteBuffer.putFloat(((pixel shr 16 and 0xFF) / 255.0f))
                byteBuffer.putFloat(((pixel shr 8 and 0xFF) / 255.0f))
                byteBuffer.putFloat(((pixel and 0xFF) / 255.0f))
            }
        }
        
        return byteBuffer
    }

    fun close() {
        interpreter?.close()
    }
}

data class ModicAnalysisResult(
    val hasModicChange: Boolean,
    val confidence: Float,
    val modicScore: Float,
    val noModicScore: Float,
    val error: String? = null
)