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
    fun `log tag DiagTrunc exists in ViewModel with detailed formats`() {
        val src = findFile(
            "app/src/main/java/com/example/ui/TranscriptionViewModel.kt",
            "src/main/java/com/example/ui/TranscriptionViewModel.kt"
        ).readText()
        // Tag exists
        assertTrue("missing DiagTrunc tag in ViewModel", src.contains("DiagTrunc"))
        // Required structured fields per brief
        assertTrue("missing file=", src.contains("file="))
        assertTrue("missing mime=", src.contains("mime="))
        assertTrue("missing size=", src.contains("size="))
        assertTrue("missing durMs=", src.contains("durMs="))
        assertTrue("missing needsChunk=", src.contains("needsChunk="))
        assertTrue("missing base64Len=", src.contains("base64Len="))
        assertTrue("missing provider=", src.contains("provider="))
        assertTrue("missing model=", src.contains("model="))
        // Chunk and merge logs
        assertTrue("missing chunks=", src.contains("chunks="))
        assertTrue("missing mergedTextLen=", src.contains("mergedTextLen="))
        // No misleading zero-length log before read
        assertTrue("misleading base64Len=0 still present", !src.contains("base64Len=0"))
        // Ensure per-branch accurate base64Len with actual length
        assertTrue("missing base64Len=\\$\\{", src.contains("base64Len=\${"))
    }

    @Test
    fun `log tag DiagTrunc exists in OpenRouter provider with clean format`() {
        val src = findFile(
            "app/src/main/java/com/example/data/provider/OpenRouterTranscriptionProvider.kt",
            "src/main/java/com/example/data/provider/OpenRouterTranscriptionProvider.kt"
        ).readText()
        assertTrue(src.contains("DiagTrunc"))
        assertTrue("missing clean in=", src.contains("clean in="))
        assertTrue("missing out=", src.contains("out="))
        assertTrue("missing segments", src.contains("segments"))
        assertTrue("missing raw=", src.contains("raw="))
        assertTrue("missing cleaned=", src.contains("cleaned="))
    }

    @Test
    fun `log tag DiagTrunc exists in SegmentUtils with clean format`() {
        val src = findFile(
            "app/src/main/java/com/example/data/SegmentUtils.kt",
            "src/main/java/com/example/data/SegmentUtils.kt"
        ).readText()
        assertTrue(src.contains("DiagTrunc"))
        assertTrue("missing clean in=", src.contains("clean in="))
        assertTrue("missing out=", src.contains("out="))
        assertTrue("missing paras in=", src.contains("paras in="))
    }
}
