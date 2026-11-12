package com.roottap.clicker.feature.setup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.zeroui.kit.components.Badge
import com.zeroui.kit.components.Button
import com.zeroui.kit.components.ButtonVariant
import com.zeroui.kit.components.ListCard
import com.zeroui.kit.components.ListRow
import com.zeroui.kit.components.SectionTitle
import com.zeroui.kit.components.Text
import com.zeroui.kit.components.TextButton
import com.zeroui.kit.components.ZeroPageFrame
import com.roottap.clicker.ZeroApp
import com.roottap.clicker.core.ClickerRuntime
import com.roottap.clicker.core.RootStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How one requirement currently looks. */
enum class PermissionState { Granted, Missing, Optional }

/** Everything the clicker needs, read straight from the system. */
class PermissionSnapshot(
    val root: RootStatus,
    val sink: String,
    val overlay: Boolean,
    val notifications: Boolean,
    val battery: Boolean,
) {
    val missingRequired: List<String>
        get() = buildList {
            if (root != RootStatus.GRANTED) add("root")
            if (!overlay) add("悬浮窗")
            if (!notifications) add("通知")
        }

    val ready: Boolean get() = missingRequired.isEmpty()
}

fun readPermissions(context: Context): PermissionSnapshot = PermissionSnapshot(
    root = ClickerRuntime.rootStatus.value,
    sink = ClickerRuntime.sinkDescription,
    overlay = Settings.canDrawOverlays(context),
    notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED,
    battery = runCatching {
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(false),
)

/** Opens the system page that grants [permission]; every one of them lands back here on resume. */
fun openPermissionPage(context: Context, permission: SetupPermission) {
    val packageUri = Uri.parse("package:${context.packageName}")
    val intent = when (permission) {
        SetupPermission.Overlay -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri)
        SetupPermission.Notifications ->
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        SetupPermission.Battery ->
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
        SetupPermission.AppDetails -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Requirements shown on the setup page. */
enum class SetupPermission { Overlay, Notifications, Battery, AppDetails }

// ------------------------------------------------------------------ rows

/**
 * The permission block. It is used on the first run page and in the settings page, so the wording
 * of "what is missing, why it is needed and where to grant it" only exists once.
 */
@Composable
fun PermissionSection(
    snapshot: PermissionSnapshot,
    onRequestRoot: () -> Unit,
    onChecking: Boolean,
) {
    val context = LocalContext.current
    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    SectionTitle("需要授权的权限")
    ListCard(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
        // ---- root
        ListRow(
            title = "root 权限（必须）",
            summary = when (snapshot.root) {
                RootStatus.GRANTED -> "已授权 · ${snapshot.sink}"
                RootStatus.DENIED -> "未授权。点这一行会再次申请，root 管理器里选「允许」"
                RootStatus.UNKNOWN -> "还没检测。点这一行开始检测，root 管理器里选「允许」"
            },
            onClick = onRequestRoot,
            endActions = {
                if (onChecking) {
                    Text(text = "检测中…")
                } else {
                    Badge(
                        text = if (snapshot.root == RootStatus.GRANTED) "已授权" else "需要授权",
                        accent = snapshot.root == RootStatus.GRANTED,
                    )
                }
            },
        )
        // ---- overlay
        ListRow(
            title = "悬浮窗权限（必须）",
            summary = if (snapshot.overlay) {
                "已授权 · 控制条才能显示在其他应用上面"
            } else {
                "未授权。点这一行去打开「显示在其他应用上层 / 悬浮窗」"
            },
            onClick = { openPermissionPage(context, SetupPermission.Overlay) },
            endActions = {
                Badge(
                    text = if (snapshot.overlay) "已授权" else "需要授权",
                    accent = snapshot.overlay,
                )
            },
        )
        // ---- notifications
        ListRow(
            title = "通知权限（必须）",
            summary = if (snapshot.notifications) {
                "已授权 · 前台服务需要一条常驻通知"
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                "未授权。点这一行允许通知，否则连点会被系统悄悄掐掉"
            } else {
                "系统版本低于 Android 13，不需要单独授权"
            },
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !snapshot.notifications) {
                    notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openPermissionPage(context, SetupPermission.Notifications)
                }
            },
            endActions = {
                Badge(
                    text = if (snapshot.notifications) "已授权" else "需要授权",
                    accent = snapshot.notifications,
                )
            },
        )
    }

    SectionTitle("建议开启")
    ListCard(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
        ListRow(
            title = "电池优化白名单",
            summary = if (snapshot.battery) {
                "已加入白名单 · 后台连点不会被省电策略打断"
            } else {
                "未加入。长时间连点建议加入，否则息屏后可能被系统冻结"
            },
            onClick = { openPermissionPage(context, SetupPermission.Battery) },
            endActions = {
                Badge(
                    text = if (snapshot.battery) "已加入" else "未加入",
                    accent = snapshot.battery,
                )
            },
        )
        ListRow(
            title = "自启动 / 后台弹出界面",
            summary = "小米、OPPO、vivo 等系统还要在应用详情里允许自启动和后台弹出，否则悬浮窗会被拦",
            onClick = { openPermissionPage(context, SetupPermission.AppDetails) },
            endActions = { Text(text = "去设置") },
        )
    }
}

// ------------------------------------------------------------------ first run page

/**
 * Replaces the template welcome flow: one short page that says exactly which permissions are
 * needed, why, and takes the user straight to the system dialog for each of them. Coming back
 * from a system page re-checks everything automatically.
 */
@Composable
fun SetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf(readPermissions(context)) }
    var checkingRoot by remember { mutableStateOf(false) }

    fun refresh() {
        snapshot = readPermissions(context)
    }

    fun requestRoot() {
        if (checkingRoot) return
        checkingRoot = true
        scope.launch {
            withContext(Dispatchers.IO) { ClickerRuntime.checkRoot() }
            checkingRoot = false
            refresh()
        }
    }

    // Coming back from any system permission page refreshes the whole list, the user never has to
    // hunt for a "re-check" button.
    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose { }
    }

    LaunchedEffect(Unit) {
        if (ClickerRuntime.rootStatus.value == RootStatus.UNKNOWN) requestRoot()
    }

    ZeroPageFrame(title = "开始使用") { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
        ) {
            Text(
                text = "连点器靠 root 直接注入触摸事件，不用无障碍。下面几项给上就能用，" +
                    "点每一行会跳到对应的地方，回来会自动重新检测。",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            )

            if (snapshot.ready) {
                SectionTitle("状态")
                ListCard(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
                    ListRow(
                        title = "都准备好了",
                        summary = "root、悬浮窗、通知都已经授权",
                        endActions = { Badge(text = "就绪", accent = true) },
                    )
                }
            }

            SectionTitle("需要授权的权限")
            PermissionSection(
                snapshot = snapshot,
                onRequestRoot = { requestRoot() },
                onChecking = checkingRoot,
            )

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    text = "重新检测",
                    onClick = { requestRoot() },
                    modifier = Modifier.weight(1f),
                    minHeight = 46.dp,
                )
                Button(
                    onClick = {
                        (context.applicationContext as? ZeroApp)?.container?.settings?.update {
                            it.copy(welcomeCompleted = true)
                        }
                        onDone()
                    },
                    variant = ButtonVariant.Primary,
                    modifier = Modifier.weight(1f),
                    minHeight = 46.dp,
                ) {
                    Text(text = if (snapshot.ready) "开始使用" else "先这样，继续")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** One line for the home page: what is still missing, with a way to fix it. */
@Composable
fun MissingPermissionsRow(
    snapshot: PermissionSnapshot,
    onCheck: () -> Unit,
) {
    if (snapshot.ready) return
    val missing = snapshot.missingRequired
    ListRow(
        title = "还差 ${missing.size} 项权限",
        summary = missing.joinToString(" / ") + " · 点这一行重新检测",
        onClick = onCheck,
        endActions = { Badge(text = "待处理", accent = false) },
    )
}

/** Padding used by the setup page content when it is embedded somewhere else. */
val SetupContentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
