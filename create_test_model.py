#!/usr/bin/env python3
"""
Script to create a simple test TensorFlow Lite model for testing the app
This creates a basic model that takes image input and outputs binary classification
"""

import tensorflow as tf
import numpy as np

def create_test_model():
    """Create a simple test model for Modic analysis"""
    
    # Create a simple model
    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=(224, 224, 3)),
        tf.keras.layers.Conv2D(32, 3, activation='relu'),
        tf.keras.layers.MaxPooling2D(),
        tf.keras.layers.Conv2D(64, 3, activation='relu'),
        tf.keras.layers.GlobalAveragePooling2D(),
        tf.keras.layers.Dense(64, activation='relu'),
        tf.keras.layers.Dense(2, activation='softmax')  # Binary classification: No Modic, Modic
    ])
    
    # Compile the model
    model.compile(
        optimizer='adam',
        loss='sparse_categorical_crossentropy',
        metrics=['accuracy']
    )
    
    # Create some dummy data for testing
    dummy_input = np.random.random((1, 224, 224, 3))
    dummy_output = np.array([0])  # No Modic
    
    # Train for one step just to initialize weights
    model.fit(dummy_input, dummy_output, epochs=1, verbose=0)
    
    return model

def convert_to_tflite(model, output_path):
    """Convert Keras model to TensorFlow Lite"""
    
    # Convert to TensorFlow Lite
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    
    # Set input/output specs
    converter.representative_dataset = lambda: [np.random.random((1, 224, 224, 3)).astype(np.float32)]
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
    
    tflite_model = converter.convert()
    
    # Save the model
    with open(output_path, 'wb') as f:
        f.write(tflite_model)
    
    print(f"Test model saved to: {output_path}")
    print(f"Model size: {len(tflite_model)} bytes")
    
    return tflite_model

def main():
    """Main function"""
    print("Creating test TensorFlow Lite model...")
    
    # Create model
    model = create_test_model()
    print(f"Model created with input shape: {model.input_shape}")
    print(f"Model output shape: {model.output_shape}")
    
    # Convert to TFLite
    output_path = "test_modic_model.tflite"
    tflite_model = convert_to_tflite(model, output_path)
    
    print("Test model created successfully!")
    print("You can now replace the corrupted model with this test model.")

if __name__ == "__main__":
    main()