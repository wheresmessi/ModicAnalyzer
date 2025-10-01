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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin
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
                    primary = Color(0xFF3DDC84),     // Match logo green
                    secondary = Color(0xFF00C853),   // Complementary green
                    background = Color(0xFFFAFBFC),
                    surface = Color.White,
                    tertiary = Color(0xFF10B981)     // Keep existing tertiary green
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
    var t1Image by remember { mutableStateOf<Bitmap?>(null) }
    var t2Image by remember { mutableStateOf<Bitmap?>(null) }
    var analysisResult by remember { mutableStateOf<String?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    var resultAnimationTrigger by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    
    // Animated state for card entrance
    val cardVisibleState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) {
        cardVisibleState.targetState = true
    }
    
    // Image pickers for dual-input medical model (T1 and T2 weighted MRI)
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
    
    // Analysis function using official TF Lite pattern
    fun performAnalysis() {
        val t1 = t1Image
        val t2 = t2Image
        
        if (t1 == null || t2 == null) {
            Toast.makeText(context, "Please select both T1 and T2 weighted images", Toast.LENGTH_SHORT).show()
            return
        }
        
        isAnalyzing = true
        resultAnimationTrigger = false
        
        // Use official TensorFlow Lite async pattern
        classifier.classifyAsync(t1, t2)
            .addOnSuccessListener { result: String ->
                isAnalyzing = false
                analysisResult = result
                resultAnimationTrigger = true
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
                    containerColor = Color(0xFF2E7D32)  // Dark green to match logo theme
                ),
                actions = {
                    // Animated status indicator
                    AnimatedStatusIndicator()
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFAFBFC),
                            Color(0xFFF1F5F9)
                        )
                    )
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                // Animated Header Section
                AnimatedVisibility(
                    visibleState = cardVisibleState,
                    enter = slideInVertically(
                        initialOffsetY = { -it },
                        animationSpec = tween(600, easing = EaseOutCubic)
                    ) + fadeIn(animationSpec = tween(600))
                ) {
                    ModernHeaderCard()
                }
            }
            
            item {
                // Animated Status Card
                AnimatedVisibility(
                    visibleState = cardVisibleState,
                    enter = slideInVertically(
                        initialOffsetY = { it / 2 },
                        animationSpec = tween(700, delayMillis = 200, easing = EaseOutCubic)
                    ) + fadeIn(animationSpec = tween(700, delayMillis = 200))
                ) {
                    ModernStatusCard()
                }
            }
            
            item {
                // Animated Image Selection
                AnimatedVisibility(
                    visibleState = cardVisibleState,
                    enter = slideInVertically(
                        initialOffsetY = { it / 2 },
                        animationSpec = tween(800, delayMillis = 400, easing = EaseOutCubic)
                    ) + fadeIn(animationSpec = tween(800, delayMillis = 400))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // T1 Image Card
                        ModernImageCard(
                            modifier = Modifier.weight(1f),
                            title = "T1 Weighted",
                            subtitle = "Select T1 MRI",
                            image = t1Image,
                            onClick = { t1ImagePicker.launch("image/*") },
                            icon = Icons.Default.Add
                        )
                        
                        // T2 Image Card
                        ModernImageCard(
                            modifier = Modifier.weight(1f),
                            title = "T2 Weighted",
                            subtitle = "Select T2 MRI",
                            image = t2Image,
                            onClick = { t2ImagePicker.launch("image/*") },
                            icon = Icons.Default.Add
                        )
                    }
                }
            }
            
            item {
                // Animated Analysis Button
                AnimatedVisibility(
                    visibleState = cardVisibleState,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(900, delayMillis = 600, easing = EaseOutCubic)
                    ) + fadeIn(animationSpec = tween(900, delayMillis = 600))
                ) {
                    ModernAnalysisButton(
                        isAnalyzing = isAnalyzing,
                        isEnabled = !isAnalyzing && t1Image != null && t2Image != null,
                        onClick = { performAnalysis() }
                    )
                }
            }
        }
    }
    
    // Result Dialog with Animation
    if (showResultDialog && analysisResult != null) {
        ModernResultDialog(
            result = analysisResult!!,
            animationTrigger = resultAnimationTrigger,
            onDismiss = { 
                showResultDialog = false
                analysisResult = null
                resultAnimationTrigger = false
            }
        )
    }
}

@Composable
fun AnimatedStatusIndicator() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Box(
        modifier = Modifier
            .padding(end = 16.dp)
            .size(12.dp)
            .clip(CircleShape)
            .background(Color(0xFF10B981).copy(alpha = alpha))
    )
}

@Composable
fun ModernHeaderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated icon with gradient background
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF3DDC84).copy(alpha = 0.2f),  // Logo green
                                Color(0xFF00C853).copy(alpha = 0.1f)   // Complementary green
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),  // Dark green to match theme
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "AI-Powered Spine Analysis",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Advanced deep learning for precise Modic change detection in spinal MRI",
                fontSize = 14.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun ModernStatusCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF0FDF4)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    "Medical AI Ready",
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF065F46),
                    fontSize = 16.sp
                )
                Text(
                    "Neural network initialized successfully",
                    color = Color(0xFF047857),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun ModernImageCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    image: Bitmap?,
    onClick: () -> Unit,
    icon: ImageVector
) {
    val scale by animateFloatAsState(
        targetValue = if (image != null) 1f else 0.98f,
        animationSpec = spring(dampingRatio = 0.8f)
    )
    
    Card(
        modifier = modifier
            .aspectRatio(0.85f)
            .scale(scale)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (image != null) 8.dp else 4.dp
        ),
        shape = RoundedCornerShape(20.dp),
        border = if (image != null) BorderStroke(2.dp, Color(0xFF10B981).copy(alpha = 0.5f)) else null
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
                
                // Success overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFF10B981).copy(alpha = 0.8f)
                                )
                            )
                        )
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3DDC84).copy(alpha = 0.1f)),  // Logo green
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),  // Dark green to match theme
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1E293B),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun ModernAnalysisButton(
    isAnalyzing: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    val buttonScale by animateFloatAsState(
        targetValue = if (isAnalyzing) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f)
    )
    
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .scale(buttonScale),
        enabled = isEnabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF3DDC84),      // Logo green
            disabledContainerColor = Color(0xFF3DDC84).copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (isEnabled) 8.dp else 2.dp
        )
    ) {
        if (isAnalyzing) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(rotation),
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Analyzing MRI Images...",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        } else {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Start AI Analysis",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}

@Composable
fun ModernResultDialog(
    result: String,
    animationTrigger: Boolean,
    onDismiss: () -> Unit
) {
    // Determine if Modic change is detected - improved logic
    val resultLower = result.lowercase()
    val isModicDetected = when {
        // Check for negative indicators first (no modic changes)
        resultLower.contains("no modic") ||
        resultLower.contains("negative") ||
        resultLower.contains("absent") ||
        resultLower.contains("not detected") ||
        resultLower.contains("not present") ||
        resultLower.contains("normal") -> false
        
        // Then check for positive indicators (modic changes detected)
        resultLower.contains("modic") && (
            resultLower.contains("detected") ||
            resultLower.contains("present") ||
            resultLower.contains("positive") ||
            resultLower.contains("found") ||
            resultLower.contains("identified")
        ) -> true
        
        // Default to false if unclear
        else -> false
    }
    
    val resultColor = if (isModicDetected) Color(0xFFDC2626) else Color(0xFF059669)
    val backgroundColor = if (isModicDetected) Color(0xFFFEF2F2) else Color(0xFFF0FDF4)
    val borderColor = if (isModicDetected) Color(0xFFDC2626) else Color(0xFF059669)
    val icon = if (isModicDetected) Icons.Default.Warning else Icons.Default.CheckCircle
    val title = if (isModicDetected) "Modic Change Detected" else "Normal Spine Analysis"
    
    // Animation states
    val scale by animateFloatAsState(
        targetValue = if (animationTrigger) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessLow
        )
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (animationTrigger) 1f else 0f,
        animationSpec = tween(400)
    )
    
    // Pulsing animation for Modic detected
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.scale(scale)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            resultColor.copy(
                                alpha = if (isModicDetected) pulseAlpha * 0.2f else 0.2f
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = resultColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        title,
                        fontWeight = FontWeight.Bold,
                        color = resultColor,
                        fontSize = 18.sp
                    )
                    Text(
                        "AI Analysis Complete",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        },
        text = {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = backgroundColor
                ),
                border = BorderStroke(1.dp, borderColor.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.alpha(alpha)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Analysis Results:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF374151)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = result,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = Color(0xFF1F2937)
                    )
                    
                    if (isModicDetected) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Recommend clinical correlation",
                                fontSize = 12.sp,
                                color = Color(0xFFDC2626),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = resultColor
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.scale(scale)
            ) {
                Text(
                    "Continue",
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp)
    )
}