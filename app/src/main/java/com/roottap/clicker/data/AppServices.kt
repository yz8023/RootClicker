package com.roottap.clicker.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Applies the language setting: 0 system, 1 Simplified Chinese, 2 English, 3 Russian. */
object LocaleHelper {
    private fun localeFor(language: Int): Locale? = when (language) {
        1 -> Locale.SIMPLIFIED_CHINESE
        2 -> Locale.ENGLISH
        3 -> Locale.forLanguageTag("ru")
        else -> null
    }

    /** Wraps [context] with the configured locale. */
    fun wrap(context: Context, language: Int): Context {
        val locale = localeFor(language) ?: return context
        // Resources follow the context, but String.format and date formatting read the
        // JVM default.
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}

/**
 * Visibility of the launcher alias.
 * The alias is switched, not the activity: a disabled activity cannot be started at all,
 * and a hidden app still has to open from its other entry points.
 */
object LauncherIcon {
    private fun component(context: Context) =
        ComponentName(context.packageName, "${context.packageName}.MainActivityLauncher")

    /** True when the launcher icon is shown. */
    fun isVisible(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(component(context)) !=
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    /** Shows or hides the launcher icon. */
    fun setVisible(context: Context, visible: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            component(context),
            if (visible) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            // Without this flag the system kills the app the moment the switch is flipped.
            PackageManager.DONT_KILL_APP,
        )
    }
}

/** Writes uncaught exceptions to the app log directory. */
object CrashLogger {
    private const val Directory = "logs"
    private const val MaxFiles = 5

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var enabled = true

    /** Installs the uncaught exception handler. */
    fun install(context: Context, enabled: Boolean) {
        appContext = context.applicationContext
        this.enabled = enabled
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // A failed log write must not replace the crash that is being reported.
            if (this.enabled) runCatching { write(throwable, thread.name) }
            // The previous handler is the one that ends the process and shows the dialog.
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Turns logging on or off. */
    fun setEnabled(value: Boolean) {
        enabled = value
    }

    private fun logDir(): File? {
        val context = appContext ?: return null
        // External first, where a user can pull the file off the phone without root.
        val dir =context.getExternalFilesDir(Directory) ?: File(context.filesDir, Directory)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun write(throwable: Throwable, threadName: String) {
        val dir = logDir() ?: return
        pruneOld(dir)
        // Locale.US keeps the digits ASCII; the default locale may not.
        val time = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val stack = StringWriter()
        PrintWriter(stack).use { throwable.printStackTrace(it) }
        val text = buildString {
            appendLine("Time: $time")
            appendLine("Thread: $threadName")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            append(stack.toString())
        }
        runCatching { File(dir, "crash_$time.log").writeText(text) }
    }

    private fun pruneOld(dir: File) {
        val files = dir.listFiles { file -> file.isFile && file.name.startsWith("crash_") } ?: return
        if (files.size < MaxFiles) return
        // Runs before the new file is written, so one slot is left free for it.
        files.sortedBy { it.lastModified() }.dropLast(MaxFiles - 1).forEach { it.delete() }
    }
}
