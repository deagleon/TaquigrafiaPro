package com.example

import com.example.data.SegmentUtils
import com.example.data.api.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class HallucinationGuardRegressionTest {

    @Test
    fun `200 repeats collapsed even with 50percent guard conservative`() {
        val repeats = (0 until 200).map { i -> Segment(id=i, seek=0, start=i*1.0, end=i*1.0+0.8, text="Vereador Marcelo vota sim.") }
        assertTrue(SegmentUtils.isRepetitiveHallucinationSegments(repeats))
        val cleaned = SegmentUtils.cleanAndDeduplicate(repeats, aggressive=false, threshold=0.5f)
        assertEquals(1, cleaned!!.size)
    }

    @Test
    fun `legit 60 unique not collapsed even with aggressive`() {
        val legit = (1..60).map { i -> Segment(id=i, seek=0, start=i*2.0, end=i*2.0+1.5, text="Frase única $i com conteúdo distinto $i") }
        assertFalse(SegmentUtils.isRepetitiveHallucinationSegments(legit))
        val cleaned = SegmentUtils.cleanAndDeduplicate(legit, aggressive=true, threshold=0.5f)
        assertEquals(60, cleaned!!.size)
    }

    @Test
    fun `mixed 20 legit plus 200 repeats collapses to 21`() {
        val legit = (1..20).map { i -> Segment(id=i, seek=0, start=i*2.0, end=i*2.0+1.5, text="Frase única $i com conteúdo parlamentar distinto e extenso para teste") }
        val repeats = (0 until 200).map { i -> Segment(id=100+i, seek=0, start=50.0 + i*1.0, end=50.0 + i*1.0+0.8, text="Vereador Marcelo vota sim.") }
        val mixed = legit + repeats
        assertTrue(SegmentUtils.isRepetitiveHallucinationSegments(mixed))
        val cleaned = SegmentUtils.cleanAndDeduplicate(mixed, aggressive=false, threshold=0.5f)
        // 20 legit + 1 collapsed repeat =21
        assertEquals(21, cleaned!!.size)
    }

    @Test
    fun `text 200 repeats not reverted by 50percent guard`() {
        val repeats = List(200) { "Vereador Marcelo vota sim." }.joinToString("\n\n")
        assertTrue(SegmentUtils.isRepetitiveHallucinationText(repeats))
        val cleaned = SegmentUtils.cleanTranscriptText(repeats, aggressive=false)
        assertEquals(1, cleaned.split(Regex("\n\n")).size)
        // Simulate provider guard: should KEEP deduped, not revert
        val isRep = SegmentUtils.isRepetitiveHallucinationText(repeats)
        val shouldKeep = cleaned.length < repeats.length * 0.5 && repeats.length > 1000 && isRep
        assertTrue(shouldKeep) // hallucination detected, keep cleaned
    }

    @Test
    fun `legit 60 unique text not flagged repetitive`() {
        val legit = (1..60).map { "Frase única $it com conteúdo distinto $it" }.joinToString("\n\n")
        assertFalse(SegmentUtils.isRepetitiveHallucinationText(legit))
        val cleaned = SegmentUtils.cleanTranscriptText(legit, aggressive=true)
        assertEquals(60, cleaned.split(Regex("\n\n")).size)
    }

    @Test
    fun `paraphrased repetition detected`() {
        val paras = (0 until 100).map { i -> if (i%2==0) "Vereadora A vota sim." else "Vereadora A votou sim" }
        val raw = paras.joinToString("\n\n")
        assertTrue(SegmentUtils.isRepetitiveHallucinationText(raw))
        val cleaned = SegmentUtils.cleanTranscriptText(raw, aggressive=true)
        // Levenshtein <=2 should collapse paraphrased
        assertEquals(1, cleaned.split(Regex("\n\n")).size)
    }
}
