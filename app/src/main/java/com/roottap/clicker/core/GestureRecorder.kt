package com.roottap.clicker.core

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Records real finger gestures by reading the touchscreen device with root.
 *
 * `getevent` gives raw multi touch events; they are converted into single finger gestures
 * (tap, long press, drag) with normalized coordinates, which is exactly what the injector can
 * replay. No accessibility service and no screen recording permission involved.
 */
object GestureRecorder {

    /** A device able to report multi touch positions. */
    data class TouchDevice(
        val path: String,
        val name: String,
        val maxX: Int,
        val maxY: Int,
    )

    private val maxPattern = Regex("max\\s+(\\d+)")

    /** Finds the touchscreen (or touchpad) event device. Blocking. */
    fun findTouchDevice(): TouchDevice? {
        val text = RootShell.exec("getevent -pl", 12_000)
        if (text.isBlank()) return null
        val devices = mutableListOf<TouchDevice>()
        var path: String? = null
        var name = ""
        var maxX = 0
        var maxY = 0
        var hasX = false
        var hasY = false

        fun flush() {
            val current = path
            if (current != null && hasX && hasY && maxX > 0 && maxY > 0) {
                devices += TouchDevice(current, name, maxX, maxY)
            }
        }

        for (raw in text.lines()) {
            val line = raw.trim()
            when {
                line.startsWith("add device") -> {
                    flush()
                    path = line.substringAfterLast(": ").trim()
                    name = ""
                    maxX = 0
                    maxY = 0
                    hasX = false
                    hasY = false
                }
                line.startsWith("name:") -> name = line.removePrefix("name:").trim().trim('"')
                line.startsWith("ABS_MT_POSITION_X") -> {
                    maxPattern.find(line)?.groupValues?.get(1)?.toIntOrNull()?.let {
                        maxX = it
                        hasX = true
                    }
                }
                line.startsWith("ABS_MT_POSITION_Y") -> {
                    maxPattern.find(line)?.groupValues?.get(1)?.toIntOrNull()?.let {
                        maxY = it
                        hasY = true
                    }
                }
            }
        }
        flush()
        if (devices.isEmpty()) return null
        // Prefer a device whose name looks like a touchscreen; fall back to the largest one.
        val named = devices.firstOrNull { device ->
            val lower = device.name.lowercase()
            listOf("touch", "ts", "tp", "ft", "synaptics", "goodix", "focal").any { lower.contains(it) }
        }
        return named ?: devices.maxByOrNull { it.maxX.toLong() * it.maxY }
    }

    /** Live recording session. Feed [onAction] from any thread. */
    class Session(
        private val device: TouchDevice,
        private val onAction: (RecordedAction) -> Unit,
        private val onError: (String) -> Unit,
    ) {
        private var process: Process? = null
        private val running = AtomicBoolean(false)
        private var gestures = 0

        val recordedCount: Int get() = gestures

        fun start(): Boolean {
            if (running.get()) return true
            return try {
                val started = ProcessBuilder("su", "-c", "getevent -lt ${device.path}")
                    .redirectErrorStream(true)
                    .start()
                process = started
                running.set(true)
                Thread({ pump(started) }, "gesture-recorder").apply { isDaemon = true }.start()
                true
            } catch (t: Throwable) {
                onError(t.message ?: "无法读取触摸设备")
                false
            }
        }

        fun stop() {
            running.set(false)
            val current = process
            process = null
            runCatching { current?.destroy() }
        }

        private fun pump(started: Process) {
            var fingerDown = false
            var downTimeSec = 0.0
            var lastUpSec = 0.0
            var startX = 0.0
            var startY = 0.0
            var currentX = -1
            var currentY = -1
            var pendingX: Int? = null
            var pendingY: Int? = null
            var trackingId = -1
            var buttonDown = false

            try {
                BufferedReader(InputStreamReader(started.inputStream)).use { reader ->
                    while (running.get()) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank() || line.startsWith("/dev/input")) continue
                        val close = line.indexOf(']')
                        if (close <= 0 || !line.startsWith("[")) continue
                        val timeSec = line.substring(1, close).trim().toDoubleOrNull() ?: continue
                        val parts = line.substring(close + 1).trim().split(Regex("\\s+"))
                        if (parts.size < 3) continue
                        val type = parts[0]
                        val code = parts[1]
                        val value = parts[2]

                        when {
                            type == "EV_ABS" && code == "ABS_MT_POSITION_X" ->
                                pendingX = parseRaw(value)
                            type == "EV_ABS" && code == "ABS_MT_POSITION_Y" ->
                                pendingY = parseRaw(value)
                            type == "EV_ABS" && code == "ABS_MT_TRACKING_ID" ->
                                trackingId = parseRaw(value)
                            type == "EV_KEY" && (code == "BTN_TOUCH" || code == "BTN_MOUSE") ->
                                buttonDown = value == "DOWN"
                            type == "EV_SYN" && code == "SYN_REPORT" -> {
                                pendingX?.let { currentX = it }
                                pendingY?.let { currentY = it }
                                pendingX = null
                                pendingY = null
                                val present = trackingId >= 0 || buttonDown
                                if (present && currentX >= 0 && currentY >= 0) {
                                    if (!fingerDown) {
                                        fingerDown = true
                                        downTimeSec = timeSec
                                        startX = currentX.toDouble()
                                        startY = currentY.toDouble()
                                    }
                                } else if (!present && fingerDown) {
                                    fingerDown = false
                                    val endX = if (currentX >= 0) currentX.toDouble() else startX
                                    val endY = if (currentY >= 0) currentY.toDouble() else startY
                                    val durationMs = ((timeSec - downTimeSec) * 1000).toInt()
                                    val delayMs = ((downTimeSec - lastUpSec) * 1000).toInt()
                                    lastUpSec = timeSec
                                    if (durationMs in 1..60_000) {
                                        gestures++
                                        onAction(
                                            RecordedAction(
                                                x1 = (startX / device.maxX).toFloat().coerceIn(0f, 1f),
                                                y1 = (startY / device.maxY).toFloat().coerceIn(0f, 1f),
                                                x2 = (endX / device.maxX).toFloat().coerceIn(0f, 1f),
                                                y2 = (endY / device.maxY).toFloat().coerceIn(0f, 1f),
                                                delayMs = delayMs.coerceIn(0, 120_000),
                                                durationMs = durationMs.coerceIn(1, 60_000),
                                            ),
                                        )
                                    }
                                    currentX = -1
                                    currentY = -1
                                    trackingId = -1
                                    buttonDown = false
                                }
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                if (running.get()) onError(t.message ?: "录制中断")
            } finally {
                running.set(false)
            }
        }

        /** `getevent` prints hexadecimal values; ff.. means -1. */
        private fun parseRaw(text: String): Int {
            val parsed = text.toLongOrNull(16) ?: return -1
            val value = parsed.toInt()
            return if (value == -1 || text.length > 4) {
                if (parsed > Int.MAX_VALUE) -1 else value
            } else {
                value
            }
        }
    }
}
