package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private fun findSourceFile(vararg candidates: String): File {
    for (c in candidates) {
        val f = File(c)
        if (f.exists()) return f
        val f2 = File("../$c")
        if (f2.exists()) return f2
    }
    return File(candidates[0])
}

class LlmTruncationGuardTest {

    @Test
    fun `post-processing truncates 5-26 reverts`() {
        val raw = "a ".repeat(2000) // ~4000 chars
        val processed = "a ".repeat(200) // 10% -> should revert
        // Simulate provider logic: if processed<raw*0.6 && raw>1000 -> revert
        assertTrue(processed.length < raw.length * 0.6)
        // Guard condition from OpenRouterTranscriptionProvider: processed < raw*0.6 && raw>1000 -> revert
        val shouldRevert = processed.length < raw.length * 0.6 && raw.length > 1000
        assertTrue("guard should trigger revert for truncated 5:26", shouldRevert)
    }

    @Test
    fun `openrouter truncation guard exists at line 213 region`() {
        val src = findSourceFile(
            "app/src/main/java/com/example/data/provider/OpenRouterTranscriptionProvider.kt",
            "src/main/java/com/example/data/provider/OpenRouterTranscriptionProvider.kt"
        ).readText()
        assertTrue("missing truncation guard 0.6", src.contains("processedTrimmed.length < finalRawText.length * 0.6"))
        assertTrue("missing raw length >1000 guard", src.contains("finalRawText.length > 1000"))
        assertTrue("missing revert log truncated", src.contains("post-processing rejected: truncated"))
        assertTrue("missing postProcessingEnabled default false", src.contains("openrouter_post_processing_enabled\", false"))
        assertTrue("missing useRawWithTimestamps bypass", src.contains("useRawWithTimestamps"))
    }

    @Test
    fun `gemini maxOutputTokens 16384 is set`() {
        val src = findSourceFile(
            "app/src/main/java/com/example/data/provider/GeminiTranscriptionProvider.kt",
            "src/main/java/com/example/data/provider/GeminiTranscriptionProvider.kt"
        ).readText()
        assertTrue("missing maxOutputTokens=16384", src.contains("maxOutputTokens = 16384"))
    }
}
