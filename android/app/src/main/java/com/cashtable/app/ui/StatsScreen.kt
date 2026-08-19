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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cashtable.app.data.CategoryDto
import com.cashtable.app.data.TransactionDto
import com.cashtable.app.ui.components.CategoryIcon
import com.cashtable.app.ui.components.displayCategory
import com.cashtable.app.ui.theme.IncomeGreen
import com.cashtable.app.ui.theme.PrimaryGreen
import com.cashtable.app.ui.theme.TextGray
import com.cashtable.app.util.Formatters
import java.time.YearMonth

/** 统计页：月份切换 + 收支合计 + 分类支出排行（本地聚合当月账单）。 */
@Composable
fun StatsScreen(vm: AppViewModel) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var transactions by remember { mutableStateOf<List<TransactionDto>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

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
