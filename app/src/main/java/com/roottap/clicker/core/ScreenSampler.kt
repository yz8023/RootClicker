package com.roottap.clicker.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.abs

/**
 * Screen reading through root. `screencap` is slow (100–300 ms), so it is only used for the
 * colour condition, never inside the click loop itself.
 */
object ScreenSampler {

    /** Full screen shot, or null when root denies it. */
    fun capture(): Bitmap? {
        return try {
            val process = ProcessBuilder("su", "-c", "screencap -p")
                .redirectErrorStream(false)
                .start()
            val bytes = process.inputStream.use { it.readBytes() }
            process.waitFor()
            if (bytes.size < 64) return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (t: Throwable) {
            null
        }
    }

    /** Colour at one pixel, or null when the screen could not be read. */
    fun sample(x: Int, y: Int): Int? {
        val bitmap = capture() ?: return null
        return try {
            if (x < 0 || y < 0 || x >= bitmap.width || y >= bitmap.height) return null
            // Average a small patch so a single antialiased pixel cannot flip the condition.
            average(bitmap, x, y, 2)
        } finally {
            bitmap.recycle()
        }
    }

    /** Average colour of a square patch around ([x], [y]). */
    fun average(bitmap: Bitmap, x: Int, y: Int, radius: Int): Int {
        var r = 0
        var g = 0
        var b = 0
        var count = 0
        for (px in (x - radius)..(x + radius)) {
            if (px < 0 || px >= bitmap.width) continue
            for (py in (y - radius)..(y + radius)) {
                if (py < 0 || py >= bitmap.height) continue
                val color = bitmap.getPixel(px, py)
                r += (color shr 16) and 0xFF
                g += (color shr 8) and 0xFF
                b += color and 0xFF
                count++
            }
        }
        if (count == 0) return 0
        return (0xFF shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
    }

    /** True when [pixel] is within [tolerance] of [target] on every channel. */
    fun matches(pixel: Int, target: Int, tolerance: Int): Boolean {
        val dr = abs(((pixel shr 16) and 0xFF) - ((target shr 16) and 0xFF))
        val dg = abs(((pixel shr 8) and 0xFF) - ((target shr 8) and 0xFF))
        val db = abs((pixel and 0xFF) - (target and 0xFF))
        val limit = tolerance.coerceIn(0, 255)
        return dr <= limit && dg <= limit && db <= limit
    }

    fun toHex(color: Int): String =
        String.format("#%02X%02X%02X", (color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF)
}
