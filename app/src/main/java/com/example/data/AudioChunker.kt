package com.example.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
object AudioChunker {
    private const val TAG = "AudioChunker"
    private const val DEFAULT_CHUNK_MS = 5 * 60 * 1000 // 5 min
    private const val MAX_CHUNK_MS = 8 * 60 * 1000 // 8 min soft limit

    fun isChunkingNeeded(durationMs: Int?, fileSize: Long): Boolean {
        return (durationMs != null && durationMs > MAX_CHUNK_MS) || fileSize > 20L * 1024 * 1024
    }

    data class Chunk(val file: File, val startMs: Long, val durationMs: Long)

    suspend fun splitIfNeeded(context: Context, uri: Uri, fileName: String, durationMs: Int?, fileSize: Long? = null): List<Chunk> {
        var dur = durationMs
        if (dur == null) {
            dur = try {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(context, uri)
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.toInt()
                } finally { try { r.release() } catch (_: Exception) {} }
            } catch (_: Exception) { null }
        }
        // Se dur ainda null mas arquivo >20MB, estima dur via bytes e força chunk por bytes
        if (dur == null) {
            if (fileSize != null && fileSize > 20L * 1024 * 1024) {
                // estima 7.5 chars/sec ~ 1KB per ~2 sec para fallback
                val estDurMs = ((fileSize / 1024.0) * 2000).toInt().coerceAtLeast(MAX_CHUNK_MS + 1000)
                return splitAudio(context, uri, fileName, estDurMs, DEFAULT_CHUNK_MS)
            }
            return emptyList()
        }
        if (dur <= MAX_CHUNK_MS && (fileSize == null || fileSize <= 20L * 1024 * 1024)) return emptyList()
        // Se dur <=8min mas size >20MB, ainda chunk por bytes (ex: WAV 5min 30MB)
        val effectiveDur = dur.coerceAtLeast(MAX_CHUNK_MS + 1000)
        return splitAudio(context, uri, fileName, effectiveDur, DEFAULT_CHUNK_MS)
    }

    fun splitAudio(context: Context, uri: Uri, fileName: String, totalDurationMs: Int, chunkDurationMs: Int = DEFAULT_CHUNK_MS): List<Chunk> {
        // Tenta via MediaExtractor/Muxer (funciona bem para m4a/aac/mp4/ogg)
        val viaExtractor = trySplitViaExtractor(context, uri, fileName, totalDurationMs, chunkDurationMs)
        if (viaExtractor.isNotEmpty()) return viaExtractor
        // Fallback: fatiamento por bytes (mp3/wav)
        return trySplitByBytes(context, uri, fileName, totalDurationMs, chunkDurationMs)
    }

    private fun trySplitViaExtractor(context: Context, uri: Uri, fileName: String, totalDurationMs: Int, chunkDurationMs: Int): List<Chunk> {
        val chunks = mutableListOf<Chunk>()
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var pfd: ParcelFileDescriptor? = null
        try {
            extractor = MediaExtractor()
            pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
            extractor.setDataSource(pfd.fileDescriptor)
            if (extractor.trackCount == 0) return emptyList()
            var audioTrackIdx = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) { audioTrackIdx = i; audioFormat = f; break }
            }
            if (audioTrackIdx == -1 || audioFormat == null) return emptyList()
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: ""
            // MediaMuxer não suporta audio/mpeg (mp3) direto com MPEG_4
            if (mime == "audio/mpeg") return emptyList()

            extractor.selectTrack(audioTrackIdx)
            val totalDurationUs = totalDurationMs * 1000L
            val chunkUs = chunkDurationMs * 1000L
            var chunkIndex = 0
            var chunkStartUs = 0L
            var currentMuxer: MediaMuxer? = null
            var muxerTrackIdx = -1
            var chunkFile: File? = null
            var chunkStartMs = 0L

            val buffer = ByteBuffer.allocate(256 * 1024)
            var sampleTime: Long
            var sawInputEOS = false

            fun startNewChunk(startUs: Long) {
                try { currentMuxer?.stop(); currentMuxer?.release() } catch (_: Exception) {}
                val f = File(context.cacheDir, "chunk_${System.currentTimeMillis()}_${chunkIndex}_${fileName.substringBeforeLast(".")}.m4a")
                chunkFile = f
                chunkStartMs = startUs / 1000
                currentMuxer = MediaMuxer(f.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also { mux ->
                    muxerTrackIdx = mux.addTrack(audioFormat)
                    mux.start()
                }
                chunkIndex++
            }

            startNewChunk(0)
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            while (!sawInputEOS) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) { sawInputEOS = true; break }
                sampleTime = extractor.sampleTime
                if (sampleTime == -1L) { extractor.advance(); continue }

                // Se cruzou limite do chunk, fecha e abre novo
                if (sampleTime - chunkStartUs >= chunkUs && chunkFile != null) {
                    // Fecha chunk atual
                    try { currentMuxer?.stop(); currentMuxer?.release() } catch (_: Exception) {}
                    if (chunkFile != null && chunkFile!!.length() > 1024) {
                        chunks.add(Chunk(chunkFile!!, chunkStartMs, (sampleTime - chunkStartUs) / 1000))
                    } else { chunkFile?.delete() }
                    chunkStartUs = sampleTime
                    // Novo muxer
                    val f = File(context.cacheDir, "chunk_${System.currentTimeMillis()}_${chunkIndex}_${fileName.substringBeforeLast(".")}.m4a")
                    chunkFile = f
                    chunkStartMs = sampleTime / 1000
                    currentMuxer = MediaMuxer(f.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also { mux ->
                        muxerTrackIdx = mux.addTrack(audioFormat)
                        mux.start()
                    }
                    chunkIndex++
                }

                val info = android.media.MediaCodec.BufferInfo().apply {
                    offset = 0; size = sampleSize; presentationTimeUs = sampleTime
                    flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                }
                currentMuxer?.writeSampleData(muxerTrackIdx, buffer, info)
                extractor.advance()
            }
            try { currentMuxer?.stop(); currentMuxer?.release() } catch (_: Exception) {}
            if (chunkFile != null && chunkFile!!.length() > 1024) {
                val lastDuration = (totalDurationMs - chunkStartMs).coerceAtLeast(1000)
                chunks.add(Chunk(chunkFile!!, chunkStartMs, lastDuration.toLong()))
            } else { chunkFile?.delete() }

            // Se falhou ou gerou só 1 chunk pequeno, descarta chunking
            if (chunks.size <= 1) {
                chunks.forEach { it.file.delete() }
                return emptyList()
            }
            Log.d(TAG, "splitViaExtractor total=${totalDurationMs}ms chunks=${chunks.size} mime=$mime")
            return chunks
        } catch (e: Exception) {
            Log.w(TAG, "splitViaExtractor failed: ${e.message}", e)
            return emptyList()
        } finally {
            try { pfd?.close() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    private fun trySplitByBytes(context: Context, uri: Uri, fileName: String, totalDurationMs: Int, chunkDurationMs: Int): List<Chunk> {
        return try {
            val inputFile = File(context.cacheDir, "tmp_src_${System.currentTimeMillis()}_$fileName")
            context.contentResolver.openInputStream(uri)?.use { ins ->
                FileOutputStream(inputFile).use { outs -> ins.copyTo(outs) }
            } ?: return emptyList()
            if (!inputFile.exists() || inputFile.length() < 1024) { inputFile.delete(); return emptyList() }

            val totalBytes = inputFile.length()
            val bytesPerMs = totalBytes.toDouble() / totalDurationMs.coerceAtLeast(1)
            val chunkBytesApprox = (chunkDurationMs * bytesPerMs).toLong().coerceAtLeast(512 * 1024)

            // Para WAV, preserva header
            val isWav = fileName.lowercase().endsWith(".wav")
            val headerSize = if (isWav) 44 else 0
            val dataSize = totalBytes - headerSize
            if (dataSize <= chunkBytesApprox) { inputFile.delete(); return emptyList() }

            val chunks = mutableListOf<Chunk>()
            var offset: Long = headerSize.toLong()
            var chunkIdx = 0
            var chunkStartMs = 0L
            val headerBytes = if (isWav) {
                FileInputStream(inputFile).use { it.readNBytes(headerSize) } ?: ByteArray(0)
            } else ByteArray(0)

            while (offset < totalBytes) {
                val remaining = totalBytes - offset
                var curChunkDataBytes = chunkBytesApprox.coerceAtMost(remaining)
                // Para mp3, alinha em frame sync (0xFF 0xFB/E)
                if (!isWav && curChunkDataBytes < remaining) {
                    curChunkDataBytes = findMp3FrameSync(inputFile, offset + curChunkDataBytes, totalBytes) - offset
                    if (curChunkDataBytes <= 0) curChunkDataBytes = chunkBytesApprox
                }
                val chunkFile = File(context.cacheDir, "chunk_${System.currentTimeMillis()}_${chunkIdx}_${fileName}")
                FileOutputStream(chunkFile).use { outs ->
                    if (isWav) {
                        // header com tamanhos atualizados
                        val wavHeader = updateWavHeader(headerBytes, curChunkDataBytes.toInt())
                        outs.write(wavHeader)
                    }
                    FileInputStream(inputFile).use { ins ->
                        ins.skip(offset)
                        val buf = ByteArray(8192)
                        var toCopy = curChunkDataBytes
                        while (toCopy > 0) {
                            val read = ins.read(buf, 0, minOf(buf.size.toLong(), toCopy).toInt())
                            if (read <= 0) break
                            outs.write(buf, 0, read)
                            toCopy -= read
                        }
                    }
                }
                val chunkDurMs = ((curChunkDataBytes / bytesPerMs).toLong()).coerceAtLeast(1000)
                chunks.add(Chunk(chunkFile, chunkStartMs, chunkDurMs))
                offset += curChunkDataBytes
                chunkStartMs += chunkDurMs
                chunkIdx++
                if (chunks.size > 20) break // safety
            }
            inputFile.delete()
            if (chunks.size <= 1) { chunks.forEach { it.file.delete() }; return emptyList() }
            Log.d(TAG, "splitByBytes totalBytes=$totalBytes chunks=${chunks.size} isWav=$isWav")
            chunks
        } catch (e: Exception) {
            Log.w(TAG, "splitByBytes failed: ${e.message}", e)
            emptyList()
        }
    }

    private fun findMp3FrameSync(file: File, approxPos: Long, totalBytes: Long): Long {
        val searchWindow = 8192
        val start = approxPos.coerceIn(0, totalBytes - 1)
        val end = minOf(start + searchWindow, totalBytes - 1)
        FileInputStream(file).use { ins ->
            ins.skip(start)
            val buf = ByteArray((end - start).toInt().coerceAtLeast(0))
            val read = ins.read(buf)
            if (read <= 0) return approxPos
            for (i in 0 until read - 1) {
                val b1 = buf[i].toInt() and 0xFF
                val b2 = buf[i + 1].toInt() and 0xFF
                if (b1 == 0xFF && (b2 and 0xE0) == 0xE0) { // frame sync 11 bits
                    return start + i
                }
            }
        }
        return approxPos
    }

    private fun updateWavHeader(originalHeader: ByteArray, newDataSize: Int): ByteArray {
        if (originalHeader.size < 44) return originalHeader
        val h = originalHeader.copyOf()
        val fileSize = newDataSize + 36
        // ChunkSize at 4
        h[4] = (fileSize and 0xFF).toByte(); h[5] = ((fileSize shr 8) and 0xFF).toByte()
        h[6] = ((fileSize shr 16) and 0xFF).toByte(); h[7] = ((fileSize shr 24) and 0xFF).toByte()
        // Subchunk2Size at 40
        h[40] = (newDataSize and 0xFF).toByte(); h[41] = ((newDataSize shr 8) and 0xFF).toByte()
        h[42] = ((newDataSize shr 16) and 0xFF).toByte(); h[43] = ((newDataSize shr 24) and 0xFF).toByte()
        return h
    }

    fun cleanupChunks(chunks: List<Chunk>) {
        chunks.forEach { try { it.file.delete() } catch (_: Exception) {} }
    }
}
