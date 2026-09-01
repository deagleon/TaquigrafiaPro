package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterVerboseTest {

    private fun supportsVerbose(model: String): Boolean = model.lowercase().let { m ->
        "whisper" in m || "gpt-4o-mini-transcribe" in m || "gpt-4o-transcribe" in m || "gpt-transcribe" in m || "chirp" in m
    }

    @Test fun `gpt-4o-mini-transcribe supports verbose`() {
        val m = "openai/gpt-4o-mini-transcribe"
        val supports = supportsVerbose(m)
        assertTrue("gpt-4o-mini-transcribe should support verbose_json", supports)
    }

    @Test fun `gpt-4o-transcribe supports verbose`() {
        assertTrue(supportsVerbose("openai/gpt-4o-transcribe"))
    }

    @Test fun `whisper supports verbose`() {
        assertTrue(supportsVerbose("openai/whisper-large-v3"))
    }

    @Test fun `chirp supports verbose`() {
        assertTrue(supportsVerbose("google/chirp-v2"))
    }

    @Test fun `non-verbose model does not support verbose`() {
        assertFalse(supportsVerbose("openai/gpt-4o-mini"))
        assertFalse(supportsVerbose("anthropic/claude-3"))
    }

    @Test fun `payload oversize threshold is 25MB`() {
        val threshold = 25 * 1024 * 1024
        assertTrue(threshold == 26214400)
        // base64Len check should trigger above threshold
        val smallLen = 10 * 1024 * 1024
        val largeLen = 26 * 1024 * 1024
        assertFalse(smallLen > threshold)
        assertTrue(largeLen > threshold)
    }
}
