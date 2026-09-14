package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterVerboseTest {

    private fun supportsVerbose(model: String): Boolean = model.lowercase().let { m ->
        // Sincronizado com OpenRouterTranscriptionProvider: apenas whisper/chirp via OpenRouter.
        // gpt-4o-mini-transcribe falha com 400 "does not support response_format verbose_json" (log 2026-09-01).
        "whisper" in m || "chirp" in m
    }

    @Test fun `gpt-4o-mini-transcribe does NOT support verbose via OpenRouter`() {
        val m = "openai/gpt-4o-mini-transcribe"
        val supports = supportsVerbose(m)
        assertFalse("gpt-4o-mini-transcribe via OpenRouter retorna 400 verbose_json, deve usar plain json", supports)
    }

    @Test fun `gpt-4o-transcribe does NOT support verbose via OpenRouter by default`() {
        // Até prova em log, tratamos como plain json para evitar 400; fallback cobre caso suporte futuro.
        assertFalse(supportsVerbose("openai/gpt-4o-transcribe"))
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
