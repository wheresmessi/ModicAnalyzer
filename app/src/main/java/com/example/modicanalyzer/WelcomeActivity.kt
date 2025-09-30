package com.example.modicanalyzer

import android.content.Intent
import android.os.Bundle
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
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF3B82F6),
                    secondary = Color(0xFF1E3A8A),
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
                        Color(0xFF1E3A8A), // Deep blue
                        Color(0xFF3B82F6), // Blue
                        Color(0xFF60A5FA)  // Light blue
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Animated logo
            Card(
                modifier = Modifier
                    .size(120.dp)
                    .scale(logoScale)
                    .alpha(logoAlpha),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.modicare_logo),
                        contentDescription = "Modicare Logo",
                        modifier = Modifier
                            .size(80.dp)
                            .padding(8.dp),
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