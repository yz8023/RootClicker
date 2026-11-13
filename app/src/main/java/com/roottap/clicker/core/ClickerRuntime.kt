package com.roottap.clicker.core

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Root state as the UI sees it. */
enum class RootStatus { UNKNOWN, GRANTED, DENIED }

/** Process wide wiring: one injector, one engine, one log. */
object ClickerRuntime {

    private val _rootStatus = MutableStateFlow(RootStatus.UNKNOWN)
    val rootStatus: StateFlow<RootStatus> = _rootStatus.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private var appContext: Context? = null
    private var injector: InjectorProcess? = null

    lateinit var engine: ClickEngine
        private set

    val sinkDescription: String
        get() = injector?.let { RootTouchSink(it).description } ?: "未连接"

    private var lastVibration = 0L

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        ClickerStore.init(context.applicationContext)
        engine = ClickEngine { ensureSink() }
        engine.onClick = {
            if (ClickerStore.data.value.options.vibrateOnClick) {
                val now = System.currentTimeMillis()
                // Never more than ten buzzes per second, otherwise the motor just hums.
                if (now - lastVibration > 100) {
                    lastVibration = now
                    runCatching {
                        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val manager = context.getSystemService(
                                android.os.VibratorManager::class.java,
                            )
                            manager.defaultVibrator
                        } else {
                            @Suppress("DEPRECATION")
                            context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(
                                android.os.VibrationEffect.createOneShot(
                                    18,
                                    android.os.VibrationEffect.DEFAULT_AMPLITUDE,
                                ),
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(18)
                        }
                    }
                }
            }
        }
    }

    fun log(line: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _log.update { (listOf("$stamp  $line") + it).take(120) }
    }

    /** Asks `su` for a shell; safe to call repeatedly. */
    suspend fun checkRoot(): Boolean = withContext(Dispatchers.IO) {
        val granted = RootShell.hasRoot()
        _rootStatus.value = if (granted) RootStatus.GRANTED else RootStatus.DENIED
        // Remembered so the next cold start knows whether it may jump straight to the overlay.
        runCatching { ClickerStore.updateOptions { it.copy(lastRootOk = granted) } }
        log(if (granted) "root 已授权 · ${RootShell.describeDevice()}" else "root 授权失败")
        granted
    }

    /**
     * Returns a working injection channel. The fast path is the persistent `app_process`
     * injector; when that fails the sink still works through `input tap`.
     */
    suspend fun ensureSink(): TouchSink? = withContext(Dispatchers.IO) {
        val context = appContext ?: return@withContext null
        if (_rootStatus.value != RootStatus.GRANTED) {
            val granted = RootShell.hasRoot()
            _rootStatus.value = if (granted) RootStatus.GRANTED else RootStatus.DENIED
            if (!granted) {
                log("没有 root 权限，已停止")
                return@withContext null
            }
        }
        val existing = injector
        val session = existing ?: InjectorProcess { InjectorDex.prepare(context) }.also { created ->
            created.listener = object : InjectorProcess.Listener {
                override fun onLog(line: String) {
                    if (line.startsWith("ERR")) log("注入器: ${line.removePrefix("ERR").trim()}")
                }

                override fun onDied(reason: String) {
                    log("注入进程退出: $reason")
                }
            }
            injector = created
        }
        if (!session.isAlive) {
            val started = session.ensureStarted()
            log(if (started) "注入进程已启动（app_process）" else "注入进程启动失败，改用 input 兼容模式")
        }
        RootTouchSink(session)
    }

    fun shutdown() {
        engine.stop()
        injector?.stop()
        injector = null
    }

    /** Full display size in pixels, the same space injected coordinates live in. */
    fun screenSize(context: Context): Point {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowManager = context.getSystemService(WindowManager::class.java)
            val bounds = windowManager.currentWindowMetrics.bounds
            Point(bounds.width(), bounds.height())
        } else {
            val windowManager =
                context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            Point(metrics.widthPixels, metrics.heightPixels)
        }
    }
}
