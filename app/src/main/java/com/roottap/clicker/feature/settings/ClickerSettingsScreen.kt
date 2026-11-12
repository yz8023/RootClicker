package com.roottap.clicker.feature.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zeroui.kit.components.ArrowPreference
import com.zeroui.kit.components.ListCard
import com.zeroui.kit.components.ListRow
import com.zeroui.kit.components.SectionTitle
import com.zeroui.kit.components.SwitchPreference
import com.zeroui.kit.components.Text
import com.zeroui.kit.components.TextButton
import com.zeroui.kit.components.ZeroPageFrame
import com.roottap.clicker.BuildConfig
import com.roottap.clicker.core.ClickerRuntime
import com.roottap.clicker.core.ClickerStore
import com.roottap.clicker.core.RootStatus
import com.roottap.clicker.core.Templates
import com.roottap.clicker.feature.setup.PermissionSection
import com.roottap.clicker.feature.setup.readPermissions
import com.roottap.clicker.feature.common.TextInputDialog
import com.roottap.clicker.service.ClickerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CardModifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
private const val RepoUrl = "https://github.com/yz8023/RootClicker"

/** Third tab: permissions, injector diagnostics and about. */
@Composable
fun ClickerSettingsScreen(bottomPadding: Dp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rootStatus by ClickerRuntime.rootStatus.collectAsState()
    val log by ClickerRuntime.log.collectAsState()
    val engine by ClickerRuntime.engine.state.collectAsState()
    val data by ClickerStore.data.collectAsState()
    val serviceRunning by ClickerService.running.collectAsState()

    val appController = LocalAppController.current
    var checking by remember { mutableStateOf(false) }
    var renamingTemplate by remember { mutableStateOf<com.roottap.clicker.core.ClickTemplate?>(null) }
    var savingTemplate by remember { mutableStateOf(false) }
    var selfTest by remember { mutableStateOf("点一下运行自检：root、注入 dex、注入通道、一次真实点击") }
    var snapshot by remember { mutableStateOf(readPermissions(context)) }

    // Every system permission page comes back here; re-read everything instead of asking the
    // user to press a refresh button.
    LifecycleResumeEffect(Unit) {
        snapshot = readPermissions(context)
        onPauseOrDispose { }
    }

    ZeroPageFrame(title = "设置", bottomInset = bottomPadding) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                PermissionSection(
                    snapshot = snapshot,
                    onRequestRoot = {
                        if (!checking) {
                            checking = true
                            scope.launch {
                                withContext(Dispatchers.IO) { ClickerRuntime.checkRoot() }
                                checking = false
                                snapshot = readPermissions(context)
                            }
                        }
                    },
                    onChecking = checking,
                )
            }

            item { SectionTitle("引导") }
            item {
                ListCard(CardModifier) {
                    ArrowPreference(
                        title = "重新看一遍权限引导",
                        summary = "root / 悬浮窗 / 通知 / 电池白名单 的说明和入口",
                        onClick = { appController.replayWelcome() },
                    )
                }
            }

            item { SectionTitle("模板") }
            item {
                ListCard(CardModifier) {
                    if (data.templates.isEmpty()) {
                        ListRow(
                            title = "还没有自定义模板",
                            summary = "悬浮窗里点「＋ 存为模板」，或在这里保存当前参数",
                        )
                    }
                    data.templates.forEach { template ->
                        ListRow(
                            title = template.name,
                            summary = "间隔 ${template.profile.intervalMs}ms · 按压 ${template.profile.pressMs}ms" +
                                if (template.profile.randomDelayMs > 0) {
                                    " · 随机 0~${template.profile.randomDelayMs}ms"
                                } else {
                                    ""
                                },
                            onClick = { ClickerStore.applyTemplate(template) },
                            endActions = {
                                TextButton(text = "改名", onClick = { renamingTemplate = template })
                                TextButton(text = "删除", onClick = { ClickerStore.deleteTemplate(template.id) })
                            },
                        )
                    }
                    ArrowPreference(
                        title = "把当前参数存为模板",
                        summary = "内置模板不可删除；自定义模板可以改名",
                        onClick = { savingTemplate = true },
                    )
                }
            }

            item { SectionTitle("快捷设置") }
            item {
                ListCard(CardModifier) {
                    ListRow(
                        title = "通知栏磁贴",
                        summary = "下拉通知栏 → 编辑磁贴 → 把「连点器」拖进快捷设置，一点即开始 / 停止",
                    )
                    ListRow(
                        title = "悬浮窗面板",
                        summary = "悬浮条上的 ⚙ 展开全部参数：模式、间隔、随机延迟、按压、循环、条件、模板、取点",
                    )
                }
            }

            item { SectionTitle("引擎") }
            item {
                ListCard(CardModifier) {
                    ListRow(
                        title = "注入通道",
                        summary = "app_process 直注为高速通道，失败时自动退到 input 命令",
                        endActions = { Text(text = ClickerRuntime.sinkDescription) },
                    )
                    ListRow(
                        title = "前台服务",
                        summary = if (serviceRunning) "运行中，可在其他应用上层操作" else "未启动",
                        endActions = {
                            TextButton(
                                text = if (serviceRunning) "停止" else "启动",
                                onClick = {
                                    if (serviceRunning) ClickerService.stop(context)
                                    else ClickerService.start(context)
                                },
                            )
                        },
                    )
                    ListRow(
                        title = "自检 / 测试点击",
                        summary = selfTest,
                        onClick = {
                            scope.launch {
                                selfTest = "检测中…"
                                val size = ClickerRuntime.screenSize(context)
                                val report = withContext(Dispatchers.IO) {
                                    val builder = StringBuilder()
                                    builder.append("root: ")
                                    builder.append(if (com.roottap.clicker.core.RootShell.hasRoot()) "已授权" else "未授权")
                                    builder.append(" · dex: ")
                                    builder.append(
                                        com.roottap.clicker.core.InjectorDex.prepare(context)
                                            ?.substringAfterLast('/') ?: "未找到",
                                    )
                                    val sink = ClickerRuntime.ensureSink()
                                    builder.append(" · 通道: ")
                                    builder.append(sink?.description ?: "不可用")
                                    val sent = sink?.tap(size.x / 2, size.y / 2, 60) ?: false
                                    builder.append(" · 测试点击: ")
                                    builder.append(if (sent) "已下发给 (${size.x / 2}, ${size.y / 2})" else "失败")
                                    builder.toString()
                                }
                                selfTest = report
                            }
                        },
                    )
                    ListRow(
                        title = "重启注入进程",
                        summary = "点击后没反应时可以试一下",
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    ClickerRuntime.shutdown()
                                    ClickerRuntime.ensureSink()
                                }
                            }
                        },
                    )
                    SwitchPreference(
                        checked = data.options.overlayEnabled,
                        onCheckedChange = { enabled ->
                            ClickerStore.updateOptions { it.copy(overlayEnabled = enabled) }
                            if (enabled) ClickerService.start(context)
                        },
                        title = "启用悬浮控制条",
                        summary = "在其他应用上层显示开始 / 停止 / 取点",
                    )
                    SwitchPreference(
                        checked = data.options.volumeKeysToggle,
                        onCheckedChange = { on ->
                            ClickerStore.updateOptions { it.copy(volumeKeysToggle = on) }
                            // Make sure a service is alive to own the key watcher.
                            if (on && !ClickerService.running.value) ClickerService.start(context)
                        },
                        title = "音量键开关设置面板",
                        summary = "音量上 = 展开悬浮设置面板，音量下 = 收起（需要 root）",
                    )
                    SwitchPreference(
                        checked = data.options.stopOnAppSwitch,
                        onCheckedChange = { on ->
                            ClickerStore.updateOptions { it.copy(stopOnAppSwitch = on) }
                        },
                        title = "切到别的应用就停",
                        summary = "连点跑着的时候切走会自动停止，避免点到别的界面",
                    )
                    SwitchPreference(
                        checked = data.options.vibrateOnClick,
                        onCheckedChange = { vibrate ->
                            ClickerStore.updateOptions { it.copy(vibrateOnClick = vibrate) }
                        },
                        title = "点击时震动反馈",
                        summary = "部分机型需在系统设置里允许震动",
                    )
                }
            }

            item { SectionTitle("运行日志") }
            item {
                ListCard(CardModifier) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = if (log.isEmpty()) "暂无日志" else log.take(12).joinToString("\n"),
                        )
                        Spacer(Modifier.height(8.dp))
                        Column {
                            Text(text = "已点击 ${engine.clicks} 次 · 循环 ${engine.cycles} 次")
                            if (engine.message != null) {
                                Text(text = engine.message.orEmpty())
                            }
                        }
                    }
                }
            }

            item { SectionTitle("关于") }
            item {
                ListCard(CardModifier) {
                    ListRow(
                        title = "版本",
                        summary = "Root连点器 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    )
                    ListRow(
                        title = "GitHub 项目",
                        summary = RepoUrl,
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(RepoUrl))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                    )
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = "原理：授权 root 后通过 app_process 直接向 InputManager 注入 " +
                                "MotionEvent，绕开无障碍服务，因此没有每次授权弹窗，" +
                                "也更适合高频连点和多指同点。请仅用于自己可控的场景。",
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    renamingTemplate?.let { template ->
        TextInputDialog(
            title = "模板改名",
            initial = template.name,
            hint = "模板名称",
            onDismiss = { renamingTemplate = null },
            onConfirm = { name ->
                ClickerStore.deleteTemplate(template.id)
                ClickerStore.update { current ->
                    current.copy(
                        templates = current.templates + template.copy(
                            id = System.currentTimeMillis(),
                            name = name,
                        ),
                    )
                }
                renamingTemplate = null
            },
        )
    }

    if (savingTemplate) {
        TextInputDialog(
            title = "保存模板",
            initial = "模板 ${data.templates.size + 1}",
            hint = "模板名称",
            onDismiss = { savingTemplate = false },
            onConfirm = { name ->
                ClickerStore.saveTemplate(name)
                savingTemplate = false
            },
        )
    }
}
