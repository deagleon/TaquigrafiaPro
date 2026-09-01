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
}
