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
            Log.d(TAG, "Device info: ${android.os.Build.MODEL} (${android.os.Build.MANUFACTURER})")
            Log.d(TAG, "Android version: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            
            // Debug: print packaged assets
            val files = context.assets.list("") ?: emptyArray()
            Log.d(TAG, "Assets packaged: ${files.joinToString()}")
            
            // Load model file from assets
            val modelBuffer = loadModelFile(context)
                ?: return ModelInitResult.Error("Failed to load model file from assets")
            
            // Configure interpreter options for maximum compatibility
            val options = Interpreter.Options().apply {
                setNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(4))
                setUseNNAPI(false) // Disable NNAPI for compatibility
                // Note: setAllowFp16PrecisionForFp32 is deprecated, using default precision
                Log.d(TAG, "Interpreter configured with ${Runtime.getRuntime().availableProcessors().coerceAtMost(4)} threads")
            }
            
            // Create interpreter with detailed error handling and version compatibility checks
            try {
                interpreter = Interpreter(modelBuffer, options)
                Log.d(TAG, "TensorFlow Lite interpreter created successfully")
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Model compatibility error - this usually means the model was created with a newer TensorFlow version", e)
                Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
                Log.e(TAG, "Exception message: ${e.message}")
                
                // Check if it's a version compatibility issue
                if (e.message?.contains("version") == true || e.message?.contains("builtin opcode") == true) {
                    return ModelInitResult.Error("Model incompatible: Model was created with a newer TensorFlow version. Please update TensorFlow Lite or recreate the model with an older version. Error: ${e.message}")
                } else {
                    return ModelInitResult.Error("Model format error: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create TensorFlow Lite interpreter", e)
                Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
                Log.e(TAG, "Exception message: ${e.message}")
                Log.e(TAG, "Exception cause: ${e.cause}")
                return ModelInitResult.Error("Failed to create interpreter: ${e.message}")
            }
            
            // Validate model structure (skip test inference for now)
            val validationResult = validateModelStructure()
            if (validationResult != ModelInitResult.Success) {
                cleanup()
                return validationResult
            }
            
            isModelLoaded = true
            Log.d(TAG, "Model initialized successfully!")
            return ModelInitResult.Success
            
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during model initialization", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            Log.e(TAG, "Exception cause: ${e.cause}")
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            cleanup()
            return ModelInitResult.Error("Model initialization failed: ${e.message}")
        }
    }
    
    private fun loadModelFile(context: Context): MappedByteBuffer? {
        return try {
            Log.d(TAG, "Attempting to load model file: $MODEL_FILE_NAME")
            
            // Check if assets directory exists and contains the file
            val assetList = context.assets.list("") ?: emptyArray()
            if (!assetList.contains(MODEL_FILE_NAME)) {
                Log.e(TAG, "Model file '$MODEL_FILE_NAME' not found in assets. Available files: ${assetList.joinToString()}")
                return null
            }
            
            val fileDescriptor = context.assets.openFd(MODEL_FILE_NAME)
            Log.d(TAG, "File descriptor obtained: length=${fileDescriptor.length}, startOffset=${fileDescriptor.startOffset}")
            
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            
            Log.d(TAG, "Mapping file: startOffset=$startOffset, length=$declaredLength")
            val buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            
            Log.d(TAG, "Model file loaded successfully, size: $declaredLength bytes")
            Log.d(TAG, "Buffer capacity: ${buffer.capacity()}, remaining: ${buffer.remaining()}")
            
            // Log first few bytes to help identify model format/version
            val firstBytes = ByteArray(16)
            buffer.get(firstBytes)
            buffer.rewind() // Reset position for interpreter
            Log.d(TAG, "Model header bytes: ${firstBytes.joinToString(" ") { "%02x".format(it) }}")
            
            // Clean up resources
            inputStream.close()
            fileDescriptor.close()
            
            buffer
        } catch (e: java.io.FileNotFoundException) {
            Log.e(TAG, "Model file '$MODEL_FILE_NAME' not found", e)
            Log.e(TAG, "Make sure the file is located at: app/src/main/assets/$MODEL_FILE_NAME")
            null
        } catch (e: java.io.IOException) {
            Log.e(TAG, "IO error loading model file '$MODEL_FILE_NAME'", e)
            Log.e(TAG, "Exception details: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error loading model file '$MODEL_FILE_NAME'", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            Log.e(TAG, "Exception cause: ${e.cause}")
            null
        }
    }
    
    private fun validateModelStructure(): ModelInitResult {
        return try {
            val interpreter = this.interpreter ?: return ModelInitResult.Error("Interpreter is null")
            
            Log.d(TAG, "Validating model structure...")
            
            // Check input tensor
            val inputTensor = interpreter.getInputTensor(0)
            val inputShape = inputTensor.shape()
            Log.d(TAG, "Input tensor shape: ${inputShape.contentToString()}")
            Log.d(TAG, "Input tensor type: ${inputTensor.dataType()}")
            
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
            Log.d(TAG, "Output tensor shape: ${outputShape.contentToString()}")
            Log.d(TAG, "Output tensor type: ${outputTensor.dataType()}")
            
            if (outputShape.size != 2 || outputShape[1] < NUM_CLASSES) {
                return ModelInitResult.Error("Invalid output shape: ${outputShape.contentToString()}")
            }
            
            // Skip test inference for now to isolate initialization issues
            Log.d(TAG, "Model structure validation successful (test inference skipped)")
            
            ModelInitResult.Success
            
        } catch (e: Exception) {
            Log.e(TAG, "Model structure validation failed", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            Log.e(TAG, "Exception cause: ${e.cause}")
            ModelInitResult.Error("Model validation failed: ${e.message}")
        }
    }
    
    // Keep the original validateModel function for future use when test inference is needed
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
            Log.d(TAG, "Starting cleanup...")
            interpreter?.close()
            Log.d(TAG, "Interpreter closed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during interpreter cleanup", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
        } finally {
            interpreter = null
            isModelLoaded = false
            Log.d(TAG, "Model handler cleaned up")
        }
    }
}