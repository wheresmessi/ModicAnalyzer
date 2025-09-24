# Modicare

An AI-powered Android application that uses TensorFlow Lite to analyze MRI images for Modic changes in the spine.

## Features

- **T1/T2 Image Analysis**: Select T1 and T2 MRI images for comprehensive analysis
- **AI Analysis**: Uses TensorFlow Lite model to detect Modic changes
- **Confidence Scores**: Shows detailed analysis results with confidence percentages
- **Modern UI**: Built with Jetpack Compose featuring Modicare branding
- **Smart Combination**: Analyzes both images and provides combined results

## How to Use

1. **Launch the app** and grant necessary permissions when prompted
2. **Select MRI Images**: Tap "T1 Image" and "T2 Image" to choose MRI scans from your gallery
3. **Automatic Analysis**: Once both images are selected, the app automatically analyzes them
4. **View Results**: The app shows:
   - Whether Modic changes are detected (from either image)
   - Combined confidence percentage
   - Detailed scores for both "No Modic" and "Modic Change" classifications

## Technical Details

- **Model**: TensorFlow Lite model (`modic_model.tflite`)
- **Input Size**: 224x224 pixels (configurable in `ModicModelHandler.kt`)
- **Analysis Method**: T1/T2 image analysis with smart result combination
- **Framework**: Jetpack Compose + Kotlin
- **Design**: Modicare branding with coral/red accent colors
- **Minimum SDK**: Android API 24 (Android 7.0)

## Project Structure

```
app/
├── src/main/
│   ├── assets/
│   │   └── modic_model.tflite          # Your trained TensorFlow Lite model
│   ├── java/com/example/modicanalyzer/
│   │   ├── MainActivity.kt             # Main UI with Compose
│   │   ├── ModicModelHandler.kt        # TensorFlow Lite model wrapper
│   │   └── ImageUtils.kt               # Image processing utilities
│   └── AndroidManifest.xml             # App permissions and configuration
```

## Model Requirements

The app expects a TensorFlow Lite model with:
- **Input**: 224x224x3 RGB image
- **Output**: 2-class probability array [No Modic, Modic Change]

If your model has different specifications, update the constants in `ModicModelHandler.kt`:
- `inputSize`: Change to match your model's expected input dimensions
- `numClasses`: Adjust if you have more than 2 classes

## Building and Running

1. Open the project in Android Studio
2. Ensure you have placed your `modic_model.tflite` file in `app/src/main/assets/`
3. Build and run the project on a device or emulator
4. Grant storage permissions when prompted
5. Select an MRI image to analyze

## Permissions

The app requires the following permissions:
- `READ_EXTERNAL_STORAGE` (Android < 13)
- `READ_MEDIA_IMAGES` (Android 13+)

These are automatically requested when needed.