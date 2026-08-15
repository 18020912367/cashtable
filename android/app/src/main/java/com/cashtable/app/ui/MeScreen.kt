package com.cashtable.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cashtable.app.BuildConfig
import com.cashtable.app.p4.FloatingBallService
import com.cashtable.app.ui.components.ServerUrlDialog
import com.cashtable.app.ui.theme.PrimaryBlue
import com.cashtable.app.ui.theme.TextGray
import kotlinx.coroutines.launch

/** 我的页：切换记账人、服务器地址、关于。 */
@Composable
fun MeScreen(vm: AppViewModel) {
    var showUserSwitch by remember { mutableStateOf(false) }
    var showServer by remember { mutableStateOf(false) }
    var updateState by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // P4 悬浮球：开关状态（进入页面 + 每次回前台时同步服务真实状态）
    var ballRunning by remember { mutableStateOf(FloatingBallService.isActive) }
    // 自动继续标志：从系统「显示在其他应用上层」权限页返回后自动开启悬浮球。
    // 用 rememberSaveable 而非 remember：返回时 Activity 可能被系统重建，
    // 普通 remember 状态会丢失，导致"返回后不再自动继续、一直未开启且无提示"（0.6.1 问题）。
    var pendingBallStart by rememberSaveable { mutableStateOf(false) }
    // 刚请求启动前台服务（服务启动是异步的，避免 ON_RESUME 同步时误读为 false）
    var justRequestedStart by remember { mutableStateOf(false) }

    fun startFloatingBallService() {
        try {
            FloatingBallService.start(context)
            justRequestedStart = true
            ballRunning = true
        } catch (e: Exception) {
            ballRunning = false
            Toast.makeText(context, "启动悬浮球失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun continueFloatingBallStart() {
        if (!Settings.canDrawOverlays(context)) {
            // 先引导开启悬浮窗权限，授权返回后（ON_RESUME）自动开启悬浮球
            pendingBallStart = true
            try {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            } catch (e: Exception) {
                pendingBallStart = false
                Toast.makeText(context, "无法打开悬浮窗设置：${e.message}", Toast.LENGTH_SHORT).show()
            }
            return
        }
        startFloatingBallService()
    }

    // Android 13+ 前台服务通知权限（拒绝不影响悬浮球功能）；授权结果返回后自动继续后续流程
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        continueFloatingBallStart()
    }

    // P4 悬浮球：从系统「显示在其他应用上层」权限页返回后自动开启，无需再手动点一次。
    // pendingBallStart 用 rememberSaveable：从设置页返回时 Activity 可能被系统重建，
    // 普通 remember 状态会丢失，导致"返回后不再自动继续、一直未开启且无提示"（0.6.1 问题）。
    val activity = context as? ComponentActivity
    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (pendingBallStart) {
                    pendingBallStart = false
                    if (Settings.canDrawOverlays(context)) {
                        startFloatingBallService()
                    } else {
                        Toast.makeText(
                            context,
                            "未检测到悬浮窗权限，请确认已开启「显示在其他应用上层」后重试",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                if (justRequestedStart) {
                    // 服务异步启动中：乐观状态先保持"运行中"，稍后按真实状态收敛
                    justRequestedStart = false
                    Handler(Looper.getMainLooper()).postDelayed({
                        ballRunning = FloatingBallService.isActive
                    }, 500)
                } else {
                    // 同步开关状态（服务可能被系统或其他入口停止）
                    ballRunning = FloatingBallService.isActive
                }
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    fun toggleFloatingBall() {
        if (FloatingBallService.isActive) {
            FloatingBallService.stop(context)
            ballRunning = false
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // 先请求通知权限（弹窗期间不宜再跳设置页，等授权结果返回后继续）
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            continueFloatingBallStart()
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 头部：当前记账人
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column {
                Text("当前记账人", style = MaterialTheme.typography.bodySmall, color = TextGray)
                Text(
                    text = vm.currentUser?.name ?: "未选择",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        LazyColumn {
            item {
                SettingRow(icon = Icons.Filled.SwapHoriz, title = "切换记账人", value = vm.currentUser?.name.orEmpty()) {
                    showUserSwitch = true
                    // 打开窗口前拉取记账人列表，避免空白窗口
                    scope.launch { vm.loadUsers() }
                }
            }
            item {
                SettingRow(icon = Icons.Filled.Settings, title = "服务器地址", value = vm.serverUrl) {
                    showServer = true
                }
            }
            item {
                SettingRow(
                    icon = Icons.Filled.Adjust,
                    title = "悬浮球记账",
                    value = if (ballRunning) "运行中（点击关闭）" else "未开启（点击开启）",
                ) { toggleFloatingBall() }
            }
            item {
                SettingRow(icon = Icons.Filled.SystemUpdate, title = "检查更新", value = "当前 v${BuildConfig.VERSION_NAME}") {
                    scope.launch {
                        vm.loadAppConfig()
                        updateState = if (vm.hasNewVersion()) "new" else "latest"
                    }
                }
            }
            item {
                SettingRow(icon = Icons.Filled.Person, title = "关于", value = "家庭记账 v${BuildConfig.VERSION_NAME}") {}
            }
        }
    }

    // 切换记账人
    if (showUserSwitch) {
        AlertDialog(
            onDismissRequest = { showUserSwitch = false },
            title = { Text("切换记账人") },
            text = {
                Column {
                    when {
                        vm.firstLoading -> Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                        vm.users.isEmpty() -> Text(
                            text = "暂无可切换的记账人，请确认服务器已连接。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextGray,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                        else -> vm.users.forEach { u ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.updateCurrentUser(u)
                                        showUserSwitch = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = u.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                if (u.id == vm.currentUser?.id) {
                                    Text("当前", style = MaterialTheme.typography.bodySmall, color = PrimaryBlue)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showUserSwitch = false }) { Text("取消") } },
        )
    }

    // 服务器地址
    if (showServer) {
        ServerUrlDialog(
            initial = vm.serverUrl,
            onConfirm = { newUrl ->
                vm.updateServerUrl(newUrl)
                showServer = false
            },
            onDismiss = { showServer = false },
        )
    }

    // 检查更新结果
    when (updateState) {
        "new" -> AlertDialog(
            onDismissRequest = { updateState = null },
            title = { Text("发现新版本 v${vm.appConfig?.latest_version_name}") },
            text = { Text(vm.appConfig?.changelog ?: "") },
            confirmButton = {
                TextButton(onClick = {
                    vm.startUpdateDownload()
                    updateState = null
                }) { Text("立即更新") }
            },
            dismissButton = { TextButton(onClick = { updateState = null }) { Text("稍后") } },
        )
        "latest" -> AlertDialog(
            onDismissRequest = { updateState = null },
            title = { Text("已是最新版本") },
            text = { Text("当前版本 v${BuildConfig.VERSION_NAME} 已是最新。") },
            confirmButton = { TextButton(onClick = { updateState = null }) { Text("好的") } },
        )
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = TextGray)
        Spacer(Modifier.size(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = TextGray,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextGray)
    }
}
