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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zeroui.kit.components.ArrowPreference
import com.zeroui.kit.components.ButtonVariant
import com.zeroui.kit.components.ListCard
import com.zeroui.kit.components.ListRow
import com.zeroui.kit.components.SectionTitle
import com.zeroui.kit.components.SwitchPreference
import com.zeroui.kit.components.Text
import com.zeroui.kit.components.TextButton
import com.zeroui.kit.components.ZeroPageFrame
import com.roottap.clicker.core.ClickPoint
import com.roottap.clicker.core.ClickerRuntime
import com.roottap.clicker.core.ClickerStore
import com.roottap.clicker.core.RootStatus
import com.roottap.clicker.core.Templates
import com.roottap.clicker.feature.common.PointDialog
import com.roottap.clicker.feature.common.TextInputDialog
import com.roottap.clicker.service.ClickerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CardModifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)

/**
 * Home page. Deliberately small: status, the big start button, templates and the point list.
 * Every timing knob lives in the floating panel instead, so this page stays readable while the
 * user is in another app.
 */
@Composable
fun ClickScreen(bottomPadding: Dp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by ClickerStore.data.collectAsState()
    val engine by ClickerRuntime.engine.state.collectAsState()
    val rootStatus by ClickerRuntime.rootStatus.collectAsState()
    val serviceRunning by ClickerService.running.collectAsState()
    val undoCount by ClickerStore.undoCount.collectAsState()
    val profile = data.profile
    val screen = remember { ClickerRuntime.screenSize(context) }

    var editPoint by remember { mutableStateOf<ClickPoint?>(null) }
    var saveTemplate by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (rootStatus == RootStatus.UNKNOWN) ClickerRuntime.checkRoot()
    }

    fun openPicker() {
        if (!Settings.canDrawOverlays(context)) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }
        ClickerService.start(context)
        runCatching {
            context.startService(
                Intent(context, ClickerService::class.java).setAction(ClickerService.ActionPick),
            )
        }
        (context as? Activity)?.moveTaskToBack(true)
    }

    fun startClicking() {
        ClickerStore.normalizeTo(screen.x, screen.y)
        ClickerService.start(context)
        ClickerRuntime.engine.startProfile({ ClickerStore.data.value.profile }, screen.x, screen.y)
        (context as? Activity)?.moveTaskToBack(true)
    }

    ZeroPageFrame(title = "连点器", bottomInset = bottomPadding) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            item { SectionTitle("运行状态") }
            item {
                ListCard(CardModifier) {
                    ListRow(
                        title = "Root 权限",
                        summary = when (rootStatus) {
                            RootStatus.GRANTED -> "已授权 · ${ClickerRuntime.sinkDescription}"
                            RootStatus.DENIED -> "未授权 · 点按重新检测"
                            RootStatus.UNKNOWN -> "点按检测 root 状态"
                        },
                        endActions = {
                            StatusPill(
                                if (rootStatus == RootStatus.GRANTED) "正常" else "待授权",
                                rootStatus == RootStatus.GRANTED,
                            )
                        },
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
                                if (!Settings.canDrawOverlays(context)) {
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}"),
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                } else {
                                    ClickerService.start(context)
                                }
                            } else {
                                ClickerService.stop(context)
                            }
                        },
                        title = "悬浮窗控制条",
                        summary = "参数、模板、点位都在悬浮窗 ⚙ 里设置",
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
                                    if (engine.running) ClickerRuntime.engine.stop("已停止") else startClicking()
                                },
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                text = "取点",
                                onClick = { openPicker() },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton(
                                text = "撤销",
                                onClick = { message = ClickerStore.undo() ?: "没有可撤销的操作" },
                                modifier = Modifier.weight(1f),
                                enabled = undoCount > 0,
                            )
                            TextButton(
                                text = "清空点位",
                                onClick = { ClickerStore.clearPoints() },
                                modifier = Modifier.weight(1f),
                                enabled = profile.points.isNotEmpty(),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "取点、间隔、按压、随机延迟、条件点击、模板切换都在悬浮窗里：" +
                                "点悬浮条上的 ⚙ 展开。",
                        )
                        if (serviceRunning) {
                            Text(text = "前台服务运行中，退出应用后仍会继续连点")
                        }
                        if (message != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(text = message.orEmpty())
                        }
                    }
                }
            }

            item { SectionTitle("模板") }
            item {
                ListCard(CardModifier) {
                    val templates = Templates.all(data.templates)
                    val active = data.activeTemplateId
                    templates.forEach { template ->
                        ListRow(
                            title = template.name,
                            summary = describeTemplate(template.profile.mode, template.profile.intervalMs, template.profile.pressMs) +
                                if (template.profile.randomDelayMs > 0) " · 随机 0~${template.profile.randomDelayMs}ms" else "",
                            onClick = {
                                ClickerStore.applyTemplate(template)
                                message = "已套用模板「${template.name}」"
                            },
                            endActions = {
                                Text(
                                    text = if (template.id == active) "使用中" else "套用",
                                    color = if (template.id == active) Color(0xFF12A150) else Color.Unspecified,
                                )
                            },
                        )
                    }
                    ArrowPreference(
                        title = "把当前参数存为模板",
                        summary = "只保存参数，点位保持不变",
                        onClick = { saveTemplate = true },
                    )
                }
            }

            item { SectionTitle("点位 (${profile.points.size})") }
            item {
                ListCard(CardModifier) {
                    if (profile.points.isEmpty()) {
                        ListRow(
                            title = "还没有点位",
                            summary = "点「取点」后应用会退到后台，在目标界面点一下就是一个点位",
                        )
                    }
                    profile.points.forEachIndexed { index, point ->
                        ListRow(
                            title = "点位 ${index + 1}",
                            summary = if (point.pressMs > 0) {
                                "(${point.x}, ${point.y}) · 单独按压 ${point.pressMs} ms"
                            } else {
                                "(${point.x}, ${point.y}) · 用默认按压时长"
                            },
                            onClick = { editPoint = point },
                            endActions = {
                                TextButton(text = "删除", onClick = { ClickerStore.removePoint(point.id) })
                            },
                        )
                    }
                    ArrowPreference(
                        title = "手动添加点位",
                        summary = "直接输入坐标，适合固定分辨率",
                        onClick = {
                            ClickerStore.normalizeTo(screen.x, screen.y)
                            ClickerStore.addPoint(ClickPoint(x = screen.x / 2, y = screen.y / 2))
                            message = "已添加屏幕中心的点位，点它可改坐标"
                        },
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    editPoint?.let { point ->
        PointDialog(
            title = "点位 (${point.x}, ${point.y})",
            initialX = point.x,
            initialY = point.y,
            initialPress = point.pressMs,
            showPress = true,
            onDismiss = { editPoint = null },
            onConfirm = { x, y, press ->
                editPoint = null
                ClickerStore.updatePoint(point.copy(x = x, y = y, pressMs = press))
            },
        )
    }

    if (saveTemplate) {
        TextInputDialog(
            title = "保存模板",
            initial = "模板 ${data.templates.size + 1}",
            hint = "模板名称",
            onDismiss = { saveTemplate = false },
            onConfirm = { name ->
                saveTemplate = false
                ClickerStore.saveTemplate(name)
                message = "已保存模板「$name」"
            },
        )
    }
}

private fun describeTemplate(
    mode: com.roottap.clicker.core.ClickMode,
    interval: Int,
    press: Int,
): String {
    val modeName = when (mode) {
        com.roottap.clicker.core.ClickMode.SEQUENTIAL -> "依次"
        com.roottap.clicker.core.ClickMode.SIMULTANEOUS -> "同时"
        com.roottap.clicker.core.ClickMode.RANDOM -> "随机"
    }
    return "$modeName · 间隔 ${interval}ms · 按压 ${press}ms"
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
