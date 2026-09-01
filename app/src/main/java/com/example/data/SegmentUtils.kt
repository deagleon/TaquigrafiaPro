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

        // Pass 1: Remove immediate adjacent duplicate segments (normalizado) — extracted for DRY reuse
        val pass1 = pass1AdjacentDedup(valid)

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
        // Pass 3: gap>5s with short text (<20) and 4-gram >3x hallucination filter
        // Para gpt-4o-mini-transcribe sem confidence/no_speech_prob: heurística de silêncio
        // e repetição lexical via 4-gram. Só filtra texto curto para evitar falso-positivo
        // em conteúdo legítimo como "Projeto 1" vs "Projeto 2".
        val globalCounts = fourGramCountsSegments(pass2)
        val pass3 = mutableListOf<Segment>()
        for (seg in pass2) {
            val prev = pass3.lastOrNull()
            val gap = if (prev != null) seg.start - prev.end else 0.0
            val trimmedLen = seg.text.trim().length
            val short = trimmedLen < 20
            val gapHalluc = short && gap > 5.0
            val ngramHalluc = short && hasRepeatedFourGram(seg, globalCounts)
            if (gapHalluc || ngramHalluc) {
                android.util.Log.d("SegmentUtils", "filter halluc gap=$gap short=$short ngram=$ngramHalluc text=${seg.text.take(30)}")
                continue
            }
            pass3.add(seg)
        }
        // Guard: if dedup collapsed >60% of long transcript, it's likely a false-positive (e.g. 5:26 video 60->10)
        // → revert to Pass1 (adjacent dedup only) to preserve legitimate content
        val candidate = if (pass3.isEmpty() && pass2.isNotEmpty()) pass2 else pass3
        if (valid.size > 30 && candidate.size < valid.size * 0.4) {
            android.util.Log.w("OpenRouterSTT", "segment over-pruned raw=${valid.size} cleaned=${candidate.size}, reverting to Pass1")
            return pass1.ifEmpty { null }
        }
        return candidate.ifEmpty { null }
    }

    /**
     * Pass 1 adjacent deduplication — extracted so OpenRouter provider reuses the same logic
     * instead of duplicating Regex normalization per segment.
     * Uses paraphrased detection: exact normalized equality OR Levenshtein <=2 for <40 chars.
     */
    fun pass1AdjacentDedup(valid: List<Segment>): List<Segment> {
        val pass1 = mutableListOf<Segment>()
        for (seg in valid) {
            val text = seg.text.trim()
            val normalized = normalizeForComparison(text)
            if (normalized.isEmpty()) continue

            val last = pass1.lastOrNull()
            if (last != null && isParaphrased(last.text, text)) {
                continue
            }
            pass1.add(seg.copy(text = text))
        }
        return pass1
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
            }
            for (j in 0..b.length) prev[j] = curr[j]
        }
        return prev[b.length]
    }

    private fun isParaphrased(a: String, b: String): Boolean {
        val na = normalizeForComparison(a)
        val nb = normalizeForComparison(b)
        if (na == nb) return true
        if (na.length < 40 && nb.length < 40 && levenshtein(na, nb) <= 2) {
            // Avoid collapsing legit numeric enumerations like "Projeto 1" vs "Projeto 2"
            // (distance 1) or "Frase única 1 ..." vs "Frase única 2 ..." (distance 2):
            // if stripping digits makes them equal, the difference is numeric-only.
            val naNoDigits = na.replace(Regex("\\d+"), " ").replace(Regex("\\s+"), " ").trim()
            val nbNoDigits = nb.replace(Regex("\\d+"), " ").replace(Regex("\\s+"), " ").trim()
            if (naNoDigits == nbNoDigits) return false
            return true
        }
        return false
    }
    private fun fourGramCountsSegments(segments: List<Segment>): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        for (seg in segments) {
            val words = normalizeForComparison(seg.text).split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.size < 4) continue
            for (idx in 0..words.size - 4) {
                val gram = words.subList(idx, idx + 4).joinToString(" ")
                counts[gram] = (counts[gram] ?: 0) + 1
            }
        }
        return counts
    }

    private fun hasRepeatedFourGram(seg: Segment, counts: Map<String, Int>): Boolean {
        val words = normalizeForComparison(seg.text).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size < 4) return false
        for (idx in 0..words.size - 4) {
            val gram = words.subList(idx, idx + 4).joinToString(" ")
            if ((counts[gram] ?: 0) > 3) return true
        }
        return false
    }

    private fun fourGramCountsParas(paras: List<String>): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        for (p in paras) {
            val words = normalizeForComparison(p).split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.size < 4) continue
            for (idx in 0..words.size - 4) {
                val gram = words.subList(idx, idx + 4).joinToString(" ")
                counts[gram] = (counts[gram] ?: 0) + 1
            }
        }
        return counts
    }

    private fun hasRepeatedFourGramPara(para: String, counts: Map<String, Int>): Boolean {
        val words = normalizeForComparison(para).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size < 4) return false
        for (idx in 0..words.size - 4) {
            val gram = words.subList(idx, idx + 4).joinToString(" ")
            if ((counts[gram] ?: 0) > 3) return true
        }
        return false
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

        // Pass A: remove duplicatas consecutivas (paraphrased: exact ou Levenshtein <=2 para <40 chars)
        val deduped = mutableListOf<String>()
        for (p in paras) {
            val norm = normalizeForComparison(p)
            if (norm.isEmpty()) continue
            val last = deduped.lastOrNull()
            if (last != null && isParaphrased(last, p)) continue
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
        // 4-gram >3x filtering for short paras — only for paraphrased hallucination,
        // keeps first occurrence and filters subsequent duplicates with same repeated 4-gram
        val globalCounts = fourGramCountsParas(paras)
        val filtered = mutableListOf<String>()
        val keptGrams = mutableSetOf<String>()
        for (para in out) {
            if (para.trim().length < 40 && hasRepeatedFourGramPara(para, globalCounts)) {
                val words = normalizeForComparison(para).split(Regex("\\s+")).filter { it.isNotEmpty() }
                var isDup = false
                for (w in 0..words.size - 4) {
                    val gram = words.subList(w, w + 4).joinToString(" ")
                    if ((globalCounts[gram] ?: 0) > 3 && keptGrams.contains(gram)) { isDup = true; break }
                }
                if (isDup) {
                    android.util.Log.d("SegmentUtils", "filter 4-gram para=${para.take(30)}")
                    continue
                }
                for (w in 0..words.size - 4) {
                    val gram = words.subList(w, w + 4).joinToString(" ")
                    if ((globalCounts[gram] ?: 0) > 3) keptGrams.add(gram)
                }
            }
            filtered.add(para)
        }
        val result = filtered.joinToString("\n\n")
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

    fun normalizeForComparison(s: String): String {
        return s.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
            .trim()
    }
}
