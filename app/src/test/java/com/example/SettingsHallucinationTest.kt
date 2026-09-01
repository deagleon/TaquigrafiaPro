package com.example

import com.example.data.SegmentUtils
import com.example.data.api.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsHallucinationTest {

    @Test
    fun `aggressive true collapses paraphrased repetition`() {
        val raw = listOf(
            Segment(1, 0, 0.0, 2.0, "Vereadora A vota sim."),
            Segment(2, 0, 2.0, 4.0, "Vereadora A votou sim")
        )
        val cleanedAggressive = SegmentUtils.cleanAndDeduplicate(raw, aggressive = true, threshold = 0.5f)
        assertNotNull(cleanedAggressive)
        assertEquals(1, cleanedAggressive!!.size)
    }

    @Test
    fun `conservative false keeps paraphrased repetition`() {
        val raw = listOf(
            Segment(1, 0, 0.0, 2.0, "Vereadora A vota sim."),
            Segment(2, 0, 2.0, 4.0, "Vereadora A votou sim")
        )
        val cleanedConservative = SegmentUtils.cleanAndDeduplicate(raw, aggressive = false, threshold = 0.5f)
        assertNotNull(cleanedConservative)
        // conservative uses exact only, so paraphrased "vota vs votou" should NOT collapse
        assertEquals(2, cleanedConservative!!.size)
    }

    @Test
    fun `conservative still collapses exact duplicate`() {
        val raw = listOf(
            Segment(1, 0, 0.0, 2.0, "Vereadora A vota sim."),
            Segment(2, 0, 2.0, 4.0, "Vereadora A vota sim.")
        )
        val cleaned = SegmentUtils.cleanAndDeduplicate(raw, aggressive = false)
        assertEquals(1, cleaned!!.size)
    }

    @Test
    fun `cleanTranscriptText aggressive vs conservative`() {
        val para1 = "Vereadora A vota sim."
        val para2 = "Vereadora A votou sim"
        val raw = "$para1\n\n$para2"
        // With only 2 paras, early return path still dedupes adjacent
        val aggressive = SegmentUtils.cleanTranscriptText(raw, aggressive = true)
        val conservative = SegmentUtils.cleanTranscriptText(raw, aggressive = false)
        // aggressive should collapse to 1 para, conservative keep 2
        assertEquals(1, aggressive.split(Regex("\n\n")).size)
        assertEquals(2, conservative.split(Regex("\n\n")).size)
    }

    @Test
    fun `aggressive k-range covers longer cycles while conservative limited to 8`() {
        // 9-segment pattern repeated: aggressive (2..12) collapses, conservative (2..8) does not when k=9
        val pattern = (1..9).map { i -> Segment(i, 0, i * 2.0, i * 2.0 + 1.0, "Frase bloco $i distinto conteúdo $i") }
        val raw = pattern + pattern.mapIndexed { idx, s -> s.copy(id = idx + 100, start = s.start + 50, end = s.end + 50) }
        val aggressive = SegmentUtils.cleanAndDeduplicate(raw, aggressive = true)
        val conservative = SegmentUtils.cleanAndDeduplicate(raw, aggressive = false)
        assertNotNull(aggressive)
        assertNotNull(conservative)
        // aggressive should collapse 18 -> 9
        assertEquals(9, aggressive!!.size)
        // conservative k=2..8 cannot match k=9 cycle, so keeps 18 (or at least >9)
        assertEquals(18, conservative!!.size)
    }

    @Test
    fun `hallucination prefs keys and defaults in source`() {
        // Resolve SegmentUtils file across both cwd=root and cwd=app (gradle runs with cwd=app)
        val candidates = listOf(
            File("app/src/main/java/com/example/data/SegmentUtils.kt"),
            File("src/main/java/com/example/data/SegmentUtils.kt")
        )
        val segFile = candidates.firstOrNull { it.exists() } ?: candidates.first()
        assertTrue("SegmentUtils.kt not found at ${candidates.map { it.path }}", segFile.exists())
        val segText = segFile.readText()
        assertTrue(segText.contains("hallucination_aggressive") || segText.contains("aggressive"))
        assertTrue(segText.contains("threshold"))
        // Also verify ViewModel contains hallucination prefs
        val vmCandidates = listOf(
            File("app/src/main/java/com/example/ui/TranscriptionViewModel.kt"),
            File("src/main/java/com/example/ui/TranscriptionViewModel.kt")
        )
        val vmFile = vmCandidates.firstOrNull { it.exists() } ?: vmCandidates.first()
        assertTrue(vmFile.exists())
        val vmText = vmFile.readText()
        assertTrue(vmText.contains("hallucination_aggressive"))
        assertTrue(vmText.contains("hallucination_threshold"))
        assertTrue(vmText.contains("0.5f"))
    }

    @Test
    fun `hallucination UI card and tags exist in MainActivity`() {
        val candidates = listOf(
            File("app/src/main/java/com/example/MainActivity.kt"),
            File("src/main/java/com/example/MainActivity.kt")
        )
        val mainFile = candidates.firstOrNull { it.exists() } ?: candidates.first()
        assertTrue("MainActivity.kt not found", mainFile.exists())
        val text = mainFile.readText()
        assertTrue(text.contains("hallucination_card"))
        assertTrue(text.contains("hallucination_aggressive_switch"))
        assertTrue(text.contains("hallucination_threshold_slider"))
        assertTrue(text.contains("0.4f..0.6f"))
        assertTrue(text.contains("Limpeza Anti-Alucinação"))
        assertTrue(text.contains("hallucinationAggressive"))
        assertTrue(text.contains("hallucinationThreshold"))
    }

    @Test
    fun `threshold range is 0_4 to 0_6 coerced`() {
        // threshold param is coerced in SegmentUtils; verify both extremes don't crash
        val raw = (1..10).map { i -> Segment(i, 0, i * 2.0, i * 2.0 + 1.0, "Texto $i") }
        val low = SegmentUtils.cleanAndDeduplicate(raw, aggressive = true, threshold = 0.4f)
        val high = SegmentUtils.cleanAndDeduplicate(raw, aggressive = true, threshold = 0.6f)
        assertNotNull(low)
        assertNotNull(high)
    }
}
