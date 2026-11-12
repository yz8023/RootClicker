package com.roottap.clicker.service.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zeroui.kit.components.Card
import com.zeroui.kit.components.HorizontalDivider
import com.zeroui.kit.components.ListRow
import com.zeroui.kit.components.SectionTitle
import com.zeroui.kit.components.Slider
import com.zeroui.kit.components.Switch
import com.zeroui.kit.components.TabRow
import com.zeroui.kit.components.Text
import com.zeroui.kit.components.TextButton
import com.zeroui.kit.components.ButtonVariant
import com.zeroui.kit.theme.ZeroTheme
import com.roottap.clicker.ZeroApp
import com.roottap.clicker.core.ClickMode
import com.roottap.clicker.core.ClickPoint
import com.roottap.clicker.core.ClickTemplate
import com.roottap.clicker.core.ClickerRuntime
import com.roottap.clicker.core.ClickerStore
import com.roottap.clicker.core.ConditionKind
import com.roottap.clicker.core.ScreenSampler
import com.roottap.clicker.core.Templates
import com.roottap.clicker.data.toThemeSettings
import kotlin.math.roundToInt

/** Which part of the overlay is showing. Owned by the controller, read by Compose. */
class OverlayPanelState {
    var open by mutableStateOf(false)
    var detailPointId by mutableStateOf<Long?>(null)
    var markersEditable by mutableStateOf(false)
    var colorMode by mutableStateOf(false)
}

private val CompactInside = PaddingValues(horizontal = 10.dp, vertical = 2.dp)

/** Wraps overlay content in the real ZeroUI theme, exactly like the activity does. */
@Composable
fun ZeroOverlayTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as ZeroApp).container
    val settings by container.settings.settings.collectAsState()
    ZeroTheme(settings.toThemeSettings(0), content)
}

@Composable
private fun CompactButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    TextButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        variant = if (primary) ButtonVariant.Primary else ButtonVariant.Secondary,
        minWidth = 40.dp,
        minHeight = 34.dp,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    )
}

// ------------------------------------------------------------------ control bar

@Composable
fun ControlBarContent(
    running: Boolean,
    clicks: Long,
    pointCount: Int,
    modeName: String,
    onToggle: () -> Unit,
    onPanel: () -> Unit,
    onClose: () -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    Card(cornerRadius = 24.dp, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Drag grip: the buttons keep working, the grip moves the bar.
            Box(
                modifier = Modifier
                    .size(width = 22.dp, height = 30.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            onDrag(drag.x, drag.y)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "⋮⋮")
            }
            Text(
                text = when {
                    running -> "运行 ${clicks} 次"
                    pointCount == 0 -> "未取点"
                    else -> "$pointCount 点 · $modeName"
                },
                modifier = Modifier.padding(end = 4.dp),
            )
            CompactButton(
                text = if (running) "停止" else "开始",
                onClick = onToggle,
                primary = true,
            )
            CompactButton(text = "⚙", onClick = onPanel)
            CompactButton(text = "✕", onClick = onClose)
        }
    }
}

// ------------------------------------------------------------------ settings panel

@Composable
fun PanelContent(
    state: OverlayPanelState,
    onClose: () -> Unit,
    onPickPoints: () -> Unit,
    onToast: (String) -> Unit,
    onTestTap: (ClickPoint) -> Unit,
    onGrabColor: () -> Unit,
    onMarkersEditableChange: (Boolean) -> Unit,
) {
    val data by ClickerStore.data.collectAsState()
    val undoCount by ClickerStore.undoCount.collectAsState()
    val profile = data.profile
    val detail = profile.points.firstOrNull { it.id == state.detailPointId }

    Card(modifier = Modifier.fillMaxSize(), cornerRadius = 22.dp, contentPadding = PaddingValues(0.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = if (detail == null) "连点设置" else "点位 ${profile.points.indexOf(detail) + 1}")
                Spacer(Modifier.weight(1f))
                if (detail != null) {
                    CompactButton(text = "返回", onClick = { state.detailPointId = null })
                }
                CompactButton(text = "收起", onClick = onClose)
            }

            if (detail == null) {
                MainPanel(
                    state = state,
                    profile = profile,
                    templates = Templates.all(data.templates),
                    activeTemplateId = data.activeTemplateId,
                    undoCount = undoCount,
                    onPickPoints = onPickPoints,
                    onToast = onToast,
                    onTestTap = onTestTap,
                    onGrabColor = onGrabColor,
                    onMarkersEditableChange = onMarkersEditableChange,
                )
            } else {
                PointDetail(point = detail, state = state, onToast = onToast)
            }
        }
    }
}

@Composable
private fun MainPanel(
    state: OverlayPanelState,
    profile: com.roottap.clicker.core.ClickProfile,
    templates: List<ClickTemplate>,
    activeTemplateId: Long,
    undoCount: Int,
    onPickPoints: () -> Unit,
    onToast: (String) -> Unit,
    onTestTap: (ClickPoint) -> Unit,
    onGrabColor: () -> Unit,
    onMarkersEditableChange: (Boolean) -> Unit,
) {
    SectionTitle("模板")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        templates.forEach { template ->
            CompactButton(
                text = template.name,
                primary = template.id == activeTemplateId,
                onClick = {
                    ClickerStore.applyTemplate(template)
                    onToast("已切换模板：${template.name}")
                },
            )
        }
        CompactButton(
            text = "＋ 存为模板",
            onClick = {
                val saved = ClickerStore.saveTemplate("自定义 ${templates.count { it.id > 0 } + 1}")
                onToast("已保存「${saved.name}」，可在应用内改名")
            },
        )
    }

    SectionTitle("点击模式")
    TabRow(
        tabs = listOf("依次", "同时", "随机"),
        selectedTabIndex = profile.mode.ordinal,
        onTabSelected = { index -> ClickerStore.setMode(ClickMode.entries[index]) },
        modifier = Modifier.padding(horizontal = 12.dp),
    )

    SectionTitle("节奏")
    SliderRow(
        title = "点击间隔",
        value = profile.intervalMs,
        range = 10..5_000,
        step = 10,
        format = ::formatMs,
        onChange = { value -> ClickerStore.updateProfile { it.copy(intervalMs = value) } },
    )
    SliderRow(
        title = "随机延迟",
        value = profile.randomDelayMs,
        range = 0..2_000,
        step = 20,
        format = { if (it == 0) "关闭" else "每次 +0~${it} ms" },
        onChange = { value -> ClickerStore.updateProfile { it.copy(randomDelayMs = value) } },
    )
    SliderRow(
        title = "按压时长",
        value = profile.pressMs,
        range = 10..5_000,
        step = 20,
        format = { if (it >= 600) "${formatMs(it)} · 长按" else formatMs(it) },
        onChange = { value -> ClickerStore.updateProfile { it.copy(pressMs = value) } },
    )
    SliderRow(
        title = "启动延迟",
        value = profile.startDelayMs,
        range = 0..10_000,
        step = 100,
        format = { if (it == 0) "立即开始" else formatMs(it) },
        onChange = { value -> ClickerStore.updateProfile { it.copy(startDelayMs = value) } },
    )
    SliderRow(
        title = "循环次数",
        value = profile.repeatCount,
        range = 0..1_000,
        step = 1,
        format = { if (it == 0) "无限" else "$it 次" },
        onChange = { value -> ClickerStore.updateProfile { it.copy(repeatCount = value) } },
    )

    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))

    SectionTitle("点位 (${profile.points.size})")
    if (profile.points.isEmpty()) {
        ListRow(title = "还没有点位", summary = "点下面的「取点」，然后在屏幕上点一下")
    }
    profile.points.forEachIndexed { index, point ->
        ListRow(
            title = "点位 ${index + 1} · ${point.label}",
            summary = buildString {
                append("按压 ").append(formatMs(if (point.pressMs > 0) point.pressMs else profile.pressMs))
                if (point.delayMs > 0) append(" · 延迟 ").append(formatMs(point.delayMs))
                if (!point.enabled) append(" · 已停用")
                if (point.pressMs > 0) append(" · 独立")
            },
            onClick = { state.detailPointId = point.id },
            endActions = {
                Switch(
                    checked = point.enabled,
                    onCheckedChange = { enabled ->
                        ClickerStore.updatePoint(point.copy(enabled = enabled), recordUndo = false)
                    },
                )
            },
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompactButton(text = "＋ 取点", primary = true, onClick = onPickPoints)
        CompactButton(
            text = if (state.markersEditable) "锁定" else "拖动点位",
            onClick = {
                val next = !state.markersEditable
                state.markersEditable = next
                onMarkersEditableChange(next)
            },
        )
        CompactButton(
            text = if (undoCount > 0) "↶ 撤销 $undoCount" else "↶ 撤销",
            enabled = undoCount > 0,
            onClick = { onToast(ClickerStore.undo() ?: "没有可撤销的操作") },
        )
    }

    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))

    SectionTitle("条件点击")
    ListRow(
        title = "颜色匹配后开点",
        summary = if (profile.condition == ConditionKind.COLOR) {
            "${ScreenSampler.toHex(profile.color.color)} @ (${profile.color.x}, ${profile.color.y})"
        } else {
            "关闭时按节奏一直点"
        },
        endActions = {
            Switch(
                checked = profile.condition == ConditionKind.COLOR,
                onCheckedChange = { on ->
                    ClickerStore.setCondition(if (on) ConditionKind.COLOR else ConditionKind.NONE)
                },
            )
        },
    )
    if (profile.condition == ConditionKind.COLOR) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(profile.color.color)),
            )
            CompactButton(text = "屏幕取色", onClick = onGrabColor)
            Text(text = "容差", modifier = Modifier.padding(start = 6.dp))
            CompactButton(
                text = "−",
                onClick = {
                    ClickerStore.updateProfile {
                        it.copy(color = it.color.copy(tolerance = (it.color.tolerance - 4).coerceAtLeast(0)))
                    }
                },
            )
            Text(text = "${profile.color.tolerance}")
            CompactButton(
                text = "＋",
                onClick = {
                    ClickerStore.updateProfile {
                        it.copy(color = it.color.copy(tolerance = (it.color.tolerance + 4).coerceAtMost(120)))
                    }
                },
            )
        }
    }

    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))

    SectionTitle("更多")
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompactButton(
            text = "测试点击",
            enabled = profile.points.isNotEmpty(),
            onClick = { profile.points.firstOrNull()?.let(onTestTap) },
        )
        CompactButton(
            text = "清空点位",
            enabled = profile.points.isNotEmpty(),
            onClick = {
                ClickerStore.clearPoints()
                onToast("已清空，可撤销")
            },
        )
    }
}

@Composable
private fun PointDetail(
    point: ClickPoint,
    state: OverlayPanelState,
    onToast: (String) -> Unit,
) {
    val data by ClickerStore.data.collectAsState()
    val profile = data.profile
    val screen = ClickerRuntime.screenSize(LocalContext.current)

    SectionTitle("开关")
    ListRow(
        title = "启用此点位",
        summary = "关闭后连点会跳过它，位置仍保留",
        endActions = {
            Switch(
                checked = point.enabled,
                onCheckedChange = { enabled ->
                    ClickerStore.updatePoint(point.copy(enabled = enabled))
                },
            )
        },
    )

    SectionTitle("位置")
    SliderRow(
        title = "X 坐标",
        value = point.x,
        range = 0..(screen.x - 1).coerceAtLeast(1),
        step = 1,
        format = { it.toString() },
        onChange = { value -> editPoint(point.id) { it.copy(x = value) } },
    )
    SliderRow(
        title = "Y 坐标",
        value = point.y,
        range = 0..(screen.y - 1).coerceAtLeast(1),
        step = 1,
        format = { it.toString() },
        onChange = { value -> editPoint(point.id) { it.copy(y = value) } },
    )

    SectionTitle("这个点自己的节奏")
    SliderRow(
        title = "按压时长",
        value = point.pressMs,
        range = 0..10_000,
        step = 50,
        format = { if (it == 0) "用默认 ${formatMs(profile.pressMs)}" else formatMs(it) },
        onChange = { value -> editPoint(point.id) { it.copy(pressMs = value) } },
    )
    SliderRow(
        title = "点击延迟",
        value = point.delayMs,
        range = 0..10_000,
        step = 50,
        format = { if (it == 0) "不额外等待" else "点它之前等 ${formatMs(it)}" },
        onChange = { value -> editPoint(point.id) { it.copy(delayMs = value) } },
    )

    SectionTitle("其他")
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompactButton(
            text = "移到最前",
            onClick = {
                ClickerStore.updateProfile { current ->
                    val target = current.points.firstOrNull { it.id == point.id } ?: return@updateProfile current
                    current.copy(points = listOf(target) + current.points.filterNot { it.id == point.id })
                }
                onToast("已移到最前")
            },
        )
        CompactButton(
            text = "删除此点位",
            onClick = {
                ClickerStore.removePoint(point.id)
                state.detailPointId = null
                onToast("已删除，可撤销")
            },
        )
    }
}

// ------------------------------------------------------------------ picker controls

@Composable
fun PickerControlsContent(
    count: Int,
    colorMode: Boolean,
    onToggleColor: () -> Unit,
    onUndo: () -> Unit,
    onDone: () -> Unit,
) {
    Card(cornerRadius = 22.dp, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = if (colorMode) "点屏幕取色" else "点屏幕取点 · 已有 $count",
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            CompactButton(text = if (colorMode) "取色中" else "取色", primary = colorMode, onClick = onToggleColor)
            CompactButton(text = "撤销", onClick = onUndo)
            CompactButton(text = "完成", primary = true, onClick = onDone)
        }
    }
}

// ------------------------------------------------------------------ pieces

@Composable
fun SliderRow(
    title: String,
    value: Int,
    range: IntRange,
    step: Int,
    format: (Int) -> String,
    onChange: (Int) -> Unit,
) {
    ListRow(
        title = title,
        summary = format(value),
        bottomAction = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CompactButton(
                    text = "−",
                    onClick = { onChange((value - step).coerceIn(range.first, range.last)) },
                )
                Slider(
                    value = value.toFloat(),
                    onValueChange = { onChange(it.roundToInt().coerceIn(range.first, range.last)) },
                    valueRange = range.first.toFloat()..range.last.toFloat(),
                    modifier = Modifier.weight(1f),
                )
                CompactButton(
                    text = "＋",
                    onClick = { onChange((value + step).coerceIn(range.first, range.last)) },
                )
            }
        },
    )
}

/** Updates the live copy of a point; slider steps are not undo steps. */
private fun editPoint(id: Long, transform: (ClickPoint) -> ClickPoint) {
    val current = ClickerStore.data.value.profile.points.firstOrNull { it.id == id } ?: return
    ClickerStore.updatePoint(transform(current), recordUndo = false)
}

private fun formatMs(value: Int): String =
    if (value >= 1_000) String.format("%.2f 秒", value / 1000f) else "$value ms"
