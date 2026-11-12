package com.roottap.clicker.feature.record

import android.app.Activity
import android.os.Handler
import android.os.Looper
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zeroui.kit.components.ButtonVariant
import com.zeroui.kit.components.DropdownPreference
import com.zeroui.kit.components.ListCard
import com.zeroui.kit.components.ListRow
import com.zeroui.kit.components.SectionTitle
import com.zeroui.kit.components.Text
import com.zeroui.kit.components.TextButton
import com.zeroui.kit.components.ZeroPageFrame
import com.roottap.clicker.core.ClickerRuntime
import com.roottap.clicker.core.ClickerStore
import com.roottap.clicker.core.GestureRecorder
import com.roottap.clicker.core.RecordedAction
import com.roottap.clicker.core.RecordedScript
import com.roottap.clicker.core.RootShell
import com.roottap.clicker.core.ScriptPlayer
import com.roottap.clicker.feature.common.NumberDialog
import com.roottap.clicker.feature.common.TextInputDialog
import com.roottap.clicker.service.ClickerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CardModifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)

/** Recording page: capture gestures with root, then replay them. */
@Composable
fun RecordScreen(bottomPadding: Dp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by ClickerStore.data.collectAsState()
    val engine by ClickerRuntime.engine.state.collectAsState()

    var recording by remember { mutableStateOf(false) }
    var remaining by remember { mutableIntStateOf(0) }
    var durationSec by remember { mutableIntStateOf(15) }
    var status by remember { mutableStateOf("准备就绪") }
    var actions by remember { mutableStateOf<List<RecordedAction>>(emptyList()) }
    var session by remember { mutableStateOf<GestureRecorder.Session?>(null) }
    var recordJob by remember { mutableStateOf<Job?>(null) }
    var pendingActions by remember { mutableStateOf<List<RecordedAction>?>(null) }
    var showDuration by remember { mutableStateOf(false) }

    fun finishRecording() {
        session?.stop()
        session = null
        recording = false
        remaining = 0
        val captured = actions
        status = if (captured.isEmpty()) "没有录到任何手势" else "录制完成：${captured.size} 个动作"
        if (captured.isNotEmpty()) pendingActions = captured
    }

    fun startRecording() {
        if (recording) return
        recordJob = scope.launch {
            status = "正在检测触摸设备…"
            val granted = withContext(Dispatchers.IO) { RootShell.hasRoot() }
            if (!granted) {
                status = "没有 root 权限，无法读取触摸设备"
                return@launch
            }
            val device = withContext(Dispatchers.IO) { GestureRecorder.findTouchDevice() }
            if (device == null) {
                status = "没有找到多点触控设备，请确认 root 可用"
                return@launch
            }
            val main = Handler(Looper.getMainLooper())
            actions = emptyList()
            val created = GestureRecorder.Session(
                device = device,
                onAction = { action ->
                    main.post { actions = actions + action }
                },
                onError = { message ->
                    main.post {
                        status = message
                        recording = false
                    }
                },
            )
            session = created
            if (!created.start()) {
                status = "录制启动失败"
                return@launch
            }
            recording = true
            status = "${device.name} · 即将切到桌面，请在目标应用里操作"
            // Give the user a moment to leave this app before gestures start counting.
            delay(1200)
            (context as? Activity)?.moveTaskToBack(true)
            for (left in durationSec downTo 1) {
                remaining = left
                delay(1000)
                if (!recording) return@launch
            }
            finishRecording()
        }
    }

    fun stopRecording() {
        recordJob?.cancel()
        recordJob = null
        finishRecording()
    }

    ZeroPageFrame(title = "录制", bottomInset = bottomPadding) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            item { SectionTitle("手势录制") }
            item {
                ListCard(CardModifier) {
                    ListRow(
                        title = if (recording) "录制中 · 剩余 ${remaining}s" else "录制状态",
                        summary = status,
                    )
                    ListRow(title = "录制时长", summary = "到时间后自动结束并保存为脚本", onClick = { showDuration = true }, endActions = {
                        Text(text = "${durationSec} 秒")
                    })
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        TextButton(
                            text = if (recording) "停止并保存" else "开始录制",
                            variant = ButtonVariant.Primary,
                            onClick = { if (recording) stopRecording() else startRecording() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "录制通过 root 读取 /dev/input 的原始触摸事件，不需要无障碍；" +
                                "支持点击、长按和滑动，多指手势按第一根手指记录。",
                        )
                        if (actions.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(text = "本次已录到 ${actions.size} 个动作")
                        }
                    }
                }
            }

            item { SectionTitle("脚本 (${data.scripts.size})") }
            item {
                ListCard(CardModifier) {
                    if (data.scripts.isEmpty()) {
                        ListRow(
                            title = "还没有脚本",
                            summary = "录一段手势后会出现在这里",
                        )
                    }
                    data.scripts.sortedByDescending { it.createdAt }.forEach { script ->
                        val playing = engine.playingScript && data.activeScriptId == script.id
                        ListRow(
                            title = script.name,
                            summary = "${script.actions.size} 个动作 · ${script.totalMs / 1000.0} 秒 · " +
                                SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(script.createdAt)),
                            onClick = { ClickerStore.setActiveScript(script.id) },
                            endActions = {
                                TextButton(
                                    text = when {
                                        playing -> "停止"
                                        else -> "播放"
                                    },
                                    variant = if (playing) ButtonVariant.Secondary else ButtonVariant.Primary,
                                    onClick = {
                                        if (playing) {
                                            ScriptPlayer.stop()
                                        } else {
                                            ClickerStore.setActiveScript(script.id)
                                            ClickerService.start(context)
                                            ScriptPlayer.play(context, script)
                                        }
                                    },
                                )
                                TextButton(
                                    text = "删除",
                                    onClick = { ClickerStore.removeScript(script.id) },
                                )
                            },
                        )
                    }
                }
            }

            item { SectionTitle("播放设置") }
            item {
                ListCard(CardModifier) {
                    val speeds = listOf(0.5f, 1f, 1.5f, 2f, 3f)
                    DropdownPreference(
                        items = speeds.map { "${it}x" },
                        selectedIndex = speeds.indexOfFirst { it == data.options.scriptSpeed }
                            .coerceAtLeast(1),
                        onSelectedIndexChange = { index ->
                            ClickerStore.updateOptions { it.copy(scriptSpeed = speeds[index]) }
                        },
                        title = "播放速度",
                        summary = "脚本里记录的时间会按倍数缩放",
                    )
                    ListRow(
                        title = "循环次数",
                        summary = "播放完一遍后是否继续",
                        onClick = { ClickerStore.updateOptions { it.copy(scriptRepeat = if (it.scriptRepeat == 0) 1 else 0) } },
                        endActions = {
                            Text(
                                text = if (data.options.scriptRepeat == 0) "无限" else "${data.options.scriptRepeat} 次",
                            )
                        },
                    )
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(text = "已点击 ${engine.clicks} 次" + if (engine.cycles > 0) " · 第 ${engine.cycles} 轮" else "")
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showDuration) {
        NumberDialog(
            title = "录制时长 (秒)",
            initial = durationSec,
            min = 3,
            max = 300,
            suffix = " 秒",
            onDismiss = { showDuration = false },
            onConfirm = { value ->
                showDuration = false
                durationSec = value
            },
        )
    }

    pendingActions?.let { captured ->
        TextInputDialog(
            title = "保存脚本",
            initial = "录制 " + SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date()),
            hint = "脚本名称",
            onDismiss = { pendingActions = null },
            onConfirm = { name ->
                pendingActions = null
                ClickerStore.addScript(
                    RecordedScript(
                        id = System.currentTimeMillis(),
                        name = name,
                        createdAt = System.currentTimeMillis(),
                        actions = captured,
                    ),
                )
                status = "已保存脚本「$name」"
                actions = emptyList()
            },
        )
    }
}
