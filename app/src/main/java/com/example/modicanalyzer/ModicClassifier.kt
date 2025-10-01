/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.
   Adapted for Medical Image Analysis with Dual-Input Architecture */

package com.example.modicanalyzer

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Medical Image Classifier for Modic changes detection
 * Following official TensorFlow Lite documentation pattern
 * Adapted for dual-input model architecture (T1 + T2 weighted MRI)
 */
class ModicClassifier(private val context: Context) {
    // TF Lite interpreter as per official pattern
    private var interpreter: Interpreter? = null
    
    var isInitialized = false
        private set

    /** Executor to run inference task in the background. */
    private val executorService: ExecutorService = Executors.newCachedThreadPool()

    private var inputImageWidth: Int = 0 // will be inferred from TF Lite model
    private var inputImageHeight: Int = 0 // will be inferred from TF Lite model
    private var modelInputSize: Int = 0 // will be inferred from TF Lite model

    fun initialize(): Task<Void?> {
        val task = TaskCompletionSource<Void?>()
        executorService.execute {
            try {
                initializeInterpreter()
                task.setResult(null)
            } catch (e: IOException) {
                task.setException(e)
            }
        }
        return task.task
    }

    @Throws(IOException::class)
    private fun initializeInterpreter() {
        try {
            // Load the TF Lite model from asset folder (official pattern)
            val assetManager = context.assets
            val model = loadModelFile(assetManager, "modic_model.tflite")
            
            // Create interpreter with 2025 recommended options
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseNNAPI(false) // Disable for consistent behavior
                setUseXNNPACK(true) // Enable for better performance on 2025 Android
            }
            
            val interpreter = Interpreter(model, options)

            // Read input shape from model file (adapted for dual inputs)
            val inputShape = interpreter.getInputTensor(0).shape()
            inputImageWidth = inputShape[1]
            inputImageHeight = inputShape[2]
            modelInputSize = FLOAT_TYPE_SIZE * inputImageWidth * inputImageHeight * PIXEL_SIZE

            // Log dual input model information
            val inputCount = interpreter.inputTensorCount
            Log.d(TAG, "Model has $inputCount input tensor(s)")
            if (inputCount >= 2) {
                val input1Shape = interpreter.getInputTensor(0).shape()
                val input2Shape = interpreter.getInputTensor(1).shape()
                Log.d(TAG, "Input 0 (T1 weighted) shape: [${input1Shape.joinToString(", ")}]")
                Log.d(TAG, "Input 1 (T2 weighted) shape: [${input2Shape.joinToString(", ")}]")
            }

            // Finish interpreter initialization
            this.interpreter = interpreter

            isInitialized = true
            Log.d(TAG, "✅ TFLite 2.17.0 interpreter initialized successfully for medical imaging.")
            
        } catch (e: IllegalArgumentException) {
            // Handle any remaining compatibility issues
            Log.w(TAG, "⚠️ Model compatibility issue detected", e)
            Log.w(TAG, "Ensure your model was trained with TensorFlow 2.17.0")
            
            // Set default values for fallback
            inputImageWidth = 224
            inputImageHeight = 224
            modelInputSize = FLOAT_TYPE_SIZE * inputImageWidth * inputImageHeight * PIXEL_SIZE
            
            // Mark as initialized but with fallback mode
            isInitialized = true
            interpreter = null // Keep null to trigger fallback
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Unexpected error initializing TensorFlow Lite", e)
            throw IOException("Failed to initialize model: ${e.message}", e)
        }
    }

    @Throws(IOException::class)
    private fun loadModelFile(assetManager: AssetManager, filename: String): ByteBuffer {
        val fileDescriptor = assetManager.openFd(filename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun classify(t1Image: Bitmap, t2Image: Bitmap): String {
        check(isInitialized) { "TF Lite Classifier is not initialized yet." }

        // Check if we're in fallback mode (model incompatible)
        if (interpreter == null) {
            Log.d(TAG, "Using fallback mode - model version incompatible")
            return createCompatibilityFallbackResult()
        }

        // Pre-processing: resize both input images to match model input shape
        val resizedT1 = Bitmap.createScaledBitmap(
            t1Image,
            inputImageWidth,
            inputImageHeight,
            true
        )
        val resizedT2 = Bitmap.createScaledBitmap(
            t2Image,
            inputImageWidth,
            inputImageHeight,
            true
        )

        // Convert bitmaps to ByteBuffers for dual input
        val t1Buffer = convertBitmapToByteBuffer(resizedT1)
        val t2Buffer = convertBitmapToByteBuffer(resizedT2)

        // Define array to store model output
        val output = Array(1) { FloatArray(OUTPUT_CLASSES_COUNT) }

        // Run inference with dual inputs
        val inputs = arrayOf(t1Buffer, t2Buffer)
        val outputs = mapOf(0 to output)
        
        interpreter?.runForMultipleInputsOutputs(inputs, outputs)

        // Post-processing: interpret medical results
        val result = output[0]
        val noModicProb = result[0]
        val modicProb = result[1]
        
        val prediction = if (modicProb > noModicProb) "Modic Changes Detected" else "No Modic Changes"
        val confidence = maxOf(noModicProb, modicProb)

        val resultString = """
            Medical Image Analysis Result:
            Prediction: $prediction
            Confidence: ${String.format("%.1f%%", confidence * 100)}
            
            Detailed Probabilities:
            • No Modic Changes: ${String.format("%.1f%%", noModicProb * 100)}
            • Modic Changes Present: ${String.format("%.1f%%", modicProb * 100)}
        """.trimIndent()

        return resultString
    }

    fun classifyAsync(t1Image: Bitmap, t2Image: Bitmap): Task<String> {
        val task = TaskCompletionSource<String>()
        executorService.execute {
            try {
                val result = classify(t1Image, t2Image)
                task.setResult(result)
            } catch (e: Exception) {
                Log.e(TAG, "Classification error", e)
                val fallbackResult = """
                    Medical Analysis Unavailable
                    Status: Model processing failed
                    Recommendation: Please consult healthcare provider
                    Note: This tool is for research purposes only
                """.trimIndent()
                task.setResult(fallbackResult)
            }
        }
        return task.task
    }

    fun close() {
        executorService.execute {
            interpreter?.close()
            Log.d(TAG, "Closed TFLite interpreter.")
        }
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(modelInputSize)
        byteBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputImageWidth * inputImageHeight)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (pixelValue in pixels) {
            val r = (pixelValue shr 16 and 0xFF)
            val g = (pixelValue shr 8 and 0xFF)
            val b = (pixelValue and 0xFF)

            // Medical imaging normalization (different from grayscale)
            val normalizedR = (r - NORMALIZATION_MEAN) / NORMALIZATION_STD
            val normalizedG = (g - NORMALIZATION_MEAN) / NORMALIZATION_STD
            val normalizedB = (b - NORMALIZATION_MEAN) / NORMALIZATION_STD

            byteBuffer.putFloat(normalizedR)
            byteBuffer.putFloat(normalizedG)
            byteBuffer.putFloat(normalizedB)
        }

        return byteBuffer
    }

    /**
     * Create compatibility fallback result when model version is incompatible
     */
    private fun createCompatibilityFallbackResult(): String {
        return """
            Medical Image Analysis - Compatibility Mode
            
            ⚠️ Model Version Incompatible
            Your TensorFlow Lite model uses newer operators (FULLY_CONNECTED v12) 
            that are not supported in TensorFlow Lite 2.5.0.
            
            Solutions:
            1. Convert your model to be compatible with TF Lite 2.5.0
            2. Update to a newer TensorFlow Lite version
            3. Use this app for image organization and manual review
            
            Status: App functional in compatibility mode
            Recommendation: Consult healthcare provider for analysis
            Note: This tool is for research purposes only
        """.trimIndent()
    }

    companion object {
        private const val TAG = "ModicClassifier"

        private const val FLOAT_TYPE_SIZE = 4
        private const val PIXEL_SIZE = 3 // RGB for medical images

        private const val OUTPUT_CLASSES_COUNT = 2 // Binary classification
        
        // Medical imaging normalization constants
        private const val NORMALIZATION_MEAN = 127.5f
        private const val NORMALIZATION_STD = 127.5f
    }
}