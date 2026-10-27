package com.actuagt.shared.media

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

// Minimal JPEG/EXIF reader: recovers the capture date (DateTimeOriginal) and
// the GPS coordinates without platform APIs, so it runs in commonMain.
object ExifMetadataExtractor {

    fun extract(jpeg: ByteArray): PhotoMetadata {
        val tiff = findExif(jpeg) ?: return PhotoMetadata(null, null, null)
        return tiff.readMetadata()
    }

    private fun findExif(jpeg: ByteArray): TiffReader? {
        if (jpeg.size < 4 || jpeg[0] != 0xFF.toByte() || jpeg[1] != 0xD8.toByte()) return null
        var pos = 2
        while (pos + 1 < jpeg.size) {
            if (jpeg[pos] != 0xFF.toByte()) return null
            val marker = jpeg[pos + 1].toInt() and 0xFF
            if (marker == 0xD9 || marker == 0xDA) return null
            if (marker == 0xD8 || marker in 0xD0..0xD7) {
                pos += 2
                continue
            }
            if (pos + 3 >= jpeg.size) return null
            val length = ((jpeg[pos + 2].toInt() and 0xFF) shl 8) or (jpeg[pos + 3].toInt() and 0xFF)
            if (length < 2 || pos + 1 + length >= jpeg.size) return null
            if (marker == 0xE1 && length >= 8 && hasExifHeader(jpeg, pos + 4)) {
                return TiffReader(jpeg, pos + 10)
            }
            pos += 2 + length
        }
        return null
    }

    private fun hasExifHeader(jpeg: ByteArray, pos: Int): Boolean {
        if (pos + 6 > jpeg.size) return false
        return jpeg[pos] == 'E'.code.toByte() &&
            jpeg[pos + 1] == 'x'.code.toByte() &&
            jpeg[pos + 2] == 'i'.code.toByte() &&
            jpeg[pos + 3] == 'f'.code.toByte() &&
            jpeg[pos + 4] == 0.toByte() &&
            jpeg[pos + 5] == 0.toByte()
    }

    private class TiffReader(val bytes: ByteArray, val base: Int) {
        val littleEndian: Boolean = bytes.getOrNull(base) == 'I'.code.toByte()

        fun inRange(pos: Int, len: Int): Boolean =
            pos >= 0 && len >= 0 && base + pos + len <= bytes.size

        fun u16(pos: Int): Int {
            val a = bytes[base + pos].toInt() and 0xFF
            val b = bytes[base + pos + 1].toInt() and 0xFF
            return if (littleEndian) a or (b shl 8) else (a shl 8) or b
        }

        fun u32(pos: Int): Long {
            val a = bytes[base + pos].toLong() and 0xFF
            val b = bytes[base + pos + 1].toLong() and 0xFF
            val c = bytes[base + pos + 2].toLong() and 0xFF
            val d = bytes[base + pos + 3].toLong() and 0xFF
            return if (littleEndian) {
                a or (b shl 8) or (c shl 16) or (d shl 24)
            } else {
                (a shl 24) or (b shl 16) or (c shl 8) or d
            }
        }

        fun ascii(pos: Int, count: Int): String? {
            if (!inRange(pos, count)) return null
            return bytes.decodeToString(base + pos, base + pos + count).trimEnd('\u0000')
        }

        fun rational(pos: Int): Double {
            if (!inRange(pos, 8)) return 0.0
            val numerator = u32(pos).toDouble()
            val denominator = u32(pos + 4).toDouble()
            return if (denominator == 0.0) 0.0 else numerator / denominator
        }

        // Value bytes of an IFD entry: inline when they fit, otherwise an offset.
        fun entryValuePos(entryPos: Int, type: Int, count: Long): Int? {
            val unitSize = when (type) {
                1, 2, 7 -> 1
                3 -> 2
                4, 9 -> 4
                5, 10 -> 8
                else -> return null
            }
            val total = unitSize * count
            if (total > Int.MAX_VALUE || !inRange(entryPos, 12)) return null
            if (total <= 4) return entryPos + 8
            val offset = u32(entryPos + 8)
            if (offset > Int.MAX_VALUE || !inRange(offset.toInt(), total.toInt())) return null
            return offset.toInt()
        }

        fun findEntry(ifdPos: Int, tag: Int): Pair<Int, Int> {
            if (!inRange(ifdPos, 2)) return -1 to -1
            val count = u16(ifdPos)
            if (!inRange(ifdPos + 2, count * 12)) return -1 to -1
            for (i in 0 until count) {
                val entryPos = ifdPos + 2 + i * 12
                if (u16(entryPos) == tag) return entryPos to u16(entryPos + 2)
            }
            return -1 to -1
        }

        fun readMetadata(): PhotoMetadata {
            if (!inRange(0, 8)) return PhotoMetadata(null, null, null)
            val ifd0 = u32(4).let { if (it > Int.MAX_VALUE) return PhotoMetadata(null, null, null) else it.toInt() }

            var capturedAt: Long? = null
            val (exifEntry, exifType) = findEntry(ifd0, 0x8769)
            if (exifEntry >= 0 && exifType == 4) {
                entryValuePos(exifEntry, exifType, 1)?.let { u32(it).toInt() }?.let { exifIfd ->
                    val (dateEntry, dateType) = findEntry(exifIfd, 0x9003)
                    if (dateEntry >= 0 && dateType == 2) {
                        entryValuePos(dateEntry, dateType, u32(dateEntry + 4))?.let { datePos ->
                            capturedAt = ascii(datePos, u32(dateEntry + 4).toInt())?.let(::parseExifDate)
                        }
                    }
                }
            }

            var latitude: Double? = null
            var longitude: Double? = null
            val (gpsEntry, gpsType) = findEntry(ifd0, 0x8825)
            if (gpsEntry >= 0 && gpsType == 4) {
                entryValuePos(gpsEntry, gpsType, 1)?.let { u32(it).toInt() }?.let { gpsIfd ->
                    latitude = readCoordinate(gpsIfd, 0x0001, 0x0002, positiveRef = 'N')
                    longitude = readCoordinate(gpsIfd, 0x0003, 0x0004, positiveRef = 'E')
                }
            }
            return PhotoMetadata(capturedAt, latitude, longitude)
        }

        private fun readCoordinate(ifd: Int, refTag: Int, valueTag: Int, positiveRef: Char): Double? {
            val (refEntry, refType) = findEntry(ifd, refTag)
            val (valueEntry, valueType) = findEntry(ifd, valueTag)
            if (refEntry < 0 || valueEntry < 0 || refType != 2 || valueType != 5) return null
            val refCount = u32(refEntry + 4)
            val refPos = entryValuePos(refEntry, refType, refCount) ?: return null
            val ref = ascii(refPos, refCount.toInt())?.firstOrNull() ?: return null
            val valuePos = entryValuePos(valueEntry, valueType, 3) ?: return null
            val degrees = rational(valuePos)
            val minutes = rational(valuePos + 8)
            val seconds = rational(valuePos + 16)
            val decimal = degrees + minutes / 60.0 + seconds / 3600.0
            return if (ref == positiveRef) decimal else -decimal
        }

        // EXIF dates look like "2026:10:27 12:34:56"; interpreted as UTC.
        private fun parseExifDate(raw: String): Long? {
            return try {
                val year = raw.substring(0, 4).toInt()
                val month = raw.substring(5, 7).toInt()
                val day = raw.substring(8, 10).toInt()
                val hour = raw.substring(11, 13).toInt()
                val minute = raw.substring(14, 16).toInt()
                val second = raw.substring(17, 19).toInt()
                LocalDateTime(year, month, day, hour, minute, second)
                    .toInstant(TimeZone.UTC)
                    .toEpochMilliseconds()
            } catch (_: Exception) {
                null
            }
        }
    }
}
