package com.cashtable.app.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cashtable.app.BuildConfig
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
                    vm.openApkDownload()
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
