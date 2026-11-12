package com.roottap.clicker.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.roottap.clicker.core.ClickPoint
import com.roottap.clicker.core.ClickerRuntime
import com.roottap.clicker.core.ClickerStore
import com.roottap.clicker.core.EngineState
import kotlin.math.abs

/**
 * Floating controls: a draggable bar with start/stop, the numbered point markers, and a
 * full screen picker used to place points. All of it runs on the overlay permission, so it
 * floats above other apps without an accessibility service.
 */
@SuppressLint("ClickableViewAccessibility")
class OverlayController(
    private val context: Context,
    private val actions: Actions,
) {

    interface Actions {
        fun onToggle()
        fun onOpenPicker()
        fun onCloseOverlay()
        fun onPointMoved(point: ClickPoint)
    }

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density

    private var barView: View? = null
    private var barParams: WindowManager.LayoutParams? = null
    private var barToggle: TextView? = null
    private var barStatus: TextView? = null

    private val markers = mutableListOf<View>()
    private var markersTouchable = false

    private var pickerRoot: View? = null
    private var pickerParams: WindowManager.LayoutParams? = null

    private val pickerMarkers = mutableListOf<String>()

    fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    fun show() {
        if (!canDraw()) {
            toast("请先授予悬浮窗权限")
            return
        }
        attachBar()
        refreshMarkers()
    }

    fun hide() {
        detachBar()
        clearMarkers()
        closePicker()
    }

    fun onState(state: EngineState) {
        barToggle?.text = if (state.running) "停止" else "开始"
        barToggle?.setBackground(rounded(if (state.running) Color.parseColor("#E5484D") else Color.parseColor("#12A150"), 22f))
        val pointCount = ClickerStore.data.value.profile.points.size
        barStatus?.text = when {
            state.running -> "运行中 · ${state.clicks} 次"
            state.message != null -> state.message
            else -> "$pointCount 个点位"
        }
        if (!state.running) refreshMarkers()
    }

    fun refreshMarkers() {
        clearMarkers()
        if (!ClickerStore.data.value.options.showMarkers) return
        val points = ClickerStore.data.value.profile.points
        points.forEachIndexed { index, point ->
            val marker = buildMarker(index + 1, point)
            val params = overlayParams(
                width = dp(26),
                height = dp(26),
                x = point.x - dp(13),
                y = point.y - dp(13),
                touchable = markersTouchable,
            )
            marker.tag = params
            runCatching {
                windowManager.addView(marker, params)
                markers += marker
            }
        }
    }

    fun setMarkersEditable(editable: Boolean) {
        markersTouchable = editable
        refreshMarkers()
        toast(if (editable) "拖动圆点调整位置" else "点位已锁定")
    }

    /** Full screen picker: every tap adds a point. */
    fun openPicker() {
        if (!canDraw()) {
            toast("请先授予悬浮窗权限")
            return
        }
        if (pickerRoot != null) {
            closePicker()
            return
        }
        pickerMarkers.clear()
        val root = View(context)
        root.setBackgroundColor(Color.parseColor("#33000000"))
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.START
        root.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_UP -> {
                    val x = event.rawX.toInt().coerceAtLeast(0)
                    val y = event.rawY.toInt().coerceAtLeast(0)
                    // Ignore taps on the hint chip so the "done" button stays reachable.
                    if (y > dp(96)) {
                        ClickerStore.addPoint(ClickPoint(x = x, y = y))
                        val size = ClickerRuntime.screenSize(context)
                        ClickerStore.normalizeTo(size.x, size.y)
                        toast("已添加 (${x}, ${y})")
                        refreshMarkers()
                    }
                    view.performClick()
                }
            }
            true
        }
        runCatching {
            windowManager.addView(root, params)
            pickerRoot = root
            pickerParams = params
            attachPickerPanel()
        }.onFailure { toast("取点窗口打开失败：${it.message}") }
    }

    fun closePicker() {
        pickerRoot?.let { runCatching { windowManager.removeView(it) } }
        pickerRoot = null
        pickerParams = null
        panelView?.let { runCatching { windowManager.removeView(it) } }
        panelView = null
        refreshMarkers()
    }

    private var panelView: View? = null

    private fun attachPickerPanel() {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(8), dp(8), dp(8))
            background = rounded(Color.parseColor("#F2202024"), 24f)
        }
        val hint = TextView(context).apply {
            text = "点击屏幕任意位置添加点位"
            setTextColor(Color.WHITE)
            textSize = 13f
        }
        val done = TextView(context).apply {
            text = "完成"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = rounded(Color.parseColor("#12A150"), 18f)
            setOnClickListener {
                closePicker()
                refreshMarkers()
            }
        }
        panel.addView(hint)
        val gap = View(context)
        panel.addView(gap, LinearLayout.LayoutParams(dp(12), 1))
        panel.addView(done)
        val params = overlayParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            x = 0,
            y = dp(40),
            touchable = true,
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }
        runCatching {
            windowManager.addView(panel, params)
            panelView = panel
        }
    }

    private fun attachBar() {
        if (barView != null) return
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(8), dp(6))
            background = rounded(Color.parseColor("#E61C1C1E"), 26f)
            elevation = dp(6).toFloat()
        }
        val dot = TextView(context).apply {
            text = "●"
            setTextColor(Color.parseColor("#33C759"))
            textSize = 11f
        }
        barStatus = TextView(context).apply {
            text = "${ClickerStore.data.value.profile.points.size} 个点位"
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(dp(4), 0, dp(10), 0)
            maxLines = 1
        }
        barToggle = pill("开始", Color.parseColor("#12A150")).apply {
            setOnClickListener { actions.onToggle() }
        }
        val pick = pill("取点", Color.parseColor("#3A3A3E")).apply {
            setOnClickListener { openPicker() }
        }
        val edit = pill("编辑", Color.parseColor("#3A3A3E")).apply {
            setOnClickListener {
                setMarkersEditable(!markersTouchable)
                text = if (markersTouchable) "锁定" else "编辑"
            }
        }
        val close = pill("✕", Color.parseColor("#3A3A3E")).apply {
            setOnClickListener { actions.onCloseOverlay() }
        }
        bar.addView(dot)
        bar.addView(barStatus)
        bar.addView(barToggle)
        bar.addView(space())
        bar.addView(pick)
        bar.addView(space())
        bar.addView(edit)
        bar.addView(space())
        bar.addView(close)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(160)
        }
        makeDraggable(bar, params)
        runCatching {
            windowManager.addView(bar, params)
            barView = bar
            barParams = params
        }.onFailure { toast("悬浮窗打开失败：${it.message}") }
    }

    private fun detachBar() {
        barView?.let { runCatching { windowManager.removeView(it) } }
        barView = null
        barParams = null
        barToggle = null
        barStatus = null
    }

    private fun clearMarkers() {
        markers.forEach { runCatching { windowManager.removeView(it) } }
        markers.clear()
    }

    private fun buildMarker(index: Int, point: ClickPoint): View {
        val marker = TextView(context).apply {
            text = index.toString()
            setTextColor(Color.WHITE)
            textSize = 11f
            gravity = Gravity.CENTER
            background = rounded(Color.parseColor("#CC1B7FFF"), 13f, ring = true)
        }
        if (markersTouchable) {
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
                        if (params != null && abs(event.rawX - startRawX) + abs(event.rawY - startRawY) > dp(4)) {
                            val moved = point.copy(
                                x = (params.x + dp(13)).coerceAtLeast(0),
                                y = (params.y + dp(13)).coerceAtLeast(0),
                            )
                            actions.onPointMoved(moved)
                        }
                    }
                }
                true
            }
        }
        return marker
    }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var startRawX = 0f
        var startRawY = 0f
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    startRawX = event.rawX
                    startRawY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - startRawX).toInt()
                    params.y = startY + (event.rawY - startRawY).toInt()
                    runCatching { windowManager.updateViewLayout(view, params) }
                }
            }
            false
        }
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
        return WindowManager.LayoutParams(
            width,
            height,
            overlayType(),
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun pill(label: String, color: Int): TextView = TextView(context).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 12f
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(6), dp(12), dp(6))
        background = rounded(color, 18f)
    }

    private fun space(): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(6), 1)
    }

    private fun rounded(color: Int, radiusDp: Float, ring: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * density
            setColor(color)
            if (ring) {
                setStroke(dp(2), Color.parseColor("#E6FFFFFF"))
            }
        }

    private fun dp(value: Int): Int = (value * density).toInt()

    private fun toast(text: String) {
        runCatching { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }
}

