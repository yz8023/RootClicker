package com.roottap.clicker.core

import android.content.Context
import com.roottap.clicker.BuildConfig
import java.io.File
import java.util.zip.ZipFile

/**
 * `app_process` only reads `classes.dex` from a classpath entry, so a multi dex APK cannot be
 * used directly. This finds the dex that really defines the injector main class, copies it out
 * of the APK once, and hands the path to the root process.
 */
object InjectorDex {

    private const val Descriptor = "Lcom/roottap/clicker/inject/Injector;"
    private const val ClassName = "com.roottap.clicker.inject.Injector"
    private val fileName = "injector-${BuildConfig.VERSION_CODE}.dex"

    @Volatile
    private var cached: String? = null

    /** Absolute path of a single dex file that defines [ClassName], or null. */
    fun prepare(context: Context): String? {
        cached?.let { path -> if (File(path).length() > 0L) return path }
        val target = File(context.cacheDir, fileName)
        if (target.length() > 0L) {
            cached = target.absolutePath
            return target.absolutePath
        }
        val candidates = mutableListOf<ByteArray>()
        try {
            ZipFile(File(context.packageCodePath)).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.name.startsWith("classes") || !entry.name.endsWith(".dex")) continue
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    // Cheap filter first: other dex files only *reference* the class.
                    if (containsAscii(bytes, Descriptor)) candidates += bytes
                }
            }
        } catch (t: Throwable) {
            return null
        }
        if (candidates.isEmpty()) return null
        // Only one candidate actually defines the class; ask the runtime which one.
        val probeDir = context.cacheDir
        for (bytes in candidates) {
            if (definesClass(probeDir, bytes, ClassName)) {
                target.writeBytes(bytes)
                // Keep the temporary copies from piling up.
                context.cacheDir.listFiles { file -> file.name.startsWith("injector-probe") }
                    ?.forEach { it.delete() }
                cached = target.absolutePath
                return target.absolutePath
            }
        }
        return null
    }

    fun invalidate(context: Context) {
        cached = null
        runCatching { File(context.cacheDir, fileName).delete() }
    }

    private fun definesClass(dir: File, bytes: ByteArray, className: String): Boolean {
        val probe = File.createTempFile("injector-probe", ".dex", dir)
        return try {
            probe.writeBytes(bytes)
            @Suppress("DEPRECATION")
            val dex = dalvik.system.DexFile(probe.absolutePath)
            val iterator = dex.entries()
            var found = false
            while (iterator.hasMoreElements()) {
                if (iterator.nextElement() == className) {
                    found = true
                    break
                }
            }
            runCatching { dex.close() }
            found
        } catch (t: Throwable) {
            false
        } finally {
            runCatching { probe.delete() }
        }
    }

    private fun containsAscii(haystack: ByteArray, needle: String): Boolean {
        val pattern = needle.toByteArray(Charsets.US_ASCII)
        if (pattern.isEmpty() || haystack.size < pattern.size) return false
        val last = haystack.size - pattern.size
        var index = 0
        while (index <= last) {
            if (haystack[index] == pattern[0]) {
                var matched = true
                for (offset in 1 until pattern.size) {
                    if (haystack[index + offset] != pattern[offset]) {
                        matched = false
                        break
                    }
                }
                if (matched) return true
            }
            index++
        }
        return false
    }
}
