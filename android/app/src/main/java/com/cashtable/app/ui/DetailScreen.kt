package com.cashtable.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashtable.app.data.CategoryDto
import com.cashtable.app.data.TransactionDto
import com.cashtable.app.ui.components.AmountText
import com.cashtable.app.ui.components.CategoryIcon
import com.cashtable.app.ui.components.displayCategory
import com.cashtable.app.ui.theme.BgGray
import com.cashtable.app.ui.theme.IncomeGreen
import com.cashtable.app.ui.theme.TextGray
import com.cashtable.app.util.Formatters
import java.time.YearMonth
import kotlinx.coroutines.launch

/** 明细页（首页）：月份切换 + 月概览 + 按日分组账单 + 筛选 + 下拉刷新 + 点击查看/编辑/删除。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: AppViewModel, onEdit: (TransactionDto) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var filterCategoryId by remember { mutableStateOf<Int?>(null) }
    var filterUserId by remember { mutableStateOf<Int?>(null) }
    var filterKeyword by remember { mutableStateOf<String?>(null) }
    var transactions by remember { mutableStateOf<List<TransactionDto>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var showCategoryFilter by remember { mutableStateOf(false) }
    var showUserFilter by remember { mutableStateOf(false) }
    var showKeywordFilter by remember { mutableStateOf(false) }
    var keywordInput by remember { mutableStateOf("") }
    var detailTx by remember { mutableStateOf<TransactionDto?>(null) }
    var confirmDeleteTx by remember { mutableStateOf<TransactionDto?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val monthStr = remember(month) { "%d-%02d".format(month.year, month.monthValue) }

    LaunchedEffect(monthStr, vm.transactionsVersion, vm.serverUrl, filterCategoryId, filterUserId, filterKeyword) {
        loading = true
        transactions = vm.fetchTransactions(monthStr, filterCategoryId, filterUserId, filterKeyword)
        loading = false
    }

    LaunchedEffect(refreshing) {
        if (refreshing) {
            transactions = vm.fetchTransactions(monthStr, filterCategoryId, filterUserId, filterKeyword)
            refreshing = false
        }
    }

    // 月概览
    val income = transactions.filter { it.amount > 0 }.sumOf { it.amount }
    val expense = transactions.filter { it.amount < 0 }.sumOf { -it.amount }
    val balance = income - expense

    Column(Modifier.fillMaxSize()) {
        // 顶部：月份切换
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一月")
            }
            Text(
                text = Formatters.monthLabel(monthStr),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { month = month.plusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一月")
            }
        }

        // 月概览卡
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                OverviewItem("收入", income, color = IncomeGreen)
                OverviewItem("支出", expense, color = androidx.compose.ui.graphics.Color.White)
                OverviewItem("结余", balance, color = androidx.compose.ui.graphics.Color.White)
            }
        }

        // 筛选行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val catName = filterCategoryId?.let { id -> vm.categories.firstOrNull { it.id == id }?.name } ?: "全部分类"
            FilterChip(
                selected = filterCategoryId != null,
                onClick = { showCategoryFilter = true },
                label = { Text(catName) },
            )
            val userName = filterUserId?.let { id -> vm.users.firstOrNull { it.id == id }?.name } ?: "全部记账人"
            FilterChip(
                selected = filterUserId != null,
                onClick = { showUserFilter = true },
                label = { Text(userName) },
            )
            FilterChip(
                selected = filterKeyword != null,
                onClick = { showKeywordFilter = true },
                label = { Text(filterKeyword ?: "关键词") },
            )
        }

        // 账单列表
        Box(Modifier.weight(1f)) {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { refreshing = true },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (loading && transactions.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (transactions.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("本月暂无账单", color = TextGray)
                    }
                } else {
                    val grouped = transactions
                        .groupBy { it.happened_at.take(10) }
                        .toSortedMap(compareByDescending { it })
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 12.dp, end = 12.dp, bottom = 88.dp, top = 4.dp,
                        ),
                    ) {
                        grouped.forEach { (date, list) ->
                            item(key = "h_$date") {
                                DayHeader(
                                    date = date,
                                    dayIncome = list.filter { it.amount > 0 }.sumOf { it.amount },
                                    dayExpense = list.filter { it.amount < 0 }.sumOf { -it.amount },
                                )
                            }
                            items(list, key = { it.id }) { tx ->
                                TransactionRow(
                                    tx = tx,
                                    category = categoryOf(vm, tx.category_id),
                                    iconCategory = displayCategory(categoryOf(vm, tx.category_id), vm.categories),
                                ) {
                                    detailTx = tx
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- 筛选对话框 ----
    if (showCategoryFilter) {
        val options = vm.categories.filter { it.parent_id == null }
        PickDialog(
            title = "选择分类",
            options = listOf("全部分类" to null) + options.map { it.name to it.id },
            current = filterCategoryId,
            onPick = { filterCategoryId = it; showCategoryFilter = false },
            onDismiss = { showCategoryFilter = false },
        )
    }
    if (showUserFilter) {
        PickDialog(
            title = "选择记账人",
            options = listOf("全部记账人" to null) + vm.users.map { it.name to it.id },
            current = filterUserId,
            onPick = { filterUserId = it; showUserFilter = false },
            onDismiss = { showUserFilter = false },
        )
    }
    if (showKeywordFilter) {
        AlertDialog(
            onDismissRequest = { showKeywordFilter = false },
            title = { Text("交易内容关键词") },
            text = {
                OutlinedTextField(
                    value = keywordInput,
                    onValueChange = { keywordInput = it },
                    singleLine = true,
                    placeholder = { Text("如：美团") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    filterKeyword = keywordInput.trim().ifEmpty { null }
                    showKeywordFilter = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showKeywordFilter = false }) { Text("取消") }
            },
        )
    }

    // ---- 记录详情：默认查看，可编辑/删除 ----
    detailTx?.let { tx ->
        val rawCat = categoryOf(vm, tx.category_id)
        val iconCat = displayCategory(rawCat, vm.categories)
        val kind = rawCat?.kind ?: iconCat?.kind ?: "expense"
        val name = rawCat?.name ?: tx.category_name ?: "其他"
        val userName = tx.created_by?.let { uid -> vm.users.firstOrNull { it.id == uid }?.name } ?: "未知"
        AlertDialog(
            onDismissRequest = { detailTx = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryIcon(category = iconCat, size = 34.dp, iconSize = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(name)
                }
            },
            text = {
                Column {
                    AmountText(
                        amount = tx.amount,
                        fontSize = 30.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(12.dp))
                    DetailRow("交易时间", tx.happened_at)
                    if (tx.content.isNotBlank()) DetailRow("交易内容", tx.content)
                    DetailRow("记账人", userName)
                    if (tx.created_at.isNotBlank()) DetailRow("记录时间", tx.created_at)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onEdit(tx)
                    detailTx = null
                }) { Text("编辑") }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmDeleteTx = tx
                    detailTx = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }

    // ---- 删除确认（弹窗手动确认才生效） ----
    confirmDeleteTx?.let { tx ->
        AlertDialog(
            onDismissRequest = { if (!deleting) confirmDeleteTx = null },
            title = { Text("删除记录") },
            text = { Text("确定删除这笔 ${Formatters.money(tx.amount)} 元的记录吗？删除后不可恢复。") },
            confirmButton = {
                TextButton(enabled = !deleting, onClick = {
                    scope.launch {
                        deleting = true
                        vm.deleteTransaction(tx.id)
                        deleting = false
                        confirmDeleteTx = null
                    }
                }) {
                    Text(if (deleting) "删除中…" else "删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { confirmDeleteTx = null }) { Text("取消") }
            },
        )
    }
}

/** 详情字段行：左侧标签，右侧值。 */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextGray,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun categoryOf(vm: AppViewModel, id: Int?): CategoryDto? =
    id?.let { cid -> vm.categories.firstOrNull { it.id == cid } }

@Composable
private fun OverviewItem(label: String, value: Double, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = Formatters.money(value),
            style = MaterialTheme.typography.titleLarge,
            color = color,
        )
    }
}

@Composable
private fun DayHeader(date: String, dayIncome: Double, dayExpense: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = Formatters.dayLabel(date),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        if (dayIncome > 0) {
            Text("收入 ${Formatters.money(dayIncome)}", style = MaterialTheme.typography.bodySmall, color = IncomeGreen)
        }
        if (dayIncome > 0 && dayExpense > 0) {
            Spacer(Modifier.width(8.dp))
        }
        if (dayExpense > 0) {
            Text("支出 ${Formatters.money(dayExpense)}", style = MaterialTheme.typography.bodySmall, color = TextGray)
        }
    }
}

@Composable
private fun TransactionRow(
    tx: TransactionDto,
    category: CategoryDto?,
    iconCategory: CategoryDto?,
    onClick: () -> Unit,
) {
    val kind = category?.kind ?: "expense"
    val name = category?.name ?: tx.category_name ?: "其他"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(category = iconCategory, size = 40.dp, iconSize = 22.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            if (tx.content.isNotBlank()) {
                Text(
                    text = tx.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                    maxLines = 1,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            AmountText(tx.amount)
            Text(
                text = Formatters.timeLabel(tx.happened_at),
                style = MaterialTheme.typography.bodySmall,
                color = TextGray,
            )
        }
    }
}

@Composable
private fun PickDialog(
    title: String,
    options: List<Pair<String, Int?>>,
    current: Int?,
    onPick: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                options.forEach { (label, id) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(id) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (id == current) {
                            Icon(
                                imageVector = Icons.Filled.FilterAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
