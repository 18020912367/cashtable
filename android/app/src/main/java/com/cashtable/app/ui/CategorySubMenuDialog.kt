package com.cashtable.app.ui

import android.widget.Toast
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cashtable.app.data.CategoryDto
import com.cashtable.app.ui.theme.TextGray
import kotlinx.coroutines.launch

/**
 * 二级分类菜单弹窗（长按一级分类图标触发；记一笔/截图记账/自动记账三页共用）。
 *
 * 除选择/新增二级分类外，还提供分类维护能力：
 * - 底部按钮行与「取消」同排：「编辑」（名称解锁为输入框）与「删除」（确认后删除一级分类并级联删除其全部二级分类，账单保留、分类置空）；
 * - 每个二级分类行：「编辑」点击后该二级分类进入编辑态，「删除」位于最右侧（确认后删除）。
 */
@Composable
internal fun CategorySubMenuDialog(
    top: CategoryDto,
    kind: String,
    vm: AppViewModel,
    onSelect: (CategoryDto) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 从分类缓存取最新值：改名/增删后自动反映，避免外部快照过期
    val topNow = vm.categories.firstOrNull { it.id == top.id } ?: top
    val subs = vm.categories.filter { it.parent_id == topNow.id }

    var editingTop by remember { mutableStateOf(false) }
    var editingSubId by remember { mutableStateOf<Int?>(null) }
    var editName by remember { mutableStateOf("") }
    var subNameInput by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<CategoryDto?>(null) }

    val isEditing = editingTop || editingSubId != null

    fun exitEditing() {
        editingTop = false
        editingSubId = null
    }

    if (confirmDelete != null) {
        val target = confirmDelete!!
        val isTop = target.parent_id == null
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(if (isTop) "删除一级分类" else "删除二级分类") },
            text = {
                Text(
                    if (isTop) {
                        "将删除一级分类「${target.name}」及其下全部二级分类，已关联账单的分类会置空（账单保留）。确定删除？"
                    } else {
                        "将删除二级分类「${target.name}」，已关联账单的分类会置空（账单保留）。确定删除？"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val targetId = target.id
                    val isDeletingTop = isTop
                    confirmDelete = null
                    scope.launch {
                        val ok = vm.deleteCategory(targetId)
                        Toast.makeText(
                            context,
                            if (ok) "已删除" else "删除失败，请重试",
                            Toast.LENGTH_SHORT,
                        ).show()
                        if (ok && isDeletingTop) onDismiss()
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    editingTop -> "编辑一级分类"
                    editingSubId != null -> "编辑二级分类"
                    else -> "${topNow.name} · 二级分类"
                }
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (editingTop) {
                    // 编辑态：一级分类名称解锁为输入框
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("一级分类名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (editingSubId != null) {
                    // 编辑态：二级分类名称解锁为输入框
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("二级分类名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    // 一级分类本身：选择后回到一级
                    Text(
                        text = "${topNow.name}（一级分类）",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    onSelect(top)
                                    onDismiss()
                                },
                            )
                            .padding(vertical = 10.dp),
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    if (subs.isEmpty()) {
                        Text("暂无二级分类，可在下方新增", color = TextGray, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                    }
                    subs.forEach { sub ->
                        // 二级行：名称点击选择；「编辑」进入编辑态；「删除」位于最右侧（确认后删除）
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = sub.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .weight(1f)
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            onSelect(sub)
                                            onDismiss()
                                        },
                                    )
                                    .padding(vertical = 10.dp),
                            )
                            TextButton(onClick = {
                                editName = sub.name
                                editingTop = false
                                editingSubId = sub.id
                            }) { Text("编辑") }
                            TextButton(onClick = { confirmDelete = sub }) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = subNameInput,
                            onValueChange = { subNameInput = it },
                            label = { Text("新增二级分类") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                val name = subNameInput.trim()
                                if (name.isNotEmpty()) {
                                    scope.launch {
                                        val created = vm.createCategory(name, kind, parentId = topNow.id)
                                        if (created != null) {
                                            subNameInput = ""
                                            onSelect(created)
                                            onDismiss()
                                        }
                                    }
                                }
                            },
                        ) { Text("新增") }
                    }
                }
            }
        },
        confirmButton = {
            // 底部操作行（与「取消」同排）：编辑态为 保存/取消；非编辑态为 编辑/删除/取消
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isEditing) {
                    TextButton(onClick = {
                        val name = editName.trim()
                        val targetId = if (editingTop) topNow.id else editingSubId
                        val currentName = if (editingTop) {
                            topNow.name
                        } else {
                            subs.firstOrNull { it.id == editingSubId }?.name
                        }
                        if (targetId == null || name.isEmpty() || name == currentName) {
                            exitEditing()
                            return@TextButton
                        }
                        scope.launch {
                            val ok = vm.updateCategory(targetId, name)
                            Toast.makeText(
                                context,
                                if (ok) "已保存" else "保存失败，请重试",
                                Toast.LENGTH_SHORT,
                            ).show()
                            if (ok) exitEditing()
                        }
                    }) { Text("保存") }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { exitEditing() }) { Text("取消") }
                } else {
                    TextButton(onClick = {
                        editName = topNow.name
                        editingTop = true
                    }) { Text("编辑") }
                    TextButton(onClick = { confirmDelete = topNow }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onDismiss) { Text("取消") }
                }
            }
        },
    )
}
