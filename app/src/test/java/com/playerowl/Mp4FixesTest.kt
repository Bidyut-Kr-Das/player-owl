package com.playerowl

import androidx.media3.common.C
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class Mp4FixesTest {
    private fun hex(s: String) = s.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    // The 'mp4a' entry and 'esds' box of a real download: 2 channels, 44100 Hz, no AAC config.
    private val mp4a = hex(
        "00 00 00 50 6d 70 34 61 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 02 00 10 00 00 00 00 ac 44 00 00",
    )
    private val esdsWithout = hex(
        "00 00 00 2c 65 73 64 73 00 00 00 00 03 80 80 80 1b 00 02 00 04 80 80 80 0d 40 15 00 00 00 00 00 00 00 00 " +
            "00 00 00 06 80 80 80 01 02",
    )

    @Test fun `missing config becomes AAC-LC 44100 Hz stereo in the SL block's place`() {
        val data = mp4a + esdsWithout
        assertTrue(fixMissingAacConfig(data, data.size))
        assertArrayEquals(hex("05 80 80 02 12 10"), data.copyOfRange(data.size - 6, data.size))
    }

    @Test fun `box with a config stays unchanged`() {
        val esdsWith = hex(
            "00 00 00 2c 65 73 64 73 00 00 00 00 03 80 80 80 1b 00 02 00 04 80 80 80 0d 40 15 00 00 00 00 00 00 00 00 " +
                "00 00 00 05 80 80 80 02 12 10",
        )
        val data = mp4a + esdsWith
        assertFalse(fixMissingAacConfig(data, data.size))
        assertArrayEquals(mp4a + esdsWith, data)
    }

    /** Seek points every 4 s, like an 'mfra' index; the floor entry comes first, as Media3's maps do. */
    private fun indexMap(durationUs: Long, seekable: Boolean = true) = object : SeekMap {
        private val times = longArrayOf(0, 4_000_000, 8_000_000)
        override fun isSeekable() = seekable
        override fun getDurationUs() = durationUs
        override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
            val i = times.indexOfLast { it <= timeUs }.coerceAtLeast(0)
            val first = SeekPoint(times[i], i.toLong())
            return if (i < times.lastIndex) SeekMap.SeekPoints(first, SeekPoint(times[i + 1], i + 1L))
            else SeekMap.SeekPoints(first)
        }
    }

    @Test fun `unknown duration becomes last index entry plus one fragment`() {
        assertEquals(12_000_000L, withEstimatedDuration(indexMap(C.TIME_UNSET)).durationUs)
    }

    @Test fun `known duration and unseekable maps stay as they are`() {
        val known = indexMap(9_000_000)
        assertSame(known, withEstimatedDuration(known))
        val unseekable = indexMap(C.TIME_UNSET, seekable = false)
        assertSame(unseekable, withEstimatedDuration(unseekable))
    }

    @Test fun `cut-off box is left alone`() {
        val data = mp4a + esdsWithout
        assertFalse(fixMissingAacConfig(data, data.size - 1))
    }
}
