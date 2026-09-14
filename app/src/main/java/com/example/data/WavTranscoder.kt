package com.example.data

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Conversão local para WAV 16 kHz mono (ver ADR-0004): modelos que recusam
 * qualquer conteiner fora de RIFF/WAVE recebem o áudio já normalizado.
 * O fatiamento existente entende WAV com header, então a conversão acontece
 * uma vez por arquivo, antes do base64/fatiamento.
 */
object WavTranscoder {

    const val TARGET_SAMPLE_RATE = 16000

    fun isWavFile(name: String): Boolean =
        name.substringAfterLast(".", "").lowercase() == "wav"

    /** Higiene do nome no upload: `;:*?"<>|` e controles viram `_`, sem sobra antes da extensão. */
    fun sanitizeFileName(name: String): String {
        val dot = name.lastIndexOf(".")
        val hasExt = dot > 0 && dot < name.length - 1 && name.length - dot - 1 <= 5 && "/" !in name.substring(dot + 1)
        val base = if (hasExt) name.substring(0, dot) else name
        var clean = base.replace(Regex("[;:*?\"<>|\\p{Cntrl}]"), "_").trim().trimEnd('_', '.', '-', ' ')
        if (clean.isEmpty()) clean = "audio"
        return if (hasExt) "$clean.${name.substring(dot + 1)}" else clean
    }

    internal fun downmixToMono(frames: ShortArray, channels: Int): ShortArray {
        if (channels <= 1) return frames.copyOf()
        val out = ArrayList<Short>(frames.size / channels + 1)
        var i = 0
        while (i + channels <= frames.size) {
            var mix = 0
            for (c in 0 until channels) mix += frames[i + c]
            out.add((mix / channels).toShort())
            i += channels
        }
        return out.toShortArray()
    }

    /**
     * Reamostragem linear com a mesma matemática no caminho em lote e no
     * streaming: posições além da última amostra replicam seu valor.
     */
    internal class StreamingResampler(private val inRate: Int, private val outRate: Int) {
        private var nextOut = 0L
        private var srcSeen = 0L
        private var carry: Short = 0
        private var hasCarry = false
        private val emitted = ArrayList<Short>()

        fun push(chunk: ShortArray) {
            if (chunk.isEmpty()) return
            val win = if (hasCarry) shortArrayOf(carry) + chunk else chunk
            val base = srcSeen - if (hasCarry) 1 else 0
            while (true) {
                val p = nextOut * inRate
                val g = p / outRate
                val r = (p % outRate).toInt()
                val li = (g - base).toInt()
                if (li < 0 || li + 1 >= win.size) break
                val a = win[li].toInt()
                val b = win[li + 1].toInt()
                emitted.add(((a * (outRate - r) + b * r) / outRate).toShort())
                nextOut++
            }
            carry = win.last()
            hasCarry = true
            srcSeen += chunk.size
        }

        fun flush() {
            if (!hasCarry) return
            while (nextOut * inRate < srcSeen * outRate) {
                emitted.add(carry)
                nextOut++
            }
        }

        fun drain(): ShortArray {
            val out = emitted.toShortArray()
            emitted.clear()
            return out
        }
    }

    internal fun resampleMono(samples: ShortArray, inRate: Int, outRate: Int): ShortArray {
        if (samples.isEmpty() || inRate <= 0 || outRate <= 0) return ShortArray(0)
        if (inRate == outRate) return samples.copyOf()
        val r = StreamingResampler(inRate, outRate)
        r.push(samples)
        r.flush()
        return r.drain()
    }

    internal fun wavHeader(dataBytes: Int, sampleRate: Int = TARGET_SAMPLE_RATE): ByteArray {
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataBytes)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataBytes)
        }.array()
    }

    internal fun shortsToLeBytes(samples: ShortArray): ByteArray {
        return ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            for (s in samples) putShort(s)
        }.array()
    }

    internal fun wavBytes(mono16k: ShortArray): ByteArray {
        val data = shortsToLeBytes(mono16k)
        return wavHeader(data.size) + data
    }

    /**
     * Decodifica [srcUri] por completo, reduz para mono, reamostra para 16 kHz
     * e grava WAV no cache. Nulo em qualquer falha: o chamador decide o erro.
     */
    fun transcodeToWav16kMono(context: Context, srcUri: Uri): File? {
        val dst = File(context.cacheDir, "wav16k_${System.currentTimeMillis()}.wav")
        val extractor = MediaExtractor()
        try {
            try {
                extractor.setDataSource(context, srcUri, null)
            } catch (_: Exception) {
                return null
            }
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = try { f.getString(MediaFormat.KEY_MIME) } catch (_: Exception) { null }
                if (mime?.startsWith("audio/") == true) {
                    track = i
                    format = f
                    break
                }
            }
            val fmt = format ?: return null
            if (track < 0) return null
            val mime = try { fmt.getString(MediaFormat.KEY_MIME) } catch (_: Exception) { null } ?: return null
            val channels = if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                try { fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1) } catch (_: Exception) { 1 }
            } else 1
            val sampleRate = if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                try { fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) } catch (_: Exception) { 44100 }
            } else 44100

            extractor.selectTrack(track)
            val codec = try {
                MediaCodec.createDecoderByType(mime)
            } catch (_: Exception) {
                return null
            }
            try {
                codec.configure(fmt, null, null, 0)
                codec.start()
                val pcm = ByteArrayOutputStream()
                val resampler = StreamingResampler(sampleRate, TARGET_SAMPLE_RATE)
                val info = MediaCodec.BufferInfo()
                var inputEos = false
                var outputEos = false
                var guard = 0
                while (!outputEos && guard++ < 20_000_000) {
                    if (!inputEos) {
                        val inIdx = codec.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val inBuf = codec.getInputBuffer(inIdx) ?: break
                            val n = extractor.readSampleData(inBuf, 0)
                            if (n < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEos = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                    if (outIdx >= 0) {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && info.size > 0) {
                            val shorts = outBuf.order(java.nio.ByteOrder.nativeOrder()).asShortBuffer()
                            val n = info.size / 2
                            val frame = ShortArray(n)
                            shorts.get(frame)
                            resampler.push(downmixToMono(frame, channels))
                            pcm.write(shortsToLeBytes(resampler.drain()))
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputEos = true
                    } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER && inputEos) {
                        break
                    }
                }
                resampler.flush()
                pcm.write(shortsToLeBytes(resampler.drain()))
                val data = pcm.toByteArray()
                if (data.isEmpty()) {
                    try { dst.delete() } catch (_: Exception) {}
                    return null
                }
                try {
                    java.io.FileOutputStream(dst).use {
                        it.write(wavHeader(data.size))
                        it.write(data)
                    }
                } catch (_: Exception) {
                    try { dst.delete() } catch (_: Exception) {}
                    return null
                }
                return dst
            } finally {
                try { codec.stop() } catch (_: Exception) {}
                codec.release()
            }
        } catch (_: Exception) {
            try { dst.delete() } catch (_: Exception) {}
            return null
        } finally {
            extractor.release()
        }
    }
}
