package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.AudioChunker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AudioChunkerTest {

    private fun mb(n: Long) = n * 1024 * 1024

    // ---- isChunkingNeeded size gate ----

    @Test
    fun `isChunkingNeeded true for 5min WAV 30MB`() {
        assertTrue(AudioChunker.isChunkingNeeded(durationMs = 300_000, fileSize = mb(30)))
    }

    @Test
    fun `isChunkingNeeded false for 5_26 small file`() {
        // 5:26 = 326s = 326_000 ms < 480_000, small file 5MB
        assertFalse(AudioChunker.isChunkingNeeded(durationMs = 326_000, fileSize = mb(5)))
    }

    @Test
    fun `isChunkingNeeded true for 26min small file`() {
        // 26 min = 1_560_000 ms >480_000 => chunk regardless of size
        assertTrue(AudioChunker.isChunkingNeeded(durationMs = 26 * 60 * 1000, fileSize = mb(5)))
    }

    @Test
    fun `isChunkingNeeded true when duration null but size large`() {
        assertTrue(AudioChunker.isChunkingNeeded(durationMs = null, fileSize = mb(30)))
    }

    @Test
    fun `isChunkingNeeded false when duration null and size small`() {
        assertFalse(AudioChunker.isChunkingNeeded(durationMs = null, fileSize = mb(5)))
    }

    @Test
    fun `isChunkingNeeded false at exact MAX_CHUNK_MS boundary`() {
        val max = 8 * 60 * 1000
        assertFalse(AudioChunker.isChunkingNeeded(durationMs = max, fileSize = mb(5)))
        assertTrue(AudioChunker.isChunkingNeeded(durationMs = max + 1, fileSize = mb(5)))
    }

    @Test
    fun `isChunkingNeeded size boundary 20MB`() {
        assertFalse(AudioChunker.isChunkingNeeded(durationMs = 300_000, fileSize = 20L * 1024 * 1024))
        assertTrue(AudioChunker.isChunkingNeeded(durationMs = 300_000, fileSize = 20L * 1024 * 1024 + 1))
    }

    // ---- splitIfNeeded respects size gate ----

    @Test
    fun `splitIfNeeded respects size when duration null - small file returns empty`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://invalid/test.mp3")
        val chunks = AudioChunker.splitIfNeeded(ctx, uri, "test.mp3", durationMs = null, fileSize = mb(5))
        assertTrue("small file with null duration should not chunk", chunks.isEmpty())
    }

    @Test
    fun `splitIfNeeded for 5_26 small file returns empty - no chunk`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://invalid/test.mp3")
        val chunks = AudioChunker.splitIfNeeded(ctx, uri, "test.mp3", durationMs = 326_000, fileSize = mb(5))
        assertTrue("5:26 small file should not chunk", chunks.isEmpty())
    }

    @Test
    fun `splitIfNeeded for 5_26 small file size param null also returns empty`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://invalid/test.mp3")
        val chunks = AudioChunker.splitIfNeeded(ctx, uri, "test.mp3", durationMs = 326_000, fileSize = null)
        assertTrue(chunks.isEmpty())
    }

    @Test
    fun `splitIfNeeded for 26min with real file returns chunks`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        // Create a 2MB dummy file so trySplitByBytes can produce >1 chunk
        val tmp = File(ctx.cacheDir, "audio_26min_test.mp3")
        tmp.delete()
        // Write 2MB of pseudo-mp3 data (all zeros - still passes byte-split fallback)
        FileOutputStream(tmp).use { out ->
            val block = ByteArray(8192) { 0x55.toByte() }
            var remaining = 2 * 1024 * 1024
            while (remaining > 0) {
                val w = minOf(block.size, remaining)
                out.write(block, 0, w)
                remaining -= w
            }
        }
        // Robolectric: register the file for ContentResolver
        // file:// Uri works via ContentResolver.openInputStream on Robolectric
        val uri = Uri.fromFile(tmp)
        val chunks = AudioChunker.splitIfNeeded(ctx, uri, "audio_26min_test.mp3", durationMs = 26 * 60 * 1000, fileSize = mb(5))
        // Cleanup source temp - chunk files are in cacheDir too
        try {
            assertTrue("26min should chunk into >1 piece when file exists", chunks.size > 1)
        } finally {
            AudioChunker.cleanupChunks(chunks)
            tmp.delete()
        }
    }

    @Test
    fun `splitIfNeeded for 5min WAV 30MB with real file returns chunks due to size`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val tmp = File(ctx.cacheDir, "audio_5min_30mb_test.wav")
        tmp.delete()
        // Minimal WAV header + 2MB data so file exists; fileSize param is 30MB to trigger size gate
        FileOutputStream(tmp).use { out ->
            // 44 byte WAV header stub (not validated in byte path beyond header copy)
            val header = ByteArray(44)
            header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
            out.write(header)
            val block = ByteArray(8192)
            var remaining = 2 * 1024 * 1024
            while (remaining > 0) {
                val w = minOf(block.size, remaining)
                out.write(block, 0, w)
                remaining -= w
            }
        }
        val uri = Uri.fromFile(tmp)
        val chunks = AudioChunker.splitIfNeeded(ctx, uri, "audio_5min_30mb_test.wav", durationMs = 300_000, fileSize = mb(30))
        try {
            assertTrue("5min 30MB WAV should chunk due to size gate", chunks.size > 1)
        } finally {
            AudioChunker.cleanupChunks(chunks)
            tmp.delete()
        }
    }

    @Test
    fun `ViewModel passes fileSize to splitIfNeeded - source check`() {
        val src = File("app/src/main/java/com/example/ui/TranscriptionViewModel.kt").let {
            if (it.exists()) it else File("../app/src/main/java/com/example/ui/TranscriptionViewModel.kt")
        }.readText()
        assertTrue("ViewModel must pass fileInfo.size to splitIfNeeded", src.contains("splitIfNeeded(context, uri, fileInfo.name, retrieverDurationMs, fileInfo.size)"))
    }

    @Test
    fun `AudioChunker source contains size gate in splitIfNeeded`() {
        val src = File("app/src/main/java/com/example/data/AudioChunker.kt").let {
            if (it.exists()) it else File("../app/src/main/java/com/example/data/AudioChunker.kt")
        }.readText()
        assertTrue(src.contains("fileSize != null && fileSize > 20L"))
        assertTrue(src.contains("dur <= MAX_CHUNK_MS && (fileSize == null || fileSize <= 20L"))
        assertTrue(src.contains("coerceAtLeast(MAX_CHUNK_MS"))
    }

    // ---- VAD 2min chunk for silence ----

    @Test
    fun `silence long triggers 2min chunk`() {
        // 5:26 = 326_000ms com silenceRatio 0.4 deve forçar chunk; sem silêncio não chunk
        assertTrue(AudioChunker.isChunkingNeeded(durationMs = 326_000, fileSize = mb(5), silenceRatio = 0.4))
        assertFalse(AudioChunker.isChunkingNeeded(durationMs = 326_000, fileSize = mb(5), silenceRatio = 0.2))
        assertFalse(AudioChunker.isChunkingNeeded(durationMs = 326_000, fileSize = mb(5), silenceRatio = 0.3))
        assertTrue(AudioChunker.isChunkingNeeded(durationMs = 326_000, fileSize = mb(5), silenceRatio = 0.31))
        // VAD 2min vs 5min selection
        assertEquals(2 * 60 * 1000, AudioChunker.getChunkDurationMs(silenceRatio = 0.4))
        assertEquals(5 * 60 * 1000, AudioChunker.getChunkDurationMs(silenceRatio = 0.2))
        assertEquals(2 * 60 * 1000, AudioChunker.getChunkDurationMs(hasLongSilenceGap = true))
    }

    @Test
    fun `hasLongSilenceGap detects gap greater than 5s with short text`() {
        val segsGapLong = listOf(
            com.example.data.api.Segment(id = 0, seek = 0, start = 0.0, end = 10.0, text = "Olá vereadores"),
            com.example.data.api.Segment(id = 1, seek = 100, start = 16.5, end = 20.0, text = "ok")
        )
        assertTrue(AudioChunker.hasLongSilenceGap(segsGapLong))

        val segsGapShort = listOf(
            com.example.data.api.Segment(id = 0, seek = 0, start = 0.0, end = 10.0, text = "Olá vereadores presentes na sessão"),
            com.example.data.api.Segment(id = 1, seek = 100, start = 12.0, end = 20.0, text = "Vereadora A vota sim")
        )
        // gap 2s <5s => false
        assertFalse(AudioChunker.hasLongSilenceGap(segsGapShort))

        val segsGapLongButLongText = listOf(
            com.example.data.api.Segment(id = 0, seek = 0, start = 0.0, end = 10.0, text = "Texto longo com mais de vinte caracteres aqui"),
            com.example.data.api.Segment(id = 1, seek = 100, start = 16.5, end = 20.0, text = "Outro texto longo também com mais de vinte caracteres")
        )
        // gap 6.5s mas ambos textos longos >=20 => false
        assertFalse(AudioChunker.hasLongSilenceGap(segsGapLongButLongText))
    }

    @Test
    fun `isChunkingNeeded via segments gap triggers 2min chunk`() {
        val segs = listOf(
            com.example.data.api.Segment(id = 0, seek = 0, start = 0.0, end = 5.0, text = "sim"),
            com.example.data.api.Segment(id = 1, seek = 50, start = 11.0, end = 15.0, text = "ok")
        )
        assertTrue(AudioChunker.isChunkingNeeded(durationMs = 326_000, fileSize = mb(5), silenceRatio = null, segments = segs))
        assertFalse(AudioChunker.isChunkingNeeded(durationMs = 326_000, fileSize = mb(5), silenceRatio = null, segments = emptyList()))
    }

    @Test
    fun `splitIfNeeded for 5_26 with silence true returns 2min chunks`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val tmp = File(ctx.cacheDir, "audio_5_26_silence_test.mp3")
        tmp.delete()
        FileOutputStream(tmp).use { out ->
            val block = ByteArray(8192) { 0x55.toByte() }
            var remaining = 2 * 1024 * 1024
            while (remaining > 0) {
                val w = minOf(block.size, remaining)
                out.write(block, 0, w)
                remaining -= w
            }
        }
        val uri = Uri.fromFile(tmp)
        // Sem silêncio: 5:26 não chunk
        val noSilence = AudioChunker.splitIfNeeded(ctx, uri, "audio_5_26_silence_test.mp3", durationMs = 326_000, fileSize = mb(5))
        assertTrue("5:26 sem silêncio não deve chunkar", noSilence.isEmpty())
        // Com silenceRatio 0.4: deve chunk em 2min -> 3 chunks (326k/120k = 2.7 -> 3)
        val withSilence = AudioChunker.splitIfNeeded(ctx, uri, "audio_5_26_silence_test.mp3", durationMs = 326_000, fileSize = mb(5), silenceRatio = 0.4)
        try {
            assertTrue("5:26 com silence 0.4 deve chunkar em >1", withSilence.size > 1)
            // 326k com 120k chunks => 3 peças
            assertEquals(3, withSilence.size)
            // Com gap longo também
            val segs = listOf(
                com.example.data.api.Segment(id = 0, seek = 0, start = 0.0, end = 2.0, text = "oi"),
                com.example.data.api.Segment(id = 1, seek = 20, start = 8.5, end = 10.0, text = "ok")
            )
            val gapChunks = AudioChunker.splitIfNeeded(ctx, uri, "audio_5_26_silence_test.mp3", durationMs = 326_000, fileSize = mb(5), silenceRatio = null, hasLongSilenceGap = true, segments = segs)
            assertTrue(gapChunks.size > 1)
            AudioChunker.cleanupChunks(gapChunks)
        } finally {
            AudioChunker.cleanupChunks(withSilence)
            tmp.delete()
        }
    }
}
