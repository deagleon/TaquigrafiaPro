package com.example.data

import com.example.data.api.Segment
import com.example.data.api.Word
import java.util.Locale
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

data class TimedParagraph(
    val text: String,
    val startMs: Int,
    val endMs: Int,
    val words: List<Word> = emptyList(),
)

object SegmentUtils {

    /**
     * Cleans and deduplicates segments returned by ASR models (like Whisper).
     * Eliminates empty segments, invalid timestamps, immediate adjacent duplicates,
     * and multi-segment repetition loops (hallucination patterns).
     * Default (no params) keeps aggressive behavior for backward compat.
     */
    fun cleanAndDeduplicate(segments: List<Segment>?): List<Segment>? =
        cleanAndDeduplicate(segments, aggressive = true, threshold = 0.4f)

    /**
     * Overload with hallucination control params.
     * @param aggressive true = k 2..12 + Levenshtein <=2, false = k 2..8 exact only (conservative)
     * @param threshold guard threshold 0.4..0.6: revert to Pass1 if cleaned < valid*threshold when valid>30
     */
    fun cleanAndDeduplicate(segments: List<Segment>?, aggressive: Boolean, threshold: Float = 0.5f): List<Segment>? {
        if (segments.isNullOrEmpty()) return null

        val valid = segments
            .filter { it.text.isNotBlank() && it.end >= it.start }
            .sortedBy { it.start }

        if (valid.isEmpty()) return null

        // Pass 1: Remove immediate adjacent duplicate segments (normalizado)
        val pass1 = pass1AdjacentDedup(valid, aggressive)

        if (!aggressive) {
            // Conservador: só adjacent dedup — ciclo e 4-gram desabilitados para não cortar 90% de plenária legítima
            return pass1.ifEmpty { null }
        }
        if (pass1.size < 4) return pass1.ifEmpty { null }
        // Pass 2: Detect and collapse multi-segment cycle repetition loops
        val kRange = if (aggressive) 2..12 else 2..8
        val pass2 = mutableListOf<Segment>()
        var i = 0
        while (i < pass1.size) {
            var matchedCycleLen = 0
            var repetitionsToSkip = 0

            for (k in kRange) {
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
                try { android.util.Log.d("SegmentUtils", "filter halluc gap=$gap short=$short ngram=$ngramHalluc text=${seg.text.take(30)}") } catch (_: Exception) {}
                continue
            }
            pass3.add(seg)
        }
        // Guard: se colapso >60% em transcrição longa, provavelmente falso-positivo → reverte para Pass1 (adjacent only)
        // Não mantém cleaned mesmo se repetitivo, para não cortar 90% de plenária legítima (user report)
        val candidate = if (pass3.isEmpty() && pass2.isNotEmpty()) pass2 else pass3
        val thr = threshold.coerceIn(0.4f, 0.6f).toDouble()
        if (valid.size > 30 && candidate.size < valid.size * thr) {
            try { android.util.Log.w("OpenRouterSTT", "segment over-pruned raw=${valid.size} cleaned=${candidate.size} thr=$thr, reverting to Pass1 (corte 90% desativado)") } catch (_: Exception) {}
            return pass1.ifEmpty { null }
        }
        return candidate.ifEmpty { null }
    }

    /**
     * Pass 1 adjacent deduplication — extracted so OpenRouter provider reuses the same logic
     * instead of duplicating Regex normalization per segment.
     * Uses paraphrased detection: exact normalized equality OR Levenshtein <=2 for <40 chars when aggressive.
     */
    fun pass1AdjacentDedup(valid: List<Segment>): List<Segment> = pass1AdjacentDedup(valid, aggressive = true)

    fun pass1AdjacentDedup(valid: List<Segment>, aggressive: Boolean): List<Segment> {
        val pass1 = mutableListOf<Segment>()
        for (seg in valid) {
            val text = seg.text.trim()
            val normalized = normalizeForComparison(text)
            if (normalized.isEmpty()) continue

            val last = pass1.lastOrNull()
            val isDup = if (last != null) {
                if (aggressive) isParaphrased(last.text, text)
                else normalizeForComparison(last.text) == normalized
            } else false
            if (isDup) {
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
        // Fix 5:26 truncated duplicate: "Obrigado, vereadora Adriana... encaminhar votos?" vs truncated without last words
        // Se uma é prefixo da outra com >85% de overlap, é alucinação truncada, não voto distinto.
        if (na.length >= 40 || nb.length >= 40) {
            val longer = if (na.length > nb.length) na else nb
            val shorter = if (na.length > nb.length) nb else na
            if (longer.startsWith(shorter) && shorter.length.toDouble() / longer.length > 0.85) {
                val longerNoDigits = longer.replace(Regex("\\d+"), " ").replace(Regex("\\s+"), " ").trim()
                val shorterNoDigits = shorter.replace(Regex("\\d+"), " ").replace(Regex("\\s+"), " ").trim()
                // Se diferença é só truncamento, não números distintos, considera paraphrased
                if (longerNoDigits.startsWith(shorterNoDigits)) return true
            }
            // Para frases longas, tolera distância de edição proporcional (até 10% ou 10 chars)
            val maxDist = (minOf(longer.length, 80) * 0.12).toInt().coerceAtLeast(3).coerceAtMost(12)
            if (levenshtein(na, nb) <= maxDist) {
                val naNoDigits = na.replace(Regex("\\d+"), " ").replace(Regex("\\s+"), " ").trim()
                val nbNoDigits = nb.replace(Regex("\\d+"), " ").replace(Regex("\\s+"), " ").trim()
                if (naNoDigits == nbNoDigits) return false
                // Evita colapsar votos de vereadores diferentes: "Vereador Marcelo Mendes vota sim" vs "Vereador Marcelo Tchelo vota sim"
                // têm nomes diferentes, mas distância pode ser pequena. Verifica se palavras de nome diferem muito.
                return true
            }
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
     * Limpa texto puro (Gemini ou fallback sem segments) removendo alucinação textual.
     * Default keeps aggressive behavior for backward compat.
     */
    fun cleanTranscriptText(raw: String): String = cleanTranscriptText(raw, aggressive = true)

    /**
     * Overload with hallucination control.
     * @param aggressive true = Levenshtein + k 2..12 + 4-gram filtering; false = exact only, k 2..8, no 4-gram
     */
    fun cleanTranscriptText(raw: String, aggressive: Boolean, threshold: Float = 0.5f): String {
        // Fix Cláudio Lima hallucination: gpt-4o-mini gerou repetições inline separadas por vírgulas em linha única.
        // Antes splitParagraphs não detectava, então paras.size=1 e dedup não colapsava. Remove loops inline primeiro.
        val afterInlineLoop = removeInlinePhraseLoops(raw.trim())
        val trimmed = afterInlineLoop.trim()
        if (trimmed.isEmpty()) return trimmed
        val paras: List<String> = when {
            trimmed.contains("\n\n") -> trimmed.split(Regex("\n{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
            trimmed.contains("\n") -> trimmed.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            else -> splitParagraphs(trimmed)
        }.ifEmpty { return trimmed }

        // Pass A: remove duplicatas consecutivas (exato ou Levenshtein se aggressive)
        val deduped = mutableListOf<String>()
        for (p in paras) {
            val norm = normalizeForComparison(p)
            if (norm.isEmpty()) continue
            val last = deduped.lastOrNull()
            val isDup = if (last != null) {
                if (aggressive) isParaphrased(last, p) else normalizeForComparison(last) == norm
            } else false
            if (isDup) continue
            deduped.add(p)
        }
        if (!aggressive) {
            // Conservador: só adjacent dedup, sem colapso de ciclo nem 4-gram — evita corte de 90% (issue 2026-09-01)
            val early = deduped.joinToString("\n\n")
            try { android.util.Log.d("DiagTrunc", "clean in=${trimmed.length} out=${early.length} paras in=${paras.size} out=${deduped.size} aggr=$aggressive conservative-only-adjacent") } catch (_: Exception) {}
            return early
        }
        if (deduped.size < 4) {
            val early = deduped.joinToString("\n\n")
            try { android.util.Log.d("DiagTrunc", "clean in=${trimmed.length} out=${early.length} paras in=${paras.size} out=${deduped.size} early=true aggr=$aggressive") } catch (_: Exception) {}
            return early
        }
        val kRange = 2..12
        val out = mutableListOf<String>()
        var idx = 0
        while (idx < deduped.size) {
            var matchedLen = 0
            var skip = 0
            for (k in kRange) {
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
        // 4-gram filtering only when aggressive
        val globalCounts = fourGramCountsParas(paras)
        val tmp = mutableListOf<String>()
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
                    try { android.util.Log.d("SegmentUtils", "filter 4-gram para=${para.take(30)}") } catch (_: Exception) {}
                    continue
                }
                for (w in 0..words.size - 4) {
                    val gram = words.subList(w, w + 4).joinToString(" ")
                    if ((globalCounts[gram] ?: 0) > 3) keptGrams.add(gram)
                }
            }
            tmp.add(para)
        }
        val result = tmp.joinToString("\n\n")
        try { android.util.Log.d("DiagTrunc", "clean in=${trimmed.length} out=${result.length} paras in=${paras.size} out=${result.split(Regex("\n\n")).size} aggr=$aggressive") } catch (_: Exception) {}
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
                    endMs = (seg.end * 1000).toInt().coerceAtLeast(0),
                    words = seg.words
                )
            }
        }

        // If clean segments exist but paragraph count differs (e.g. grouped text),
        // anchor trechos to real segment spans by text and interpolate the rest.
        if (!cleanSegs.isNullOrEmpty()) {
            val totalSegDuration = (cleanSegs.last().end * 1000).toInt().coerceAtLeast(1000)
            val effectiveDuration = audioDurationMs?.coerceAtLeast(1000) ?: totalSegDuration
            return alignTimedParagraphs(paras, cleanSegs, effectiveDuration)
                ?: buildEstimatedTimedParagraphs(paras, effectiveDuration)
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

    /**
     * Remove repetições intra-frase separadas por vírgula: "vereador Cláudio Lima, vereador Cláudio Lima, ..." -> "vereador Cláudio Lima".
     * Detecta frase de 4..40 chars repetida 2+ vezes via ", phrase" (case-insensitive).
     * Iterativo para colapsar cadeias longas (200 repetições).
     */
    fun removeInlinePhraseLoops(text: String): String {
        if (text.length < 30) return text
        // Regex: captura frase curta (4..40 chars sem quebras) repetida via ", " 2+ vezes.
        // Ex: "vereador Cláudio Lima, vereador Cláudio Lima, vereador Cláudio Lima" -> "vereador Cláudio Lima"
        val loopRegex = Regex("""(\b.{4,40}?\b)(?:\s*,\s*\1){2,}""", setOf(RegexOption.IGNORE_CASE))
        var cur = text
        var prev: String
        var iterations = 0
        do {
            prev = cur
            cur = cur.replace(loopRegex, "$1")
            iterations++
        } while (cur != prev && iterations < 5)
        // Fallback heurístico para separação por vírgula sem regex (quando frases variam levemente em caixa/espaço):
        // Se texto ainda tem >500 chars e é single-line com muitas vírgulas e baixa diversidade, colapsa via split por vírgula.
        if (cur.length > 500 && !cur.contains("\n") && cur.count { it == ',' } >= 9) {
            val parts = cur.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 10) {
                val norms = parts.map { normalizeForComparison(it) }
                val uniqueRatio = norms.toSet().size.toDouble() / norms.size
                if (uniqueRatio < 0.5) {
                    // Mantém apenas primeira ocorrência de cada frase normalizada consecutiva
                    val deduped = mutableListOf<String>()
                    for (p in parts) {
                        val n = normalizeForComparison(p)
                        val lastNorm = deduped.lastOrNull()?.let { normalizeForComparison(it) }
                        if (lastNorm == n) continue
                        // também colapsa paraphrased quando aggressive
                        if (lastNorm != null && deduped.isNotEmpty()) {
                            val last = deduped.last()
                            if (isParaphrased(last, p)) continue
                        }
                        deduped.add(p)
                    }
                    // Se colapso reduziu >50%, provavelmente hallucination -> retorna colapsado
                    if (deduped.size.toDouble() / parts.size < 0.5) {
                        cur = deduped.joinToString(", ")
                    }
                }
            }
        }
        return cur
    }

    /** Heurística: raw é alucinação repetitiva? Usa unique ratio + pass1 aggressive ratio. */
    fun isRepetitiveHallucinationSegments(segments: List<Segment>?): Boolean {
        if (segments == null || segments.size < 10) return false
        val norms = segments.map { normalizeForComparison(it.text) }.filter { it.isNotEmpty() }
        if (norms.isEmpty()) return false
        val unique = norms.toSet().size
        if (unique.toDouble() / norms.size < 0.5) return true
        val valid = segments.filter { it.text.isNotBlank() && it.end >= it.start }.sortedBy { it.start }
        val pass1AggSize = pass1AdjacentDedup(valid, aggressive = true).size
        if (pass1AggSize.toDouble() / segments.size < 0.5) return true
        return false
    }

    fun isRepetitiveHallucinationText(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.length < 500) return false
        // Detecção rápida para loops inline por vírgula em single-line (gpt-4o-mini): não depende de paras.size>=10
        if (!trimmed.contains("\n") && trimmed.contains(",")) {
            val commaParts = trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (commaParts.size >= 10) {
                val norms = commaParts.map { normalizeForComparison(it) }.filter { it.isNotEmpty() }
                if (norms.isNotEmpty() && norms.toSet().size.toDouble() / norms.size < 0.5) return true
                val dedupedComma = mutableListOf<String>()
                for (p in commaParts) {
                    val last = dedupedComma.lastOrNull()
                    if (last != null && isParaphrased(last, p)) continue
                    if (last != null && normalizeForComparison(last) == normalizeForComparison(p)) continue
                    dedupedComma.add(p)
                }
                if (dedupedComma.size.toDouble() / commaParts.size < 0.5) return true
            }
            // Também tenta regex de loop inline diretamente
            val cleanedViaRegex = removeInlinePhraseLoops(trimmed)
            if (cleanedViaRegex.length < trimmed.length * 0.5) return true
        }
        val paras: List<String> = when {
            trimmed.contains("\n\n") -> trimmed.split(Regex("\n{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
            trimmed.contains("\n") -> trimmed.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            else -> splitParagraphs(trimmed)
        }
        if (paras.size < 10) return false
        val norms = paras.map { normalizeForComparison(it) }.filter { it.isNotEmpty() }
        if (norms.isEmpty()) return false
        val unique = norms.toSet().size
        if (unique.toDouble() / norms.size < 0.5) return true
        // pass1 aggressive detecta paraphrased repetição (vota vs votou)
        val dedupedAgg = mutableListOf<String>()
        for (p in paras) {
            val last = dedupedAgg.lastOrNull()
            val isDup = if (last != null) isParaphrased(last, p) else false
            if (isDup) continue
            dedupedAgg.add(p)
        }
        if (dedupedAgg.size.toDouble() / paras.size < 0.5) return true
        return false
    }
    private val segmentsAdapter: com.squareup.moshi.JsonAdapter<List<Segment>> by lazy {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            .adapter(Types.newParameterizedType(List::class.java, Segment::class.java))
    }
    /** Serializes ASR segments for storage, or null when there is nothing to preserve. */
    fun segmentsToJson(segments: List<Segment>?): String? {
        if (segments.isNullOrEmpty()) return null
        return try { segmentsAdapter.toJson(segments) } catch (_: Exception) { null }
    }

    /** Parses stored ASR segments, or null when absent or unreadable. */
    fun segmentsFromJson(json: String?): List<Segment>? {
        if (json.isNullOrBlank()) return null
        return try { segmentsAdapter.fromJson(json)?.takeIf { it.isNotEmpty() } } catch (_: Exception) { null }
    }

    /**
     * Realigns ASR segments from [oldParas] onto [newParas] after a Revisão correction.
     * Trechos with identical text keep their exact timestamps; only the edited span is
     * re-estimated proportionally inside its original time bounds. Returns null when the
     * old segments cannot be mapped (absent, or count diverges from [oldParas]) so the
     * caller falls back to discarding them.
     */
    fun realignSegments(
        oldParas: List<String>,
        newParas: List<String>,
        oldSegments: List<Segment>?
    ): List<Segment>? {
        val keptSegments = cleanAndDeduplicate(oldSegments) ?: return null
        if (keptSegments.size != oldParas.size || newParas.isEmpty()) return null

        var prefix = 0
        while (prefix < oldParas.size && prefix < newParas.size && oldParas[prefix] == newParas[prefix]) prefix++
        var suffix = 0
        while (suffix < oldParas.size - prefix && suffix < newParas.size - prefix &&
            oldParas[oldParas.size - 1 - suffix] == newParas[newParas.size - 1 - suffix]
        ) suffix++

        val realigned = mutableListOf<Segment>()
        for (i in 0 until prefix) realigned.add(keptSegments[i].copy(id = i))

        val oldMidStart = prefix
        val oldMidEnd = oldParas.size - suffix
        val newMidStart = prefix
        val newMidEnd = newParas.size - suffix
        val spanStartMs: Int
        val spanEndMs: Int
        if (oldMidStart < oldMidEnd) {
            spanStartMs = (keptSegments[oldMidStart].start * 1000).toInt().coerceAtLeast(0)
            spanEndMs = (keptSegments[oldMidEnd - 1].end * 1000).toInt().coerceAtLeast(spanStartMs)
        } else {
            // Pure insertion between two kept trechos: the gap is the span (possibly zero-width).
            val leftEndMs = if (prefix > 0) (keptSegments[prefix - 1].end * 1000).toInt() else 0
            val rightStartMs = if (suffix > 0) (keptSegments[oldParas.size - suffix].start * 1000).toInt() else leftEndMs
            spanStartMs = leftEndMs.coerceAtLeast(0)
            spanEndMs = rightStartMs.coerceAtLeast(spanStartMs)
        }
        if (newMidStart < newMidEnd) {
            val newMiddle = newParas.subList(newMidStart, newMidEnd)
            val spanMs = (spanEndMs - spanStartMs).coerceAtLeast(newMiddle.size * 400)
            buildEstimatedTimedParagraphs(newMiddle, spanMs).forEachIndexed { middleIndex, timed ->
                realigned.add(
                    Segment(
                        id = newMidStart + middleIndex,
                        seek = 0,
                        start = (spanStartMs + timed.startMs) / 1000.0,
                        end = (spanStartMs + timed.endMs) / 1000.0,
                        text = newMiddle[middleIndex]
                    )
                )
            }
        }
        for (suffixOffset in 0 until suffix) {
            val oldIdx = oldParas.size - suffix + suffixOffset
            realigned.add(keptSegments[oldIdx].copy(id = newParas.size - suffix + suffixOffset))
        }
        return realigned
    }
    /**
     * Maps [paras] onto the real ASR timeline by anchoring each trecho to the contiguous
     * run of segments whose text it contains. Anchored trechos keep exact segment spans;
     * unanchored ones split the gap between neighboring anchors proportionally by word
     * count. Returns null when no trecho anchors, so the caller keeps proportional
     * estimation over the whole duration.
     */
    fun alignTimedParagraphs(
        paras: List<String>,
        segments: List<Segment>?,
        totalDurationMs: Int
    ): List<TimedParagraph>? {
        val clean = cleanAndDeduplicate(segments) ?: return null
        if (paras.isEmpty() || clean.isEmpty()) return null
        val normParas = paras.map { normalizeForComparison(it) }
        val normSegs = clean.map { normalizeForComparison(it.text) }

        // First pass: anchor trechos to contiguous contained segment runs.
        val spans = arrayOfNulls<Pair<Int, Int>>(paras.size)
        var segIdx = 0
        for (i in paras.indices) {
            if (normParas[i].isBlank()) continue
            var j = segIdx
            while (j < clean.size && !normParas[i].contains(normSegs[j])) j++
            if (j >= clean.size) continue
            var k = j
            while (k + 1 < clean.size && normParas[i].contains(normSegs[k + 1])) k++
            spans[i] = Pair(
                (clean[j].start * 1000).toInt().coerceAtLeast(0),
                (clean[k].end * 1000).toInt().coerceAtLeast(0)
            )
            segIdx = k + 1
        }
        if (spans.all { it == null }) return null

        // Second pass: anchored spans stay exact, gaps split by word count.
        val out = mutableListOf<TimedParagraph>()
        var i = 0
        var cursor = 0
        while (i < paras.size) {
            val span = spans[i]
            if (span != null) {
                val start = span.first.coerceAtLeast(cursor)
                val end = span.second.coerceAtLeast(start)
                out.add(TimedParagraph(paras[i], start, end))
                cursor = end
                i++
            } else {
                var j = i
                while (j < paras.size && spans[j] == null) j++
                val runStart = cursor
                val runEnd = if (j < paras.size) spans[j]!!.first.coerceAtLeast(cursor) else totalDurationMs.coerceAtLeast(cursor)
                val run = paras.subList(i, j)
                val words = run.map { p -> p.split(Regex("\\s+")).count { w -> w.isNotBlank() }.coerceAtLeast(1) }
                val totalWords = words.sum()
                var acc = runStart
                run.forEachIndexed { k, text ->
                    val dur = if (k == run.lastIndex) runEnd - acc
                    else ((words[k].toFloat() / totalWords) * (runEnd - runStart)).toInt()
                    out.add(TimedParagraph(text, acc, (acc + dur).coerceAtMost(runEnd)))
                    acc += dur
                }
                cursor = runEnd
                i = j
            }
        }
        return out
    }

    /** Nests provider word timings into their segment spans; stray words are dropped. */
    fun attachWords(segments: List<Segment>, words: List<Word>?): List<Segment> {
        if (words.isNullOrEmpty()) return segments
        return segments.map { seg ->
            seg.copy(words = words.filter { it.start >= seg.start && it.end <= seg.end })
        }
    }

    /** Active word for [posMs], mirroring paragraph gap semantics; -1 when unusable. */
    fun findActiveWordIndex(posMs: Int, words: List<Word>): Int {
        if (words.isEmpty()) return -1
        val items = words.map {
            TimedParagraph(it.word, (it.start * 1000).toInt(), (it.end * 1000).toInt())
        }
        return findActiveIndex(posMs, items)
    }

    fun buildWordAnnotated(base: String, words: List<Word>, activeIdx: Int, highlight: SpanStyle): AnnotatedString? {
        if (words.isEmpty() || activeIdx !in words.indices) return null
        val ranges = mutableListOf<Pair<Int, Int>>()
        var cursor = 0
        for (w in words) {
            val found = base.indexOf(w.word, cursor, ignoreCase = true)
            if (found < 0) return null
            ranges.add(found to found + w.word.length)
            cursor = found + w.word.length
        }
        return androidx.compose.ui.text.buildAnnotatedString {
            append(base)
            ranges.forEachIndexed { i, (s, e) ->
                if (i == activeIdx) addStyle(highlight, s, e)
            }
        }
    }
}
