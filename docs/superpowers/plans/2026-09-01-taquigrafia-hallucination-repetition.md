# Taquigrafia Hallucination Repetition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix recurrent repetition and hallucination for 5:26 GPT-4o Mini Transcribe (10 lines) with lexical variation and non-repetitive invention, keeping 5:26 single and 26min chunked.

**Architecture:** Extend SegmentUtils with Levenshtein <=2 and 4-gram plus confidence/gap heuristics, VAD 2min chunk for silence, harden LLM prompt/top_p, expose aggressive/conservative switch in UI.

**Tech Stack:** Android Kotlin 2.2.10, Room 2.7.0, Retrofit 2.12.0, MediaExtractor/MediaMuxer, Moshi 1.15.2, Compose BOM 2024.09.00

## Global Constraints

- minSdk 24, targetSdk 36, Java 11, Kotlin 2.2.10 — do not change
- Keep postProcessingEnabled default false — do not revert
- GenerationConfig.maxOutputTokens 16384, OpenRouter max_tokens 16384, temperature 0.1 — do not lower
- No new dependencies; use android.media.* already present
- All logs DiagTrunc/Halluc keep

---

## File Structure

- `app/src/main/java/com/example/data/SegmentUtils.kt` — dedup with Levenshtein, n-gram, confidence
- `app/src/main/java/com/example/data/AudioChunker.kt` — VAD 2min
- `app/src/main/java/com/example/data/provider/OpenRouterTranscriptionProvider.kt` — strict prompt, top_p
- `app/src/main/java/com/example/data/provider/GeminiTranscriptionProvider.kt` — topP
- `app/src/main/java/com/example/MainActivity.kt` — hallucination Card switch/slider
- `app/src/test/java/com/example/SegmentUtilsTest.kt` — new tests

---

### Task 1: Capture Hallucination Pattern 225658

**Files:**
- Modify: `app/src/main/java/com/example/ui/TranscriptionViewModel.kt:179`
- Modify: `app/src/main/java/com/example/data/provider/OpenRouterTranscriptionProvider.kt:132`
- Test: manual logcat

**Interfaces:**
- Consumes: `rawSegments`, `cleanedSegments`, `dedupedRaw`
- Produces: `Halluc` logs

- [ ] **Step 1: Add Halluc logs**

```kotlin
android.util.Log.d("Halluc", "rawTextLen=${raw.length} segmentsRaw=${rawSegments?.size} cleaned=${segments?.size} dedupedRawLen=${dedupedRaw.length} finalLen=${finalRawText.length} first3=${rawSegments?.take(3)?.map{normalizeForComparison(it.text)}} last3=${rawSegments?.takeLast(3)?.map{normalizeForComparison(it.text)}}")
```

- [ ] **Step 2: Run assemble**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/ui/TranscriptionViewModel.kt app/src/main/java/com/example/data/provider/OpenRouterTranscriptionProvider.kt
git commit -m "chore: add Halluc logs for 225658"
```

---

### Task 2: Levenshtein and N-gram Cleaning

**Files:**
- Modify: `app/src/main/java/com/example/data/SegmentUtils.kt:19,98`
- Test: `app/src/test/java/com/example/SegmentUtilsTest.kt`

**Interfaces:**
- Consumes: `List<Segment>` with normalize
- Produces: cleaned list/text

- [ ] **Step 1: Write failing test paraphrased**

```kotlin
@Test fun `paraphrased repetition collapses`() {
  val raw = listOf(
    Segment(1,0,0.0,2.0,"Vereadora A vota sim."),
    Segment(2,0,2.0,4.0,"Vereadora A votou sim")
  )
  val cleaned = SegmentUtils.cleanAndDeduplicate(raw)
  assertEquals(1, cleaned!!.size)
}
```

- [ ] **Step 2: Run test fails**

Run: `./gradlew testDebugUnitTest --tests com.example.SegmentUtilsTest -v`
Expected: FAIL (2 vs 1)

- [ ] **Step 3: Implement Levenshtein <=2 for <40 chars when valid>30 and 4-gram >3x**

```kotlin
private fun levenshtein(a:String,b:String):Int { /* classic DP */ }
private fun isParaphrased(a:String,b:String):Boolean {
  val na=normalizeForComparison(a); val nb=normalizeForComparison(b)
  if (na==nb) return true
  if (na.length<40 && nb.length<40 && levenshtein(na,nb)<=2) return true
  return false
}
// In cleanAndDeduplicate Pass1: if isParaphrased(last.text, seg.text) continue
// In cleanTranscriptText Pass A: same
// Add 4-gram check: for each para, 4-grams, if any 4-gram appears >3x in whole text, mark para as hallucination if gap >5s and text<20 chars
```

- [ ] **Step 4: Run test passes**

Run: `./gradlew testDebugUnitTest --tests com.example.SegmentUtilsTest -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/data/SegmentUtils.kt app/src/test/java/com/example/SegmentUtilsTest.kt
git commit -m "fix: Levenshtein and 4-gram cleaning for paraphrased hallucination"
```

---

### Task 3: VAD Chunk 2min for Silence

**Files:**
- Modify: `app/src/main/java/com/example/data/AudioChunker.kt:20`
- Test: `app/src/test/java/com/example/AudioChunkerTest.kt`

**Interfaces:**
- Consumes: durationMs, fileSize, uri
- Produces: List<Chunk> 2min when silence

- [ ] **Step 1: Write test VAD**

```kotlin
@Test fun `silence long triggers 2min chunk`() {
  // mock duration 326000 with silenceRatio 0.4 -> should chunk into 2min pieces vs 5min
}
```

- [ ] **Step 2: Implement VAD check**

```kotlin
// In splitIfNeeded, after dur extraction, check MediaMetadataRetriever hasAudio or no_speech_prob
// If gap >5s with text<20 chars in segments, or silenceRatio>0.3, use chunkDuration 2*60*1000
```

- [ ] **Step 3: Run tests**

Run: `./gradlew testDebugUnitTest --tests com.example.AudioChunkerTest -v`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/data/AudioChunker.kt
git commit -m "fix: VAD 2min chunk for silence hallucination"
```

---

### Task 4: Harden LLM Prompt

**Files:**
- Modify: `app/src/main/java/com/example/data/provider/OpenRouterTranscriptionProvider.kt:171`
- Modify: `app/src/main/java/com/example/data/provider/GeminiTranscriptionProvider.kt:26`

**Interfaces:**
- Consumes: systemPrompt, strictUserPrompt
- Produces: TranscriptionResult

- [ ] **Step 1: Update strictUserPrompt**

```kotlin
val strictUserPrompt = """Se o texto bruto contiver repetição exata, mantenha apenas uma ocorrência; se contiver trecho que não parece fala (ex: lista de vereadores repetida), remova. ${finalRawText}"""
```

- [ ] **Step 2: Add top_p**

```kotlin
// Gemini GenerationConfig(topP=0.1f), OpenRouter Chat top_p 0.1
```

- [ ] **Step 3: Run tests**

Run: `./gradlew testDebugUnitTest -v`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/data/provider/*.kt app/src/main/java/com/example/data/api/*.kt
git commit -m "fix: harden LLM prompt and top_p for hallucination"
```

---

### Task 5: Expose Hallucination Control in UI

**Files:**
- Modify: `app/src/main/java/com/example/MainActivity.kt:1654`
- Modify: `app/src/main/java/com/example/data/SegmentUtils.kt` to read pref
- Test: `app/src/test/java/com/example/SettingsHallucinationTest.kt`

**Interfaces:**
- Consumes: SharedPreferences hallucination_aggressive, threshold
- Produces: Switch/Slider UI

- [ ] **Step 1: Add UI Card**

```kotlin
Card { Switch(checked=aggressive, onCheckedChange={...}); Slider(value=threshold, valueRange=0.4f..0.6f) }
```

- [ ] **Step 2: Persist pref and read in SegmentUtils**

```kotlin
val aggressive = prefs.getBoolean("hallucination_aggressive", false)
val threshold = prefs.getFloat("hallucination_threshold", 0.5f)
```

- [ ] **Step 3: Run tests**

Run: `./gradlew testDebugUnitTest -v`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/MainActivity.kt app/src/main/java/com/example/data/SegmentUtils.kt
git commit -m "feat: add hallucination control UI switch/slider"
```

---

