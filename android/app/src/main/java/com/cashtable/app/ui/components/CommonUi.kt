package com.cashtable.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 服务器地址编辑对话框（首次启动引导与设置页共用）。
 *  支持"本地 / 云服务器"双地址快捷切换；端口仅允许 8000~8009。 */
@Composable
fun ServerUrlDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf(initial) }
    // 从地址中提取端口并校验范围 8000~8009
    val port = input.trim().let { t ->
        Regex(":(\\d{1,5})/?$").find(t)?.groupValues?.get(1)?.toIntOrNull()
    }
    val portValid = port != null && port in 8000..8009
    val isLocal = input.contains("192.168.1.100")
    val isCloud = input.contains("124.223.74.139")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("服务器地址") },
        text = {
            Column {
                Text(
                    text = "本地（家庭局域网）与云服务器二选一，端口仅允许 8000~8009",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isLocal,
                        onClick = { input = "http://192.168.1.100:8000/" },
                        label = { Text("本地") },
                    )
                    FilterChip(
                        selected = isCloud,
                        onClick = { input = "http://124.223.74.139:8000/" },
                        label = { Text("云服务器") },
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    isError = !portValid,
                    label = { Text("服务器地址") },
                    supportingText = {
                        if (!portValid) Text("端口仅允许 8000~8009")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(input.trim()) }, enabled = portValid) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 底部导航 Tab 定义。 */
enum class MainTab(val label: String) {
    Detail("明细"),
    Stats("统计"),
    Me("我的"),
}
