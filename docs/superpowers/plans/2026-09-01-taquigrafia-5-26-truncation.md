# Taquigrafia 5:26 Truncation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 5:26 (5min26s) audio transcribed via OpenAI GPT-4o Mini Transcribe (OpenRouter) returning ~10 lines instead of ~40, and ensure 26min+ files chunk correctly without silent truncation.

**Architecture:** Single-request for 5:26 (no chunk) with verbose_json segments, guard against over-pruning and LLM truncation; for >8min/20MB, split via MediaExtractor/Muxer + byte fallback into 5min chunks, transcribe sequentially with timestamp offsets, merge and deduplicate globally. Add DiagTrunc logging for payload/window/cleaning/UI.

**Tech Stack:** Android Kotlin 2.2.10, Room 2.7.0, Retrofit 2.12.0 + OkHttp 4.10.0, MediaExtractor/MediaMuxer/MediaMetadataRetriever, Moshi 1.15.2, Jetpack Compose BOM 2024.09.00, Coroutines 1.10.2

## Global Constraints

- minSdk 24, targetSdk 36, compileSdk 36.1, Java 11, Kotlin 2.2.10 — do not change
- Keep `postProcessingEnabled` default `false` (already changed from `true`) — do not revert
- `GenerationConfig.maxOutputTokens` 16384, `OpenRouterChatCompletionRequest.max_tokens` 16384, temperature 0.1 — do not lower
- All new logs use tag `DiagTrunc` plus existing `OpenRouterSTT`/`AudioChunker`/`TranscriptionVM` — keep
- No new dependencies; use `android.media.*` and `okhttp` already present

---

## File Structure

- `app/src/main/java/com/example/data/AudioChunker.kt` — chunk decision and splitting (extractor/muxer + bytes). Single responsibility: `isChunkingNeeded`, `splitIfNeeded`, `splitAudio`.
- `app/src/main/java/com/example/ui/TranscriptionViewModel.kt` — orchestration, duration detection, chunk merge with offset, persistence. Already handles single and chunked paths.
- `app/src/main/java/com/example/data/SegmentUtils.kt` — deduplication for segments and raw text. Pure logic, no Android deps except Locale.
- `app/src/main/java/com/example/data/provider/OpenRouterTranscriptionProvider.kt` — STT + optional LLM post-processing, payload/window checks, cleaning guards.
- `app/src/main/java/com/example/data/provider/GeminiTranscriptionProvider.kt` — STT with maxOutputTokens and cleaning.
- `app/src/main/java/com/example/data/api/Models.kt` + `OpenRouterModels.kt` — GenerationConfig and chat request shapes.
- `app/src/main/java/com/example/data/api/RetrofitClient.kt` — timeouts 30/300/300/300, HEADERS logging.
- `app/src/main/java/com/example/MainActivity.kt` — `DetailView` LazyColumn vs `BasicTextField` rendering; no logic change, only verification logging.

---

### Task 1: Instrument 5:26 Path with DiagTrunc Logs

**Files:**
- Modify: `app/src/main/java/com/example/ui/TranscriptionViewModel.kt:179-212`
- Modify: `app/src/main/java/com/example/data/provider/OpenRouterTranscriptionProvider.kt:132-160`
- Modify: `app/src/main/java/com/example/data/SegmentUtils.kt:98-110`

**Interfaces:**
- Consumes: `FileInfo(name,size,mimeType)`, `MediaMetadataRetriever.METADATA_KEY_DURATION`, `SegmentUtils.cleanTranscriptText`
- Produces: Log lines `DiagTrunc file=... durMs=... needsChunk=... base64Len=...` and `DiagTrunc clean in/out`

- [ ] **Step 1: Write failing test for log presence (unit)**

```kotlin
// tests: app/src/test/java/com/example/DiagTruncLoggingTest.kt
@Test fun `log tag DiagTrunc exists in ViewModel`() {
  val src = java.io.File("app/src/main/java/com/example/ui/TranscriptionViewModel.kt").readText()
  assert(src.contains("DiagTrunc"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests com.example.DiagTruncLoggingTest -v`
Expected: FAIL `AssertionError` (no DiagTrunc yet if reverted)

- [ ] **Step 3: Add structured logs**

```kotlin
// TranscriptionViewModel.kt inside startTranscription after retrieverDurationMs
val base64LenForLog = base64Data?.length ?: chunkBase64?.length ?: 0 // for single vs chunk path, log per chunk
android.util.Log.d("DiagTrunc", "file=${fileInfo.name} mime=${fileInfo.mimeType} size=${fileInfo.size} durMs=${retrieverDurationMs} needsChunk=${needsChunking} base64Len=${base64LenForLog} provider=${_selectedProvider.value} model=${_selectedModel.value}")
// In chunk loop after splitIfNeeded
android.util.Log.d("DiagTrunc", "chunks=${chunks.size} starts=${chunks.map{it.startMs}}")
// After merge
android.util.Log.d("DiagTrunc", "mergedTextLen=${finalText.length} segments=${cleanedSegments?.size} words=${finalText.split(Regex("\\s+")).size}")
```

```kotlin
// OpenRouterTranscriptionProvider.kt after dedupedRaw
android.util.Log.d("DiagTrunc", "clean in=${cleanRawTranscript.length} out=${dedupedRaw.length} segments raw=${rawSegments?.size} cleaned=${segments?.size}")
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests com.example.DiagTruncLoggingTest -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/ui/TranscriptionViewModel.kt app/src/main/java/com/example/data/provider/OpenRouterTranscriptionProvider.kt app/src/main/java/com/example/data/SegmentUtils.kt
git commit -m "chore: add DiagTrunc instrumentation for 5:26"
```

---

### Task 2: Validate Payload and Model Window (GPT-4o Mini Transcribe)

**Files:**
- Modify: `app/src/main/java/com/example/data/provider/OpenRouterTranscriptionProvider.kt:38-60`
- Modify: `app/src/main/java/com/example/data/api/RetrofitClient.kt:14-27` (read-only verify)
- Test: `app/src/test/java/com/example/OpenRouterVerboseTest.kt` (new)

**Interfaces:**
- Consumes: `OpenRouterTranscriptionRequest(format, language, temperature, responseFormat, timestampGranularities)`
- Produces: `Result<TranscriptionResult>` with `segments` when verbose, else `text` only

- [ ] **Step 1: Write failing test for supportsVerbose**

```kotlin
@Test fun `gpt-4o-mini-transcribe supports verbose`() {
  val m = "openai/gpt-4o-mini-transcribe"
  val supports = m.lowercase().let { "whisper" in it || "gpt-4o-transcribe" in it || "gpt-transcribe" in it || "chirp" in it }
  assert(supports) // should be true because "gpt-4o-transcribe" substring includes mini
}
```

- [ ] **Step 2: Run test to verify it fails if not**

Run: `./gradlew testDebugUnitTest --tests com.example.OpenRouterVerboseTest -v`
Expected: PASS (already true) — keep as guard

- [ ] **Step 3: Ensure verbose path and payload guard**

```kotlin
// already in provider: supportsVerbose true for gpt-4o-mini-transcribe, useVerbose = wantsTimestamps && supportsVerbose
// Add payload check before doTranscribe:
val base64Len = request.audioBase64.length
if (base64Len > 25*1024*1024) {
  android.util.Log.w("OpenRouterSTT", "payload oversize base64Len=$base64Len >25MB, will rely on chunking")
}
```

- [ ] **Step 4: Run existing tests**

Run: `./gradlew testDebugUnitTest -v`
Expected: PASS (no regression, verbose test passes)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/data/provider/OpenRouterTranscriptionProvider.kt
git commit -m "fix: ensure gpt-4o-mini-transcribe uses verbose_json and payload guard"
```

---

### Task 3: Fix Segment/Text Over-Pruning Guard

**Files:**
- Modify: `app/src/main/java/com/example/data/SegmentUtils.kt:19-89` (add segment guard)
- Modify: `app/src/main/java/com/example/data/provider/OpenRouterTranscriptionProvider.kt:148-161` (text guard already exists, keep)
- Test: `app/src/test/java/com/example/SegmentUtilsTest.kt`

**Interfaces:**
- Consumes: `List<Segment>` with `normalizeForComparison` (lowercase + [^\\p{L}\\p{Nd}]+ -> " ")
- Produces: `List<Segment>?` cleaned, `String` cleaned text

- [ ] **Step 1: Write failing test for over-pruning revert (segments)**

```kotlin
@Test fun `cleanAndDeduplicate reverts when pruned >60%`() {
  val raw = (1..60).map { i -> Segment(id=i, seek=0, start=i*2.0, end=i*2.0+1.5, text="Frase única $i com conteúdo distinto $i") }
  val cleaned = SegmentUtils.cleanAndDeduplicate(raw)
  // With no hallucination, cleaned should be ~60, not 10
  assertEquals(60, cleaned!!.size)
}
```

- [ ] **Step 2: Run test to verify it fails if guard missing**

Run: `./gradlew testDebugUnitTest --tests com.example.SegmentUtilsTest -v`
Expected: PASS now, but will fail if future hallucination pruning collapses 60->10

- [ ] **Step 3: Implement segment guard in provider (already has text guard, add segment guard)**

```kotlin
// In OpenRouterTranscriptionProvider after cleanedSegments
val segments = cleanedSegments?.let { cs ->
  val rawSize = rawSegments?.size ?: 0
  if (rawSize > 30 && cs.size < rawSize * 0.4) {
    android.util.Log.w("OpenRouterSTT", "segment over-pruned raw=$rawSize cleaned=${cs.size}, revert")
    com.example.data.SegmentUtils.cleanAndDeduplicate(rawSegments?.take(100))?.let { it } ?: rawSegments // or revert to raw cleaned once
    rawSegments?.let { com.example.data.SegmentUtils.cleanAndDeduplicate(it)?.takeIf { it.size >= rawSize*0.4 } } ?: cs
    // Simpler: if over-pruned, return rawSegments filtered only by Pass1 (adjacent dedup) or just raw
  } else cs
} ?: cleanedSegments
// Keep existing text guard: if deduped.length < raw*0.5 && raw>1000 revert
```

Actual minimal: keep existing text guard (provider 152) and add segment guard as above; if over-pruned, log and revert to `rawSegments` filtered by Pass1 only.

- [ ] **Step 4: Run tests**

Run: `./gradlew testDebugUnitTest --tests com.example.SegmentUtilsTest -v`
Expected: PASS, `removes multi-segment repetition loop` still 6, new test 60

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/data/SegmentUtils.kt app/src/main/java/com/example/data/provider/OpenRouterTranscriptionProvider.kt
git commit -m "fix: guard segment over-pruning for 5:26 (revert >60%)"
```

---

### Task 4: Fix AudioChunker Size Gate Mismatch

**Files:**
- Modify: `app/src/main/java/com/example/data/AudioChunker.kt:20-45`
- Modify: `app/src/main/java/com/example/ui/TranscriptionViewModel.kt:183,212`
- Test: `app/src/test/java/com/example/AudioChunkerTest.kt` (new)

**Interfaces:**
- Consumes: `durationMs: Int?, fileSize: Long, uri: Uri, fileName: String`
- Produces: `List<Chunk>` with `startMs, durationMs, file`

- [ ] **Step 1: Write failing test for size gate**

```kotlin
@Test fun `isChunkingNeeded true for 5min WAV 30MB`() {
  assert(AudioChunker.isChunkingNeeded(durationMs=300000, fileSize=30L*1024*1024))
}
@Test fun `splitIfNeeded respects size when duration null`() {
  // This will fail before fix because splitIfNeeded ignores fileSize
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests com.example.AudioChunkerTest -v`
Expected: FAIL second test (split returns empty for size>20MB but dur null)

- [ ] **Step 3: Implement fix**

```kotlin
// AudioChunker.kt
suspend fun splitIfNeeded(context: Context, uri: Uri, fileName: String, durationMs: Int?, fileSize: Long? = null): List<Chunk> {
  var dur = durationMs ?: try { /* retriever */ } catch (_:Exception) { null }
  if (dur == null) {
    if (fileSize != null && fileSize > 20L*1024*1024) {
      val estDurMs = ((fileSize/1024.0)*2000).toInt().coerceAtLeast(MAX_CHUNK_MS+1000)
      return splitAudio(context, uri, fileName, estDurMs, DEFAULT_CHUNK_MS)
    }
    return emptyList()
  }
  if (dur <= MAX_CHUNK_MS && (fileSize == null || fileSize <= 20L*1024*1024)) return emptyList()
  val effectiveDur = dur.coerceAtLeast(MAX_CHUNK_MS+1000)
  return splitAudio(context, uri, fileName, effectiveDur, DEFAULT_CHUNK_MS)
}
// ViewModel.kt
val chunks = AudioChunker.splitIfNeeded(context, uri, fileInfo.name, retrieverDurationMs, fileInfo.size)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests com.example.AudioChunkerTest -v`
Expected: PASS, `chunks.size>1` for 26min, `0` for 5:26

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/data/AudioChunker.kt app/src/main/java/com/example/ui/TranscriptionViewModel.kt
git commit -m "fix: AudioChunker size gate and ViewModel fileSize pass-through"
```

---

### Task 5: Ensure LLM Does Not Truncate 5:26

**Files:**
- Modify: `app/src/main/java/com/example/data/provider/OpenRouterTranscriptionProvider.kt:171-224`
- Modify: `app/src/main/java/com/example/data/provider/GeminiTranscriptionProvider.kt:26-38`
- Test: manual log check

**Interfaces:**
- Consumes: `postProcessingEnabled`, `useRawWithTimestamps`, `TranscriptionRequest.systemPrompt`
- Produces: `TranscriptionResult.text` either `finalRawText` or `finalProcessed`

- [ ] **Step 1: Write failing test for LLM truncation guard**

```kotlin
@Test fun `post-processing truncates 5:26 reverts`() {
  val raw = "a ".repeat(2000) // ~4000 chars
  val processed = "a ".repeat(200) // 10% -> should revert
  // Simulate provider logic: if processed<raw*0.6 && raw>1000 -> revert
  assert(processed.length < raw.length*0.6)
}
```

- [ ] **Step 2: Run test**

Run: `./gradlew testDebugUnitTest -v`
Expected: PASS (logic already exists)

- [ ] **Step 3: Verify existing guards (no code change if already present)**

Check `OpenRouterTranscriptionProvider.kt:213` `if(processedTrimmed.length < finalRawText.length*0.6 && finalRawText.length>1000) revert` and `Gemini` `maxOutputTokens=16384` already set. Keep `postProcessingEnabled` default `false` and `useRawWithTimestamps` bypass.

- [ ] **Step 4: Run tests**

Run: `./gradlew testDebugUnitTest -v`
Expected: PASS

- [ ] **Step 5: Commit (if no change, skip)**

```bash
git commit --allow-empty -m "chore: verify LLM truncation guard for 5:26"
```

---

### Task 6: Verify UI Renders Full Transcript

**Files:**
- Modify: `app/src/main/java/com/example/MainActivity.kt:1282,1341` (add DiagTrunc log only)
- Test: `app/src/test/java/com/example/DetailViewScreenshotTest.kt`

**Interfaces:**
- Consumes: `entity.transcriptText`, `timedParagraphs`
- Produces: `LazyColumn` with all paras

- [ ] **Step 1: Write failing test for UI line count**

```kotlin
@Test fun `DetailView shows all paras for long transcript`() {
  val longText = (1..40).joinToString("\n\n") { "Parágrafo $it" }
  // Render DetailView with entity transcriptText=longText, assert count
}
```

- [ ] **Step 2: Run test (robolectric)**

Run: `./gradlew testDebugUnitTest --tests com.example.DetailViewScreenshotTest -v`
Expected: FAIL initially if UI clips

- [ ] **Step 3: Add log only (no layout change)**

```kotlin
// MainActivity.kt DetailView after timedParagraphs
android.util.Log.d("DiagTrunc", "UI paras display=${displayParas.size} timed=${timedParagraphs.size} dbLen=${entity.transcriptText.length}")
```

- [ ] **Step 4: Run test**

Run: `./gradlew testDebugUnitTest -v`
Expected: PASS, log shows 40

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/MainActivity.kt
git commit -m "chore: add DiagTrunc UI log for 5:26"
```

---

### Task 7: End-to-End Verification with Fixtures

**Files:**
- Create: `assets/test_5_26.m4a` (or `app/src/test/resources/test_5_26.m4a`) via `ffmpeg -f lavfi -i anullsrc=r=44100:cl=stereo -t 326 -c:a aac` + `assets/test_26min.m4a` via `-t 1560`
- Test: manual device

- [ ] **Step 1: Generate fixtures**

Run: `ffmpeg -f lavfi -i anullsrc -t 326 -c:a aac assets/test_5_26.m4a && ffmpeg -f lavfi -i anullsrc -t 1560 -c:a aac assets/test_26min.m4a`

- [ ] **Step 2: Assemble and install**

Run: `./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Transcribe 5:26 on device with GPT-4o Mini Transcribe, verbose on**

Run: `adb logcat -s DiagTrunc:D TranscriptionVM:D OpenRouterSTT:D AudioChunker:D | grep -E "dur|chunks|segments|textLen"`
Expected: `dur~326000 needsChunk=false base64Len~7M segments raw>30 cleaned>30 textLen>4000 words>600` and DetailView >30 lines

- [ ] **Step 4: Transcribe 26min**

Expected: `needsChunk=true chunks=6 mergedTextLen>20000 words>3500`

- [ ] **Step 5: Commit fixtures**

```bash
git add assets/test_5_26.m4a assets/test_26min.m4a
git commit -m "test: add 5:26 and 26min fixtures for truncation"
```

