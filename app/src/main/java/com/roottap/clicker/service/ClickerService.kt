package com.roottap.clicker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.roottap.clicker.MainActivity
import com.roottap.clicker.R
import com.roottap.clicker.core.ClickPoint
import com.roottap.clicker.core.ClickerRuntime
import com.roottap.clicker.core.ClickerStore
import com.roottap.clicker.core.EngineState
import com.roottap.clicker.core.ScriptPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Keeps the running clicker alive while the user works in other apps and owns the floating
 * controls. Everything is driven by root; the service itself only holds state.
 */
class ClickerService : Service() {

    companion object {
        private const val ChannelId = "clicker"
        private const val NotificationId = 2101
        const val ActionToggle = "com.roottap.clicker.TOGGLE"
        const val ActionStop = "com.roottap.clicker.STOP"
        const val ActionShowOverlay = "com.roottap.clicker.OVERLAY"
        const val ActionPick = "com.roottap.clicker.PICK"

        private val _running = MutableStateFlow(false)

        /** True while the foreground service is alive. */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, ClickerService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ClickerService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlay: OverlayController? = null

    override fun onCreate() {
        super.onCreate()
        ClickerRuntime.init(applicationContext)
        createChannel()
        startForeground(NotificationId, buildNotification(ClickerRuntime.engine.state.value))
        _running.value = true
        overlay = OverlayController(this, object : OverlayController.Actions {
            override fun onToggle() = toggleClicking()

            override fun onCloseOverlay() {
                overlay?.hide()
                ClickerStore.updateOptions { it.copy(overlayEnabled = false) }
                toast("悬浮窗已关闭，可在应用内重新打开")
            }

            override fun onPointMoved(point: ClickPoint) {
                ClickerStore.updatePoint(point)
            }
        })
        scope.launch {
            ClickerRuntime.engine.state.collect { state ->
                overlay?.onState(state)
                notify(buildNotification(state))
            }
        }
        scope.launch {
            // Keeps the floating panel in sync with points, templates and timing changes.
            ClickerStore.data.collect { overlay?.onDataChanged() }
        }
        if (ClickerStore.data.value.options.overlayEnabled) {
            overlay?.show()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ActionToggle -> toggleClicking()
            ActionStop -> stopClicking()
            ActionShowOverlay -> overlay?.show()
            ActionPick -> {
                overlay?.show()
                overlay?.openPicker()
            }
            else -> if (ClickerStore.data.value.options.overlayEnabled) overlay?.show()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        _running.value = false
        ClickerRuntime.engine.stop()
        ScriptPlayer.stop()
        overlay?.hide()
        overlay = null
        scope.cancel()
        super.onDestroy()
    }

    private fun toggleClicking() {
        if (ClickerRuntime.engine.isRunning) {
            stopClicking()
            return
        }
        val profile = ClickerStore.data.value.profile
        if (profile.points.isEmpty()) {
            toast("还没有点位，请先取点")
            return
        }
        val size = ClickerRuntime.screenSize(this)
        if (profile.baseWidth == 0 || profile.baseHeight == 0) {
            ClickerStore.normalizeTo(size.x, size.y)
        }
        ClickerRuntime.engine.startProfile({ ClickerStore.data.value.profile }, size.x, size.y)
    }

    private fun stopClicking() {
        ClickerRuntime.engine.stop("已停止")
        ScriptPlayer.stop()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            ChannelId,
            "连点器运行状态",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "显示连点器的运行状态和开关"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(state: EngineState): Notification {
        val toggleIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ClickerService::class.java).setAction(ActionToggle),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openApp = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = when {
            state.running -> "运行中 · 已点击 ${state.clicks} 次 · ${state.cycles} 轮"
            state.message != null -> state.message
            else -> "待机中"
        }
        return NotificationCompat.Builder(this, ChannelId)
            .setSmallIcon(R.drawable.ic_stat_click)
            .setContentTitle("Root连点器")
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(state.running)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                0,
                if (state.running) "停止" else "开始",
                toggleIntent,
            )
            .build()
    }

    private fun notify(notification: Notification) {
        runCatching {
            getSystemService(NotificationManager::class.java).notify(NotificationId, notification)
        }
    }

    private fun toast(text: String) {
        runCatching { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
    }
}
