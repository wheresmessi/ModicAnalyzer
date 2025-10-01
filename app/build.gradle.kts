plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.modicanalyzer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.modicanalyzer"
        minSdk = 26  // Updated to Android O (API 26) to support MethodHandle APIs
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true  // Enable desugaring for better API compatibility
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    
    // Prevent Android from compressing TF Lite model files (modern Kotlin DSL syntax)
    androidResources {
        noCompress += "tflite"
    }
}

// LiteRT dependency resolution (no conflicts)
configurations.all {
    resolutionStrategy {
        // Force LiteRT version for consistency
        force("com.google.ai.edge.litert:litert:1.0.1")
    }
}

dependencies {
    // LiteRT 2025 - Google's future-proof solution (eliminates conflicts)
    implementation("com.google.ai.edge.litert:litert:1.0.1")
    
    // Task API for official async pattern
    implementation("com.google.android.gms:play-services-tasks:18.0.2")
    
    // Core library desugaring for better API compatibility
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}