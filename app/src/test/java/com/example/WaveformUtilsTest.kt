package com.example

import com.example.data.WaveformUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformUtilsTest {

    @Test fun `silence maps to all zeros`() {
        val silent = ShortArray(10_000)
        val peaks = WaveformUtils.computePeaks(silent, 100)
        assertEquals(100, peaks.size)
        assertTrue(peaks.all { it == 0f })
    }

    @Test fun `peaks normalize to unit max and follow loudness`() {
        val samples = ShortArray(10_000) { i ->
            if (i < 5000) (i % 100 * 100).toShort() else (10_000 + i % 100 * 200).toShort()
        }
        val peaks = WaveformUtils.computePeaks(samples, 10)
        assertEquals(10, peaks.size)
        assertEquals(1f, peaks.max(), 0.001f)
        val quiet = peaks.take(5).average()
        val loud = peaks.drop(5).average()
        assertTrue("loud half $loud should exceed quiet half $quiet", loud > quiet * 1.5)
    }

    @Test fun `short input still fills every bucket`() {
        val peaks = WaveformUtils.computePeaks(shortArrayOf(1000, -2000, 3000), 8)
        assertEquals(8, peaks.size)
        assertEquals(1f, peaks.max(), 0.001f)
    }

    @Test fun `empty input or no buckets gives empty peaks`() {
        assertTrue(WaveformUtils.computePeaks(ShortArray(0), 100).isEmpty())
        assertTrue(WaveformUtils.computePeaks(ShortArray(100), 0).isEmpty())
    }

    @Test fun `peaks survive a json round trip`() {
        val peaks = listOf(0f, 0.125f, 0.5f, 1f)
        val json = WaveformUtils.peaksToJson(peaks)
        assertNotNull(json)
        val back = WaveformUtils.peaksFromJson(json)
        assertNotNull(back)
        assertEquals(4, back!!.size)
        back.forEachIndexed { i, v -> assertEquals(peaks[i], v, 0.001f) }
    }

    @Test fun `peaksFromJson rejects garbage`() {
        assertNull(WaveformUtils.peaksFromJson(null))
        assertNull(WaveformUtils.peaksFromJson("  "))
        assertNull(WaveformUtils.peaksFromJson("not json"))
    }
}
