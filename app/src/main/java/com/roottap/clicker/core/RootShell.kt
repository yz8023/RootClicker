package com.roottap.clicker.core

import com.roottap.clicker.inject.Injector
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Writer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One root shell session. Every call goes through `su -c`, so Magisk / KernelSU / SuperSU all
 * work; nothing here needs an accessibility service.
 */
object RootShell {

    private const val DEFAULT_TIMEOUT = 12_000L

    /** Runs [command] as root and returns its combined output. */
    fun exec(command: String, timeoutMs: Long = DEFAULT_TIMEOUT): String {
        return try {
            val process = startSu(command)
            val text = process.inputStream.bufferedReader().use { it.readText() }
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroy()
                ""
            } else {
                text
            }
        } catch (t: Throwable) {
            ""
        }
    }

    /** Runs [command] as root and returns the exit code (-1 when the shell could not start). */
    fun execCode(command: String, timeoutMs: Long = DEFAULT_TIMEOUT): Int {
        return try {
            val process = startSu(command)
            process.inputStream.bufferedReader().use { it.readText() }
            if (process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) process.exitValue() else {
                process.destroy()
                -1
            }
        } catch (t: Throwable) {
            -1
        }
    }

    private fun startSu(command: String) = ProcessBuilder("su", "-c", command)
        .redirectErrorStream(true)
        .start()

    /** True when `su` exists and hands out a root shell. */
    fun hasRoot(): Boolean {
        val output = exec("id", 6_000)
        return output.contains("uid=0")
    }

    /** Device model and Android version, used in the status card. */
    fun describeDevice(): String {
        val text = exec("getprop ro.product.model; getprop ro.build.version.release", 6_000)
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return text.joinToString(" · ")
    }
}

/**
 * Keeps one `app_process` injector alive under root.
 *
 * Starting it once and feeding it commands over a pipe is what makes millisecond intervals
 * possible: forking `input tap` per click costs 150 ms and up.
 */
class InjectorProcess(private val dexPathProvider: () -> String?) {

    interface Listener {
        fun onLog(line: String)
        fun onDied(reason: String)
    }

    var listener: Listener? = null

    private var process: Process? = null
    private var writer: Writer? = null
    private val ready = AtomicBoolean(false)
    private val alive = AtomicBoolean(false)
    private var lastError: String? = null

    val isAlive: Boolean get() = alive.get() && ready.get()
    val error: String? get() = lastError

    /** Starts the injector when it is not running yet. Blocking, call it off the main thread. */
    fun ensureStarted(): Boolean {
        if (isAlive) return true
        stop()
        val dexPath = dexPathProvider()
        if (dexPath == null) {
            lastError = "找不到注入器 dex"
            return false
        }
        val command = buildString {
            append("CLASSPATH='")
            append(dexPath)
            append("' app_process /system/bin ")
            append(Injector::class.java.name)
        }
        return try {
            val started = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            process = started
            writer = OutputStreamWriter(started.outputStream)
            alive.set(true)
            val firstLine = AtomicBoolean(false)
            Thread({ pump(started, firstLine) }, "injector-reader").apply { isDaemon = true }.start()
            // Wait for READY so the caller knows whether injection is really available.
            val deadline = System.currentTimeMillis() + 8_000
            while (System.currentTimeMillis() < deadline) {
                if (ready.get()) return true
                if (!alive.get()) break
                Thread.sleep(30)
            }
            if (!isAlive) {
                lastError = lastError ?: "无法启动注入进程（请确认已授予 root 权限）"
                listener?.onLog("injector failed: $lastError")
                stop()
                false
            } else {
                true
            }
        } catch (t: Throwable) {
            lastError = t.message ?: t.javaClass.simpleName
            listener?.onLog("injector start error: $lastError")
            false
        }
    }

    private fun pump(started: Process, sawReady: AtomicBoolean) {
        try {
            BufferedReader(InputStreamReader(started.inputStream)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line == "READY") {
                        ready.set(true)
                        sawReady.set(true)
                    } else if (line.startsWith("ERR")) {
                        lastError = line.removePrefix("ERR").trim()
                    }
                    listener?.onLog(line)
                }
            }
        } catch (t: Throwable) {
            lastError = t.message ?: t.javaClass.simpleName
        } finally {
            val wasReady = ready.getAndSet(false)
            alive.set(false)
            if (wasReady) {
                listener?.onDied(lastError ?: "注入进程已退出")
            }
        }
    }

    /** Fire and forget: the injector acknowledges on its own thread. */
    fun send(command: String): Boolean {
        if (!isAlive) return false
        val stream = writer ?: return false
        return try {
            synchronized(this) {
                stream.write(command)
                stream.write("\n")
                stream.flush()
            }
            true
        } catch (t: IOException) {
            alive.set(false)
            false
        }
    }

    fun stop() {
        ready.set(false)
        alive.set(false)
        runCatching { writer?.close() }
        writer = null
        val current = process
        process = null
        if (current != null) {
            runCatching {
                current.outputStream.write("Q\n".toByteArray())
                current.outputStream.flush()
            }
            runCatching { current.destroy() }
        }
    }
}

/** Where injected touches go. Swapped between the fast injector and the `input` fallback. */
interface TouchSink {
    fun tap(x: Int, y: Int, durationMs: Int): Boolean

    fun multiTap(xs: IntArray, ys: IntArray, durationMs: Int): Boolean

    fun stroke(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): Boolean

    val description: String
}

/** Uses the persistent root injector; falls back to the `input` binary when it is unavailable. */
class RootTouchSink(private val injector: InjectorProcess) : TouchSink {

    override val description: String
        get() = if (injector.isAlive) "app_process 直注" else "input 兼容模式"

    override fun tap(x: Int, y: Int, durationMs: Int): Boolean {
        if (injector.isAlive && injector.send("T $x $y $durationMs")) return true
        return fallbackTap(x, y, durationMs)
    }

    override fun multiTap(xs: IntArray, ys: IntArray, durationMs: Int): Boolean {
        if (injector.isAlive) {
            val cmd = buildString {
                append("M ")
                append(durationMs)
                for (i in xs.indices) {
                    append(' ').append(xs[i]).append(' ').append(ys[i])
                }
            }
            if (injector.send(cmd)) return true
        }
        xs.indices.forEach { fallbackTap(xs[it], ys[it], durationMs) }
        return true
    }

    override fun stroke(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): Boolean {
        if (injector.isAlive && injector.send("S $x1 $y1 $x2 $y2 $durationMs")) return true
        val shown = durationMs.coerceIn(1, 60_000)
        return RootShell.execCode("input swipe $x1 $y1 $x2 $y2 $shown", 20_000L) == 0
    }

    private fun fallbackTap(x: Int, y: Int, durationMs: Int): Boolean {
        val ms = durationMs.coerceAtLeast(1)
        val code = if (ms <= 120) {
            RootShell.execCode("input tap $x $y", 15_000L)
        } else {
            RootShell.execCode("input swipe $x $y $x $y $ms", 15_000L + ms)
        }
        return code == 0
    }
}
