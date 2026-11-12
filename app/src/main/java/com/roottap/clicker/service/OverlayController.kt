package com.roottap.clicker.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.roottap.clicker.core.ClickMode
import com.roottap.clicker.core.ClickPoint
import com.roottap.clicker.core.ClickTemplate
import com.roottap.clicker.core.ClickerRuntime
import com.roottap.clicker.core.ClickerStore
import com.roottap.clicker.core.ConditionKind
import com.roottap.clicker.core.EngineState
import com.roottap.clicker.core.ScreenSampler
import com.roottap.clicker.core.Templates
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The whole control surface of the clicker: a small draggable bar plus a "quick settings" panel
 * with templates, timing, points and undo. Everything happens on the overlay, so the app itself
 * stays out of the way while another app is being automated.
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
    private val screenWidth = ClickerRuntime.screenSize(context).x
    private val screenHeight = ClickerRuntime.screenSize(context).y

    private var barView: View? = null
    private var barToggle: TextView? = null
    private var barStatus: TextView? = null

    private var panelView: View? = null
    private var panelHost: LinearLayout? = null

    private val markers = mutableListOf<View>()
    private var markersTouchable = false

    private var pickerRoot: View? = null
    private var pickerPanel: View? = null
    private var pickerCount: TextView? = null
    private var pickerColorMode = false
    private var pickerColorChip: TextView? = null

    fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    // ---------------------------------------------------------------- lifecycle

    fun show() {
        if (!canDraw()) {
            toast("请先授予悬浮窗权限")
            return
        }
        attachBar()
        refreshMarkers()
        syncBar()
    }

    fun hide() {
        detachBar()
        closePanel()
        clearMarkers()
        closePicker()
    }

    fun onState(state: EngineState) {
        barToggle?.text = if (state.running) "■" else "▶"
        barToggle?.setBackground(rounded(if (state.running) Red else Green, 20f))
        syncBar()
        if (!state.running) refreshMarkers()
    }

    /** Call after the profile changed; rebuilds the panel content in place. */
    fun onDataChanged() {
        syncBar()
        if (panelView != null) buildPanel()
        if (pickerRoot != null) updatePickerCount()
        if (!ClickerRuntime.engine.isRunning) refreshMarkers()
    }

    // ---------------------------------------------------------------- control bar

    private fun attachBar() {
        if (barView != null) return
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(6), dp(6))
            background = rounded(PanelColor, 26f)
            elevation = dp(6).toFloat()
        }
        barStatus = label("", 12f).apply {
            setPadding(dp(6), 0, dp(10), 0)
            maxLines = 1
        }
        barToggle = pill("▶", Green).apply {
            setOnClickListener { actions.onToggle() }
        }
        val settings = pill("⚙", ChipColor).apply {
            setOnClickListener { if (panelView == null) openPanel() else closePanel() }
        }
        val close = pill("✕", ChipColor).apply {
            setOnClickListener { actions.onCloseOverlay() }
        }
        bar.addView(barStatus)
        bar.addView(barToggle)
        bar.addView(space(dp(6)))
        bar.addView(settings)
        bar.addView(space(dp(6)))
        bar.addView(close)

        val params = overlayParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            x = dp(10),
            y = screenHeight / 4,
            touchable = true,
        )
        makeDraggable(bar, params)
        runCatching {
            windowManager.addView(bar, params)
            barView = bar
            syncBar()
        }.onFailure { toast("悬浮窗打开失败：${it.message}") }
    }

    private fun detachBar() {
        barView?.let { runCatching { windowManager.removeView(it) } }
        barView = null
        barToggle = null
        barStatus = null
    }

    private fun syncBar() {
        val status = barStatus ?: return
        val data = ClickerStore.data.value
        val state = ClickerRuntime.engine.state.value
        status.text = when {
            state.running -> "运行 · ${state.clicks} 次"
            data.profile.points.isEmpty() -> "未取点"
            else -> "${data.profile.points.size} 点 · ${modeName(data.profile.mode)}"
        }
        status.setTextColor(Color.WHITE)
    }

    // ---------------------------------------------------------------- settings panel

    private fun openPanel() {
        if (!canDraw()) {
            toast("请先授予悬浮窗权限")
            return
        }
        val scroll = ScrollView(context).apply {
            isFillViewport = false
            setBackgroundColor(Color.TRANSPARENT)
        }
        val host = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(12))
            background = rounded(PanelColor, 22f)
        }
        scroll.addView(
            host,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        val width = min(dp(300), (screenWidth * 0.9f).roundToInt())
        val params = overlayParams(
            width = width,
            height = min((screenHeight * 0.66f).roundToInt(), dp(660)),
            x = (screenWidth - width) / 2,
            y = screenHeight / 6,
            touchable = true,
        )
        runCatching {
            windowManager.addView(scroll, params)
            panelView = scroll
            panelHost = host
            buildPanel()
        }.onFailure { toast("设置面板打开失败：${it.message}") }
    }

    private fun closePanel() {
        panelView?.let { runCatching { windowManager.removeView(it) } }
        panelView = null
        panelHost = null
    }

    private fun buildPanel() {
        val host = panelHost ?: return
        host.removeAllViews()
        val data = ClickerStore.data.value
        val profile = data.profile

        // header
        host.addView(
            row(
                label("快速设置", 15f, bold = true),
                spacer(),
                small("收起") { closePanel() },
            ).apply { setPadding(0, 0, 0, dp(8)) },
        )

        // templates
        host.addView(label("模板", 12f, color = DimText).apply { setPadding(0, dp(2), 0, dp(4)) })
        val templates = Templates.all(data.templates)
        val chipStrip = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val chips = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipStrip)
        }
        val activeId = data.activeTemplateId
        templates.forEach { template ->
            val active = template.id == activeId
            chipStrip.addView(
                chip(template.name, active) {
                    ClickerStore.applyTemplate(template)
                    toast("已切换模板：${template.name}")
                },
            )
            chipStrip.addView(space(dp(6)))
        }
        chipStrip.addView(
            chip("＋ 存为模板", false) {
                val index = data.templates.size + 1
                val saved = ClickerStore.saveTemplate("自定义 $index")
                toast("已保存模板「${saved.name}」，可在应用内改名或删除")
            },
        )
        host.addView(chips, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)))
        if (data.templates.any { it.id == activeId }) {
            val saved = data.templates.first { it.id == activeId }
            host.addView(
                row(
                    label(saved.name, 12f, color = DimText),
                    spacer(),
                    small("删除此模板") {
                        ClickerStore.deleteTemplate(saved.id)
                        toast("已删除模板「${saved.name}」")
                    },
                ).apply { setPadding(0, dp(2), 0, 0) },
            )
        }

        host.addView(divider())

        // mode
        host.addView(label("点击模式", 12f, color = DimText))
        val modeRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            ClickMode.SEQUENTIAL to "依次",
            ClickMode.SIMULTANEOUS to "同时",
            ClickMode.RANDOM to "随机",
        ).forEach { (mode, name) ->
            modeRow.addView(
                chip(name, profile.mode == mode, weight = 1f) {
                    ClickerStore.setMode(mode)
                },
                LinearLayout.LayoutParams(0, dp(34), 1f).apply { rightMargin = dp(6) },
            )
        }
        host.addView(modeRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38)))

        // timing
        host.addView(stepper(
            title = "点击间隔",
            value = "${profile.intervalMs} ms",
            step = 10,
            bigStep = 100,
            onDelta = { ClickerStore.bumpInterval(it) },
        ))
        host.addView(stepper(
            title = "随机延迟",
            value = if (profile.randomDelayMs == 0) "关闭" else "0~${profile.randomDelayMs} ms",
            step = 20,
            bigStep = 100,
            onDelta = { ClickerStore.bumpRandomDelay(it) },
        ))
        host.addView(stepper(
            title = "按压时长",
            value = if (profile.pressMs >= 600) "${profile.pressMs} ms · 长按" else "${profile.pressMs} ms",
            step = 20,
            bigStep = 200,
            onDelta = { ClickerStore.bumpPress(it) },
        ))
        host.addView(stepper(
            title = "启动延迟",
            value = if (profile.startDelayMs == 0) "立即" else "${profile.startDelayMs} ms",
            step = 100,
            bigStep = 1_000,
            onDelta = { ClickerStore.bumpStartDelay(it) },
        ))
        host.addView(stepper(
            title = "循环次数",
            value = if (profile.repeatCount == 0) "无限" else "${profile.repeatCount} 次",
            step = 1,
            bigStep = 10,
            onDelta = { ClickerStore.bumpRepeat(it) },
        ))

        host.addView(divider())

        // condition
        host.addView(
            row(
                label("条件点击 · 颜色匹配", 12f, color = DimText),
                spacer(),
                chip(if (profile.condition == ConditionKind.COLOR) "已开启" else "开启", profile.condition == ConditionKind.COLOR) {
                    ClickerStore.setCondition(
                        if (profile.condition == ConditionKind.COLOR) ConditionKind.NONE else ConditionKind.COLOR,
                    )
                },
            ),
        )
        if (profile.condition == ConditionKind.COLOR) {
            host.addView(
                row(
                    swatch(profile.color.color),
                    label(
                        "${ScreenSampler.toHex(profile.color.color)} @ (${profile.color.x}, ${profile.color.y})",
                        12f,
                        color = DimText,
                    ).apply { setPadding(dp(8), 0, 0, 0) },
                    spacer(),
                    small("取色") { grabColor() },
                ).apply { setPadding(0, dp(6), 0, 0) },
            )
            host.addView(stepper(
                title = "颜色容差",
                value = "${profile.color.tolerance}",
                step = 4,
                bigStep = 20,
                onDelta = { delta ->
                    ClickerStore.updateProfile {
                        it.copy(color = it.color.copy(tolerance = (it.color.tolerance + delta).coerceIn(0, 120)))
                    }
                },
            ))
        }

        host.addView(divider())

        // points + undo
        val actionsRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        actionsRow.addView(
            chip("＋ 取点", true, weight = 1f) { openPicker() },
            LinearLayout.LayoutParams(0, dp(36), 1f).apply { rightMargin = dp(6) },
        )
        val undoCount = ClickerStore.undoCount.value
        actionsRow.addView(
            chip(if (undoCount > 0) "↶ 撤销 ($undoCount)" else "↶ 撤销", false, weight = 1f) {
                val message = ClickerStore.undo()
                toast(message ?: "没有可撤销的操作")
            },
            LinearLayout.LayoutParams(0, dp(36), 1f).apply { rightMargin = dp(6) },
        )
        actionsRow.addView(
            chip(if (markersTouchable) "锁定点位" else "拖动点位", markersTouchable, weight = 1f) {
                setMarkersEditable(!markersTouchable)
            },
            LinearLayout.LayoutParams(0, dp(36), 1f),
        )
        host.addView(actionsRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)))
        host.addView(
            row(
                label("${profile.points.size} 个点位 · 长按标记可微调", 11f, color = DimText),
                spacer(),
                small("清空") { ClickerStore.clearPoints() },
                space(dp(8)),
                small("测试点击") { testTap() },
            ).apply { setPadding(0, dp(6), 0, 0) },
        )
        if (profile.points.isNotEmpty()) {
            val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            profile.points.forEachIndexed { index, point ->
                list.addView(
                    row(
                        label("${index + 1}. (${point.x}, ${point.y})", 12f),
                        spacer(),
                        small("删除") { ClickerStore.removePoint(point.id) },
                    ).apply { setPadding(0, dp(3), 0, dp(3)) },
                )
            }
            host.addView(list)
        }
    }

    // ---------------------------------------------------------------- markers

    fun refreshMarkers() {
        clearMarkers()
        if (!ClickerStore.data.value.options.showMarkers) return
        ClickerStore.data.value.profile.points.forEachIndexed { index, point ->
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
        buildPanel()
        toast(if (editable) "拖动圆点调整位置，松手即保存" else "点位已锁定")
    }

    private fun clearMarkers() {
        markers.forEach { runCatching { windowManager.removeView(it) } }
        markers.clear()
    }

    private fun buildMarker(index: Int, point: ClickPoint): View {
        val marker = TextView(context).apply {
            text = index.toString()
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            background = rounded(MarkerColor, 13f, ring = true)
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
        if (pickerRoot != null) {
            closePicker()
            return
        }
        val size = ClickerRuntime.screenSize(context)
        val root = View(context)
        root.setBackgroundColor(Color.parseColor("#26000000"))
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        root.setOnTouchListener { view, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                val x = event.rawX.toInt().coerceIn(0, size.x - 1)
                val y = event.rawY.toInt().coerceIn(0, size.y - 1)
                // Keep the top strip free so the picker controls stay reachable.
                if (y > dp(80)) {
                    if (pickerColorMode) {
                        sampleColorAt(x, y)
                    } else {
                        ClickerStore.addPoint(ClickPoint(x = x, y = y))
                        ClickerStore.normalizeTo(size.x, size.y)
                        updatePickerCount()
                        refreshMarkers()
                    }
                }
                view.performClick()
            }
            true
        }
        runCatching {
            windowManager.addView(root, params)
            pickerRoot = root
            attachPickerPanel()
        }.onFailure { toast("取点窗口打开失败：${it.message}") }
    }

    fun closePicker() {
        pickerRoot?.let { runCatching { windowManager.removeView(it) } }
        pickerRoot = null
        pickerPanel?.let { runCatching { windowManager.removeView(it) } }
        pickerPanel = null
        pickerCount = null
        pickerColorChip = null
        pickerColorMode = false
        refreshMarkers()
        buildPanel()
    }

    private fun attachPickerPanel() {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(8), dp(8))
            background = rounded(PanelColor, 24f)
        }
        pickerCount = label("", 12f).apply { setPadding(0, 0, dp(10), 0) }
        panel.addView(pickerCount)
        pickerColorChip = chip("取色", pickerColorMode) {
            pickerColorMode = !pickerColorMode
            pickerColorChip?.setBackground(rounded(if (pickerColorMode) Accent else ChipColor, 16f))
            updatePickerCount()
        }
        panel.addView(pickerColorChip)
        panel.addView(space(dp(6)))
        panel.addView(
            chip("↶ 撤销", false) {
                val message = ClickerStore.undo()
                toast(message ?: "没有可撤销的操作")
                updatePickerCount()
                refreshMarkers()
            },
        )
        panel.addView(space(dp(6)))
        panel.addView(
            chip("完成", true) {
                closePicker()
            },
        )
        val params = overlayParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            x = 0,
            y = dp(28),
            touchable = true,
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }
        runCatching {
            windowManager.addView(panel, params)
            pickerPanel = panel
            updatePickerCount()
        }
    }

    private fun updatePickerCount() {
        val count = ClickerStore.data.value.profile.points.size
        pickerCount?.text = if (pickerColorMode) {
            "点屏幕取色（条件点击用）"
        } else {
            "点屏幕取点 · 已有 $count 个"
        }
    }

    /** Samples the pixel under the finger and uses it as the colour condition target. */
    private fun sampleColorAt(x: Int, y: Int) {
        Thread {
            val pixel = ScreenSampler.sample(x, y)
            post {
                if (pixel == null) {
                    toast("取色失败，请确认 root 权限")
                } else {
                    ClickerStore.updateProfile {
                        it.copy(
                            condition = ConditionKind.COLOR,
                            color = it.color.copy(x = x, y = y, color = pixel),
                        )
                    }
                    toast("已取色 ${ScreenSampler.toHex(pixel)} @ ($x, $y)")
                }
            }
        }.start()
    }

    // ---------------------------------------------------------------- helpers

    private fun grabColor() {
        toast("正在截屏取色…")
        Thread {
            val profile = ClickerStore.data.value.profile
            val target = profile.points.firstOrNull()
            val x = if (profile.color.x > 0 || profile.color.y > 0) {
                profile.color.x
            } else {
                target?.x ?: (screenWidth / 2)
            }
            val y = if (profile.color.x > 0 || profile.color.y > 0) {
                profile.color.y
            } else {
                target?.y ?: (screenHeight / 2)
            }
            val pixel = ScreenSampler.sample(x, y)
            post {
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

    private fun testTap() {
        val profile = ClickerStore.data.value.profile
        val point = profile.points.firstOrNull()
        if (point == null) {
            toast("还没有点位")
            return
        }
        toast("2 秒后在第一个点位点一下")
        post {
            Thread {
                Thread.sleep(2_000)
                ClickerRuntime.engine.testTap(point.x, point.y, profile.pressMs)
                post { toast("测试点击已下发 (${point.x}, ${point.y})") }
            }.start()
        }
    }

    /** Runs on the main thread. */
    private fun post(block: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(block)
    }

    private fun modeName(mode: ClickMode): String = when (mode) {
        ClickMode.SEQUENTIAL -> "依次"
        ClickMode.SIMULTANEOUS -> "同时"
        ClickMode.RANDOM -> "随机"
    }

    private fun stepper(
        title: String,
        value: String,
        step: Int,
        bigStep: Int,
        onDelta: (Int) -> Unit,
    ): View {
        val valueLabel = label(value, 13f, bold = true).apply {
            gravity = Gravity.CENTER
            minWidth = dp(96)
        }
        fun button(text: String, delta: Int) = TextView(context).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            background = rounded(ChipColor, 14f)
            setOnClickListener {
                onDelta(delta)
                // The panel is rebuilt by the store listener.
            }
            setOnLongClickListener {
                // Long press jumps further, without a second gesture.
                val factor = (bigStep / step.coerceAtLeast(1)).coerceAtLeast(2)
                onDelta(delta * factor)
                true
            }
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, dp(5))
            addView(label(title, 13f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(button("−", -step), LinearLayout.LayoutParams(dp(34), dp(30)))
            addView(valueLabel, LinearLayout.LayoutParams(dp(96), dp(30)))
            addView(button("＋", step), LinearLayout.LayoutParams(dp(34), dp(30)))
        }
        return container
    }

    private fun label(
        text: String,
        size: Float,
        color: Int = Color.WHITE,
        bold: Boolean = false,
    ): TextView = TextView(context).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun small(text: String, onClick: () -> Unit): TextView = TextView(context).apply {
        this.text = text
        setTextColor(Accent)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(dp(6), dp(3), dp(6), dp(3))
        setOnClickListener { onClick() }
    }

    private fun chip(
        text: String,
        active: Boolean,
        weight: Float = 0f,
        onClick: () -> Unit,
    ): TextView = TextView(context).apply {
        this.text = text
        setTextColor(if (active) Color.WHITE else Color.parseColor("#D9FFFFFF"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(6), dp(12), dp(6))
        background = rounded(if (active) Accent else ChipColor, 16f)
        setOnClickListener { onClick() }
        if (weight > 0f) {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
        }
    }

    private fun pill(text: String, color: Int): TextView = TextView(context).apply {
        this.text = text
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        gravity = Gravity.CENTER
        minWidth = dp(34)
        setPadding(dp(10), dp(5), dp(10), dp(5))
        background = rounded(color, 18f)
    }

    private fun swatch(color: Int): View = View(context).apply {
        background = rounded(color, 6f, ring = true)
        layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
    }

    private fun row(vararg children: View): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        children.forEach { addView(it) }
    }

    private fun spacer(): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
    }

    private fun space(width: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(width, 1)
    }

    private fun divider(): View = View(context).apply {
        setBackgroundColor(Color.parseColor("#28FFFFFF"))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            1,
        ).apply { topMargin = dp(10); bottomMargin = dp(10) }
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
        return WindowManager.LayoutParams(width, height, overlayType(), flags, PixelFormat.TRANSLUCENT)
            .apply {
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

    private fun rounded(color: Int, radiusDp: Float, ring: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * density
            setColor(color)
            if (ring) setStroke(dp(2), Color.parseColor("#E6FFFFFF"))
        }

    private fun dp(value: Int): Int = (value * density).toInt()

    private fun toast(text: String) {
        runCatching { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }

    private companion object {
        val PanelColor: Int = Color.parseColor("#F218181B")
        val ChipColor: Int = Color.parseColor("#33FFFFFF")
        val Accent: Int = Color.parseColor("#1B7FFF")
        val Green: Int = Color.parseColor("#12A150")
        val Red: Int = Color.parseColor("#E5484D")
        val MarkerColor: Int = Color.parseColor("#CC1B7FFF")
        val DimText: Int = Color.parseColor("#99FFFFFF")
    }
}
