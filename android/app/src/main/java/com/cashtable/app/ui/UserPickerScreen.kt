package com.cashtable.app.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cashtable.app.data.AppMode
import com.cashtable.app.data.UserDto
import com.cashtable.app.ui.theme.PrimaryGreen
import kotlinx.coroutines.launch

/** 首次启动/当前记账人失效：选择"当前记账人"（可新增；无登录）。 */
@Composable
fun UserPickerScreen(
    vm: AppViewModel = viewModel(),
    onDone: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 进入时拉取记账人（本地模式读本机 sqlite；自定义/全功能模式读服务器）
    LaunchedEffect(vm.mode) {
        vm.loadUsers()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            text = "选择记账人",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = when (vm.mode) {
                AppMode.Local -> "本地模式：数据保存在本机，先选择当前由谁来记账"
                AppMode.Custom -> "自定义模式：连接你自己的服务器，选择当前由谁来记账"
                AppMode.Full -> "全功能模式：数据在云端，选择当前由谁来记账"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

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
                        val created = vm.createUser(newName)
                        adding = false
                        newName = ""
                        created?.let { u ->
                            vm.updateCurrentUser(u)
                            onDone()
                        }
                    }
                },
            ) { Text(if (adding) "…" else "新增并选择") }
        }
        Spacer(Modifier.height(16.dp))

        when {
            vm.firstLoading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            vm.users.isEmpty() && vm.serverError == null -> {
                Text("暂无可用的记账人，请在下方新增。")
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(vm.users) { user ->
                        UserRow(user = user) {
                            vm.updateCurrentUser(user)
                            onDone()
                        }
                    }
                }
            }
        }

        vm.serverError?.let { err ->
            Spacer(Modifier.height(16.dp))
            Text(
                text = err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { scope.launch { vm.loadUsers() } }) { Text("重试") }
        }

        Spacer(Modifier.weight(1f))
        if (vm.mode != AppMode.Local) {
            Card {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("当前服务器", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = vm.serverUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Card {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = PrimaryGreen)
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text("当前为本地模式", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "数据仅保存在本机；如需云端多人记账，请到「我的 → 切换模式」",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserRow(user: UserDto, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = PrimaryGreen,
            )
            Spacer(Modifier.size(12.dp))
            Text(
                text = user.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
