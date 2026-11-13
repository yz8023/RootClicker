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
import com.roottap.clicker.service.overlay.PointBubbleContent
import com.roottap.clicker.service.overlay.ZeroOverlayTheme
import com.roottap.clicker.service.overlay.modeName
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Floating windows.
 *
 * The whole point of this controller is to stay out of the way: one small bar, points created by
 * tapping the screen directly, and a point edited by tapping its own dot. Every panel is a real
 * Compose view wrapped in the same ZeroUI theme as the app.
 */
@SuppressLint("ClickableViewAccessibility")
class OverlayController(
    private val context: Context,
    private val actions: Actions,
) {

    interface Actions {
        fun onToggle()
        fun onOpenApp()
        fun onCloseOverlay()
        fun onPointMoved(point: ClickPoint)
    }

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private val screen = ClickerRuntime.screenSize(context)
    private val main = Handler(Looper.getMainLooper())

    val panelState = OverlayPanelState()

    private var barHost: OverlayComposeHost? = null
    private var barParams: WindowManager.LayoutParams? = null

    private var panelHost: OverlayComposeHost? = null

    /** Transparent full screen layer that turns a tap into a point while picking. */
    private var pickerView: View? = null

    private var bubbleHost: OverlayComposeHost? = null
    private var pickingStartCount = 0

    /** First tap of a swipe that is still waiting for its end point. */
    private var swipeAnchor: android.graphics.Point? = null
    private var anchorMarker: View? = null
    private var linesView: com.roottap.clicker.service.overlay.SwipeLinesView? = null

    private val markers = mutableListOf<View>()
    private var markerSignature: String? = null

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
        finishPicking(silent = true)
        closeBubble()
        closePanel()
        detachBar()
        clearMarkers()
    }

    fun onState(state: EngineState) {
        if (!state.running) refreshMarkers()
    }

    /** Called on every store change; markers follow the points, panels observe the store themselves. */
    fun onDataChanged() {
        val data = ClickerStore.data.value
        val signature = buildString {
            append(data.options.showMarkers).append(panelState.picking)
            data.profile.points.forEach {
                append('|').append(it.id).append(',').append(it.kind)
                append(',').append(it.x).append(',').append(it.y)
                append(',').append(it.x2).append(',').append(it.y2)
            }
            data.profile.points.forEach { if (!it.enabled) append('/').append(it.id) }
        }
        if (signature == markerSignature) return
        markerSignature = signature
        refreshMarkers()
    }

    // ---------------------------------------------------------------- volume keys

    /** Volume up: slide the panel in, slide it out again, or finish picking. */
    fun onVolumeUp() {
        if (panelState.picking) {
            finishPicking()
            return
        }
        if (panelState.open) closePanel() else openPanel()
    }

    /** Volume down: close the panel, or finish picking. */
    fun onVolumeDown() {
        if (panelState.picking) {
            finishPicking()
            return
        }
        if (panelState.open) closePanel() else closeBubble()
    }

    // ---------------------------------------------------------------- bar

    private fun attachBar() {
        if (barHost != null) {
            barHost?.view?.visibility = View.VISIBLE
            return
        }
        val host = OverlayComposeHost(context)
        host.setContent {
            ZeroOverlayTheme {
                val data by ClickerStore.data.collectAsState()
                val state by ClickerRuntime.engine.state.collectAsState()
                val undoCount by ClickerStore.undoCount.collectAsState()
                CompositionLocalProvider(
                    // The overlay draws at the system density, ignoring the in-app UI scale.
                    LocalDensity provides Density(density, Density(density).fontScale),
                ) {
                    ControlBarContent(
                        picking = panelState.picking,
                        swipePicking = panelState.swipePicking,
                        colorMode = panelState.colorMode,
                        running = state.running,
                        clicks = state.clicks,
                        pointCount = data.profile.points.size,
                        modeName = modeName(data.profile.mode),
                        undoCount = undoCount,
                        onToggle = { actions.onToggle() },
                        onPickTap = { startPicking(swipe = false) },
                        onPickSwipe = { startPicking(swipe = true) },
                        onUndo = {
                            toast(ClickerStore.undo() ?: "没有可撤销的操作")
                            refreshMarkers()
                        },
                        onPanel = { if (panelState.open) closePanel() else openPanel() },
                        onToggleColor = { panelState.colorMode = !panelState.colorMode },
                        onFinishPicking = { finishPicking() },
                        onDrag = { dx, dy -> moveBar(dx, dy) },
                        onDragEnd = {
                            val params = barParams
                            if (params != null) {
                                ClickerStore.updateOptions { it.copy(barX = params.x, barY = params.y) }
                            }
                        },
                    )
                }
            }
        }
        val saved = ClickerStore.data.value.options
        val params = overlayParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            // The bar comes back exactly where the user left it.
            x = if (saved.barX >= 0) saved.barX else dp(10),
            y = if (saved.barY >= 0) saved.barY else screen.y / 4,
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

    /** Moves the bar to the top of the window stack, so it stays clickable over the picker layer. */
    private fun raiseBar() {
        val host = barHost ?: return
        val params = barParams ?: return
        runCatching {
            windowManager.removeView(host.view)
            windowManager.addView(host.view, params)
        }
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
        if (panelState.picking) finishPicking(silent = true)
        closeBubble()
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
                        onPickTap = { startPicking(swipe = false) },
                        onPickSwipe = { startPicking(swipe = true) },
                        onToast = { toast(it) },
                        onTestTap = { point -> testTap(point) },
                        onGrabColor = { grabColor() },
                        onOpenApp = { actions.onOpenApp() },
                        onHideOverlay = {
                            closePanel()
                            actions.onCloseOverlay()
                        },
                    )
                }
            }
        }
        val width = min(dp(320), (screen.x * 0.92f).roundToInt())
        val params = overlayParams(
            width = width,
            height = min((screen.y * 0.74f).roundToInt(), dp(760)),
            x = (screen.x - width) / 2,
            y = (screen.y * 0.10f).roundToInt(),
        )
        runCatching {
            windowManager.addView(host.view, params)
            panelHost = host
        }.onFailure { toast("设置面板打开失败：${it.message}") }
        panelState.open = true
    }

    fun closePanel() {
        panelHost?.let { host ->
            runCatching { windowManager.removeView(host.view) }
            host.destroy()
        }
        panelHost = null
        panelState.open = false
    }

    // ---------------------------------------------------------------- picking

    fun isPicking(): Boolean = panelState.picking

    /**
     * Creating a point is one tap on the bar and then taps on the real screen: no dimmed overlay,
     * no second control bar. The floating bar steps aside while the user taps.
     */
    fun startPicking(swipe: Boolean = false) {
        if (!canDraw()) {
            toast("请先授予悬浮窗权限")
            return
        }
        if (panelState.picking) {
            // Tapping the other creation button switches mode without leaving picking.
            panelState.swipePicking = swipe
            panelState.colorMode = false
            clearSwipeAnchor()
            return
        }
        pickingStartCount = ClickerStore.data.value.profile.points.size
        panelState.swipePicking = swipe
        closePanel()
        closeBubble()
        if (barHost == null) attachBar()

        val layer = View(context)
        layer.setBackgroundColor(Color.TRANSPARENT)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        layer.setOnTouchListener { view, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                val x = event.rawX.toInt().coerceIn(0, screen.x - 1)
                val y = event.rawY.toInt().coerceIn(0, screen.y - 1)
                when {
                    panelState.colorMode -> sampleColorAt(x, y)
                    panelState.swipePicking -> handleSwipeTap(x, y)
                    else -> {
                        ClickerStore.addPoint(ClickPoint(x = x, y = y))
                        ClickerStore.normalizeTo(screen.x, screen.y)
                    }
                }
                view.performClick()
            }
            true
        }
        runCatching {
            windowManager.addView(layer, params)
            pickerView = layer
        }.onFailure {
            toast("取点失败：${it.message}")
            return
        }
        panelState.picking = true
        // The bar turns into "取点中 … 完成" and is lifted above the picker layer.
        raiseBar()
        refreshMarkers()
    }

    /** Leaves picking and puts the bar back. Volume down and the 完成 button both land here. */
    fun finishPicking(silent: Boolean = false) {
        if (!panelState.picking && pickerView == null) return
        pickerView?.let { runCatching { windowManager.removeView(it) } }
        pickerView = null
        val added = ClickerStore.data.value.profile.points.size - pickingStartCount
        panelState.picking = false
        panelState.colorMode = false
        panelState.swipePicking = false
        clearSwipeAnchor()
        refreshMarkers()
        if (!silent && added > 0) toast("加了 $added 个点 · 点圆点能改它的时长和延迟")
    }

    /**
     * A swipe is two taps: the first one parks a preview dot, the second one creates the point.
     * After that the next tap starts a new swipe, so several can be drawn without leaving picking.
     */
    private fun handleSwipeTap(x: Int, y: Int) {
        val anchor = swipeAnchor
        if (anchor == null) {
            swipeAnchor = android.graphics.Point(x, y)
            showAnchorMarker(x, y)
            toast("起点 ($x, $y) · 再点一下画终点")
            return
        }
        ClickerStore.addPoint(
            ClickPoint(
                x = anchor.x,
                y = anchor.y,
                kind = com.roottap.clicker.core.PointKind.SWIPE,
                x2 = x,
                y2 = y,
                swipeMs = 300,
            ),
        )
        ClickerStore.normalizeTo(screen.x, screen.y)
        clearSwipeAnchor()
        refreshMarkers()
        toast("已加滑动 (${anchor.x}, ${anchor.y}) → ($x, $y)")
    }

    private fun showAnchorMarker(x: Int, y: Int) {
        clearSwipeAnchor()
        val dot = TextView(context).apply {
            text = "A"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            background = androidx.core.content.ContextCompat.getDrawable(
                context,
                com.roottap.clicker.R.drawable.marker_anchor,
            )
        }
        val params = overlayParams(width = dp(26), height = dp(26), x = x - dp(13), y = y - dp(13))
            .apply { flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE }
        runCatching {
            windowManager.addView(dot, params)
            anchorMarker = dot
        }
    }

    private fun clearSwipeAnchor() {
        anchorMarker?.let { runCatching { windowManager.removeView(it) } }
        anchorMarker = null
        swipeAnchor = null
    }

    // ---------------------------------------------------------------- bubble

    /** Opens the per-point editor right next to the dot that was tapped. */
    fun openBubble(pointId: Long) {
        val data = ClickerStore.data.value
        val index = data.profile.points.indexOfFirst { it.id == pointId }
        if (index < 0) return
        closeBubble()
        val host = OverlayComposeHost(context)
        host.setContent {
            ZeroOverlayTheme {
                val state by ClickerStore.data.collectAsState()
                val point = state.profile.points.firstOrNull { it.id == pointId }
                if (point != null) {
                    CompositionLocalProvider(
                        LocalDensity provides Density(density, Density(density).fontScale),
                    ) {
                        PointBubbleContent(
                            index = index + 1,
                            point = point,
                            defaultPress = state.profile.pressMs,
                            onPress = { value -> editPoint(pointId) { it.copy(pressMs = value) } },
                            onSwipeMs = { value -> editPoint(pointId) { it.copy(swipeMs = value) } },
                            onDelay = { value -> editPoint(pointId) { it.copy(delayMs = value) } },
                            onToggleEnabled = { enabled ->
                                editPoint(pointId, recordUndo = true) { it.copy(enabled = enabled) }
                            },
                            onDelete = {
                                ClickerStore.removePoint(pointId)
                                closeBubble()
                                toast("已删除，可撤销")
                            },
                            onClose = { closeBubble() },
                        )
                    }
                }
            }
        }
        val width = min(dp(268), (screen.x * 0.82f).roundToInt())
        val height = min(dp(330), (screen.y * 0.52f).roundToInt())
        val anchor = data.profile.points[index]
        var x = anchor.x + dp(26)
        var y = anchor.y - dp(30)
        if (x + width > screen.x) x = anchor.x - width - dp(26)
        x = x.coerceIn(0, (screen.x - width).coerceAtLeast(0))
        y = y.coerceIn(dp(40), (screen.y - height - dp(24)).coerceAtLeast(dp(40)))
        val params = overlayParams(width = width, height = height, x = x, y = y)
        runCatching {
            windowManager.addView(host.view, params)
            bubbleHost = host
        }.onFailure { toast("点位面板打开失败：${it.message}") }
        panelState.bubblePointId = pointId
    }

    fun closeBubble() {
        bubbleHost?.let { host ->
            runCatching { windowManager.removeView(host.view) }
            host.destroy()
        }
        bubbleHost = null
        panelState.bubblePointId = null
    }

    // ---------------------------------------------------------------- markers

    fun refreshMarkers() {
        val points = ClickerStore.data.value.profile.points
        if (!ClickerStore.data.value.options.showMarkers) {
            clearMarkers()
            return
        }
        syncSwipeLines(points.filter { it.isSwipe })
        if (markers.size == points.size && markers.isNotEmpty()) {
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
            ).apply { flags = markerFlags() }
            marker.tag = params
            runCatching {
                windowManager.addView(marker, params)
                markers += marker
            }
        }
    }

    private fun clearMarkers() {
        markers.forEach { runCatching { windowManager.removeView(it) } }
        markers.clear()
        linesView?.let { runCatching { windowManager.removeView(it) } }
        linesView = null
        markerSignature = null
    }

    /** Dashed A→B lines for swipe points, kept below the numbered dots. */
    private fun syncSwipeLines(swipes: List<ClickPoint>) {
        if (swipes.isEmpty()) {
            linesView?.let { runCatching { windowManager.removeView(it) } }
            linesView = null
            return
        }
        val existing = linesView
        if (existing != null) {
            existing.points = swipes
            return
        }
        val view = com.roottap.clicker.service.overlay.SwipeLinesView(context)
        view.points = swipes
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        runCatching {
            windowManager.addView(view, params)
            linesView = view
        }
    }

    private fun markerFlags(): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        // While picking, taps belong to the picker; markers must not swallow them.
        if (panelState.picking) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return flags
    }

    /**
     * A dot the user can act on: tap opens its editor, drag moves it. No edit mode to switch on.
     */
    private fun buildMarker(index: Int, point: ClickPoint): View {
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
        var startX = 0
        var startY = 0
        var startRawX = 0f
        var startRawY = 0f
        var dragging = false
        marker.setOnTouchListener { view, event ->
            val params = view.tag as? WindowManager.LayoutParams
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params?.x ?: 0
                    startY = params?.y ?: 0
                    startRawX = event.rawX
                    startRawY = event.rawY
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startRawX
                    val dy = event.rawY - startRawY
                    if (!dragging && abs(dx) + abs(dy) > dp(6)) dragging = true
                    if (dragging && params != null) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        if (params != null) {
                            val nx = (params.x + dp(13)).coerceIn(0, screen.x - 1)
                            val ny = (params.y + dp(13)).coerceIn(0, screen.y - 1)
                            val dx = nx - point.x
                            val dy = ny - point.y
                            actions.onPointMoved(
                                if (point.isSwipe) {
                                    // Dragging a swipe moves the whole gesture, not just point A.
                                    point.copy(
                                        x = nx,
                                        y = ny,
                                        x2 = (point.x2 + dx).coerceIn(0, screen.x - 1),
                                        y2 = (point.y2 + dy).coerceIn(0, screen.y - 1),
                                    )
                                } else {
                                    point.copy(x = nx, y = ny)
                                },
                            )
                        }
                    } else {
                        if (panelState.bubblePointId == point.id) closeBubble() else openBubble(point.id)
                        view.performClick()
                    }
                }
            }
            true
        }
        return marker
    }

    // ---------------------------------------------------------------- actions

    private fun editPoint(id: Long, recordUndo: Boolean = false, transform: (ClickPoint) -> ClickPoint) {
        val current = ClickerStore.data.value.profile.points.firstOrNull { it.id == id } ?: return
        ClickerStore.updatePoint(transform(current), recordUndo = recordUndo)
    }

    private fun testTap(point: ClickPoint) {
        val press = point.pressMs.takeIf { it > 0 } ?: ClickerStore.data.value.profile.pressMs
        toast("2 秒后在 (${point.x}, ${point.y}) 点一下")
        main.postDelayed({ ClickerRuntime.engine.testTap(point.x, point.y, press) }, 2_000)
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

    private fun overlayParams(
        width: Int,
        height: Int,
        x: Int,
        y: Int,
    ): WindowManager.LayoutParams {
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
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
