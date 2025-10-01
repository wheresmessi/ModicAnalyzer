package com.example.modicanalyzer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class WelcomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Debug: Check what assets are packaged in the APK
        try {
            val files = assets.list("") ?: emptyArray()
            Log.d("AssetCheck", "Assets packaged: ${files.joinToString()}")
        } catch (e: Exception) {
            Log.e("AssetCheck", "Error listing assets: ${e.message}")
        }
        
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF3DDC84),     // Match logo green
                    secondary = Color(0xFF00C853),   // Complementary green
                    tertiary = Color(0xFF1B5E20),    // Dark green accent
                    background = Color(0xFFF8FAFC)
                )
            ) {
                WelcomeScreen {
                    // Navigate to main activity after animation
                    startActivity(Intent(this@WelcomeActivity, SimpleMainActivity::class.java))
                    finish()
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen(onAnimationComplete: () -> Unit) {
    var animationStarted by remember { mutableStateOf(false) }
    
    // Animation states
    val logoScale by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0.3f,
        animationSpec = tween(
            durationMillis = 1500,
            easing = FastOutSlowInEasing
        )
    )
    
    val logoAlpha by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = tween(
            durationMillis = 1200,
            easing = LinearOutSlowInEasing
        )
    )
    
    val textAlpha by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = tween(
            durationMillis = 1000,
            delayMillis = 800,
            easing = LinearOutSlowInEasing
        )
    )
    
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = tween(
            durationMillis = 1000,
            delayMillis = 1200,
            easing = LinearOutSlowInEasing
        )
    )
    
    // Start animation and navigation timer
    LaunchedEffect(Unit) {
        animationStarted = true
        delay(3500) // Total welcome screen duration
        onAnimationComplete()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1B5E20), // Dark green
                        Color(0xFF2E7D32), // Medium green  
                        Color(0xFF3DDC84)  // Logo green
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Animated logo - bigger and without white padding
            Card(
                modifier = Modifier
                    .size(160.dp)  // Increased from 120dp
                    .scale(logoScale)
                    .alpha(logoAlpha),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                        contentDescription = "App Logo",
                        modifier = Modifier
                            .fillMaxSize()  // Fill entire card to remove padding
                            .padding(4.dp), // Minimal padding
                        contentScale = ContentScale.Fit
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // App title with animation
            Text(
                text = "ModicAnalyzer",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(textAlpha)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Subtitle with animation
            Text(
                text = "AI-Powered MRI Analysis\nfor Modic Changes Detection",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.alpha(subtitleAlpha)
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Loading indicator
            if (animationStarted) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(32.dp)
                        .alpha(subtitleAlpha),
                    color = Color.White,
                    strokeWidth = 3.dp
                )
            }
        }
        
        // Footer text
        Text(
            text = "Powered by Modicare Healthcare Solutions",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .alpha(subtitleAlpha)
        )
    }
}