package com.example.modicanalyzer

/**
 * Sealed class representing the result of model initialization
 */
sealed class ModelInitResult {
    object Success : ModelInitResult()
    data class Error(val message: String) : ModelInitResult()
}