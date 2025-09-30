package com.example.modicanalyzer

data class AnalysisResult(
    val hasModicChange: Boolean,
    val confidence: Float,
    val changeType: String? = null,
    val details: String? = null,
    val error: String? = null,
    val noModicScore: Float = 0f,
    val modicScore: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromPair(result: Pair<String, Float>): AnalysisResult {
            val hasModic = result.first.contains("Modic Change Detected", ignoreCase = true)
            val confidence = result.second.coerceIn(0f, 1f) // Ensure confidence is between 0 and 1
            
            return AnalysisResult(
                hasModicChange = hasModic,
                confidence = confidence,
                noModicScore = if (hasModic) 1f - confidence else confidence,
                modicScore = if (hasModic) confidence else 1f - confidence
            )
        }
        
        fun error(message: String): AnalysisResult {
            return AnalysisResult(
                hasModicChange = false,
                confidence = 0f,
                error = message
            )
        }
    }
}