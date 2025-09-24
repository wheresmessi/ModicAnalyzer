package com.example.modicanalyzer

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class OnnxModelHandler(private val context: Context) : ModelHandler {
    private var ortSession: OrtSession? = null
    private var ortEnvironment: OrtEnvironment? = null
    private val inputSize = 224
    
    init {
        loadModel()
    }
    
    private fun loadModel() {
        try {
            println("DEBUG: ONNX ModelHandler initialization started")
            
            // Create ONNX Runtime environment
            ortEnvironment = OrtEnvironment.getEnvironment()
            println("DEBUG: ONNX Environment created successfully")
            
            // Load model from assets
            val modelBytes = context.assets.open("modic_model.onnx").readBytes()
            println("DEBUG: ONNX model loaded from assets, size: ${modelBytes.size} bytes")
            
            // Create session options
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.addCPU(false) // Use CPU
            
            // Create session
            ortSession = ortEnvironment?.createSession(modelBytes, sessionOptions)
            println("DEBUG: ONNX session created successfully")
            
            // Print model info
            ortSession?.let { session ->
                println("DEBUG: Model inputs:")
                session.inputInfo.forEach { (name, info) ->
                    println("DEBUG: Input - Name: $name, Info: $info")
                }
                
                println("DEBUG: Model outputs:")
                session.outputInfo.forEach { (name, info) ->
                    println("DEBUG: Output - Name: $name, Info: $info")
                }
            }
            
        } catch (e: Exception) {
            println("DEBUG: ONNX model loading failed: ${e.message}")
            println("DEBUG: Error type: ${e.javaClass.simpleName}")
            when (e) {
                is java.io.FileNotFoundException -> {
                    println("DEBUG: modic_model.onnx not found in assets folder")
                }
                is java.lang.UnsatisfiedLinkError -> {
                    println("DEBUG: ONNX Runtime native library not loaded properly")
                }
                else -> {
                    println("DEBUG: Other error occurred during model loading")
                }
            }
            e.printStackTrace()
            ortSession = null
            ortEnvironment = null
        }
    }
    
    override fun analyzeImage(bitmap: Bitmap): Pair<String, Float> {
        return try {
            println("DEBUG: Starting ONNX image analysis...")
            
            val session = ortSession
            if (session == null) {
                println("DEBUG: ONNX session is null! Model failed to load.")
                println("DEBUG: Check if modic_model.onnx exists in assets folder")
                println("DEBUG: Check Logcat for model loading errors above")
                return Pair("Model failed to load", 0.0f)
            }
            
            println("DEBUG: ONNX session is available, proceeding with inference...")
            
            // Preprocess image
            val inputData = preprocessImage(bitmap)
            println("DEBUG: Image preprocessed, input size: ${inputData.remaining()}")
            
            // Create input tensor
            val inputArray = FloatArray(inputData.remaining())
            inputData.rewind()
            inputData.get(inputArray)
            
            // Reshape array for ONNX tensor (NCHW format: 1 x 3 x 224 x 224)
            val tensorData = Array(1) { Array(3) { Array(inputSize) { FloatArray(inputSize) } } }
            var idx = 0
            for (c in 0..2) {
                for (h in 0 until inputSize) {
                    for (w in 0 until inputSize) {
                        tensorData[0][c][h][w] = inputArray[idx++]
                    }
                }
            }
            val inputTensor = OnnxTensor.createTensor(ortEnvironment!!, tensorData)
            
            println("DEBUG: Input tensor created")
            
            // Run inference - get the actual input name from model info
            val inputName = session.inputInfo.keys.first()
            println("DEBUG: Using input name: $inputName")
            val inputs = mapOf(inputName to inputTensor)
            val outputs = session.run(inputs)
            
            println("DEBUG: ONNX inference completed")
            
            // Get output with safe casting
            val outputValue = outputs[0].value
            println("DEBUG: Output type: ${outputValue?.javaClass?.simpleName}")
            
            val predictions = when (outputValue) {
                is Array<*> -> {
                    // Handle 2D array output: Array<FloatArray>
                    @Suppress("UNCHECKED_CAST")
                    val outputArray = outputValue as Array<FloatArray>
                    outputArray[0]
                }
                is FloatArray -> {
                    // Handle 1D array output: FloatArray
                    outputValue
                }
                else -> {
                    println("DEBUG: Unexpected output type: ${outputValue?.javaClass}")
                    throw RuntimeException("Unsupported output format: ${outputValue?.javaClass}")
                }
            }
            
            println("DEBUG: Raw predictions: ${predictions.contentToString()}")
            
            // Apply softmax if needed
            val softmaxPredictions = softmax(predictions)
            println("DEBUG: Softmax predictions: ${softmaxPredictions.contentToString()}")
            
            val noModicProb = softmaxPredictions[0]
            val modicProb = softmaxPredictions[1]
            
            val result = if (modicProb > noModicProb) {
                "Modic Change Detected"
            } else {
                "No Modic Change"
            }
            
            val confidence = maxOf(noModicProb, modicProb)
            
            println("DEBUG: Analysis result: $result (confidence: $confidence)")
            
            // Clean up
            inputTensor.close()
            outputs.close()
            
            Pair(result, confidence)
            
        } catch (e: Exception) {
            println("DEBUG: ONNX analysis error: ${e.message}")
            e.printStackTrace()
            Pair("Analysis failed: ${e.message}", 0.0f)
        }
    }
    
    private fun preprocessImage(bitmap: Bitmap): FloatBuffer {
        println("DEBUG: Preprocessing image for ONNX...")
        
        // Resize bitmap to model input size
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        println("DEBUG: Resized bitmap to ${resizedBitmap.width}x${resizedBitmap.height}")
        
        // Create buffer
        val buffer = ByteBuffer.allocateDirect(4 * 3 * inputSize * inputSize)
        buffer.order(ByteOrder.nativeOrder())
        val floatBuffer = buffer.asFloatBuffer()
        
        // Convert to RGB float values normalized to [0, 1] with CHW format (Channel-Height-Width)
        val pixels = IntArray(inputSize * inputSize)
        resizedBitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        
        // Channel-first format (CHW): Red channel, Green channel, Blue channel
        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            floatBuffer.put(r)
        }
        for (pixel in pixels) {
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            floatBuffer.put(g)
        }
        for (pixel in pixels) {
            val b = (pixel and 0xFF) / 255.0f
            floatBuffer.put(b)
        }
        
        floatBuffer.rewind()
        println("DEBUG: Image preprocessing completed, buffer size: ${floatBuffer.remaining()}")
        
        return floatBuffer
    }
    
    private fun softmax(values: FloatArray): FloatArray {
        val max = values.maxOrNull() ?: 0f
        val exp = values.map { kotlin.math.exp((it - max).toDouble()).toFloat() }
        val sum = exp.sum()
        return exp.map { it / sum }.toFloatArray()
    }
    
    override fun analyzeDualImages(t1Image: Bitmap, t2Image: Bitmap): Pair<String, Float> {
        return try {
            println("DEBUG: Starting dual image ONNX analysis...")
            
            val session = ortSession
            if (session == null) {
                println("DEBUG: ONNX session is null! Model failed to load.")
                return Pair("Model failed to load", 0.0f)
            }
            
            println("DEBUG: ONNX session is available, proceeding with dual image inference...")
            
            // Preprocess both images
            val t1InputData = preprocessImage(t1Image)
            val t2InputData = preprocessImage(t2Image)
            
            println("DEBUG: Both images preprocessed")
            
            // Create input tensors for both T1 and T2 images
            val t1InputArray = FloatArray(t1InputData.remaining())
            t1InputData.rewind()
            t1InputData.get(t1InputArray)
            
            val t2InputArray = FloatArray(t2InputData.remaining())  
            t2InputData.rewind()
            t2InputData.get(t2InputArray)
            
            // Reshape arrays for ONNX tensors (NCHW format: 1 x 3 x 224 x 224)
            val t1TensorData = Array(1) { Array(3) { Array(inputSize) { FloatArray(inputSize) } } }
            val t2TensorData = Array(1) { Array(3) { Array(inputSize) { FloatArray(inputSize) } } }
            
            var idx = 0
            for (c in 0..2) {
                for (h in 0 until inputSize) {
                    for (w in 0 until inputSize) {
                        t1TensorData[0][c][h][w] = t1InputArray[idx]
                        t2TensorData[0][c][h][w] = t2InputArray[idx]
                        idx++
                    }
                }
            }
            
            val t1InputTensor = OnnxTensor.createTensor(ortEnvironment!!, t1TensorData)
            val t2InputTensor = OnnxTensor.createTensor(ortEnvironment!!, t2TensorData)
            
            println("DEBUG: Input tensors created for both T1 and T2 images")
            
            // Run inference with both inputs
            val inputs = mapOf(
                "t1_image" to t1InputTensor,
                "t2_image" to t2InputTensor
            )
            val outputs = session.run(inputs)
            
            println("DEBUG: ONNX dual image inference completed")
            
            // Get output with safe casting
            val outputValue = outputs[0].value
            println("DEBUG: Output type: ${outputValue?.javaClass?.simpleName}")
            
            val predictions = when (outputValue) {
                is Array<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val outputArray = outputValue as Array<FloatArray>
                    outputArray[0]
                }
                is FloatArray -> {
                    outputValue
                }
                else -> {
                    println("DEBUG: Unexpected output type: ${outputValue?.javaClass}")
                    throw RuntimeException("Unsupported output format: ${outputValue?.javaClass}")
                }
            }
            
            println("DEBUG: Raw predictions: ${predictions.contentToString()}")
            
            // Apply softmax to get probabilities
            val softmaxPredictions = softmax(predictions)
            println("DEBUG: Softmax predictions: ${softmaxPredictions.contentToString()}")
            
            val noModicProb = softmaxPredictions[0]
            val modicProb = softmaxPredictions[1]
            
            val result = if (modicProb > noModicProb) {
                "Modic Change Detected"
            } else {
                "No Modic Change"
            }
            
            val confidence = maxOf(noModicProb, modicProb)
            
            println("DEBUG: Dual image analysis result: $result (confidence: $confidence)")
            
            // Clean up
            t1InputTensor.close()
            t2InputTensor.close()
            outputs.close()
            
            Pair(result, confidence)
            
        } catch (e: Exception) {
            println("DEBUG: ONNX dual image analysis error: ${e.message}")
            e.printStackTrace()
            Pair("Analysis failed: ${e.message}", 0.0f)
        }
    }
    
    override fun isModelLoaded(): Boolean {
        return ortSession != null
    }
    
    override fun close() {
        try {
            ortSession?.close()
            ortEnvironment?.close()
            println("DEBUG: ONNX resources cleaned up")
        } catch (e: Exception) {
            println("DEBUG: Error cleaning up ONNX resources: ${e.message}")
        }
    }
}
