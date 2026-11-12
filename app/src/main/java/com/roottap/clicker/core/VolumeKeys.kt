package com.roottap.clicker.core

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Watches the hardware volume keys through root, so the floating panel can be slid in and out
 * without touching the screen. A normal app cannot see those keys while another app is focused;
 * reading the kernel input device can.
 */
object VolumeKeys {

    private val maxPattern = Regex("max\\s+(\\d+)")

    /** Device node that reports KEY_VOLUMEUP, usually the gpio-keys device. */
    fun findKeyDevice(): String? {
        val text = RootShell.exec("getevent -pl", 12_000)
        if (text.isBlank()) return null
        var path: String? = null
        var candidate: String? = null
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.startsWith("add device")) {
                path = line.substringAfterLast(": ").trim()
            } else if (line.startsWith("KEY (") && line.contains("KEY_VOLUMEUP")) {
                candidate = path
            }
        }
        return candidate
    }

    /** One getevent reader; calls back on key down with a small debounce. */
    class Watcher(
        private val onVolumeUp: () -> Unit,
        private val onVolumeDown: () -> Unit,
    ) {
        private var process: Process? = null
        private val running = AtomicBoolean(false)
        private var lastTrigger = 0L
        private var device: String? = null

        val isRunning: Boolean get() = running.get()
        val devicePath: String? get() = device

        fun start(): Boolean {
            if (running.get()) return true
            val path = findKeyDevice() ?: return false
            device = path
            return try {
                val started = ProcessBuilder("su", "-c", "getevent -lt $path")
                    .redirectErrorStream(true)
                    .start()
                process = started
                running.set(true)
                Thread({ pump(started) }, "volume-keys").apply { isDaemon = true }.start()
                true
            } catch (t: Throwable) {
                false
            }
        }

        fun stop() {
            running.set(false)
            runCatching { process?.destroy() }
            process = null
        }

        private fun pump(started: Process) {
            try {
                BufferedReader(InputStreamReader(started.inputStream)).use { reader ->
                    while (running.get()) {
                        val line = reader.readLine() ?: break
                        if (!line.contains("EV_KEY")) continue
                        val upper = line.contains("KEY_VOLUMEUP")
                        val lower = line.contains("KEY_VOLUMEDOWN")
                        if (!upper && !lower) continue
                        // Only react to the press, not to the release or the auto repeat.
                        if (!line.trimEnd().endsWith("DOWN")) continue
                        val now = System.currentTimeMillis()
                        if (now - lastTrigger < 350) continue
                        lastTrigger = now
                        if (upper) onVolumeUp() else onVolumeDown()
                    }
                }
            } catch (t: Throwable) {
                // The process was killed or root went away; the service restarts it on demand.
            } finally {
                running.set(false)
            }
        }
    }
}
