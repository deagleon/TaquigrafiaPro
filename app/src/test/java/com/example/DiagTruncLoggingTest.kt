package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private fun findFile(vararg candidates: String): File {
    for (c in candidates) {
        val f = File(c)
        if (f.exists()) return f
        val f2 = File("../$c")
        if (f2.exists()) return f2
    }
    return File(candidates[0])
}

class DiagTruncLoggingTest {

    @Test
    fun `log tag DiagTrunc exists in ViewModel`() {
        val src = findFile(
            "app/src/main/java/com/example/ui/TranscriptionViewModel.kt",
            "src/main/java/com/example/ui/TranscriptionViewModel.kt"
        ).readText()
        assertTrue(src.contains("DiagTrunc"))
    }

    @Test
    fun `log tag DiagTrunc exists in OpenRouter provider`() {
        val src = findFile(
            "app/src/main/java/com/example/data/provider/OpenRouterTranscriptionProvider.kt",
            "src/main/java/com/example/data/provider/OpenRouterTranscriptionProvider.kt"
        ).readText()
        assertTrue(src.contains("DiagTrunc"))
    }

    @Test
    fun `log tag DiagTrunc exists in SegmentUtils`() {
        val src = findFile(
            "app/src/main/java/com/example/data/SegmentUtils.kt",
            "src/main/java/com/example/data/SegmentUtils.kt"
        ).readText()
        assertTrue(src.contains("DiagTrunc"))
    }
}
