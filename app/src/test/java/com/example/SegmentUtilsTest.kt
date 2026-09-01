package com.example

import com.example.data.SegmentUtils
import com.example.data.TimedParagraph
import com.example.data.api.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentUtilsTest {

    @Test
    fun `test cleanAndDeduplicate removes immediate duplicate segments`() {
        val rawSegments = listOf(
            Segment(id = 1, seek = 0, start = 0.0, end = 2.5, text = "Vereadora Graciela, para encaminhar."),
            Segment(id = 2, seek = 0, start = 2.5, end = 4.8, text = "Obrigado, vereadora Mariela."),
            Segment(id = 3, seek = 0, start = 4.8, end = 7.0, text = "Obrigado, vereadora Mariela."), // Immediate duplicate
            Segment(id = 4, seek = 0, start = 7.0, end = 10.0, text = "Vereador Cláudio Lima, para justificar.")
        )

        val cleaned = SegmentUtils.cleanAndDeduplicate(rawSegments)
        assertNotNull(cleaned)
        assertEquals(3, cleaned!!.size)
        assertEquals("Vereadora Graciela, para encaminhar.", cleaned[0].text)
        assertEquals("Obrigado, vereadora Mariela.", cleaned[1].text)
        assertEquals("Vereador Cláudio Lima, para justificar.", cleaned[2].text)
    }

    @Test
    fun `test cleanAndDeduplicate removes multi-segment repetition loop`() {
        // Simulating the exact Whisper loop artifact seen in the user's video
        val loopPattern = listOf(
            Segment(id = 1, seek = 0, start = 10.0, end = 13.0, text = "Deixa nós justificativos de voto, momento oportuno, só para aclamar votos."),
            Segment(id = 2, seek = 0, start = 13.0, end = 16.0, text = "Ok, só para os senhores terem conhecimento de um projeto aprovado nesta casa."),
            Segment(id = 3, seek = 0, start = 16.0, end = 17.5, text = "Tranquilo."),
            Segment(id = 4, seek = 0, start = 17.5, end = 19.5, text = "Vereadora Julierme, vota sim."),
            Segment(id = 5, seek = 0, start = 19.5, end = 21.0, text = "Vereadora Sena, vota sim.")
        )

        val rawWithLoop = listOf(
            Segment(id = 0, seek = 0, start = 0.0, end = 5.0, text = "Abertura da sessão.")
        ) + loopPattern + loopPattern.mapIndexed { idx, s ->
            s.copy(id = idx + 10, start = s.start + 15.0, end = s.end + 15.0)
        }

        val cleaned = SegmentUtils.cleanAndDeduplicate(rawWithLoop)
        assertNotNull(cleaned)
        // The repeated cycle should be pruned
        assertEquals(6, cleaned!!.size)
        assertEquals("Abertura da sessão.", cleaned[0].text)
        assertEquals(loopPattern[0].text, cleaned[1].text)
        assertEquals(loopPattern[4].text, cleaned[5].text)
    }

    @Test
    fun `test findActiveIndex with direct hit, pause gap and boundaries`() {
        val timed = listOf(
            TimedParagraph("Parágrafo 1", 0, 3000),      // 0 - 3s
            TimedParagraph("Parágrafo 2", 4000, 7000),   // 4s - 7s (1s pause before)
            TimedParagraph("Parágrafo 3", 8000, 12000)   // 8s - 12s (1s pause before)
        )

        // Inside segment 1
        assertEquals(0, SegmentUtils.findActiveIndex(1500, timed))

        // During pause at 3.2s (closer to paragraph 1) -> stays at 0
        assertEquals(0, SegmentUtils.findActiveIndex(3200, timed))

        // During pause at 3.9s (within 250ms of paragraph 2 at 4.0s) -> transitions to 1
        assertEquals(1, SegmentUtils.findActiveIndex(3900, timed))

        // Inside segment 2
        assertEquals(1, SegmentUtils.findActiveIndex(5500, timed))

        // Inside segment 3
        assertEquals(2, SegmentUtils.findActiveIndex(9000, timed))

        // Past end of all segments -> stays at last
        assertEquals(2, SegmentUtils.findActiveIndex(15000, timed))

        // Negative or zero position -> returns 0
        assertEquals(0, SegmentUtils.findActiveIndex(-100, timed))
        assertEquals(0, SegmentUtils.findActiveIndex(0, timed))
    }

    @Test
    fun `test buildTimedParagraphs with proportional estimation`() {
        val paragraphs = listOf(
            "Primeira fala curta.",
            "Segunda fala um pouco mais longa com varias palavras para testar proporcao.",
            "Terceira fala."
        )
        val totalDurationMs = 30000 // 30s

        val timed = SegmentUtils.buildTimedParagraphs(paragraphs, null, totalDurationMs)
        assertEquals(3, timed.size)
        assertEquals(0, timed[0].startMs)
        assertTrue(timed[0].endMs < timed[1].endMs)
        assertTrue(timed[1].endMs < timed[2].endMs)
        assertEquals(totalDurationMs, timed[2].endMs)
    }

    @Test
    fun `test splitParagraphs handles line breaks and long sentences`() {
        val text = "Primeiro bloco de texto.\n\nSegundo bloco de texto.\nTerceiro bloco."
        val split = SegmentUtils.splitParagraphs(text)
        assertEquals(2, split.size)
        assertEquals("Primeiro bloco de texto.", split[0])
        assertEquals("Segundo bloco de texto.\nTerceiro bloco.", split[1])
    }
}
