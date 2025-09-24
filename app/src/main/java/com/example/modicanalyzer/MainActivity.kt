package com.example.modicanalyzer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.modicanalyzer.ui.theme.ModicAnalyzerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var modelHandler: ModicModelHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize the TensorFlow Lite model
        modelHandler = ModicModelHandler(this)
        
        setContent {
            ModicAnalyzerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ModicAnalyzerScreen(
                        modelHandler = modelHandler,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        modelHandler.close()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModicAnalyzerScreen(
    modelHandler: ModicModelHandler,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedImage1 by remember { mutableStateOf<Bitmap?>(null) }
    var selectedImage2 by remember { mutableStateOf<Bitmap?>(null) }
    var analysisResult by remember { mutableStateOf<ModicAnalysisResult?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }
    var activeImagePicker by remember { mutableStateOf<Int?>(null) }

    // Check for permissions
    LaunchedEffect(Unit) {
        hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Analyze images when both are selected
    LaunchedEffect(selectedImage1, selectedImage2) {
        if (selectedImage1 != null && selectedImage2 != null) {
            isAnalyzing = true
            analyzeImages(modelHandler, selectedImage1!!, selectedImage2!!) { result ->
                analysisResult = result
                isAnalyzing = false
            }
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = ImageUtils.getBitmapFromUri(context, it)
            
            when (activeImagePicker) {
                1 -> selectedImage1 = bitmap
                2 -> selectedImage2 = bitmap
            }
            activeImagePicker = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo and Title
        Image(
            painter = painterResource(id = R.drawable.modicare_logo),
            contentDescription = "Modicare Logo",
            modifier = Modifier
                .size(80.dp)
                .padding(bottom = 8.dp)
        )
        
        Text(
            text = "Modicare",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE57373),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        Text(
            text = "AI-Powered Modic Analysis",
            fontSize = 16.sp,
            color = Color(0xFF757575),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Image selection buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (hasPermission) {
                        activeImagePicker = 1
                        imagePickerLauncher.launch("image/*")
                    } else {
                        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Manifest.permission.READ_MEDIA_IMAGES
                        } else {
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        }
                        permissionLauncher.launch(permission)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE57373)
                )
            ) {
                Text(
                    text = "T1 Image",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Button(
                onClick = {
                    if (hasPermission) {
                        activeImagePicker = 2
                        imagePickerLauncher.launch("image/*")
                    } else {
                        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Manifest.permission.READ_MEDIA_IMAGES
                        } else {
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        }
                        permissionLauncher.launch(permission)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE57373)
                )
            ) {
                Text(
                    text = "T2 Image",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Image displays
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Image 1
            Card(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                selectedImage1?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "T1 MRI Image",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } ?: run {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Color(0xFFFFF0F0),
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                2.dp,
                                Color(0xFFFFCDD2),
                                RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "T1 Image",
                                color = Color(0xFFE57373),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Not selected",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
            
            // Image 2
            Card(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                selectedImage2?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "T2 MRI Image",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } ?: run {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Color(0xFFFFF0F0),
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                2.dp,
                                Color(0xFFFFCDD2),
                                RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "T2 Image",
                                color = Color(0xFFE57373),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Not selected",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Status message
        if (selectedImage1 == null && selectedImage2 == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
            ) {
                Text(
                    text = "Please select both T1 and T2 images for analysis",
                    fontSize = 16.sp,
                    color = Color(0xFF757575),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else if (selectedImage1 == null || selectedImage2 == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
            ) {
                Text(
                    text = "Select the ${if (selectedImage1 == null) "T1" else "T2"} image to begin analysis",
                    fontSize = 16.sp,
                    color = Color(0xFFE57373),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // Analysis result
        if (isAnalyzing) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = Color(0xFFE57373)
            )
            Text(
                text = "Analyzing images...",
                fontSize = 16.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 16.dp)
            )
        } else {
            analysisResult?.let { result ->
                Spacer(modifier = Modifier.height(16.dp))
                AnalysisResultCard(result)
            }
        }
    }
}

// Analysis function for two images
suspend fun analyzeImages(
    modelHandler: ModicModelHandler,
    image1: Bitmap,
    image2: Bitmap,
    onResult: (ModicAnalysisResult) -> Unit
) {
    withContext(Dispatchers.IO) {
        val combinedResult = modelHandler.analyzeTwoImages(image1, image2)
        
        withContext(Dispatchers.Main) {
            onResult(combinedResult)
        }
    }
}

@Composable
fun AnalysisResultCard(result: ModicAnalysisResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (result.hasModicChange) 
                Color(0xFFFFEBEE) else Color(0xFFE8F5E8)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main result
            Text(
                text = if (result.error != null) {
                    "Analysis Error"
                } else if (result.hasModicChange) {
                    "Modic Change Detected"
                } else {
                    "No Modic Change"
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (result.error != null) {
                    Color(0xFFE57373)
                } else if (result.hasModicChange) {
                    Color(0xFFE57373)
                } else {
                    Color(0xFF4CAF50)
                },
                textAlign = TextAlign.Center
            )

            if (result.error != null) {
                Text(
                    text = result.error,
                    fontSize = 14.sp,
                    color = Color.Red,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))

                // Confidence
                Text(
                    text = "Confidence: ${String.format("%.1f%%", result.confidence * 100)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Detailed scores
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No Modic",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${String.format("%.1f%%", result.noModicScore * 100)}",
                            fontSize = 16.sp,
                            color = Color(0xFF4CAF50)
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Modic Change",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${String.format("%.1f%%", result.modicScore * 100)}",
                            fontSize = 16.sp,
                            color = Color(0xFFE57373)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ModicAnalyzerPreview() {
    ModicAnalyzerTheme {
        // Create a mock model handler for preview
        val mockResult = ModicAnalysisResult(
            hasModicChange = true,
            confidence = 0.85f,
            modicScore = 0.85f,
            noModicScore = 0.15f
        )
        AnalysisResultCard(mockResult)
    }
}