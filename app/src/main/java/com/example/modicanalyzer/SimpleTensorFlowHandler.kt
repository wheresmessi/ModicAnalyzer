package com.example.modicanalyzer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp

class SimpleTensorFlowHandler private constructor() {
    private var interpreter: Interpreter? = null
    private var isModelLoaded = false
    
    companion object {
        private const val TAG = "SimpleTFHandler"
        private const val MODEL_FILE_NAME = "modic_model.tflite"
        private const val INPUT_SIZE = 224
        private const val PIXEL_SIZE = 3 // RGB
        private const val NUM_CLASSES = 3
        private const val NORMALIZATION_MEAN = 127.5f
        private const val NORMALIZATION_STD = 127.5f
        
        @Volatile
        private var INSTANCE: SimpleTensorFlowHandler? = null
        
        fun getInstance(): SimpleTensorFlowHandler {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SimpleTensorFlowHandler().also { INSTANCE = it }
            }
        }
    }
    
    fun initializeModel(context: Context): ModelInitResult {
        return try {
            if (isModelLoaded) {
                Log.d(TAG, "Model already initialized")
                return ModelInitResult.Success
            }
            
            Log.d(TAG, "Starting model initialization...")
            
            // Load model file from assets
            val modelBuffer = loadModelFile(context)
                ?: return ModelInitResult.Error("Failed to load model file from assets")
            
            // Configure interpreter options (CPU only)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            
            // Create interpreter
            interpreter = Interpreter(modelBuffer, options)
            
            // Validate model
            val validationResult = validateModel()
            if (validationResult != ModelInitResult.Success) {
                cleanup()
                return validationResult
            }
            
            isModelLoaded = true
            Log.d(TAG, "Model initialized successfully!")
            return ModelInitResult.Success
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing model: ${e.message}", e)
            cleanup()
            return ModelInitResult.Error("Model initialization failed: ${e.message}")
        }
    }
    
    private fun loadModelFile(context: Context): MappedByteBuffer? {
        return try {
            val fileDescriptor = context.assets.openFd(MODEL_FILE_NAME)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            val buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            Log.d(TAG, "Model file loaded successfully, size: $declaredLength bytes")
            buffer
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model file: ${e.message}", e)
            null
        }
    }
    
    private fun validateModel(): ModelInitResult {
        return try {
            val interpreter = this.interpreter ?: return ModelInitResult.Error("Interpreter is null")
            
            // Check input tensor
            val inputTensor = interpreter.getInputTensor(0)
            val inputShape = inputTensor.shape()
            Log.d(TAG, "Input shape: ${inputShape.contentToString()}")
            
            if (inputShape.size != 4) {
                return ModelInitResult.Error("Invalid input shape: expected 4D tensor, got ${inputShape.size}D")
            }
            
            val batchSize = inputShape[0]
            val height = inputShape[1] 
            val width = inputShape[2]
            val channels = inputShape[3]
            
            Log.d(TAG, "Model expects: batch=$batchSize, height=$height, width=$width, channels=$channels")
            
            // Check output tensor
            val outputTensor = interpreter.getOutputTensor(0)
            val outputShape = outputTensor.shape()
            Log.d(TAG, "Output shape: ${outputShape.contentToString()}")
            
            if (outputShape.size != 2 || outputShape[1] < NUM_CLASSES) {
                return ModelInitResult.Error("Invalid output shape: ${outputShape.contentToString()}")
            }
            
            // Test inference with dummy data
            val testInput = ByteBuffer.allocateDirect(4 * height * width * channels).apply {
                order(ByteOrder.nativeOrder())
                rewind()
            }
            
            val testOutput = Array(1) { FloatArray(outputShape[1]) }
            
            interpreter.run(testInput, testOutput)
            Log.d(TAG, "Model validation successful - test inference completed")
            
            ModelInitResult.Success
            
        } catch (e: Exception) {
            Log.e(TAG, "Model validation failed: ${e.message}", e)
            ModelInitResult.Error("Model validation failed: ${e.message}")
        }
    }
    
    fun analyzeImages(t1Image: Bitmap, t2Image: Bitmap): AnalysisResult? {
        if (!isModelLoaded || interpreter == null) {
            Log.w(TAG, "Model not loaded, cannot perform analysis")
            return null
        }
        
        return try {
            Log.d(TAG, "Starting image analysis...")
            
            // Preprocess images
            val combinedImage = combineImages(t1Image, t2Image)
            val inputBuffer = preprocessImage(combinedImage)
            
            // Prepare output buffer
            val outputArray = Array(1) { FloatArray(NUM_CLASSES) }
            
            // Run inference
            interpreter?.run(inputBuffer, outputArray)
            
            // Process results
            val predictions = outputArray[0]
            val probabilities = softmax(predictions)
            
            Log.d(TAG, "Raw predictions: ${predictions.contentToString()}")
            Log.d(TAG, "Probabilities: ${probabilities.contentToString()}")
            
            // Interpret results (assuming classes: 0=Normal, 1=Type1, 2=Type2)
            val maxIndex = probabilities.indexOfMax()
            val confidence = probabilities[maxIndex]
            val hasModicChange = maxIndex > 0 // Any non-normal classification
            
            val changeType = when (maxIndex) {
                0 -> "Normal"
                1 -> "Modic Type 1"
                2 -> "Modic Type 2"
                else -> "Unknown"
            }
            
            Log.d(TAG, "Analysis complete - Type: $changeType, Confidence: ${confidence * 100}%")
            
            AnalysisResult(
                hasModicChange = hasModicChange,
                confidence = confidence,
                changeType = changeType,
                details = "Detected: $changeType with ${(confidence * 100).toInt()}% confidence"
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during analysis: ${e.message}", e)
            null
        }
    }
    
    private fun combineImages(t1Image: Bitmap, t2Image: Bitmap): Bitmap {
        // Resize both images to half width
        val halfWidth = INPUT_SIZE / 2
        val resizedT1 = Bitmap.createScaledBitmap(t1Image, halfWidth, INPUT_SIZE, true)
        val resizedT2 = Bitmap.createScaledBitmap(t2Image, halfWidth, INPUT_SIZE, true)
        
        // Create combined bitmap
        val combined = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combined)
        
        // Draw T1 on left, T2 on right
        canvas.drawBitmap(resizedT1, 0f, 0f, null)
        canvas.drawBitmap(resizedT2, halfWidth.toFloat(), 0f, null)
        
        return combined
    }
    
    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * PIXEL_SIZE)
        byteBuffer.order(ByteOrder.nativeOrder())
        
        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        var pixel = 0
        for (i in 0 until INPUT_SIZE) {
            for (j in 0 until INPUT_SIZE) {
                val value = intValues[pixel++]
                
                // Extract RGB and normalize to [-1, 1]
                val r = ((value shr 16) and 0xFF) / NORMALIZATION_STD - NORMALIZATION_MEAN / NORMALIZATION_STD
                val g = ((value shr 8) and 0xFF) / NORMALIZATION_STD - NORMALIZATION_MEAN / NORMALIZATION_STD
                val b = (value and 0xFF) / NORMALIZATION_STD - NORMALIZATION_MEAN / NORMALIZATION_STD
                
                byteBuffer.putFloat(r)
                byteBuffer.putFloat(g)
                byteBuffer.putFloat(b)
            }
        }
        
        return byteBuffer
    }
    
    private fun softmax(predictions: FloatArray): FloatArray {
        val maxLogit = predictions.maxOrNull() ?: 0f
        val exps = predictions.map { exp((it - maxLogit).toDouble()).toFloat() }.toFloatArray()
        val sumExps = exps.sum()
        return exps.map { it / sumExps }.toFloatArray()
    }
    
    private fun FloatArray.indexOfMax(): Int {
        var maxIndex = 0
        var maxValue = this[0]
        for (i in 1 until size) {
            if (this[i] > maxValue) {
                maxValue = this[i]
                maxIndex = i
            }
        }
        return maxIndex
    }
    
    fun isInitialized(): Boolean = isModelLoaded
    
    fun cleanup() {
        try {
            interpreter?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}", e)
        } finally {
            interpreter = null
            isModelLoaded = false
            Log.d(TAG, "Model handler cleaned up")
        }
    }
}