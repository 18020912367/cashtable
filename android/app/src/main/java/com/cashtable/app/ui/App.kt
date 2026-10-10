package com.cashtable.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cashtable.app.ui.components.MainTab
import com.cashtable.app.ui.theme.CashtableTheme
import com.cashtable.app.ui.theme.TextGray

/** 页面路由。 */
sealed class Screen {
    data object Detail : Screen()
    data object Stats : Screen()
    data object Me : Screen()
    data object Add : Screen()
    data object Ocr : Screen()
    data class AutoAccount(val seq: Int) : Screen()
    data class Edit(val tx: com.cashtable.app.data.TransactionDto) : Screen()
}

@Composable
fun CashtableApp(
    vm: AppViewModel = viewModel(),
    autoAccountingSeq: Int = 0,
) {
    CashtableTheme {
        var screen by remember { mutableStateOf<Screen>(Screen.Detail) }
        val currentUser = vm.currentUser

        // 悬浮球点击 → 跳转「自动记账」页；seq 每次递增，保证重复进入时页面全新（重新读相册识别）
        LaunchedEffect(autoAccountingSeq) {
            if (autoAccountingSeq > 0) {
                screen = Screen.AutoAccount(autoAccountingSeq)
            }
        }

        // 启动即加载分类缓存与 App 配置（记一笔页/明细筛选/版本检查依赖）；
        // 全功能模式启动时先做一次云端→本地数据同步，再加载其余内容
        LaunchedEffect(Unit) {
            vm.syncCloudDbOnLaunch()
            vm.loadCategories()
            vm.loadAppConfig()
        }

        // 服务端有更高版本 → 弹更新提示（自动记账快速流程中不打扰）
        var showUpdate by remember { mutableStateOf(false) }
        LaunchedEffect(vm.appConfig) { showUpdate = vm.hasNewVersion() }

        if (showUpdate && vm.appConfig != null && screen !is Screen.AutoAccount) {
            AlertDialog(
                onDismissRequest = { showUpdate = false },
                title = { Text("发现新版本 v${vm.appConfig!!.latest_version_name}") },
                text = { Text(vm.appConfig!!.changelog) },
                confirmButton = {
                    TextButton(onClick = {
                        vm.startUpdateDownload()
                        showUpdate = false
                    }) { Text("立即更新") }
                },
                dismissButton = { TextButton(onClick = { showUpdate = false }) { Text("稍后") } },
            )
        }

        // App 内下载进度
        if (vm.downloading) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("正在下载更新") },
                text = {
                    Column {
                        LinearProgressIndicator(
                            progress = { vm.downloadProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "已下载 ${(vm.downloadProgress * 100).toInt()}%",
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            color = TextGray,
                        )
                    }
                },
                confirmButton = {},
            )
        }
        // 下载失败提示
        vm.downloadError?.let { err ->
            AlertDialog(
                onDismissRequest = { vm.downloadError = null },
                title = { Text("更新失败") },
                text = { Text(err) },
                confirmButton = { TextButton(onClick = { vm.downloadError = null }) { Text("确定") } },
            )
        }

        when {
            // 首次启动：未选择记账人 → 引导选择
            currentUser == null -> UserPickerScreen(vm = vm) {
                screen = Screen.Detail
            }
            screen is Screen.Add -> {
                // 全面屏手势/系统返回键：返回明细页，而非退出应用
                BackHandler { screen = Screen.Detail }
                AddScreen(vm = vm, onBack = { screen = Screen.Detail }, onOcr = { screen = Screen.Ocr })
            }
            screen is Screen.Ocr -> {
                // 截图记账（P3）：相册选图 → 识别 → 确认表单
                BackHandler { screen = Screen.Detail }
                OcrScreen(vm = vm, onBack = { screen = Screen.Detail })
            }
            screen is Screen.AutoAccount -> {
                // 自动记账（悬浮球入口，0.7.0）：key(seq) 保证每次点悬浮球都是全新页面
                BackHandler { screen = Screen.Detail }
                val seq = (screen as Screen.AutoAccount).seq
                key(seq) {
                    AutoAccountScreen(vm = vm, onBack = { screen = Screen.Detail })
                }
            }
            screen is Screen.Edit -> {
                // 编辑账单：复用记一笔页，返回明细页
                BackHandler { screen = Screen.Detail }
                val editTx = (screen as Screen.Edit).tx
                AddScreen(vm = vm, onBack = { screen = Screen.Detail }, initial = editTx)
            }
            else -> MainScaffold(vm = vm, screen = screen, onNavigate = { screen = it })
        }
    }
}

@Composable
private fun MainScaffold(
    vm: AppViewModel,
    screen: Screen,
    onNavigate: (Screen) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    val selected = when (tab) {
                        MainTab.Detail -> screen is Screen.Detail
                        MainTab.Stats -> screen is Screen.Stats
                        MainTab.Me -> screen is Screen.Me
                    }
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            onNavigate(
                                when (tab) {
                                    MainTab.Detail -> Screen.Detail
                                    MainTab.Stats -> Screen.Stats
                                    MainTab.Me -> Screen.Me
                                },
                            )
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (screen is Screen.Detail) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 加号（记一笔）右侧并排相机（截图记账），尺寸一致仅图标
                    FloatingActionButton(onClick = { onNavigate(Screen.Add) }) {
                        Icon(Icons.Filled.Add, contentDescription = "记一笔")
                    }
                    FloatingActionButton(onClick = { onNavigate(Screen.Ocr) }) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = "截图记账")
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            when (screen) {
                is Screen.Detail -> DetailScreen(vm, onEdit = { tx -> onNavigate(Screen.Edit(tx)) })
                is Screen.Stats -> StatsScreen(vm)
                is Screen.Me -> MeScreen(vm)
                else -> {}
            }
        }
    }
}

private val MainTab.icon: ImageVector
    get() = when (this) {
        MainTab.Detail -> Icons.AutoMirrored.Filled.List
        MainTab.Stats -> Icons.Filled.BarChart
        MainTab.Me -> Icons.Filled.Person
    }
