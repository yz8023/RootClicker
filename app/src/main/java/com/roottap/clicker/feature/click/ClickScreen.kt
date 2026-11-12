package com.roottap.clicker.feature.click

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zeroui.kit.components.ArrowPreference
import com.zeroui.kit.components.ButtonVariant
import com.zeroui.kit.components.DropdownPreference
import com.zeroui.kit.components.ListCard
import com.zeroui.kit.components.ListRow
import com.zeroui.kit.components.SectionTitle
import com.zeroui.kit.components.SwitchPreference
import com.zeroui.kit.components.Text
import com.zeroui.kit.components.TextButton
import com.zeroui.kit.components.ZeroPageFrame
import com.roottap.clicker.core.ClickMode
import com.roottap.clicker.core.ClickPoint
import com.roottap.clicker.core.ClickerRuntime
import com.roottap.clicker.core.ClickerStore
import com.roottap.clicker.core.ConditionKind
import com.roottap.clicker.core.RootStatus
import com.roottap.clicker.core.ScreenSampler
import com.roottap.clicker.feature.common.NumberDialog
import com.roottap.clicker.feature.common.PointDialog
import com.roottap.clicker.service.ClickerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CardModifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)

/** Which numeric preference a dialog is editing. */
private enum class NumField(val title: String, val min: Int, val max: Int, val suffix: String) {
    Interval("点击间隔 (毫秒)", 1, 60_000, " ms"),
    PointGap("点位间隔 (毫秒)", 0, 60_000, " ms"),
    Press("按压时长 (毫秒)", 1, 60_000, " ms"),
    Repeat("循环次数 (0 为无限)", 0, 1_000_000, " 次"),
    StartDelay("启动延迟 (毫秒)", 0, 60_000, " ms"),
    Jitter("随机偏移 (像素)", 0, 200, " px"),
    Tolerance("颜色容差", 0, 120, ""),
    Timeout("条件超时 (毫秒, 0 为一直等)", 0, 600_000, " ms"),
    Poll("采样间隔 (毫秒)", 80, 5_000, " ms"),
}

/** Main page: profile settings, point list and the start / stop button. */
@Composable
fun ClickScreen(bottomPadding: Dp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by ClickerStore.data.collectAsState()
    val engine by ClickerRuntime.engine.state.collectAsState()
    val rootStatus by ClickerRuntime.rootStatus.collectAsState()
    val serviceRunning by ClickerService.running.collectAsState()
    val profile = data.profile
    val screen = remember { ClickerRuntime.screenSize(context) }

    var numField by remember { mutableStateOf<NumField?>(null) }
    var addPoint by remember { mutableStateOf(false) }
    var editPoint by remember { mutableStateOf<ClickPoint?>(null) }
    var pickingColor by remember { mutableStateOf(false) }
    var editColorXY by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (rootStatus == RootStatus.UNKNOWN) ClickerRuntime.checkRoot()
    }

    fun startClicking() {
        ClickerStore.normalizeTo(screen.x, screen.y)
        ClickerService.start(context)
        val current = ClickerStore.data.value
        ClickerRuntime.engine.startProfile(current.profile, screen.x, screen.y)
        // Clicking into this app's own UI is never what the user wants.
        (context as? Activity)?.moveTaskToBack(true)
    }

    fun requestOverlayPermission() {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun openPicker() {
        if (!Settings.canDrawOverlays(context)) {
            requestOverlayPermission()
            return
        }
        ClickerService.start(context)
        runCatching {
            context.startService(
                Intent(context, ClickerService::class.java).setAction(ClickerService.ActionPick),
            )
        }
        // The picker is an overlay window, so it stays reachable over the target app.
        (context as? Activity)?.moveTaskToBack(true)
    }

    ZeroPageFrame(
        title = "连点器",
        bottomInset = bottomPadding,
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            item { SectionTitle("运行状态") }
            item {
                ListCard(CardModifier) {
                    ListRow(
                        title = "Root 权限",
                        summary = when (rootStatus) {
                            RootStatus.GRANTED -> "已授权 · 注入方式：${ClickerRuntime.sinkDescription}"
                            RootStatus.DENIED -> "未授权 · 点按重新检测（需要 Magisk / KernelSU / SuperSU）"
                            RootStatus.UNKNOWN -> "点按检测 root 状态"
                        },
                        endActions = { StatusPill(if (rootStatus == RootStatus.GRANTED) "正常" else "待授权", rootStatus == RootStatus.GRANTED) },
                        onClick = {
                            scope.launch { withContext(Dispatchers.IO) { ClickerRuntime.checkRoot() } }
                        },
                    )
                    ListRow(
                        title = "点击状态",
                        summary = when {
                            engine.running -> "运行中 · 已点击 ${engine.clicks} 次 · 第 ${engine.cycles} 轮"
                            engine.message != null -> engine.message
                            else -> "待机中"
                        },
                        endActions = { StatusPill(if (engine.running) "运行中" else "空闲", engine.running) },
                    )
                    SwitchPreference(
                        checked = data.options.overlayEnabled,
                        onCheckedChange = { enabled ->
                            ClickerStore.updateOptions { it.copy(overlayEnabled = enabled) }
                            if (enabled) {
                                if (!Settings.canDrawOverlays(context)) requestOverlayPermission()
                                else ClickerService.start(context)
                            }
                        },
                        title = "悬浮控制条",
                        summary = "在其他应用上层显示开始 / 停止按钮",
                    )
                    SwitchPreference(
                        checked = data.options.showMarkers,
                        onCheckedChange = { shown ->
                            ClickerStore.updateOptions { it.copy(showMarkers = shown) }
                        },
                        title = "显示点位标记",
                        summary = "在屏幕上标出每个点位的序号",
                    )
                }
            }

            item { SectionTitle("控制") }
            item {
                ListCard(CardModifier) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton(
                                text = if (engine.running) "停止连点" else "开始连点",
                                variant = ButtonVariant.Primary,
                                onClick = {
                                    if (engine.running) {
                                        ClickerRuntime.engine.stop("已停止")
                                    } else {
                                        startClicking()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                text = "悬浮窗取点",
                                onClick = { openPicker() },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton(
                                text = "手动添加点位",
                                onClick = { addPoint = true },
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                text = "清空点位",
                                onClick = { ClickerStore.clearPoints() },
                                modifier = Modifier.weight(1f),
                                enabled = profile.points.isNotEmpty(),
                            )
                        }
                        if (serviceRunning) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "前台服务运行中，退出应用后连点会继续",
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }

            item { SectionTitle("点击参数 (${profile.points.size} 个点位)") }
            item {
                ListCard(CardModifier) {
                    DropdownPreference(
                        items = listOf("依次点击", "同时点击", "随机点击"),
                        selectedIndex = profile.mode.ordinal,
                        onSelectedIndexChange = { index ->
                            ClickerStore.updateProfile {
                                it.copy(mode = ClickMode.entries[index.coerceIn(0, 2)])
                            }
                        },
                        title = "点击模式",
                        summary = "依次逐个点 / 所有点在同一帧按下 / 每轮随机一个点",
                    )
                    DropdownPreference(
                        items = listOf("默认顺序", "随机顺序"),
                        selectedIndex = if (profile.randomOrder) 1 else 0,
                        onSelectedIndexChange = { index ->
                            ClickerStore.updateProfile { it.copy(randomOrder = index == 1) }
                        },
                        title = "点位顺序",
                        summary = "每轮是否打乱点位先后",
                    )
                    NumberRow("点击间隔", "${profile.intervalMs} ms", "两轮点击之间的等待") {
                        numField = NumField.Interval
                    }
                    NumberRow("点位间隔", "${profile.pointGapMs} ms", "同一轮内两个点位之间") {
                        numField = NumField.PointGap
                    }
                    NumberRow("按压时长", "${profile.pressMs} ms", "≥600ms 即为长按") {
                        numField = NumField.Press
                    }
                    NumberRow(
                        "循环次数",
                        if (profile.repeatCount == 0) "无限" else "${profile.repeatCount} 次",
                        "0 表示一直点下去",
                    ) { numField = NumField.Repeat }
                    NumberRow("启动延迟", "${profile.startDelayMs} ms", "点开始后等多久才动手") {
                        numField = NumField.StartDelay
                    }
                    NumberRow("随机偏移", "${profile.jitterPx} px", "每个坐标随机抖动，防检测") {
                        numField = NumField.Jitter
                    }
                }
            }

            item { SectionTitle("点位") }
            item {
                ListCard(CardModifier) {
                    if (profile.points.isEmpty()) {
                        ListRow(
                            title = "还没有点位",
                            summary = "用「悬浮窗取点」在屏幕上点一下即可添加",
                        )
                    }
                    val list = profile.points
                    list.forEachIndexed { index, point ->
                        ListRow(
                            title = "点位 ${index + 1}",
                            summary = if (point.pressMs > 0) {
                                "(${point.x}, ${point.y}) · 按压 ${point.pressMs} ms"
                            } else {
                                "(${point.x}, ${point.y}) · 按压用默认值"
                            },
                            onClick = { editPoint = point },
                            endActions = {
                                TextButton(
                                    text = "删除",
                                    onClick = { ClickerStore.removePoint(point.id) },
                                )
                            },
                        )
                    }
                    ArrowPreference(
                        title = "添加点位",
                        summary = "手动输入坐标，或长按列表用悬浮窗取点",
                        onClick = { addPoint = true },
                    )
                }
            }

            item { SectionTitle("条件点击") }
            item {
                ListCard(CardModifier) {
                    DropdownPreference(
                        items = listOf("无条件 (一直点)", "屏幕颜色匹配"),
                        selectedIndex = if (profile.condition == ConditionKind.NONE) 0 else 1,
                        onSelectedIndexChange = { index ->
                            ClickerStore.updateProfile {
                                it.copy(
                                    condition = if (index == 0) ConditionKind.NONE else ConditionKind.COLOR,
                                )
                            }
                        },
                        title = "触发条件",
                        summary = "颜色条件：检测到指定像素颜色后才开始点击",
                    )
                    if (profile.condition == ConditionKind.COLOR) {
                        ListRow(
                            title = "检测坐标",
                            summary = "(${profile.color.x}, ${profile.color.y})",
                            onClick = { editColorXY = true },
                            endActions = {
                                TextButton(text = "修改", onClick = { editColorXY = true })
                            },
                        )
                        ListRow(
                            title = "目标颜色",
                            summary = ScreenSampler.toHex(profile.color.color),
                            endActions = {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(Color(profile.color.color), CircleShape),
                                )
                                TextButton(
                                    text = if (pickingColor) "取色中" else "取色",
                                    onClick = {
                                        scope.launch {
                                            pickingColor = true
                                            val pixel = withContext(Dispatchers.IO) {
                                                ScreenSampler.sample(profile.color.x, profile.color.y)
                                            }
                                            pickingColor = false
                                            if (pixel != null) {
                                                ClickerStore.updateProfile {
                                                    it.copy(color = it.color.copy(color = pixel))
                                                }
                                            }
                                        }
                                    },
                                )
                            },
                        )
                        NumberRow("颜色容差", "${profile.color.tolerance}", "每个通道允许的误差") {
                            numField = NumField.Tolerance
                        }
                        NumberRow(
                            "条件超时",
                            if (profile.color.timeoutMs == 0) "一直等" else "${profile.color.timeoutMs} ms",
                            "等待颜色出现的最长时间",
                        ) { numField = NumField.Timeout }
                        NumberRow("采样间隔", "${profile.color.pollMs} ms", "screencap 轮询频率") {
                            numField = NumField.Poll
                        }
                        if (profile.color.x == 0 && profile.color.y == 0) {
                            ListRow(
                                title = "提示",
                                summary = "检测坐标还是 (0,0)，请先把坐标改成要观察的像素位置",
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    numField?.let { field ->
        val current = when (field) {
            NumField.Interval -> profile.intervalMs
            NumField.PointGap -> profile.pointGapMs
            NumField.Press -> profile.pressMs
            NumField.Repeat -> profile.repeatCount
            NumField.StartDelay -> profile.startDelayMs
            NumField.Jitter -> profile.jitterPx
            NumField.Tolerance -> profile.color.tolerance
            NumField.Timeout -> profile.color.timeoutMs
            NumField.Poll -> profile.color.pollMs
        }
        NumberDialog(
            title = field.title,
            initial = current,
            min = field.min,
            max = field.max,
            suffix = field.suffix,
            allowSlider = field == NumField.Interval || field == NumField.Press,
            onDismiss = { numField = null },
            onConfirm = { value ->
                numField = null
                ClickerStore.updateProfile { updated ->
                    when (field) {
                        NumField.Interval -> updated.copy(intervalMs = value)
                        NumField.PointGap -> updated.copy(pointGapMs = value)
                        NumField.Press -> updated.copy(pressMs = value)
                        NumField.Repeat -> updated.copy(repeatCount = value)
                        NumField.StartDelay -> updated.copy(startDelayMs = value)
                        NumField.Jitter -> updated.copy(jitterPx = value)
                        NumField.Tolerance -> updated.copy(color = updated.color.copy(tolerance = value))
                        NumField.Timeout -> updated.copy(color = updated.color.copy(timeoutMs = value))
                        NumField.Poll -> updated.copy(color = updated.color.copy(pollMs = value))
                    }
                }
            },
        )
    }

    if (addPoint) {
        PointDialog(
            title = "添加点位",
            initialX = screen.x / 2,
            initialY = screen.y / 2,
            onDismiss = { addPoint = false },
            onConfirm = { x, y ->
                addPoint = false
                ClickerStore.updateProfile { it.copy(baseWidth = screen.x, baseHeight = screen.y) }
                ClickerStore.addPoint(ClickPoint(x = x, y = y))
            },
        )
    }

    if (editColorXY) {
        PointDialog(
            title = "条件检测坐标",
            initialX = profile.color.x,
            initialY = profile.color.y,
            onDismiss = { editColorXY = false },
            onConfirm = { x, y ->
                editColorXY = false
                ClickerStore.updateProfile { it.copy(color = it.color.copy(x = x, y = y)) }
            },
        )
    }

    editPoint?.let { point ->
        PointDialog(
            title = "编辑点位",
            initialX = point.x,
            initialY = point.y,
            onDismiss = { editPoint = null },
            onConfirm = { x, y ->
                editPoint = null
                ClickerStore.updatePoint(point.copy(x = x, y = y))
            },
        )
    }
}

@Composable
private fun NumberRow(title: String, value: String, summary: String, onClick: () -> Unit) {
    ListRow(title = title, summary = summary, onClick = onClick, endActions = {
        Text(text = value)
    })
}

@Composable
private fun StatusPill(text: String, positive: Boolean) {
    val color = if (positive) Color(0xFF12A150) else Color(0xFFB8860B)
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text = text, color = color)
    }
}

