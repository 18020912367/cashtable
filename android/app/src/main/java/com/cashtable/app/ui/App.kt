package com.cashtable.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cashtable.app.ui.components.MainTab
import com.cashtable.app.ui.theme.CashtableTheme

/** 页面路由。 */
sealed class Screen {
    data object Detail : Screen()
    data object Stats : Screen()
    data object Me : Screen()
    data object Add : Screen()
    data class Edit(val tx: com.cashtable.app.data.TransactionDto) : Screen()
}

@Composable
fun CashtableApp(vm: AppViewModel = viewModel()) {
    CashtableTheme {
        var screen by remember { mutableStateOf<Screen>(Screen.Detail) }
        val currentUser = vm.currentUser

        // 启动即加载分类缓存与 App 配置（记一笔页/明细筛选/版本检查依赖）
        LaunchedEffect(Unit) {
            vm.loadCategories()
            vm.loadAppConfig()
        }

        // 服务端有更高版本 → 弹更新提示
        var showUpdate by remember { mutableStateOf(false) }
        LaunchedEffect(vm.appConfig) { showUpdate = vm.hasNewVersion() }

        if (showUpdate && vm.appConfig != null) {
            AlertDialog(
                onDismissRequest = { showUpdate = false },
                title = { Text("发现新版本 v${vm.appConfig!!.latest_version_name}") },
                text = { Text(vm.appConfig!!.changelog) },
                confirmButton = {
                    TextButton(onClick = {
                        vm.openApkDownload()
                        showUpdate = false
                    }) { Text("立即更新") }
                },
                dismissButton = { TextButton(onClick = { showUpdate = false }) { Text("稍后") } },
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
                AddScreen(vm = vm, onBack = { screen = Screen.Detail })
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
                FloatingActionButton(onClick = { onNavigate(Screen.Add) }) {
                    Icon(Icons.Filled.Add, contentDescription = "记一笔")
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            when (screen) {
                is Screen.Detail -> DetailScreen(vm) { tx -> onNavigate(Screen.Edit(tx)) }
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
