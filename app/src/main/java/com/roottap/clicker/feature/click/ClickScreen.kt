package com.roottap.clicker.feature.click

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.zeroui.kit.components.ArrowPreference
import com.zeroui.kit.components.Badge
import com.zeroui.kit.components.Button
import com.zeroui.kit.components.ButtonVariant
import com.zeroui.kit.components.Icon
import com.zeroui.kit.components.IconButton
import com.zeroui.kit.components.ListCard
import com.zeroui.kit.components.ListRow
import com.zeroui.kit.components.SectionTitle
import com.zeroui.kit.components.Text
import com.zeroui.kit.components.ZeroPageFrame
import com.roottap.clicker.R
import com.roottap.clicker.core.ClickPoint
import com.roottap.clicker.core.ClickerRuntime
import com.roottap.clicker.core.ClickerStore
import com.roottap.clicker.core.RootStatus
import com.roottap.clicker.core.Shortcuts
import com.roottap.clicker.core.Templates
import com.roottap.clicker.feature.common.PointDialog
import com.roottap.clicker.feature.common.TextInputDialog
import com.roottap.clicker.feature.common.formatMs
import com.roottap.clicker.feature.setup.readPermissions
import com.roottap.clicker.service.ClickerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CardModifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)

/**
 * Home page. Three blocks only: what the app is doing, the buttons that matter, and the lists -
 * every row says in words what it is, so nothing has to be guessed from a name.
 */
@Composable
fun ClickScreen(
    bottomPadding: Dp,
    onOpenSetup: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by ClickerStore.data.collectAsState()
    val engine by ClickerRuntime.engine.state.collectAsState()
    val rootStatus by ClickerRuntime.rootStatus.collectAsState()
    val undoCount by ClickerStore.undoCount.collectAsState()
    val profile = data.profile
    val screen = remember { ClickerRuntime.screenSize(context) }

    var permissions by remember { mutableStateOf(readPermissions(context)) }
    var editPoint by remember { mutableStateOf<ClickPoint?>(null) }
    var savePlan by remember { mutableStateOf(false) }
    var saveTemplate by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (rootStatus == RootStatus.UNKNOWN) ClickerRuntime.checkRoot()
    }

    // Coming back from a system permission page re-reads everything.
    LifecycleResumeEffect(Unit) {
        permissions = readPermissions(context)
        onPauseOrDispose { }
    }

    fun recheck() {
        scope.launch {
            withContext(Dispatchers.IO) { ClickerRuntime.checkRoot() }
            permissions = readPermissions(context)
            message = if (permissions.ready) "权限都齐了" else "还差：${permissions.missingRequired.joinToString(" / ")}"
        }
    }

    fun pick(action: String) {
        if (!Settings.canDrawOverlays(context)) {
            onOpenSetup()
            message = "先给悬浮窗权限才能取点"
            return
        }
        ClickerService.start(context)
        runCatching {
            context.startService(
                Intent(context, ClickerService::class.java).setAction(action),
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
            // ---- one status line, nothing else
            item { SectionTitle("状态") }
            item {
                ListCard(CardModifier) {
                    ListRow(
                        title = when {
                            !permissions.ready -> "还差 ${permissions.missingRequired.size} 项权限"
                            engine.running -> "运行中 · 已点 ${engine.clicks} 次"
                            else -> "准备好了"
                        },
                        summary = when {
                            !permissions.ready ->
                                "缺：${permissions.missingRequired.joinToString(" / ")} · 点这一行进权限页"
                            engine.running ->
                                "第 ${engine.cycles} 轮 · ${profile.points.size} 个点位 · 点这里看详情"
                            else ->
                                "root ${if (rootStatus == RootStatus.GRANTED) "已授权" else "未授权"} · " +
                                    "${profile.points.size} 个点位 · 点这一行重新检测"
                        },
                        onClick = { if (permissions.ready) recheck() else onOpenSetup() },
                        endActions = {
                            Badge(
                                text = when {
                                    !permissions.ready -> "去授权"
                                    engine.running -> "运行中"
                                    else -> "就绪"
                                },
                                accent = permissions.ready && !engine.running,
                            )
                        },
                    )
                }
            }

            // ---- the only two actions that matter, plus undo
            item { SectionTitle("操作") }
            item {
                ListCard(CardModifier) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Button(
                            onClick = {
                                if (engine.running) {
                                    ClickerRuntime.engine.stop("已停止")
                                } else {
                                    if (permissions.missingRequired.contains("root")) {
                                        onOpenSetup()
                                        message = "还没有 root，先授权"
                                    } else {
                                        startClicking()
                                    }
                                }
                            },
                            variant = ButtonVariant.Primary,
                            modifier = Modifier.fillMaxWidth(),
                            minHeight = 54.dp,
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (engine.running) R.drawable.ic_stop else R.drawable.ic_play,
                                ),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = if (engine.running) "  停止连点" else "  开始连点",
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionButton(
                                icon = R.drawable.ic_tap,
                                label = "取点",
                                modifier = Modifier.weight(1f),
                                onClick = { pick(ClickerService.ActionPick) },
                            )
                            ActionButton(
                                icon = R.drawable.ic_swipe,
                                label = "取滑动",
                                modifier = Modifier.weight(1f),
                                onClick = { pick(ClickerService.ActionPickSwipe) },
                            )
                            ActionButton(
                                icon = R.drawable.ic_undo,
                                label = "撤销",
                                enabled = undoCount > 0,
                                modifier = Modifier.weight(1f),
                                onClick = { message = ClickerStore.undo() ?: "没有可撤销的操作" },
                            )
                        }
                        if (message != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(text = message.orEmpty())
                        }
                    }
                }
            }

            // ---- points, each row spelled out
            item { SectionTitle("点位 (${profile.points.size})") }
            item {
                ListCard(CardModifier) {
                    if (profile.points.isEmpty()) {
                        ListRow(
                            title = "还没有点位",
                            summary = "点上面的「取点」，然后在屏幕上点一下就是 1 个点位；「取滑动」点两下画出 A→B",
                        )
                    }
                    profile.points.forEachIndexed { index, point ->
                        ListRow(
                            title = "点位 ${index + 1} · ${point.kindName} ${point.label}",
                            summary = buildString {
                                if (point.isSwipe) {
                                    append("滑动用时 ${formatMs(point.swipeMs)}")
                                } else {
                                    append(
                                        if (point.pressMs > 0) "点击时长 ${formatMs(point.pressMs)}"
                                        else "点击时长 默认 ${formatMs(profile.pressMs)}",
                                    )
                                }
                                append(" · 延迟 ")
                                append(if (point.delayMs > 0) formatMs(point.delayMs) else "无")
                                if (!point.enabled) append(" · 已停用")
                                append(" · 点这一行改")
                            },
                            startAction = {
                                Icon(
                                    painter = painterResource(
                                        if (point.isSwipe) R.drawable.ic_swipe else R.drawable.ic_tap,
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            onClick = { editPoint = point },
                            endActions = {
                                IconButton(
                                    onClick = {
                                        ClickerStore.removePoint(point.id)
                                        message = "已删除点位 ${index + 1}，可撤销"
                                    },
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_delete),
                                        contentDescription = "删除点位 ${index + 1}",
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            },
                        )
                    }
                    ArrowPreference(
                        title = "手动加一个点位",
                        summary = "不用取点，直接放在屏幕正中间",
                        onClick = {
                            ClickerStore.normalizeTo(screen.x, screen.y)
                            ClickerStore.addPoint(ClickPoint(x = screen.x / 2, y = screen.y / 2))
                            message = "已在屏幕中心加点，点它可改坐标"
                        },
                    )
                }
            }

            // ---- plans
            item { SectionTitle("方案 (${data.plans.size})") }
            item {
                ListCard(CardModifier) {
                    if (data.plans.isEmpty()) {
                        ListRow(
                            title = "还没有方案",
                            summary = "方案 = 现在屏幕上的点位 + 全部参数。游戏 A / 游戏 B 各存一个，一键切换",
                        )
                    }
                    data.plans.forEach { plan ->
                        val active = plan.id == data.activePlanId
                        ListRow(
                            title = plan.name,
                            summary = "${plan.pointCount} 个点位 · ${modeName(plan.profile.mode)}" +
                                " · 间隔 ${plan.profile.intervalMs}ms" +
                                " · 按压 ${formatMs(plan.profile.pressMs)}" +
                                if (plan.profile.randomDelayMs > 0) " · 随机 +0~${plan.profile.randomDelayMs}ms" else "",
                            onClick = {
                                ClickerStore.applyPlan(plan)
                                message = "已套用方案「${plan.name}」"
                            },
                            endActions = {
                                if (active) Badge(text = "使用中", accent = true) else Text(text = "套用")
                            },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ActionButton(
                            icon = R.drawable.ic_check,
                            label = "存为方案",
                            modifier = Modifier.weight(1f),
                            onClick = { savePlan = true },
                        )
                        ActionButton(
                            icon = R.drawable.ic_app,
                            label = "桌面图标",
                            enabled = data.activePlanId != 0L,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val plan = data.plans.firstOrNull { it.id == data.activePlanId }
                                if (plan != null) {
                                    message = if (Shortcuts.pinPlan(context, plan)) {
                                        "已请求添加桌面快捷方式"
                                    } else {
                                        "这个桌面不支持固定快捷方式"
                                    }
                                }
                            },
                        )
                        ActionButton(
                            icon = R.drawable.ic_delete,
                            label = "删方案",
                            enabled = data.activePlanId != 0L,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                ClickerStore.deletePlan(data.activePlanId)
                                message = "已删除方案，屏幕上的点位还在"
                            },
                        )
                    }
                }
            }

            // ---- templates, each explained
            item { SectionTitle("模板 (${Templates.all(data.templates).size})") }
            item {
                ListCard(CardModifier) {
                    ListRow(
                        title = "模板只换参数，不动点位",
                        summary = "下面每个模板都写了它是干什么用的，点一下直接套用",
                    )
                    Templates.all(data.templates).forEach { template ->
                        val active = template.id == data.activeTemplateId
                        ListRow(
                            title = template.name,
                            summary = template.note.ifBlank {
                                "${modeName(template.profile.mode)} · 间隔 ${template.profile.intervalMs}ms" +
                                    " · 按压 ${formatMs(template.profile.pressMs)}"
                            },
                            onClick = {
                                ClickerStore.applyTemplate(template)
                                message = "已套用模板「${template.name}」"
                            },
                            endActions = {
                                if (active) Badge(text = "使用中", accent = true) else Text(text = "套用")
                            },
                        )
                    }
                    ArrowPreference(
                        title = "把当前参数存成模板",
                        summary = "只保存参数（间隔 / 按压 / 随机），点位保持不变",
                        onClick = { saveTemplate = true },
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    editPoint?.let { point ->
        PointDialog(
            title = "点位 · ${point.kindName}",
            initialX = point.x,
            initialY = point.y,
            initialPress = point.pressMs,
            initialDelay = point.delayMs,
            initialSwipeMs = point.swipeMs,
            swipe = point.isSwipe,
            onDismiss = { editPoint = null },
            onConfirm = { x, y, press, delay, swipeMs ->
                editPoint = null
                ClickerStore.updatePoint(
                    point.copy(
                        x = x,
                        y = y,
                        pressMs = press,
                        delayMs = delay,
                        swipeMs = swipeMs,
                    ),
                )
            },
        )
    }

    if (savePlan) {
        TextInputDialog(
            title = "保存方案",
            initial = "方案 ${data.plans.size + 1}",
            hint = "方案名称",
            onDismiss = { savePlan = false },
            onConfirm = { name ->
                savePlan = false
                ClickerStore.savePlan(name)
                message = "已保存方案「$name」"
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

/** Secondary action with a drawn icon and a word next to it. */
@Composable
private fun ActionButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        variant = ButtonVariant.Secondary,
        modifier = modifier,
        minHeight = 44.dp,
        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(17.dp),
        )
        Text(text = " $label", modifier = Modifier.padding(start = 3.dp))
    }
}

private fun modeName(mode: com.roottap.clicker.core.ClickMode): String = when (mode) {
    com.roottap.clicker.core.ClickMode.SEQUENTIAL -> "依次"
    com.roottap.clicker.core.ClickMode.SIMULTANEOUS -> "同时"
    com.roottap.clicker.core.ClickMode.RANDOM -> "随机"
}
