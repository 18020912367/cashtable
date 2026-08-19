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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cashtable.app.BuildConfig
import com.cashtable.app.data.ActivationCheckResponse
import com.cashtable.app.data.AppMode
import com.cashtable.app.p4.FloatingBallService
import com.cashtable.app.ui.theme.PrimaryGreen
import com.cashtable.app.ui.theme.TextGray
import kotlinx.coroutines.launch

/** 我的页：切换记账人（含新增/删除）、运行模式切换、LLM 配置、激活码激活、关于。 */
@Composable
fun MeScreen(vm: AppViewModel) {
    var showUserSwitch by remember { mutableStateOf(false) }
    var showModeSwitch by remember { mutableStateOf(false) }
    var showLlm by remember { mutableStateOf(false) }
    var showCustomServer by remember { mutableStateOf(false) }
    var showActivation by remember { mutableStateOf(false) }
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
                    justRequestedStart = false
                    Handler(Looper.getMainLooper()).postDelayed({
                        ballRunning = FloatingBallService.isActive
                    }, 500)
                } else {
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
                tint = PrimaryGreen,
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
                SettingRow(icon = Icons.Filled.Settings, title = "切换模式", value = vm.mode.label) {
                    showModeSwitch = true
                }
            }
            // 模式相关设置项
            when (vm.mode) {
                AppMode.Local -> item {
                    SettingRow(
                        icon = Icons.Filled.Lock,
                        title = "LLM 设置",
                        value = if (vm.llmKeyConfigured()) "已配置 ${vm.llmKeyMasked()}" else "未配置",
                    ) { showLlm = true }
                }
                AppMode.Custom -> item {
                    SettingRow(icon = Icons.Filled.Settings, title = "自定义服务器", value = vm.serverUrl) {
                        showCustomServer = true
                    }
                }
                AppMode.Full -> item {
                    SettingRow(
                        icon = Icons.Filled.Star,
                        title = "全功能激活",
                        value = "激活码 ${vm.activationCode().take(4)}…${vm.activationCode().takeLast(4)}",
                    ) { showActivation = true }
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
                SettingRow(icon = Icons.Filled.Person, title = "关于", value = "截记 v${BuildConfig.VERSION_NAME}") {}
            }
        }
    }

    // 切换记账人（含新增/删除）
    if (showUserSwitch) {
        UserSwitchDialog(
            vm = vm,
            onDismiss = { showUserSwitch = false },
        )
    }

    // 模式切换
    if (showModeSwitch) {
        ModeSwitchDialog(
            current = vm.mode,
            onSelect = { target ->
                showModeSwitch = false
                when (target) {
                    AppMode.Local -> scope.launch {
                        vm.switchMode(AppMode.Local)
                        vm.refreshForMode()
                    }
                    AppMode.Custom -> showCustomServer = true
                    AppMode.Full -> {
                        if (vm.activationCode().isEmpty()) {
                            showActivation = true
                        } else {
                            scope.launch {
                                vm.switchMode(AppMode.Full)
                                vm.refreshForMode()
                            }
                        }
                    }
                }
            },
            onDismiss = { showModeSwitch = false },
        )
    }

    // LLM 设置（Key 只写、加密存储、头尾展示、测试连接；全功能模式不可编辑）
    if (showLlm) {
        LlmSettingsDialog(
            vm = vm,
            editable = vm.mode != AppMode.Full,
            onDismiss = { showLlm = false },
        )
    }

    // 自定义服务器设置
    if (showCustomServer) {
        CustomServerDialog(
            initialUrl = vm.customServerUrlForDisplay(),
            initialDbPath = vm.customDbPathForDisplay(),
            onConfirm = { url, dbPath, switchMode ->
                vm.updateCustomServer(url, dbPath)
                showCustomServer = false
                if (switchMode) {
                    scope.launch {
                        vm.switchMode(AppMode.Custom)
                        vm.refreshForMode()
                    }
                }
            },
            onDismiss = { showCustomServer = false },
        )
    }

    // 全功能模式激活
    if (showActivation) {
        ActivationDialog(
            vm = vm,
            onDismiss = { showActivation = false },
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

// ---------- 切换记账人（含新增/删除） ----------

@Composable
private fun UserSwitchDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var newName by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<com.cashtable.app.data.UserDto?>(null) }
    var adding by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("切换记账人") },
        text = {
            Column {
                // 新增记账人
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        label = { Text("新记账人姓名") },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.size(8.dp))
                    Button(
                        enabled = newName.isNotBlank() && !adding,
                        onClick = {
                            adding = true
                            scope.launch {
                                vm.createUser(newName)
                                adding = false
                                newName = ""
                            }
                        },
                    ) { Text(if (adding) "…" else "新增") }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(Modifier.height(4.dp))
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
                        text = "暂无可用的记账人，请先在下方新增。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextGray,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    else -> Column {
                        vm.users.forEach { u ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.updateCurrentUser(u)
                                        onDismiss()
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
                                    Text("当前", style = MaterialTheme.typography.bodySmall, color = PrimaryGreen)
                                }
                                IconButton(onClick = { deleteTarget = u }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "删除 ${u.name}",
                                        tint = TextGray,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                vm.serverError?.let { err ->
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )

    // 删除确认
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除记账人") },
            text = { Text("确定删除「${target.name}」吗？其名下账单将保留（记账人置空）。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { vm.deleteUser(target.id) }
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

// ---------- 模式切换 ----------

@Composable
private fun ModeSwitchDialog(
    current: AppMode,
    onSelect: (AppMode) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("切换运行模式") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ModeOption(
                    selected = selected == AppMode.Local,
                    title = "本地模式",
                    desc = "数据保存在本机（App 自带 sqlite），截图识别使用个人中心配置的 LLM Key",
                    onClick = { selected = AppMode.Local },
                )
                ModeOption(
                    selected = selected == AppMode.Custom,
                    title = "自定义模式",
                    desc = "填写自己的服务器地址、端口号与 sqlite 目录，需下载标准模板自行导入服务器；多人共用存在写入冲突风险",
                    onClick = { selected = AppMode.Custom },
                )
                ModeOption(
                    selected = selected == AppMode.Full,
                    title = "全功能模式",
                    desc = "通过公共服务器激活直接使用全部功能，需绑定激活码；首次激活上传本地数据到云端（支持并发，FastAPI + SQLite WAL）",
                    onClick = { selected = AppMode.Full },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(selected) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ModeOption(selected: Boolean, title: String, desc: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = TextGray)
        }
    }
}

// ---------- LLM 设置 ----------

@Composable
private fun LlmSettingsDialog(
    vm: AppViewModel,
    editable: Boolean,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var keyInput by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf(vm.llmBaseUrl()) }
    var model by remember { mutableStateOf(vm.llmModel()) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("LLM 设置") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "用于本地模式截图识别。Key 加密存储（EncryptedSharedPreferences），只显示头尾、不可复制。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                )
                Spacer(Modifier.height(12.dp))
                if (vm.llmKeyConfigured()) {
                    Text(
                        text = "已配置：${vm.llmKeyMasked()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PrimaryGreen,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    singleLine = true,
                    enabled = editable,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("API Key（留空则不修改）") },
                    placeholder = { Text(if (editable) "输入新的 Key" else "全功能模式不可编辑") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    singleLine = true,
                    enabled = editable,
                    label = { Text("Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    singleLine = true,
                    enabled = editable,
                    label = { Text("模型") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    enabled = !testing,
                    onClick = {
                        testing = true
                        testResult = null
                        scope.launch {
                            testResult = vm.testLlmConnection()
                            testing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (testing) "测试中…" else "测试连接")
                }
                testResult?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (keyInput.isNotBlank() || editable) {
                    vm.saveLlmConfig(
                        apiKey = keyInput.trim().ifEmpty { vm.llmApiKeyForSave() },
                        baseUrl = baseUrl.trim().ifEmpty { vm.llmBaseUrl() },
                        model = model.trim().ifEmpty { vm.llmModel() },
                    )
                }
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ---------- 自定义服务器设置 ----------

@Composable
private fun CustomServerDialog(
    initialUrl: String,
    initialDbPath: String,
    onConfirm: (String, String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // 从地址解析 host 与 port
    val parsedHost = initialUrl.replace(Regex("^https?://"), "").replace(Regex(":\\d+/?$"), "")
    val parsedPort = Regex(":(\\d+)/?$").find(initialUrl)?.groupValues?.get(1) ?: "8000"
    var host by remember { mutableStateOf(parsedHost) }
    var port by remember { mutableStateOf(parsedPort) }
    var dbPath by remember { mutableStateOf(initialDbPath) }
    val portInt = port.toIntOrNull()
    val portValid = portInt != null && portInt in 8000..8009

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义服务器") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "填写你自己的服务器（需部署本项目后端并导入标准 sqlite 模板），多人共用注意写入冲突风险。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    singleLine = true,
                    label = { Text("服务器地址（IP 或域名）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() } },
                    singleLine = true,
                    isError = !portValid,
                    label = { Text("端口（仅 8000~8009）") },
                    supportingText = { if (!portValid) Text("端口仅允许 8000~8009") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = dbPath,
                    onValueChange = { dbPath = it },
                    singleLine = true,
                    label = { Text("服务器上 sqlite 目录") },
                    placeholder = { Text("/home/codefile/cashtabledb") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                // 下载标准 sqlite 模板（公共服务器提供）
                TextButton(onClick = {
                    try {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("${com.cashtable.app.data.Prefs.PUBLIC_SERVER}api/activate/template"),
                            ),
                        )
                    } catch (_: Exception) {
                        Toast.makeText(context, "无法打开浏览器下载模板", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("下载标准 sqlite 模板（导入到你的服务器）") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = portValid,
                onClick = {
                    onConfirm(
                        "http://${host.trim()}:${port}/".replace("::", ":"),
                        dbPath.trim(),
                        true,
                    )
                },
            ) { Text("保存并切换") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ---------- 全功能模式激活 ----------

@Composable
private fun ActivationDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var codeInput by remember { mutableStateOf(vm.activationCode()) }
    var status by remember { mutableStateOf<ActivationCheckResponse?>(null) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // 已有激活码时查询当前激活状态
    LaunchedEffect(Unit) {
        if (vm.activationCode().isNotEmpty()) {
            status = vm.checkActivation(vm.activationCode())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("全功能模式激活") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "绑定激活码后使用公共服务器全部功能；首次激活会把本机数据上传到云端（设备标识：${vm.deviceMac()}）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it },
                    singleLine = true,
                    label = { Text("激活码") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                when {
                    status?.is_activated == true -> Text(
                        "已激活：${status?.activated_at}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PrimaryGreen,
                    )
                    status != null -> Text(
                        status?.message.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status?.ok == true) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    )
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (working) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator() }
                }
            }
        },
        confirmButton = {
            if (vm.activationCode().isEmpty()) {
                TextButton(
                    enabled = codeInput.isNotBlank() && !working,
                    onClick = {
                        working = true
                        error = null
                        scope.launch {
                            val err = vm.activate(codeInput.trim())
                            working = false
                            if (err == null) {
                                onDismiss()
                            } else {
                                error = err
                            }
                        }
                    },
                ) { Text("校验并激活") }
            } else {
                TextButton(
                    enabled = !working,
                    onClick = {
                        scope.launch {
                            vm.switchMode(AppMode.Full)
                            vm.refreshForMode()
                            onDismiss()
                        }
                    },
                ) { Text("切换到全功能模式") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
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
