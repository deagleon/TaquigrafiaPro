package com.example.data

import com.example.data.api.Segment
import java.util.Locale

data class TimedParagraph(
    val text: String,
    val startMs: Int,
    val endMs: Int,
)

object SegmentUtils {

    /**
     * Cleans and deduplicates segments returned by ASR models (like Whisper).
     * Eliminates empty segments, invalid timestamps, immediate adjacent duplicates,
     * and multi-segment repetition loops (hallucination patterns).
     */
    fun cleanAndDeduplicate(segments: List<Segment>?): List<Segment>? {
        if (segments.isNullOrEmpty()) return null

        val valid = segments
            .filter { it.text.isNotBlank() && it.end >= it.start }
            .sortedBy { it.start }

        if (valid.isEmpty()) return null

        // Pass 1: Remove immediate adjacent duplicate segments (normalizado)
        val pass1 = mutableListOf<Segment>()
        for (seg in valid) {
            val text = seg.text.trim()
            val normalized = normalizeForComparison(text)
            if (normalized.isEmpty()) continue

            val last = pass1.lastOrNull()
            if (last != null && normalizeForComparison(last.text) == normalized) {
                continue
            }
            pass1.add(seg.copy(text = text))
        }

        if (pass1.size < 4) return pass1.ifEmpty { null }

        // Pass 2: Detect and collapse multi-segment cycle repetition loops (k 2..12, cobre listas longas de votação)
        val pass2 = mutableListOf<Segment>()
        var i = 0
        while (i < pass1.size) {
            var matchedCycleLen = 0
            var repetitionsToSkip = 0

            // hallucinação típica é 2..12 segmentos repetidos (ex: 5 vereadores em loop)
            for (k in 2..12) {
                if (i + k * 2 <= pass1.size) {
                    val pattern = pass1.subList(i, i + k).map { normalizeForComparison(it.text) }
                    var nextStart = i + k
                    var repCount = 0
                    while (nextStart + k <= pass1.size) {
                        val candidate = pass1.subList(nextStart, nextStart + k).map { normalizeForComparison(it.text) }
                        if (candidate == pattern) {
                            repCount++
                            nextStart += k
                        } else {
                            break
                        }
                    }
                    if (repCount > 0) {
                        matchedCycleLen = k
                        repetitionsToSkip = repCount * k
                        break
                    }
                }
            }

            if (matchedCycleLen > 0) {
                for (j in 0 until matchedCycleLen) {
                    pass2.add(pass1[i + j])
                }
                i += matchedCycleLen + repetitionsToSkip
            } else {
                pass2.add(pass1[i])
                i++
            }
        }

        // Pass 3: Tail hallucination — se cauda repete frase única muitas vezes fora do ciclo acima,
        // já foi colapsada por Pass1; mas se houver cauda inventada não-repetitiva, não há o que filtrar aqui.
        // Mantemos pass2 como resultado.
        // Guard: if dedup collapsed >60% of long transcript, it's likely a false-positive (e.g. 5:26 video 60->10)
        // → revert to Pass1 (adjacent dedup only) to preserve legitimate content
        if (valid.size > 30 && pass2.size < valid.size * 0.4) {
            android.util.Log.w("OpenRouterSTT", "segment over-pruned raw=${valid.size} cleaned=${pass2.size}, reverting to Pass1")
            return pass1.ifEmpty { null }
        }
        return pass2.ifEmpty { null }
    }

    /**
     * Limpa texto puro (Gemini ou fallback sem segments) removendo alucinação textual:
     * - parágrafos consecutivos duplicados
     * - ciclos de N parágrafos repetidos (ex: lista de votos em loop)
     * - cauda repetitiva longa
     * Usado como última barreira antes de persistir.
     */
    fun cleanTranscriptText(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        // Split preservando estrutura: duplo \n separa parágrafos, senão quebra por linha
        val paras: List<String> = when {
            trimmed.contains("\n\n") -> trimmed.split(Regex("\n{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
            trimmed.contains("\n") -> trimmed.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            else -> splitParagraphs(trimmed)
        }.ifEmpty { return trimmed }

        // Pass A: remove duplicatas consecutivas (exato normalizado)
        val deduped = mutableListOf<String>()
        for (p in paras) {
            val norm = normalizeForComparison(p)
            if (norm.isEmpty()) continue
            val lastNorm = deduped.lastOrNull()?.let { normalizeForComparison(it) }
            if (lastNorm == norm) continue
            deduped.add(p)
        }
        if (deduped.size < 4) {
            val early = deduped.joinToString("\n\n")
            android.util.Log.d("DiagTrunc", "clean in=${trimmed.length} out=${early.length} paras in=${paras.size} out=${deduped.size} early=true")
            return early
        }
        val out = mutableListOf<String>()
        var idx = 0
        while (idx < deduped.size) {
            var matchedLen = 0
            var skip = 0
            for (k in 2..12) {
                if (idx + k * 2 <= deduped.size) {
                    val pattern = deduped.subList(idx, idx + k).map { normalizeForComparison(it) }
                    var next = idx + k
                    var reps = 0
                    while (next + k <= deduped.size) {
                        val cand = deduped.subList(next, next + k).map { normalizeForComparison(it) }
                        if (cand == pattern) { reps++; next += k } else break
                    }
                    if (reps > 0) { matchedLen = k; skip = reps * k; break }
                }
            }
            if (matchedLen > 0) {
                for (j in 0 until matchedLen) out.add(deduped[idx + j])
                idx += matchedLen + skip
            } else {
                out.add(deduped[idx]); idx++
            }
        }
        val result = out.joinToString("\n\n")
        android.util.Log.d("DiagTrunc", "clean in=${trimmed.length} out=${result.length} paras in=${paras.size} out=${result.split(Regex("\n\n")).size}")
        return result
    }

    /**
     * Calculates the active index for the current audio playback position [posMs].
     * Deterministic, zero-jitter, and smoothly handles silence/gaps between segments.
     */
    fun findActiveIndex(posMs: Int, items: List<TimedParagraph>): Int {
        if (items.isEmpty()) return 0
        val clampedPos = posMs.coerceAtLeast(0)

        if (clampedPos <= items.first().startMs) return 0
        if (clampedPos >= items.last().startMs) return items.lastIndex

        // 1. Direct hit inside [startMs, endMs]
        for (i in items.indices) {
            val item = items[i]
            if (clampedPos in item.startMs..item.endMs) {
                return i
            }
        }

        // 2. Position falls into a pause/gap between items[i] and items[i+1]
        for (i in 0 until items.lastIndex) {
            val curr = items[i]
            val next = items[i + 1]
            if (clampedPos in curr.endMs until next.startMs) {
                // If within 250ms of next segment starting, highlight next; otherwise keep current
                return if (next.startMs - clampedPos <= 250) i + 1 else i
            }
        }

        // 3. Fallback: find closest preceding item
        val idx = items.indexOfLast { clampedPos >= it.startMs }
        return if (idx != -1) idx.coerceIn(0, items.lastIndex) else 0
    }

    /**
     * Splits raw transcription text into distinct, readable paragraphs.
     */
    fun splitParagraphs(raw: String): List<String> {
        val t = raw.trim()
        if (t.isEmpty()) return listOf("")
        val paras = when {
            t.contains("\n\n") -> t.split(Regex("\n{2,}"))
            t.contains("\n") -> t.split("\n")
            else -> Regex("(?<=[.!?]\\s)").split(t).map { it.trim() }.flatMap { s ->
                if (s.length > 360) {
                    val chunks = mutableListOf<String>()
                    var rest = s
                    while (rest.length > 360) {
                        val cut = rest.lastIndexOf(' ', 360).let { if (it < 180) 360 else it }
                        chunks.add(rest.substring(0, cut).trim())
                        rest = rest.substring(cut).trim()
                    }
                    if (rest.isNotEmpty()) chunks.add(rest)
                    chunks
                } else listOf(s)
            }
        }
        return paras.map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { listOf(t) }
    }

    /**
     * Builds timed paragraphs from display paragraphs and optional ASR segments or total duration.
     */
    fun buildTimedParagraphs(
        displayParas: List<String>,
        segments: List<Segment>?,
        audioDurationMs: Int?
    ): List<TimedParagraph> {
        val paras = displayParas.filter { it.isNotBlank() }.ifEmpty { listOf("") }
        val cleanSegs = cleanAndDeduplicate(segments)

        // If clean segments are available and match paragraph count, use segment timestamps
        if (!cleanSegs.isNullOrEmpty() && cleanSegs.size == paras.size) {
            return paras.mapIndexed { idx, text ->
                val seg = cleanSegs[idx]
                TimedParagraph(
                    text = text,
                    startMs = (seg.start * 1000).toInt().coerceAtLeast(0),
                    endMs = (seg.end * 1000).toInt().coerceAtLeast(0)
                )
            }
        }

        // If clean segments exist but paragraph count differs (e.g. grouped text)
        if (!cleanSegs.isNullOrEmpty()) {
            val totalSegDuration = (cleanSegs.last().end * 1000).toInt().coerceAtLeast(1000)
            val effectiveDuration = audioDurationMs?.coerceAtLeast(1000) ?: totalSegDuration
            return buildEstimatedTimedParagraphs(paras, effectiveDuration)
        }

        // Proportional estimation based on audio duration and word count
        val fallbackDuration = audioDurationMs?.coerceAtLeast(1000) ?: run {
            val totalWords = paras.sumOf { it.split(Regex("\\s+")).filter { w -> w.isNotBlank() }.size }.coerceAtLeast(1)
            (totalWords * 480).coerceAtLeast(1000)
        }
        return buildEstimatedTimedParagraphs(paras, fallbackDuration)
    }

    /**
     * Estimates timed paragraphs proportionally by word count across [totalDurationMs].
     */
    fun buildEstimatedTimedParagraphs(paras: List<String>, totalDurationMs: Int): List<TimedParagraph> {
        if (paras.isEmpty()) return emptyList()
        val totalWords = paras.sumOf { it.split(Regex("\\s+")).filter { w -> w.isNotBlank() }.size }.coerceAtLeast(1)
        var acc = 0
        return paras.mapIndexed { index, p ->
            val words = p.split(Regex("\\s+")).filter { it.isNotBlank() }.size.coerceAtLeast(1)
            val isLast = index == paras.lastIndex
            val dur = if (isLast) {
                (totalDurationMs - acc).coerceAtLeast(400)
            } else {
                ((words.toFloat() / totalWords) * totalDurationMs).toInt().coerceAtLeast(400)
            }
            val start = acc
            val end = (start + dur).coerceAtMost(totalDurationMs.coerceAtLeast(start + 400))
            acc = end
            TimedParagraph(p, start, end)
        }
    }

    private fun normalizeForComparison(s: String): String {
        return s.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
            .trim()
    }
}
