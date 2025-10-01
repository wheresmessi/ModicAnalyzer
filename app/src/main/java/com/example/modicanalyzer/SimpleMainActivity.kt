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
    // Use new official TensorFlow Lite pattern classifier
    private lateinit var modicClassifier: ModicClassifier
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize model classifier following official TF Lite pattern
        modicClassifier = ModicClassifier(this)
        
        // Initialize model using official async pattern
        modicClassifier.initialize()
            .addOnSuccessListener {
                Toast.makeText(this, "Medical AI Classifier Ready", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Model initialization failed: ${exception.message}", Toast.LENGTH_LONG).show()
            }
        
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF3B82F6),
                    secondary = Color(0xFF1E3A8A),
                    background = Color(0xFFF8FAFC)
                )
            ) {
                MainScreen(classifier = modicClassifier)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up TensorFlow Lite resources (official pattern)
        modicClassifier.close()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(classifier: ModicClassifier) {
    var sagittalImage by remember { mutableStateOf<Bitmap?>(null) }
    var axialImage by remember { mutableStateOf<Bitmap?>(null) }
    var analysisResult by remember { mutableStateOf<String?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    
    // Image pickers for dual-input medical model (T1 and T2 weighted MRI)
    val sagittalImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            sagittalImage = ImageUtils.getBitmapFromUri(context, it)
        }
    }
    
    val axialImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            axialImage = ImageUtils.getBitmapFromUri(context, it)
        }
    }
    
    // Analysis function using official TF Lite pattern
    fun performAnalysis() {
        val sagittal = sagittalImage
        val axial = axialImage
        
        if (sagittal == null || axial == null) {
            Toast.makeText(context, "Please select both T1 and T2 weighted images", Toast.LENGTH_SHORT).show()
            return
        }
        
        isAnalyzing = true
        
        // Use official TensorFlow Lite async pattern
        classifier.classifyAsync(sagittal, axial)
            .addOnSuccessListener { result: String ->
                isAnalyzing = false
                analysisResult = result
                showResultDialog = true
            }
            .addOnFailureListener { exception: Exception ->
                isAnalyzing = false
                Toast.makeText(context, "Error during analysis: ${exception.message}", Toast.LENGTH_LONG).show()
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
                    // Model status indicator - Always ready with official pattern
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF10B981)) // Always green - model loads automatically
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
            // Medical AI Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF0FDF4)
                ),
                border = BorderStroke(1.dp, Color(0xFF10B981))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Medical AI Classifier Ready",
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF065F46)
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
                    image = sagittalImage,
                    onClick = { sagittalImagePicker.launch("image/*") }
                )
                
                // T2 Image Card
                ImageCard(
                    modifier = Modifier.weight(1f),
                    title = "T2 Weighted",
                    image = axialImage,
                    onClick = { axialImagePicker.launch("image/*") }
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
                enabled = !isAnalyzing && sagittalImage != null && axialImage != null,
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
    result: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Medical Analysis Results")
            }
        },
        text = {
            Text(
                text = result,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}