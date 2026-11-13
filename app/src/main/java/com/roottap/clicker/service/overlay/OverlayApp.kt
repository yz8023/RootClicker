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
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import com.zeroui.kit.components.Icon
import com.zeroui.kit.components.IconButton
import com.roottap.clicker.R
import androidx.compose.ui.unit.dp
import com.zeroui.kit.components.ArrowPreference
import com.zeroui.kit.components.Button
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
    /** Picking swipes: two taps make one A → B gesture. */
    var swipePicking by mutableStateOf(false)
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

/** Every bar action is a drawn icon, never a text glyph. */
@Composable
private fun IconBtn(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    if (primary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            variant = ButtonVariant.Primary,
            minWidth = 40.dp,
            minHeight = 34.dp,
            insideMargin = PaddingValues(horizontal = 10.dp, vertical = 3.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                modifier = Modifier.size(17.dp),
            )
        }
        return
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        minWidth = 36.dp,
        minHeight = 34.dp,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ------------------------------------------------------------------ control bar

/**
 * The bar has exactly two creation buttons: one adds a tap, one draws a swipe (A to B).
 * While picking it keeps the same place and only shows "how many, undo, done".
 */
@Composable
fun ControlBarContent(
    picking: Boolean,
    swipePicking: Boolean,
    colorMode: Boolean,
    running: Boolean,
    clicks: Long,
    pointCount: Int,
    modeName: String,
    undoCount: Int,
    onToggle: () -> Unit,
    onPickTap: () -> Unit,
    onPickSwipe: () -> Unit,
    onUndo: () -> Unit,
    onPanel: () -> Unit,
    onToggleColor: () -> Unit,
    onFinishPicking: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    Card(cornerRadius = 24.dp, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            // Drag grip: the buttons keep working, the grip moves the bar.
            Box(
                modifier = Modifier
                    .size(width = 22.dp, height = 30.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { onDragEnd() },
                            onDrag = { change, drag ->
                                change.consume()
                                onDrag(drag.x, drag.y)
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_grip),
                    contentDescription = "拖动",
                    modifier = Modifier.size(16.dp),
                )
            }
            if (picking) {
                Text(
                    text = when {
                        colorMode -> "点屏幕取色"
                        swipePicking -> "点两下 · A → B"
                        else -> "点屏幕加点 · $pointCount"
                    },
                    modifier = Modifier.padding(end = 2.dp),
                )
                IconBtn(icon = R.drawable.ic_color, label = "取色", onClick = onToggleColor, primary = colorMode)
                IconBtn(icon = R.drawable.ic_undo, label = "撤销", onClick = onUndo, enabled = undoCount > 0)
                IconBtn(icon = R.drawable.ic_check, label = "完成", onClick = onFinishPicking, primary = true)
            } else {
                Text(
                    text = when {
                        running -> "运行 ${clicks} 次"
                        pointCount == 0 -> "先取点"
                        else -> "$pointCount 点 · $modeName"
                    },
                    modifier = Modifier.padding(end = 2.dp),
                )
                IconBtn(
                    icon = if (running) R.drawable.ic_stop else R.drawable.ic_play,
                    label = if (running) "停止" else "开始",
                    onClick = onToggle,
                    primary = true,
                )
                IconBtn(icon = R.drawable.ic_tap, label = "取点", onClick = onPickTap)
                IconBtn(icon = R.drawable.ic_swipe, label = "取滑动", onClick = onPickSwipe)
                IconBtn(icon = R.drawable.ic_undo, label = "撤销", onClick = onUndo, enabled = undoCount > 0)
                IconBtn(icon = R.drawable.ic_settings, label = "设置", onClick = onPanel)
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
    onSwipeMs: (Int) -> Unit,
    onDelay: (Int) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxSize(), cornerRadius = 20.dp, contentPadding = PaddingValues(0.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(if (point.isSwipe) R.drawable.ic_swipe else R.drawable.ic_tap),
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    text = "  点位 $index · ${point.kindName}",
                    modifier = Modifier.padding(end = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                IconBtn(icon = R.drawable.ic_check, label = "完成", onClick = onClose, primary = true)
            }
            SliderRow(
                title = point.label,
                value = if (point.isSwipe) point.swipeMs else point.pressMs,
                range = if (point.isSwipe) 20..5_000 else 0..10_000,
                step = if (point.isSwipe) 20 else 50,
                format = {
                    if (point.isSwipe) "滑动用时 ${formatMs(it)}"
                    else if (it == 0) "点击时长 · 用默认 ${formatMs(defaultPress)}"
                    else "点击时长 ${formatMs(it)}"
                },
                onChange = { if (point.isSwipe) onSwipeMs(it) else onPress(it) },
            )
            SliderRow(
                title = "点击延迟",
                value = point.delayMs,
                range = 0..10_000,
                step = 50,
                format = { if (it == 0) "不等待" else "轮到这个点前等 ${formatMs(it)}" },
                onChange = onDelay,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconBtn(
                    icon = if (point.enabled) R.drawable.ic_check else R.drawable.ic_close,
                    label = if (point.enabled) "停用" else "启用",
                    onClick = { onToggleEnabled(!point.enabled) },
                )
                Text(text = if (point.enabled) "已启用" else "已停用")
                Spacer(Modifier.weight(1f))
                IconBtn(icon = R.drawable.ic_delete, label = "删除", onClick = onDelete)
            }
        }
    }
}

// ------------------------------------------------------------------ settings panel

@Composable
fun PanelContent(
    state: OverlayPanelState,
    onClose: () -> Unit,
    onPickTap: () -> Unit,
    onPickSwipe: () -> Unit,
    onToast: (String) -> Unit,
    onTestTap: (ClickPoint) -> Unit,
    onGrabColor: () -> Unit,
    onOpenApp: () -> Unit,
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
                    title = "取点（点一下）",
                    summary = "开始后直接在屏幕上点，点哪加哪；点完按「完成」或音量下键",
                    startAction = {
                        Icon(
                            painter = painterResource(R.drawable.ic_tap),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = onPickTap,
                )
                ListRow(
                    title = "取滑动（A → B）",
                    summary = "屏幕上点两下：第一下是起点，第二下是终点",
                    startAction = {
                        Icon(
                            painter = painterResource(R.drawable.ic_swipe),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = onPickSwipe,
                )
                ListRow(
                    title = "改单个点位",
                    summary = "直接点屏幕上的圆点 / 滑动轨迹的起点圆点，改它的时长和延迟",
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
                    title = "打开应用页面",
                    summary = "看点位、方案、模板和设置",
                    startAction = {
                        Icon(
                            painter = painterResource(R.drawable.ic_app),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = onOpenApp,
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
