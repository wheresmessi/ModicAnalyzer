package com.example.modicanalyzer

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SimpleMainActivity : ComponentActivity() {
    private lateinit var modelHandler: SimpleTensorFlowHandler
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize model handler
        modelHandler = SimpleTensorFlowHandler.getInstance()
        
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF3B82F6),
                    secondary = Color(0xFF1E3A8A),
                    background = Color(0xFFF8FAFC)
                )
            ) {
                MainScreen(modelHandler = modelHandler)
            }
        }
        
        // Initialize model in background
        lifecycleScope.launch(Dispatchers.IO) {
            val result = modelHandler.initializeModel(this@SimpleMainActivity)
            withContext(Dispatchers.Main) {
                when (result) {
                    is ModelInitResult.Success -> {
                        Toast.makeText(this@SimpleMainActivity, "AI Model loaded successfully!", Toast.LENGTH_SHORT).show()
                    }
                    is ModelInitResult.Error -> {
                        Toast.makeText(this@SimpleMainActivity, "Model loading failed: ${result.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        modelHandler.cleanup()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modelHandler: SimpleTensorFlowHandler) {
    var t1Image by remember { mutableStateOf<Bitmap?>(null) }
    var t2Image by remember { mutableStateOf<Bitmap?>(null) }
    var analysisResult by remember { mutableStateOf<AnalysisResult?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var isModelReady by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    
    // Check model status
    LaunchedEffect(Unit) {
        while (true) {
            isModelReady = modelHandler.isInitialized()
            if (isModelReady) break
            kotlinx.coroutines.delay(500)
        }
    }
    
    // Image pickers
    val t1ImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            t1Image = ImageUtils.getBitmapFromUri(context, it)
        }
    }
    
    val t2ImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            t2Image = ImageUtils.getBitmapFromUri(context, it)
        }
    }
    
    // Analysis function
    fun performAnalysis() {
        val t1 = t1Image
        val t2 = t2Image
        
        if (t1 == null || t2 == null) {
            Toast.makeText(context, "Please select both T1 and T2 images", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!isModelReady) {
            Toast.makeText(context, "AI model is not ready yet. Please wait...", Toast.LENGTH_SHORT).show()
            return
        }
        
        isAnalyzing = true
        
        (context as ComponentActivity).lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = modelHandler.analyzeImages(t1, t2)
                
                withContext(Dispatchers.Main) {
                    isAnalyzing = false
                    if (result != null) {
                        analysisResult = result
                        showResultDialog = true
                    } else {
                        Toast.makeText(context, "Analysis failed. Please try again.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isAnalyzing = false
                    Toast.makeText(context, "Error during analysis: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "ModicAnalyzer",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E3A8A)
                ),
                actions = {
                    // Model status indicator
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isModelReady) Color(0xFF10B981) else Color(0xFFEF4444)
                            )
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFF8FAFC),
                            Color(0xFFE2E8F0)
                        )
                    )
                )
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "AI-Powered MRI Analysis",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Upload T1 and T2 weighted MRI images for automated Modic change detection",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // Model Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isModelReady) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)
                ),
                border = BorderStroke(
                    1.dp, 
                    if (isModelReady) Color(0xFF10B981) else Color(0xFFEF4444)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isModelReady) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isModelReady) Color(0xFF10B981) else Color(0xFFEF4444),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        if (isModelReady) "AI Model Ready" else "Loading AI Model...",
                        fontWeight = FontWeight.Medium,
                        color = if (isModelReady) Color(0xFF065F46) else Color(0xFF991B1B)
                    )
                }
            }
            
            // Image Selection Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // T1 Image Card
                ImageCard(
                    modifier = Modifier.weight(1f),
                    title = "T1 Weighted",
                    image = t1Image,
                    onClick = { t1ImagePicker.launch("image/*") }
                )
                
                // T2 Image Card
                ImageCard(
                    modifier = Modifier.weight(1f),
                    title = "T2 Weighted",
                    image = t2Image,
                    onClick = { t2ImagePicker.launch("image/*") }
                )
            }
            
            // Analysis Button
            val buttonScale by animateFloatAsState(
                targetValue = if (isAnalyzing) 0.95f else 1f,
                animationSpec = spring(dampingRatio = 0.6f)
            )
            
            Button(
                onClick = { performAnalysis() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .scale(buttonScale),
                enabled = !isAnalyzing && t1Image != null && t2Image != null && isModelReady,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3B82F6)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Analyzing...", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                } else {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Analyze Images", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
    
    // Result Dialog
    if (showResultDialog && analysisResult != null) {
        ResultDialog(
            result = analysisResult!!,
            onDismiss = { 
                showResultDialog = false
                analysisResult = null
            }
        )
    }
}

@Composable
fun ImageCard(
    modifier: Modifier = Modifier,
    title: String,
    image: Bitmap?,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (image != null) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Overlay with title
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f)
                                )
                            )
                        )
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B)
                    )
                    Text(
                        text = "Tap to select",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

@Composable
fun ResultDialog(
    result: AnalysisResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (result.hasModicChange) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (result.hasModicChange) Color(0xFFEF4444) else Color(0xFF10B981),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Analysis Results")
            }
        },
        text = {
            Column {
                Text(
                    text = result.changeType ?: "Unknown",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (result.hasModicChange) Color(0xFFEF4444) else Color(0xFF10B981)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Confidence: ${(result.confidence * 100).toInt()}%",
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = result.details ?: "Analysis completed successfully.",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}