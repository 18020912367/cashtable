package com.cashtable.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashtable.app.data.CategoryDto
import com.cashtable.app.ui.components.categoryImage
import com.cashtable.app.ui.components.iconChoices
import com.cashtable.app.ui.theme.BgGray
import com.cashtable.app.ui.theme.PrimaryGreen
import com.cashtable.app.ui.theme.TextGray
import com.cashtable.app.util.Formatters
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** 记一笔/编辑页：支出/收入切换、金额数字键盘、分类网格（4×4 + 其它/动态/更多）、二级分类、时间内容。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AddScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    initial: com.cashtable.app.data.TransactionDto? = null,
    onOcr: (() -> Unit)? = null,
) {
    val editing = initial != null
    val initialCatId = initial?.category_id
    var kind by remember { mutableStateOf(if (initial?.amount != null && initial.amount > 0) "income" else "expense") }
    var amountInput by remember {
        mutableStateOf(initial?.let { Formatters.money(it.amount).replace(",", "") } ?: "")
    }
    var selectedCategory by remember { mutableStateOf<CategoryDto?>(null) }
    var happenedAt by remember {
        mutableStateOf(initial?.happened_at?.let { Formatters.parseDateTime(it) } ?: LocalDateTime.now())
    }
    var content by remember { mutableStateOf(initial?.content ?: "") }
    // 动态分类：从"更多"加载的快捷分类（按 kind 持久化）
    var dynamicCatId by remember { mutableStateOf<Int?>(vm.dynamicCategoryId("expense")) }
    var showMoreDialog by remember { mutableStateOf(false) }
    // 长按弹二级菜单：目标一级分类 + 是否来自"更多"窗口
    var subMenuFor by remember { mutableStateOf<CategoryDto?>(null) }
    var subMenuFromMore by remember { mutableStateOf(false) }
    var showAddCategory by remember { mutableStateOf(false) }
    var subNameInput by remember { mutableStateOf("") }
    var addNameInput by remember { mutableStateOf("") }
    var addIconKey by remember { mutableStateOf(iconChoices.first().first) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // 进入记一笔页时刷新分类（确保可选）
    LaunchedEffect(Unit) { vm.loadCategories() }

    // 编辑模式：仅首次回填原分类选中态（此后用户操作优先，避免被 kind 切换覆盖）
    val didInitCategory = remember { mutableStateOf(!editing) }
    LaunchedEffect(editing, vm.categories) {
        if (editing && !didInitCategory.value && initialCatId != null) {
            selectedCategory = vm.categories.firstOrNull { it.id == initialCatId }
            didInitCategory.value = true
        }
    }

    // 切换支出/收入：不同 kind 分类不同，清空当前选中；动态分类位置同步切换
    LaunchedEffect(kind) {
        selectedCategory = null
        dynamicCatId = vm.dynamicCategoryId(kind)
    }

    // ---- 分类数据推导 ----
    val topAll = vm.categories
        .filter { it.parent_id == null && it.kind == kind }
        .sortedBy { it.sort }
    val otherCat = topAll.firstOrNull { it.name == "其他" }
    // 网格默认加载：非"其他"、非动态分类、按 sort 前 13 个；其余进入"更多"
    val defaultShown = topAll.filter { it.id != otherCat?.id && it.id != dynamicCatId }.take(13)
    // 更多窗口：未加载且未设为动态的分类
    val moreList = topAll
        .filter { it.id != otherCat?.id && it.id != dynamicCatId }
        .drop(13)
    val dynamicCat = vm.categories.firstOrNull { it.id == dynamicCatId }
    val dynamicParent = dynamicCat?.parent_id?.let { pid -> vm.categories.firstOrNull { it.id == pid } }

    // 网格项：默认分类 + 其它 + 动态分类 + 更多（自动换行，最多 4 行 16 格）
    val gridItems: List<GridItem> = buildList {
        defaultShown.forEach { add(GridItem.Cat(it)) }
        add(GridItem.Other)
        add(GridItem.Dynamic)
        add(GridItem.More)
    }

    fun displayNameOf(c: CategoryDto): String =
        if (selectedCategory?.parent_id == c.id) selectedCategory!!.name else c.name

    fun loadDynamic(c: CategoryDto) {
        vm.setDynamicCategory(kind, c.id)
        dynamicCatId = c.id
        selectedCategory = c
    }

    fun selectSub(sub: CategoryDto) {
        selectedCategory = sub
        if (subMenuFromMore) {
            // 从"更多"长按进入：一级图标 + 二级名称 显示在动态分类位置
            vm.setDynamicCategory(kind, sub.id)
            dynamicCatId = sub.id
        }
    }

    val displayAmount = amountInput.ifEmpty { "0" }
    val sign = if (kind == "expense") "-" else "+"

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // 顶栏：关闭 + 支出/收入切换（编辑模式显示标题）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.Close, contentDescription = "关闭")
            }
            if (editing) {
                Text(
                    text = "编辑账单",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            } else {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    KindSegment(
                        label = "支出",
                        selected = kind == "expense",
                        onClick = { kind = "expense" },
                    )
                    KindSegment(
                        label = "收入",
                        selected = kind == "income",
                        onClick = { kind = "income" },
                    )
                }
            }
            if (onOcr != null) {
                IconButton(onClick = onOcr) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = "截图记账")
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
        }

        // 金额显示
        Text(
            text = "$sign ¥ $displayAmount",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (kind == "expense") Color(0xFF333333) else MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        )

        // 分类网格 + 时间 + 内容（可滚动区）
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            gridItems.chunked(4).forEach { rowItems ->
                Row(Modifier.fillMaxWidth()) {
                    for (item in rowItems) {
                        when (item) {
                            is GridItem.Cat -> CategoryCell(
                                icon = categoryImage(item.category),
                                name = displayNameOf(item.category),
                                // 选中二级分类时，其父级格子保持选中高亮
                                selected = item.category.id == selectedCategory?.id
                                    || selectedCategory?.parent_id == item.category.id,
                                onClick = { selectedCategory = item.category },
                                onLongClick = {
                                    subMenuFor = item.category
                                    subMenuFromMore = false
                                },
                            )
                            GridItem.Other -> CategoryCell(
                                icon = otherCat?.let { categoryImage(it) } ?: Icons.Filled.MoreHoriz,
                                name = "其它",
                                selected = otherCat?.id == selectedCategory?.id
                                    || selectedCategory?.parent_id == otherCat?.id,
                                onClick = { otherCat?.let { selectedCategory = it } },
                                onLongClick = {
                                    otherCat?.let {
                                        subMenuFor = it
                                        subMenuFromMore = false
                                    }
                                },
                            )
                            GridItem.Dynamic -> CategoryCell(
                                icon = (dynamicParent ?: dynamicCat)?.let { categoryImage(it) }
                                    ?: Icons.Filled.Add,
                                name = dynamicCat?.name ?: "动态分类",
                                selected = dynamicCat?.id == selectedCategory?.id,
                                onClick = { dynamicCat?.let { selectedCategory = it } },
                                onLongClick = {
                                    // 动态分类位置：长按选二级后联动更新为"一级图标 + 二级名称"
                                    (dynamicParent ?: dynamicCat)?.let {
                                        subMenuFor = it
                                        subMenuFromMore = true
                                    }
                                },
                            )
                            GridItem.More -> CategoryCell(
                                icon = Icons.Filled.MoreHoriz,
                                name = "更多",
                                selected = false,
                                onClick = { showMoreDialog = true },
                                onLongClick = null,
                            )
                        }
                    }
                    for (i in rowItems.size until 4) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            // 交易时间
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showDatePicker = true },
                    )
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "交易时间",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = Formatters.formatDateTime(happenedAt),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // 交易内容
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("交易内容（可选）") },
                placeholder = { Text("如：美团外卖") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // 错误提示
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
        }

        // 数字键盘 + 保存（底部避让系统导航栏）
        Column(Modifier.navigationBarsPadding()) {
            Numpad(
                onKey = { key -> onNumKey(key, amountInput) { amountInput = it } },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Button(
                onClick = {
                    val amt = amountInput.toDoubleOrNull() ?: 0.0
                    when {
                        amt <= 0 -> error = "请输入金额"
                        selectedCategory == null -> error = "请选择分类"
                        else -> {
                            error = null
                            scope.launch {
                                saving = true
                                val signed = if (kind == "expense") -amt else amt
                                val ok = if (editing) {
                                    vm.updateTransaction(
                                        id = initial!!.id,
                                        amount = signed,
                                        categoryId = selectedCategory!!.id,
                                        happenedAt = Formatters.formatDateTime(happenedAt),
                                        content = content.trim(),
                                    )
                                } else {
                                    vm.createTransaction(
                                        amount = signed,
                                        categoryId = selectedCategory!!.id,
                                        happenedAt = Formatters.formatDateTime(happenedAt),
                                        content = content.trim(),
                                    )
                                }
                                saving = false
                                if (ok) onBack() else error = vm.serverError
                            }
                        }
                    }
                },
                enabled = !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(if (saving) "保存中…" else if (editing) "保存修改" else "保存")
            }
        }
    }

    // 关闭弹窗时清理输入，避免下次打开残留
    fun closeSubMenu() {
        subMenuFor = null
        subNameInput = ""
    }

    fun closeAddDialog() {
        showAddCategory = false
        addNameInput = ""
        addIconKey = iconChoices.first().first
    }

    // ---- 更多分类窗口：图标+名称列式，只显示未加载分类，可新增 ----
    if (showMoreDialog) {
        AlertDialog(
            onDismissRequest = { showMoreDialog = false },
            title = { Text("更多分类") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (moreList.isEmpty()) {
                        Text("暂无其他分类，可点击下方新增", color = TextGray, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                    }
                    moreList.forEach { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        loadDynamic(c)
                                        showMoreDialog = false
                                    },
                                    onLongClick = {
                                        subMenuFor = c
                                        subMenuFromMore = true
                                        showMoreDialog = false
                                    },
                                )
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = categoryImage(c),
                                contentDescription = c.name,
                                tint = TextGray,
                                modifier = Modifier.size(26.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(c.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    TextButton(
                        onClick = {
                            showMoreDialog = false
                            showAddCategory = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("新增分类")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMoreDialog = false }) { Text("关闭") } },
        )
    }

    // ---- 二级分类菜单（长按分类图标触发；列式名称 + 可新增二级） ----
    subMenuFor?.let { top ->
        val subs = vm.categories.filter { it.parent_id == top.id }
        AlertDialog(
            onDismissRequest = { closeSubMenu() },
            title = { Text("${top.name} · 二级分类") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    // 一级分类本身：选择后回到一级
                    Text(
                        text = "${top.name}（一级分类）",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    selectSub(top)
                                    closeSubMenu()
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
                        Text(
                            text = sub.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        selectSub(sub)
                                        closeSubMenu()
                                    },
                                )
                                .padding(vertical = 10.dp),
                        )
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
                                        val created = vm.createCategory(name, kind, parentId = top.id)
                                        if (created != null) {
                                            selectSub(created)
                                            closeSubMenu()
                                        }
                                    }
                                }
                            },
                        ) { Text("新增") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { closeSubMenu() }) { Text("取消") } },
        )
    }

    // ---- 新增分类（一级）：名称 + 内置图标库选择 ----
    if (showAddCategory) {
        AlertDialog(
            onDismissRequest = { closeAddDialog() },
            title = { Text("新增分类") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = addNameInput,
                        onValueChange = { addNameInput = it },
                        label = { Text("分类名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("选择图标", style = MaterialTheme.typography.bodySmall, color = TextGray)
                    Spacer(Modifier.height(6.dp))
                    iconChoices.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            for ((key, label, imageVector) in row) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(3.dp)
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (addIconKey == key) PrimaryGreen else BgGray)
                                        .combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { addIconKey = key },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = imageVector,
                                        contentDescription = label,
                                        tint = if (addIconKey == key) Color.White else TextGray,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                            for (i in row.size until 4) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = addNameInput.trim()
                    if (name.isEmpty()) {
                        error = "请输入分类名称"
                        closeAddDialog()
                    } else {
                        scope.launch {
                            val created = vm.createCategory(name, kind, addIconKey)
                            if (created != null) {
                                loadDynamic(created)
                            }
                            closeAddDialog()
                        }
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { closeAddDialog() }) { Text("取消") } },
        )
    }

    // ---- 日期/时间选择 ----
    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = happenedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        happenedAt = date.atTime(happenedAt.toLocalTime())
                        // 日期确定后连弹时间选择器，完成"交易时间"完整修改
                        showDatePicker = false
                        showTimePicker = true
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) {
            DatePicker(state = state)
        }
    }
    if (showTimePicker) {
        val tState = rememberTimePickerState(
            initialHour = happenedAt.hour,
            initialMinute = happenedAt.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择交易时间") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = tState)
                    Text(
                        text = "%02d:%02d".format(tState.hour, tState.minute),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    happenedAt = happenedAt
                        .withHour(tState.hour)
                        .withMinute(tState.minute)
                        .withSecond(0)
                        .withNano(0)
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("取消") } },
        )
    }
}

/** 网格项：普通分类 / 其它 / 动态分类 / 更多。 */
private sealed interface GridItem {
    data class Cat(val category: CategoryDto) : GridItem
    data object Other : GridItem
    data object Dynamic : GridItem
    data object More : GridItem
}

/** 分类格：无灰色涟漪，选中时蓝色从中心扩散（0.5s）；长按弹二级分类菜单。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.CategoryCell(
    icon: ImageVector,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val scaleAnim by animateFloatAsState(
        targetValue = if (selected) 1f else 0.3f,
        animationSpec = tween(500),
        label = "selScale",
    )
    val alphaAnim by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(500),
        label = "selAlpha",
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick ?: {},
            )
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(BgGray),
            contentAlignment = Alignment.Center,
        ) {
            // 蓝色选中层：从中心扩散覆盖（0.5s）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scaleAnim
                        scaleY = scaleAnim
                        alpha = alphaAnim
                    }
                    .background(PrimaryGreen, RoundedCornerShape(14.dp)),
            )
            Icon(
                imageVector = icon,
                contentDescription = name,
                tint = if (selected) Color.White else TextGray,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = name,
            fontSize = 12.sp,
            color = if (selected) PrimaryGreen else TextGray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun KindSegment(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = if (selected) MaterialTheme.colorScheme.primary else TextGray,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp, vertical = 6.dp),
    )
}

private fun onNumKey(key: String, input: String, set: (String) -> Unit) {
    when (key) {
        "⌫" -> set(input.dropLast(1))
        "." -> if (input.none { it == '.' }) set(if (input.isEmpty()) "0." else "$input.")
        else -> {
            if (input.contains('.')) {
                val dec = input.substringAfter('.')
                if (dec.length >= 2) return // 最多两位小数
            }
            set(if (input == "0") key else "$input$key")
        }
    }
}

@Composable
private fun Numpad(onKey: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf(".", "0", "⌫"),
        ).forEach { rowKeys ->
            Row(Modifier.fillMaxWidth().height(52.dp)) {
                for (k in rowKeys) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onKey(k) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = k,
                            fontSize = 22.sp,
                            color = Color(0xFF333333),
                        )
                    }
                }
            }
        }
    }
}
