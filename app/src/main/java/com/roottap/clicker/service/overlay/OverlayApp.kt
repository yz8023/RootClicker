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
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
import com.zeroui.kit.components.ArrowPreference
import com.zeroui.kit.components.ButtonVariant
import com.zeroui.kit.components.Card
import com.zeroui.kit.components.HorizontalDivider
import com.zeroui.kit.components.ListCard
import com.zeroui.kit.components.ListRow
import com.zeroui.kit.components.SectionTitle
import com.zeroui.kit.components.Slider
import com.zeroui.kit.components.SwitchPreference
import com.zeroui.kit.components.TabRow
import com.zeroui.kit.components.Text
import com.zeroui.kit.components.TextButton
import com.zeroui.kit.theme.ZeroTheme
import com.zeroui.kit.theme.isMaterial
import com.roottap.clicker.ZeroApp
import com.roottap.clicker.core.ClickMode
import com.roottap.clicker.core.ClickPoint
import com.roottap.clicker.core.ClickTemplate
import com.roottap.clicker.core.ClickerStore
import com.roottap.clicker.core.ConditionKind
import com.roottap.clicker.core.ScreenSampler
import com.roottap.clicker.core.Templates
import com.roottap.clicker.data.toThemeSettings
import kotlin.math.roundToInt

/** Which part of the overlay is showing. Owned by the controller, read by Compose. */
class OverlayPanelState {
    var open by mutableStateOf(false)
    var picking by mutableStateOf(false)
    var colorMode by mutableStateOf(false)
    var bubblePointId by mutableStateOf<Long?>(null)
}

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
        minWidth = 38.dp,
        minHeight = 34.dp,
        insideMargin = PaddingValues(horizontal = 11.dp, vertical = 3.dp),
    )
}

// ------------------------------------------------------------------ control bar

/**
 * One line, and it changes meaning instead of opening a new screen: normally it starts the
 * clicker, adds a point, undoes the last point and opens settings; while adding points it shows
 * the count and a way out, and the taps land on the real screen behind it.
 */
@Composable
fun ControlBarContent(
    picking: Boolean,
    colorMode: Boolean,
    running: Boolean,
    clicks: Long,
    pointCount: Int,
    modeName: String,
    undoCount: Int,
    onToggle: () -> Unit,
    onCreate: () -> Unit,
    onUndo: () -> Unit,
    onPanel: () -> Unit,
    onToggleColor: () -> Unit,
    onFinishPicking: () -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    Card(cornerRadius = 24.dp, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            // Drag grip: the buttons keep working, the grip moves the bar.
            Box(
                modifier = Modifier
                    .size(width = 20.dp, height = 30.dp)
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
            if (picking) {
                Text(
                    text = if (colorMode) "点屏幕取色" else "点屏幕加点 · $pointCount",
                    modifier = Modifier.padding(end = 2.dp),
                )
                CompactButton(text = "取色", primary = colorMode, onClick = onToggleColor)
                CompactButton(text = "↶", onClick = onUndo, enabled = undoCount > 0)
                CompactButton(text = "完成", primary = true, onClick = onFinishPicking)
            } else {
                Text(
                    text = when {
                        running -> "运行 ${clicks} 次"
                        pointCount == 0 -> "点 ＋ 取点"
                        else -> "$pointCount 点 · $modeName"
                    },
                    modifier = Modifier.padding(end = 2.dp),
                )
                CompactButton(
                    text = if (running) "■" else "▶",
                    onClick = onToggle,
                    primary = true,
                )
                CompactButton(text = "＋", onClick = onCreate)
                CompactButton(text = "↶", onClick = onUndo, enabled = undoCount > 0)
                CompactButton(text = "⚙", onClick = onPanel)
            }
        }
    }
}

// ------------------------------------------------------------------ point bubble

/**
 * Editing lives where the point is: tap a dot on screen and this small card opens next to it.
 * No second page, no list of rows to hunt through.
 */
@Composable
fun PointBubbleContent(
    index: Int,
    point: ClickPoint,
    defaultPress: Int,
    onPress: (Int) -> Unit,
    onDelay: (Int) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    Card(cornerRadius = 20.dp, contentPadding = PaddingValues(0.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "点位 $index (${point.x}, ${point.y})")
                Spacer(Modifier.weight(1f))
                CompactButton(text = "完成", onClick = onClose, primary = true)
            }
            SliderRow(
                title = "点击时长",
                value = point.pressMs,
                range = 0..10_000,
                step = 50,
                format = { if (it == 0) "用默认 ${formatMs(defaultPress)}" else formatMs(it) },
                onChange = onPress,
            )
            SliderRow(
                title = "点击延迟",
                value = point.delayMs,
                range = 0..10_000,
                step = 50,
                format = { if (it == 0) "不等待" else "点它前等 ${formatMs(it)}" },
                onChange = onDelay,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactButton(
                    text = if (point.enabled) "停用" else "启用",
                    onClick = { onToggleEnabled(!point.enabled) },
                )
                Spacer(Modifier.weight(1f))
                CompactButton(text = "删除", onClick = onDelete)
            }
        }
    }
}

// ------------------------------------------------------------------ settings panel

@Composable
fun PanelContent(
    state: OverlayPanelState,
    onClose: () -> Unit,
    onCreate: () -> Unit,
    onToast: (String) -> Unit,
    onTestTap: (ClickPoint) -> Unit,
    onGrabColor: () -> Unit,
    onHideOverlay: () -> Unit,
) {
    val data by ClickerStore.data.collectAsState()
    val undoCount by ClickerStore.undoCount.collectAsState()
    val profile = data.profile

    // Page background with ListCard groups on top: the same layout a ZeroUI settings page uses,
    // just inside a floating window instead of an activity.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(pageBackground()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "快捷设置")
                Spacer(Modifier.weight(1f))
                CompactButton(text = "收起", onClick = onClose)
            }

            // ---- templates
            SectionTitle("模板")
            ListCard(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Templates.all(data.templates).forEach { template: ClickTemplate ->
                        CompactButton(
                            text = template.name,
                            primary = template.id == data.activeTemplateId,
                            onClick = {
                                ClickerStore.applyTemplate(template)
                                onToast("已套用：${template.name}")
                            },
                        )
                    }
                }
            }

            // ---- mode
            SectionTitle("点击模式")
            ListCard(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
                TabRow(
                    tabs = ClickMode.entries.map { modeName(it) },
                    selectedTabIndex = profile.mode.ordinal,
                    onTabSelected = { index -> ClickerStore.setMode(ClickMode.entries[index]) },
                    modifier = Modifier.padding(12.dp),
                )
            }

            // ---- timing
            SectionTitle("节奏")
            ListCard(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
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
                    title = "循环次数",
                    value = profile.repeatCount,
                    range = 0..1_000,
                    step = 1,
                    format = { if (it == 0) "无限" else "$it 次" },
                    onChange = { value -> ClickerStore.updateProfile { it.copy(repeatCount = value) } },
                )
            }

            // ---- points
            SectionTitle("点位 (${profile.points.size})")
            ListCard(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
                ListRow(
                    title = "＋ 取点",
                    summary = "点一下就开始，直接在屏幕上点；点完按「完成」或音量下键",
                    onClick = onCreate,
                )
                ListRow(
                    title = "改单个点位",
                    summary = "直接点屏幕上的圆点，改它的点击时长和延迟",
                    onClick = onCreate,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CompactButton(
                        text = if (undoCount > 0) "↶ 撤销 ($undoCount)" else "↶ 撤销",
                        enabled = undoCount > 0,
                        onClick = { onToast(ClickerStore.undo() ?: "没有可撤销的操作") },
                    )
                    CompactButton(
                        text = "清空",
                        enabled = profile.points.isNotEmpty(),
                        onClick = {
                            ClickerStore.clearPoints()
                            onToast("已清空，可撤销")
                        },
                    )
                    CompactButton(
                        text = "测试",
                        enabled = profile.points.isNotEmpty(),
                        onClick = { profile.points.firstOrNull()?.let(onTestTap) },
                    )
                }
            }

            // ---- condition
            SectionTitle("条件点击")
            ListCard(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
                SwitchPreference(
                    checked = profile.condition == ConditionKind.COLOR,
                    onCheckedChange = { on ->
                        ClickerStore.setCondition(if (on) ConditionKind.COLOR else ConditionKind.NONE)
                    },
                    title = "颜色出现后才点",
                    summary = if (profile.condition == ConditionKind.COLOR) {
                        "${ScreenSampler.toHex(profile.color.color)} @ (${profile.color.x}, ${profile.color.y}) · 容差 ${profile.color.tolerance}"
                    } else {
                        "关闭时按节奏一直点"
                    },
                )
                if (profile.condition == ConditionKind.COLOR) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(profile.color.color)),
                        )
                        CompactButton(text = "屏幕取色", onClick = onGrabColor)
                        Spacer(Modifier.weight(1f))
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
            }

            // ---- misc
            SectionTitle("其他")
            ListCard(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
                SwitchPreference(
                    checked = data.options.showMarkers,
                    onCheckedChange = { shown -> ClickerStore.updateOptions { it.copy(showMarkers = shown) } },
                    title = "显示点位圆点",
                    summary = "关闭后看不到圆点，也就没法点圆点编辑",
                )
                SwitchPreference(
                    checked = data.options.volumeKeysToggle,
                    onCheckedChange = { on -> ClickerStore.updateOptions { it.copy(volumeKeysToggle = on) } },
                    title = "音量键开关面板",
                    summary = "音量上展开 / 音量下收起，取点时音量下＝完成",
                )
                ArrowPreference(
                    title = "关闭悬浮窗",
                    summary = "在应用里可以重新打开",
                    onClick = onHideOverlay,
                )
            }
        }
    }
}

/** Page background that follows the theme, Miuix or Material. */
@Composable
fun pageBackground(): Color =
    if (isMaterial()) MaterialTheme.colorScheme.background else MiuixTheme.colorScheme.background

// ------------------------------------------------------------------ pieces

/** List row whose value slides: the ZeroUI way of showing a number with a range. */
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

fun modeName(mode: ClickMode): String = when (mode) {
    ClickMode.SEQUENTIAL -> "依次"
    ClickMode.SIMULTANEOUS -> "同时"
    ClickMode.RANDOM -> "随机"
}

fun formatMs(value: Int): String =
    if (value >= 1_000) String.format("%.2f 秒", value / 1000f) else "$value ms"
