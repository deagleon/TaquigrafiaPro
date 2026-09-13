package com.example.data

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/** Waveform peaks for the Revisão player. Computed once at import, cached, never recomputed. */
object WaveformUtils {

    /** Buckets per audio; ~1.3s each on a 26min session, enough to show pauses. */
    const val WAVEFORM_BUCKETS = 1200

    /** Mono samples kept from the decode; stride keeps memory flat on long audio. */
    private const val TARGET_KEPT_SAMPLES = 240_000

    /**
     * Buckets [samples] into [buckets] peaks normalized to unit max.
     * Silence maps to zeros; short input still fills every bucket.
     */
    fun computePeaks(samples: ShortArray, buckets: Int): List<Float> {
        if (samples.isEmpty() || buckets <= 0) return emptyList()
        var max = 0
        for (s in samples) {
            val a = kotlin.math.abs(s.toInt())
            if (a > max) max = a
        }
        if (max == 0) return List(buckets) { 0f }
        val out = FloatArray(buckets)
        val perBucket = samples.size.toDouble() / buckets
        for (b in 0 until buckets) {
            val from = (b * perBucket).toInt()
            val to = ((b + 1) * perBucket).toInt().coerceAtMost(samples.size)
            var m = 0
            for (i in from until to) {
                val a = kotlin.math.abs(samples[i].toInt())
                if (a > m) m = a
            }
            out[b] = m.toFloat() / max
        }
        return out.toList()
    }

    fun peaksToJson(peaks: List<Float>?): String? {
        if (peaks.isNullOrEmpty()) return null
        return peaks.joinToString(separator = ",", prefix = "[", postfix = "]")
    }

    fun peaksFromJson(json: String?): List<Float>? {
        if (json.isNullOrBlank()) return null
        val body = json.trim().removePrefix("[").removeSuffix("]")
        if (body.isBlank()) return null
        val out = ArrayList<Float>(64)
        for (token in body.split(",")) {
            out.add(token.trim().toFloatOrNull() ?: return null)
        }
        return out.takeIf { it.isNotEmpty() }
    }

    /**
     * Decodes [uri] to mono 16-bit PCM, downmixed and strided to a flat size.
     * Null on any failure: the caller stores no peaks and the player works without a waveform.
     */
    fun decodeToMonoPcm(context: Context, uri: Uri, durationMs: Int?): ShortArray? {
        val extractor = MediaExtractor()
        try {
            try {
                extractor.setDataSource(context, uri, null)
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
            val seconds = ((durationMs ?: 0) / 1000).coerceAtLeast(1)
            val stride = ((sampleRate.toLong() * seconds * channels / TARGET_KEPT_SAMPLES).toInt()).coerceAtLeast(1)

            extractor.selectTrack(track)
            val codec = try {
                MediaCodec.createDecoderByType(mime)
            } catch (_: Exception) {
                return null
            }
            try {
                codec.configure(fmt, null, null, 0)
                codec.start()
                val kept = ArrayList<Short>(TARGET_KEPT_SAMPLES)
                val info = MediaCodec.BufferInfo()
                var inputEos = false
                var outputEos = false
                var frameCount = 0L
                var guard = 0
                while (!outputEos && guard++ < 5_000_000) {
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
                            val shorts = outBuf.order(ByteOrder.nativeOrder()).asShortBuffer()
                            val n = info.size / 2
                            val frame = ShortArray(n)
                            shorts.get(frame)
                            var i = 0
                            while (i + channels <= n) {
                                if (frameCount % stride == 0L) {
                                    var mix = 0
                                    for (c in 0 until channels) mix += frame[i + c]
                                    kept.add((mix / channels).toShort())
                                }
                                frameCount++
                                i += channels
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputEos = true
                    } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER && inputEos) {
                        break
                    }
                }
                if (!outputEos && kept.isEmpty()) return null
                return kept.toShortArray()
            } finally {
                try { codec.stop() } catch (_: Exception) {}
                codec.release()
            }
        } catch (_: Exception) {
            return null
        } finally {
            extractor.release()
        }
    }
}
