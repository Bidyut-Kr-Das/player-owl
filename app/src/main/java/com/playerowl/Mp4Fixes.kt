package com.playerowl

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.ForwardingSeekMap
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor

private val AAC_RATES = intArrayOf(96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350)

/**
 * Some MP4 encoders leave out the AAC config in 'esds'. Media3 then reads the next block as the config and fails.
 * When the next block is a 6-byte SL config, write an AAC-LC config there instead, built from the 'mp4a' entry.
 * Same length, so no offset in the file moves. Returns true when it patched something.
 * shortcut: covers only the 6-byte SL form and assumes AAC-LC; HE-AAC still plays, at core quality.
 */
fun fixMissingAacConfig(data: ByteArray, end: Int): Boolean {
    var patched = false
    var i = 40 // The 'mp4a' entry sits 40 bytes before the 'esds' type.
    while (i + 4 <= end) {
        if (data[i] == 'e'.code.toByte() && data[i + 1] == 's'.code.toByte() &&
            data[i + 2] == 'd'.code.toByte() && data[i + 3] == 's'.code.toByte()
        ) patched = patchEsds(data, end, i) || patched
        i++
    }
    return patched
}

private fun patchEsds(d: ByteArray, end: Int, esds: Int): Boolean {
    val entry = esds - 40 // Start of the 36-byte 'mp4a' box; the 'esds' size field follows it.
    if (String(d, entry + 4, 4, Charsets.US_ASCII) != "mp4a" || u16(d, entry + 16) != 0) return false // Version 0.
    val channels = u16(d, entry + 24)
    val rateIndex = AAC_RATES.indexOf(u16(d, entry + 32))
    if (channels !in 1..7 || rateIndex < 0) return false

    var p = esds + 8 // Skip the type and the version and flags.
    if (p >= end || d[p] != 0x03.toByte()) return false
    p = skipSize(d, end, p + 1) ?: return false
    if (p + 3 > end) return false
    val flags = d[p + 2].toInt()
    p += 3
    if (flags and 0x80 != 0) p += 2
    if (flags and 0x40 != 0) p += 1 + (if (p < end) d[p].toInt() and 0xFF else 0)
    if (flags and 0x20 != 0) p += 2
    if (p >= end || d[p] != 0x04.toByte()) return false
    val configStart = skipSize(d, end, p + 1) ?: return false
    if (configStart >= end || d[configStart] != 0x40.toByte()) return false // MPEG-4 audio, so AAC.
    val next = configStart + 13
    val sl = byteArrayOf(0x06, 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x01)
    if (next + 6 > end || (0 until 5).any { d[next + it] != sl[it] }) return false

    val config = (2 shl 11) or (rateIndex shl 7) or (channels shl 3) // Object type 2 is AAC-LC.
    byteArrayOf(0x05, 0x80.toByte(), 0x80.toByte(), 0x02, (config shr 8).toByte(), config.toByte())
        .copyInto(d, next)
    return true
}

/** Skips an MPEG-4 descriptor size of up to 4 bytes. */
private fun skipSize(d: ByteArray, end: Int, start: Int): Int? {
    var p = start
    repeat(4) {
        if (p >= end) return null
        if (d[p++].toInt() and 0x80 == 0) return p
    }
    return null
}

private fun u16(d: ByteArray, at: Int) = ((d[at].toInt() and 0xFF) shl 8) or (d[at + 1].toInt() and 0xFF)

/**
 * Fragmented MP4 files may state no duration but still carry an 'mfra' seek index. The last index entry, plus one
 * fragment, gives the duration, so the seek bar and resume work. Returns [map] when it already knows its duration.
 */
@OptIn(UnstableApi::class)
fun withEstimatedDuration(map: SeekMap): SeekMap {
    if (!map.isSeekable || map.durationUs != C.TIME_UNSET) return map
    val last = map.getSeekPoints(Long.MAX_VALUE / 2).first.timeUs
    if (last <= 0) return map
    val previous = map.getSeekPoints(last - 1).first.timeUs
    val duration = last + (last - previous).coerceAtLeast(0)
    return object : ForwardingSeekMap(map) {
        override fun getDurationUs() = duration
    }
}

/** Applies [fixMissingAacConfig] and [withEstimatedDuration] to the MP4 extractors. Other extractors pass through. */
@OptIn(UnstableApi::class)
class Mp4FixFactory(private val inner: ExtractorsFactory) : ExtractorsFactory by inner {
    override fun createExtractors() = wrap(inner.createExtractors())
    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>) =
        wrap(inner.createExtractors(uri, responseHeaders))

    private fun wrap(extractors: Array<Extractor>) = extractors.map {
        if (it is Mp4Extractor || it is FragmentedMp4Extractor) Fixing(it) else it
    }.toTypedArray()

    private class Fixing(private val inner: Extractor) : Extractor by inner {
        override fun init(output: ExtractorOutput) = inner.init(object : ExtractorOutput by output {
            override fun seekMap(seekMap: SeekMap) = output.seekMap(withEstimatedDuration(seekMap))
        })

        override fun read(input: ExtractorInput, seekPosition: PositionHolder) = inner.read(FixingInput(input), seekPosition)
    }

    private class FixingInput(private val inner: ExtractorInput) : ExtractorInput by inner {
        // The extractors read each box in one call of this form, so the whole 'stsd' box is in the array.
        override fun readFully(target: ByteArray, offset: Int, length: Int) {
            inner.readFully(target, offset, length)
            fixMissingAacConfig(target, offset + length)
        }
    }
}
