package com.roottap.clicker.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.roottap.clicker.core.ClickPoint
import com.roottap.clicker.core.ClickerRuntime
import com.roottap.clicker.core.ClickerStore
import com.roottap.clicker.core.EngineState
import com.roottap.clicker.core.ScreenSampler
import com.roottap.clicker.service.overlay.ControlBarContent
import com.roottap.clicker.service.overlay.OverlayComposeHost
import com.roottap.clicker.service.overlay.OverlayPanelState
import com.roottap.clicker.service.overlay.PanelContent
import com.roottap.clicker.service.overlay.PickerControlsContent
import com.roottap.clicker.service.overlay.ZeroOverlayTheme
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Floating windows: the control bar, the ZeroUI settings panel, the point markers and the
 * full screen point picker. The panels are real Compose views wrapped in the same ZeroUI theme
 * as the app, so the overlay and the app share one design system.
 */
@SuppressLint("ClickableViewAccessibility")
class OverlayController(
    private val context: Context,
    private val actions: Actions,
) {

    interface Actions {
        fun onToggle()
        fun onCloseOverlay()
        fun onPointMoved(point: ClickPoint)
    }

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private val screen = ClickerRuntime.screenSize(context)

    val panelState = OverlayPanelState()

    private var barHost: OverlayComposeHost? = null
    private var barParams: WindowManager.LayoutParams? = null

    private var panelHost: OverlayComposeHost? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private var pickerScrim: View? = null
    private var pickerHost: OverlayComposeHost? = null

    private val markers = mutableListOf<View>()
    private var markerSignature: String? = null

    private val main = Handler(Looper.getMainLooper())

    fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    // ---------------------------------------------------------------- lifecycle

    fun show() {
        if (!canDraw()) {
            toast("请先授予悬浮窗权限")
            return
        }
        attachBar()
        refreshMarkers()
    }

    fun hide() {
        closePanel()
        detachBar()
        clearMarkers()
        closePicker()
    }

    fun onState(state: EngineState) {
        // The bar reads the engine state through Compose; markers only follow a stop.
        if (!state.running) refreshMarkers()
    }

    /**
     * Called on every store change. The panels observe the store themselves, so only the markers
     * need work here - and only when the points really moved, otherwise every slider step would
     * rebuild the marker windows.
     */
    fun onDataChanged() {
        if (ClickerRuntime.engine.isRunning) return
        val data = ClickerStore.data.value
        val signature = buildString {
            append(data.options.showMarkers).append(panelState.markersEditable)
            data.profile.points.forEach { append('|').append(it.id).append(',').append(it.x).append(',').append(it.y) }
            data.profile.points.forEach { if (!it.enabled) append('/').append(it.id) }
        }
        if (signature == markerSignature) return
        markerSignature = signature
        refreshMarkers()
    }

    // ---------------------------------------------------------------- bar

    private fun attachBar() {
        if (barHost != null) return
        val host = OverlayComposeHost(context)
        host.setContent {
            ZeroOverlayTheme {
                val data by ClickerStore.data.collectAsState()
                val state by ClickerRuntime.engine.state.collectAsState()
                CompositionLocalProvider(
                    // The overlay draws at the system density, ignoring the in-app UI scale.
                    LocalDensity provides Density(density, Density(density).fontScale),
                ) {
                    ControlBarContent(
                        running = state.running,
                        clicks = state.clicks,
                        pointCount = data.profile.points.size,
                        modeName = modeName(data.profile.mode),
                        onToggle = { actions.onToggle() },
                        onPanel = { if (panelState.open) closePanel() else openPanel() },
                        onClose = { actions.onCloseOverlay() },
                        onDrag = { dx, dy -> moveBar(dx, dy) },
                    )
                }
            }
        }
        val params = overlayParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            x = dp(10),
            y = screen.y / 4,
            touchable = true,
        )
        runCatching {
            windowManager.addView(host.view, params)
            barHost = host
            barParams = params
        }.onFailure { toast("悬浮窗打开失败：${it.message}") }
    }

    private fun detachBar() {
        barHost?.let { host ->
            runCatching { windowManager.removeView(host.view) }
            host.destroy()
        }
        barHost = null
        barParams = null
    }

    private fun moveBar(dx: Float, dy: Float) {
        val params = barParams ?: return
        val host = barHost ?: return
        params.x += dx.roundToInt()
        params.y += dy.roundToInt()
        runCatching { windowManager.updateViewLayout(host.view, params) }
    }

    // ---------------------------------------------------------------- settings panel

    fun isPanelOpen(): Boolean = panelState.open

    fun togglePanel(): Boolean {
        if (panelState.open) closePanel() else openPanel()
        return panelState.open
    }

    fun openPanel() {
        if (!canDraw()) {
            toast("请先授予悬浮窗权限")
            return
        }
        if (panelState.open) return
        if (barHost == null) attachBar()
        val host = OverlayComposeHost(context)
        host.setContent {
            ZeroOverlayTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(density, Density(density).fontScale),
                ) {
                    PanelContent(
                        state = panelState,
                        onClose = { closePanel() },
                        onPickPoints = { openPicker() },
                        onToast = { toast(it) },
                        onTestTap = { point -> testTap(point) },
                        onGrabColor = { grabColor() },
                        onMarkersEditableChange = { editable -> setMarkersEditable(editable) },
                    )
                }
            }
        }
        val width = min(dp(320), (screen.x * 0.92f).roundToInt())
        val params = overlayParams(
            width = width,
            height = min((screen.y * 0.68f).roundToInt(), dp(720)),
            x = (screen.x - width) / 2,
            y = (screen.y * 0.14f).roundToInt(),
            touchable = true,
        )
        runCatching {
            windowManager.addView(host.view, params)
            panelHost = host
            panelParams = params
        }.onFailure { toast("设置面板打开失败：${it.message}") }
        panelState.open = true
        panelState.detailPointId = null
    }

    fun closePanel() {
        panelHost?.let { host ->
            runCatching { windowManager.removeView(host.view) }
            host.destroy()
        }
        panelHost = null
        panelParams = null
        panelState.open = false
        panelState.detailPointId = null
    }

    // ---------------------------------------------------------------- markers

    fun refreshMarkers() {
        val points = ClickerStore.data.value.profile.points
        if (!ClickerStore.data.value.options.showMarkers) {
            clearMarkers()
            return
        }
        if (markers.size == points.size && markers.isNotEmpty()) {
            // Same points, new positions: move the existing views instead of rebuilding them.
            points.forEachIndexed { index, point ->
                val view = markers[index]
                val params = view.tag as? WindowManager.LayoutParams ?: return@forEachIndexed
                params.x = point.x - dp(13)
                params.y = point.y - dp(13)
                params.flags = markerFlags()
                view.alpha = if (point.enabled) 1f else 0.45f
                runCatching { windowManager.updateViewLayout(view, params) }
            }
            return
        }
        clearMarkers()
        points.forEachIndexed { index, point ->
            val marker = buildMarker(index + 1, point)
            val params = overlayParams(
                width = dp(26),
                height = dp(26),
                x = point.x - dp(13),
                y = point.y - dp(13),
                touchable = panelState.markersEditable,
            )
            marker.tag = params
            runCatching {
                windowManager.addView(marker, params)
                markers += marker
            }
        }
    }

    fun setMarkersEditable(editable: Boolean) {
        panelState.markersEditable = editable
        refreshMarkers()
        toast(if (editable) "拖动圆点调整位置，松手即保存" else "点位已锁定")
    }

    private fun clearMarkers() {
        markers.forEach { runCatching { windowManager.removeView(it) } }
        markers.clear()
        markerSignature = null
    }

    private fun markerFlags(): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!panelState.markersEditable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return flags
    }

    private fun buildMarker(index: Int, point: ClickPoint): View {
        val editable = panelState.markersEditable
        val marker = TextView(context).apply {
            text = index.toString()
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            background = androidx.core.content.ContextCompat.getDrawable(
                context,
                com.roottap.clicker.R.drawable.marker_bg,
            )
            if (!point.enabled) alpha = 0.45f
        }
        if (editable) {
            var startX = 0
            var startY = 0
            var startRawX = 0f
            var startRawY = 0f
            marker.setOnTouchListener { view, event ->
                val params = view.tag as? WindowManager.LayoutParams
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params?.x ?: 0
                        startY = params?.y ?: 0
                        startRawX = event.rawX
                        startRawY = event.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (params != null) {
                            params.x = startX + (event.rawX - startRawX).toInt()
                            params.y = startY + (event.rawY - startRawY).toInt()
                            runCatching { windowManager.updateViewLayout(view, params) }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (params != null &&
                            abs(event.rawX - startRawX) + abs(event.rawY - startRawY) > dp(4)
                        ) {
                            actions.onPointMoved(
                                point.copy(
                                    x = (params.x + dp(13)).coerceAtLeast(0),
                                    y = (params.y + dp(13)).coerceAtLeast(0),
                                ),
                            )
                        }
                    }
                }
                true
            }
        }
        return marker
    }

    // ---------------------------------------------------------------- picker

    fun openPicker() {
        if (!canDraw()) {
            toast("请先授予悬浮窗权限")
            return
        }
        if (pickerScrim != null) {
            closePicker()
            return
        }
        val scrim = View(context)
        scrim.setBackgroundColor(Color.parseColor("#1F000000"))
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        scrim.setOnTouchListener { view, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                val x = event.rawX.toInt().coerceIn(0, screen.x - 1)
                val y = event.rawY.toInt().coerceIn(0, screen.y - 1)
                if (y > dp(90)) {
                    if (panelState.colorMode) {
                        sampleColorAt(x, y)
                    } else {
                        ClickerStore.addPoint(ClickPoint(x = x, y = y))
                        ClickerStore.normalizeTo(screen.x, screen.y)
                        refreshMarkers()
                    }
                }
                view.performClick()
            }
            true
        }
        runCatching {
            windowManager.addView(scrim, params)
            pickerScrim = scrim
            attachPickerControls()
            refreshMarkers()
        }.onFailure { toast("取点窗口打开失败：${it.message}") }
    }

    fun isPickerOpen(): Boolean = pickerScrim != null

    fun closePicker() {
        pickerScrim?.let { runCatching { windowManager.removeView(it) } }
        pickerScrim = null
        pickerHost?.let { host ->
            runCatching { windowManager.removeView(host.view) }
            host.destroy()
        }
        pickerHost = null
        panelState.colorMode = false
        refreshMarkers()
    }

    private fun attachPickerControls() {
        val host = OverlayComposeHost(context)
        host.setContent {
            ZeroOverlayTheme {
                val data by ClickerStore.data.collectAsState()
                CompositionLocalProvider(
                    LocalDensity provides Density(density, Density(density).fontScale),
                ) {
                    PickerControlsContent(
                        count = data.profile.points.size,
                        colorMode = panelState.colorMode,
                        onToggleColor = { panelState.colorMode = !panelState.colorMode },
                        onUndo = {
                            toast(ClickerStore.undo() ?: "没有可撤销的操作")
                            refreshMarkers()
                        },
                        onDone = { closePicker() },
                    )
                }
            }
        }
        val params = overlayParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            x = 0,
            y = dp(34),
            touchable = true,
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }
        runCatching {
            windowManager.addView(host.view, params)
            pickerHost = host
        }.onFailure { toast("取点控制条打开失败：${it.message}") }
    }

    // ---------------------------------------------------------------- actions

    private fun testTap(point: ClickPoint) {
        val press = point.pressMs.takeIf { it > 0 } ?: ClickerStore.data.value.profile.pressMs
        toast("2 秒后在 (${point.x}, ${point.y}) 点一下")
        main.postDelayed({
            ClickerRuntime.engine.testTap(point.x, point.y, press)
        }, 2_000)
    }

    private fun grabColor() {
        toast("正在截屏取色…")
        Thread {
            val profile = ClickerStore.data.value.profile
            val target = profile.points.firstOrNull()
            val x = if (profile.color.x > 0 || profile.color.y > 0) profile.color.x else target?.x ?: (screen.x / 2)
            val y = if (profile.color.x > 0 || profile.color.y > 0) profile.color.y else target?.y ?: (screen.y / 2)
            val pixel = ScreenSampler.sample(x, y)
            main.post {
                if (pixel == null) {
                    toast("取色失败，请确认 root 权限")
                } else {
                    ClickerStore.updateProfile {
                        it.copy(color = it.color.copy(x = x, y = y, color = pixel))
                    }
                    toast("已取色 ${ScreenSampler.toHex(pixel)} @ ($x, $y)")
                }
            }
        }.start()
    }

    private fun sampleColorAt(x: Int, y: Int) {
        Thread {
            val pixel = ScreenSampler.sample(x, y)
            main.post {
                if (pixel == null) {
                    toast("取色失败，请确认 root 权限")
                } else {
                    ClickerStore.updateProfile {
                        it.copy(
                            condition = com.roottap.clicker.core.ConditionKind.COLOR,
                            color = it.color.copy(x = x, y = y, color = pixel),
                        )
                    }
                    toast("已取色 ${ScreenSampler.toHex(pixel)} @ ($x, $y)")
                }
            }
        }.start()
    }

    // ---------------------------------------------------------------- helpers

    private fun modeName(mode: com.roottap.clicker.core.ClickMode): String = when (mode) {
        com.roottap.clicker.core.ClickMode.SEQUENTIAL -> "依次"
        com.roottap.clicker.core.ClickMode.SIMULTANEOUS -> "同时"
        com.roottap.clicker.core.ClickMode.RANDOM -> "随机"
    }

    private fun overlayParams(
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        touchable: Boolean,
    ): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return WindowManager.LayoutParams(width, height, overlayType(), flags, PixelFormat.TRANSLUCENT)
            .apply {
                gravity = Gravity.TOP or Gravity.START
                this.x = x
                this.y = y
            }
    }

    private fun overlayType(): Int =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun dp(value: Int): Int = (value * density).toInt()

    private fun toast(text: String) {
        runCatching { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }
}
