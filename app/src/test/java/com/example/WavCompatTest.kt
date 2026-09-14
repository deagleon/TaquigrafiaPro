package com.example

import com.example.data.WavTranscoder
import com.example.data.provider.ModelCatalog
import com.example.data.provider.OpenRouterErrors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavCompatTest {

    @Test fun `meta exige wav e whisper nao`() {
        assertTrue(ModelCatalog.requiresWav("meta/muse-voice-transcribe-1.0"))
        assertFalse(ModelCatalog.requiresWav("openai/whisper-large-v3-turbo"))
        assertFalse(ModelCatalog.requiresWav("openai/gpt-4o-transcribe"))
        assertFalse(ModelCatalog.requiresWav(""))
    }

    @Test fun `conversao so para modelo exigente com nao-wav`() {
        assertTrue(ModelCatalog.needsWavConversion("meta/muse-voice-transcribe-1.0", "13;32 Votação;.m4a"))
        assertTrue(ModelCatalog.needsWavConversion("meta/muse-voice-transcribe-1.0", "sessao.mp3"))
        assertFalse(ModelCatalog.needsWavConversion("meta/muse-voice-transcribe-1.0", "sessao.wav"))
        assertFalse(ModelCatalog.needsWavConversion("meta/muse-voice-transcribe-1.0", "SESSAO.WAV"))
        assertFalse(ModelCatalog.needsWavConversion("openai/whisper-large-v3-turbo", "sessao.m4a"))
    }

    @Test fun `sanitiza ponto-e-virgula preservando extensao`() {
        assertEquals("13_32 Votação.m4a", WavTranscoder.sanitizeFileName("13;32 Votação;.m4a"))
        assertEquals("sessao_plenaria.mp3", WavTranscoder.sanitizeFileName("sessao_plenaria.mp3"))
        assertEquals("a_b_c.mp3", WavTranscoder.sanitizeFileName("a:b?c.mp3"))
    }

    @Test fun `recusa de conteiner nao se confunde com verbose_json`() {
        assertTrue(OpenRouterErrors.isContainerRefusal("Meta transcription requires WAV audio (input is not a RIFF/WAVE container)"))
        assertFalse(OpenRouterErrors.isContainerRefusal("does not support response_format verbose_json"))
        assertFalse(OpenRouterErrors.isContainerRefusal(""))
        assertFalse(OpenRouterErrors.isContainerRefusal(null))
    }

    @Test fun `mensagem de conteiner nao fala em timestamps`() {
        val msg = OpenRouterErrors.containerRefusedMessage("Meta Muse Voice Transcribe 1.0")
        assertTrue(msg.contains("WAV"))
        assertTrue(msg.contains("Whisper"))
        assertFalse(msg.contains("timestamp", ignoreCase = true))
    }

    @Test fun `downmix tira media dos canais`() {
        assertEquals(listOf(0, 2000), WavTranscoder.downmixToMono(shortArrayOf(0, 0, 1000, 3000), 2).toList())
        assertEquals(listOf(5, 6), WavTranscoder.downmixToMono(shortArrayOf(5, 6), 1).toList())
    }

    @Test fun `reamostragem preserva duracao com interpolacao linear`() {
        assertEquals(
            listOf<Short>(0, 2000),
            WavTranscoder.resampleMono(shortArrayOf(0, 1000, 2000, 3000), 4, 2).toList()
        )
        assertEquals(
            listOf<Short>(0, 500, 1000, 1000),
            WavTranscoder.resampleMono(shortArrayOf(0, 1000), 1, 2).toList()
        )
        assertEquals(
            listOf<Short>(7, 8, 9),
            WavTranscoder.resampleMono(shortArrayOf(7, 8, 9), 3, 3).toList()
        )
        assertTrue(WavTranscoder.resampleMono(shortArrayOf(), 4, 2).isEmpty())
    }

    @Test fun `wav gerado tem cabecalho valido em 16k mono`() {
        val bytes = WavTranscoder.wavBytes(shortArrayOf(0, 1000, -1000))
        assertEquals("RIFF", String(bytes.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals("WAVE", String(bytes.copyOfRange(8, 12), Charsets.US_ASCII))
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(16000, buf.getInt(24))
        assertEquals(1, buf.getShort(22).toInt())
        assertEquals(6, buf.getInt(40))
        assertEquals(44 + 6, bytes.size)
    }
}
