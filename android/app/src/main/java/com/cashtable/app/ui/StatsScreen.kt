package com.cashtable.app.ui

import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.cashtable.app.data.CategoryDto
import com.cashtable.app.data.TransactionDto
import com.cashtable.app.ui.components.CategoryIcon
import com.cashtable.app.ui.components.displayCategory
import com.cashtable.app.ui.theme.IncomeGreen
import com.cashtable.app.ui.theme.PrimaryGreen
import com.cashtable.app.ui.theme.TextGray
import com.cashtable.app.util.Formatters
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 统计页：月份切换 + 收支合计 + 分类支出排行（本地聚合当月账单）。 */
@Composable
fun StatsScreen(vm: AppViewModel) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var transactions by remember { mutableStateOf<List<TransactionDto>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }

    val monthStr = remember(month) { "%d-%02d".format(month.year, month.monthValue) }

    LaunchedEffect(monthStr, vm.transactionsVersion, vm.serverUrl) {
        loading = true
        transactions = vm.fetchTransactions(monthStr, null, null, null)
        loading = false
    }

    val income = transactions.filter { it.amount > 0 }.sumOf { it.amount }
    val expense = transactions.filter { it.amount < 0 }.sumOf { -it.amount }
    val expenseByCategory = transactions
        .filter { it.amount < 0 }
        .groupBy { it.category_id }
        .map { (catId, list) -> Triple(catId, catName(vm, list.first()), list.sumOf { -it.amount }) }
        .sortedByDescending { it.third }
    val maxExpense = expenseByCategory.firstOrNull()?.third ?: 0.0

    Column(Modifier.fillMaxSize()) {
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
            IconButton(onClick = { showExport = true }) {
                Icon(Icons.Filled.IosShare, contentDescription = "导出")
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("收入", style = MaterialTheme.typography.bodySmall, color = TextGray)
                    Text(Formatters.money(income), style = MaterialTheme.typography.titleMedium, color = IncomeGreen)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("支出", style = MaterialTheme.typography.bodySmall, color = TextGray)
                    Text(Formatters.money(expense), style = MaterialTheme.typography.titleMedium, color = PrimaryGreen)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("结余", style = MaterialTheme.typography.bodySmall, color = TextGray)
                    Text(Formatters.money(income - expense), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "分类支出排行",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))

        Box(Modifier.weight(1f)) {
            when {
                loading && transactions.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                expenseByCategory.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("本月暂无支出数据", color = TextGray)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp, end = 12.dp, bottom = 24.dp, top = 4.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(expenseByCategory) { (catId, name, total) ->
                        val iconCat = displayCategory(
                            catId?.let { id -> vm.categories.firstOrNull { it.id == id } },
                            vm.categories,
                        )
                        RankRow(
                            name = name,
                            total = total,
                            ratio = (if (maxExpense > 0) total / maxExpense else 0.0).toFloat(),
                            iconCat = iconCat,
                        )
                    }
                }
            }
        }
    }

    // 导出账单（日期区间 → CSV，分享/保存）
    if (showExport) {
        ExportDialog(vm = vm, onDismiss = { showExport = false })
    }
}

/** 导出账单对话框：选择日期区间 → 调后端/本地导出 CSV → 分享保存。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var start by remember { mutableStateOf(LocalDate.now().minusMonths(1).withDayOfMonth(1)) }
    var end by remember { mutableStateOf(LocalDate.now()) }
    var pickTarget by remember { mutableStateOf(0) } // 1=开始日期 2=结束日期
    var exporting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // 日期选择器（每次打开一个）
    if (pickTarget != 0) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (if (pickTarget == 1) start else end)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickTarget = 0 },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val d = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        if (pickTarget == 1) start = d else end = d
                    }
                    pickTarget = 0
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { pickTarget = 0 }) { Text("取消") } },
        ) { DatePicker(state = state) }
    }

    AlertDialog(
        onDismissRequest = { if (!exporting) onDismiss() },
        title = { Text("导出账单") },
        text = {
            Column {
                Text(
                    text = "选择日期区间，导出该区间全部账单（CSV，含记账人与分类名），可分享或保存到任意位置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("开始日期", style = MaterialTheme.typography.bodySmall, color = TextGray)
                        Text(start.toString(), style = MaterialTheme.typography.titleMedium)
                    }
                    TextButton(onClick = { pickTarget = 1 }) { Text("选择") }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("结束日期", style = MaterialTheme.typography.bodySmall, color = TextGray)
                        Text(end.toString(), style = MaterialTheme.typography.titleMedium)
                    }
                    TextButton(onClick = { pickTarget = 2 }) { Text("选择") }
                }
                if (start > end) {
                    Spacer(Modifier.height(8.dp))
                    Text("开始日期不能晚于结束日期", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (exporting) {
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !exporting && start <= end,
                onClick = {
                    exporting = true
                    error = null
                    scope.launch {
                        val csv = vm.exportTransactions(start.toString(), end.toString())
                        exporting = false
                        when {
                            csv == null -> error = vm.serverError ?: "导出失败，请重试"
                            csv.isBlank() -> error = "该区间暂无账单数据"
                            else -> try {
                                val dir = File(context.filesDir, "export").apply { mkdirs() }
                                val file = File(dir, "transactions_${start}_to_${end}.csv")
                                file.writeText(csv)
                                val uri = FileProvider.getUriForFile(
                                    context, "${context.packageName}.fileprovider", file,
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "分享/保存导出账单"))
                                onDismiss()
                            } catch (e: Exception) {
                                error = "导出失败：${e.message}"
                            }
                        }
                    }
                },
            ) { Text(if (exporting) "导出中…" else "导出") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !exporting) { Text("取消") } },
    )
}

private fun catName(vm: AppViewModel, tx: TransactionDto): String {
    val c = tx.category_id?.let { id -> vm.categories.firstOrNull { it.id == id } }
    return c?.name ?: tx.category_name ?: "其他"
}

@Composable
private fun RankRow(name: String, total: Double, ratio: Float, iconCat: CategoryDto?) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon(category = iconCat, size = 34.dp, iconSize = 18.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = Formatters.money(total),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio.coerceIn(0.05f, 1f))
                    .height(6.dp)
                    .background(PrimaryGreen.copy(alpha = 0.7f), RoundedCornerShape(3.dp)),
            )
        }
        Spacer(Modifier.height(10.dp))
    }
}
