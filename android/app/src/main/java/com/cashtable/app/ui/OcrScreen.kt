package com.cashtable.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashtable.app.data.CategoryDto
import com.cashtable.app.data.VisionExtractDto
import com.cashtable.app.ui.components.categoryImage
import com.cashtable.app.ui.theme.BgGray
import com.cashtable.app.ui.theme.PrimaryGreen
import com.cashtable.app.ui.theme.TextGray
import com.cashtable.app.util.Formatters
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/** 截图记账（P3）：相册选图 → 大模型识别 → 确认/微调 → 记账。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 识别请求挂 Activity 生命周期域：页面被系统销毁重建（如熄屏）后请求不中断，
    // 避免挂在 rememberCoroutineScope 上的任务因 composition 离开被取消（ForgottenCoroutineScopeException）
    val activityScope = (context as? ComponentActivity)?.lifecycleScope ?: scope

    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var preview by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var recognizing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // 识别结果表单
    var kind by remember { mutableStateOf("expense") }
    var amountInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<CategoryDto?>(null) }
    var happenedAt by remember { mutableStateOf(LocalDateTime.now()) }
    var content by remember { mutableStateOf("") }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var subMenuFor by remember { mutableStateOf<CategoryDto?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadCategories() }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            error = null
            val (bytes, bmp) = loadCompressed(context, uri)
            imageBytes = bytes
            preview = bmp.asImageBitmap()
            recognizing = true
            activityScope.launch {
                val result = vm.extractFromImage(bytes, "image/jpeg")
                if (result != null) {
                    onExtracted(result, vm.categories) { t, k, a, c, ca ->
                        happenedAt = t
                        kind = k
                        amountInput = a
                        content = c
                        selectedCategory = ca
                    }
                    error = null
                } else {
                    error = vm.serverError ?: "识别失败，请重试"
                }
                recognizing = false
            }
        }
    }

    // 进入页面直接拉起系统相册，无需先停在选图按钮页
    LaunchedEffect(Unit) {
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.Close, contentDescription = "关闭")
            }
            Text(
                text = "截图记账",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.width(48.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // 图片预览/选图
            if (preview != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF0F0F0))
                        .clickable { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = preview!!,
                        contentDescription = "截图预览",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "点击图片可重新选择",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Button(
                    onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("从相册选择支付截图")
                    }
                }
            }

            // 识别中
            if (recognizing) {
                Spacer(Modifier.height(24.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("正在识别…", color = TextGray)
                }
            }

            // 错误提示
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = {
                    imageBytes?.let { bytes ->
                        error = null
                        recognizing = true
                        activityScope.launch {
                            val result = vm.extractFromImage(bytes, "image/jpeg")
                            if (result != null) {
                                onExtracted(result, vm.categories) { t, k, a, c, ca ->
                                    happenedAt = t
                                    kind = k
                                    amountInput = a
                                    content = c
                                    selectedCategory = ca
                                }
                                error = null
                            } else {
                                error = vm.serverError ?: "识别失败，请重试"
                            }
                            recognizing = false
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("重试") }
            }

            // 识别成功：确认表单
            if (!recognizing && error == null && preview != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    KindSegmentOcr("支出", kind == "expense") { kind = "expense" }
                    KindSegmentOcr("收入", kind == "income") { kind = "income" }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${if (kind == "expense") "-" else "+"} ¥ ${amountInput.ifEmpty { "0" }}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("金额") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF5F5F5))
                        .clickable { showCategoryPicker = true }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("分类", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        selectedCategory?.name ?: "请选择分类",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selectedCategory != null) MaterialTheme.colorScheme.primary else TextGray,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF5F5F5))
                        .clickable { showDatePicker = true }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("交易时间", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        Formatters.formatDateTime(happenedAt),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("交易内容") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
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
                                    val ok = vm.createTransaction(
                                        amount = signed,
                                        categoryId = selectedCategory!!.id,
                                        happenedAt = Formatters.formatDateTime(happenedAt),
                                        content = content.trim(),
                                    )
                                    saving = false
                                    if (ok) onBack() else error = vm.serverError ?: "保存失败"
                                }
                            }
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    Text(if (saving) "保存中…" else "保存账单")
                }
            }
        }
    }

    // 分类选择（行列式带图标，同记一笔页：仅显示一级分类，长按弹二级列式菜单）
    if (showCategoryPicker) {
        val topCats = vm.categories.filter { it.kind == kind && it.parent_id == null }
        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            title = { Text("选择分类") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (topCats.isEmpty()) {
                        Text("暂无分类，请先到记一笔页新增", color = TextGray)
                        Spacer(Modifier.height(8.dp))
                    }
                    topCats.chunked(4).forEach { rowCats ->
                        Row(Modifier.fillMaxWidth()) {
                            for (c in rowCats) {
                                OcrCategoryCell(
                                    icon = categoryImage(c),
                                    // 选中二级时其父级格子显示二级名称（同记一笔页）
                                    name = if (selectedCategory?.parent_id == c.id) selectedCategory!!.name else c.name,
                                    selected = c.id == selectedCategory?.id
                                        || selectedCategory?.parent_id == c.id,
                                    onClick = {
                                        selectedCategory = c
                                        showCategoryPicker = false
                                    },
                                    onLongClick = {
                                        subMenuFor = c
                                        showCategoryPicker = false
                                    },
                                )
                            }
                            for (i in rowCats.size until 4) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCategoryPicker = false }) { Text("取消") } },
        )
    }

    // 二级分类列式菜单（长按一级分类图标触发，含编辑/删除维护操作，三页共用组件）
    subMenuFor?.let { top ->
        CategorySubMenuDialog(
            top = top,
            kind = kind,
            vm = vm,
            onSelect = {
                selectedCategory = it
                subMenuFor = null
            },
            onDismiss = { subMenuFor = null },
        )
    }

    // 日期/时间选择（沿用记一笔页交互）
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
                    Text("%02d:%02d".format(tState.hour, tState.minute), style = MaterialTheme.typography.titleLarge)
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

/** 把识别结果填入表单（自动记账页复用）。 */
internal fun onExtracted(
    result: VisionExtractDto,
    categories: List<CategoryDto>,
    set: (LocalDateTime, String, String, String, CategoryDto?) -> Unit,
) {
    val t = result.transaction_time?.let { Formatters.parseDateTime(it) } ?: LocalDateTime.now()
    val k = if (result.amount != null && result.amount!! < 0) "expense" else "income"
    val a = result.amount?.let { Formatters.money(it).replace(",", "") } ?: ""
    val c = result.description ?: ""
    val ca = result.category_id?.let { cid -> categories.firstOrNull { it.id == cid } }
    set(t, k, a, c, ca)
}

/** 读取图片并压缩到最大边 1600px（控制上传体积与识别耗时；自动记账页复用）。 */
internal fun loadCompressed(context: Context, uri: Uri): Pair<ByteArray, Bitmap> {
    val src = BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
    val scale = min(1f, 1600f / max(src.width, src.height))
    val bmp = if (scale < 1f) {
        Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
    } else {
        src
    }
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
    return out.toByteArray() to bmp
}

@Composable
internal fun KindSegmentOcr(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = if (selected) MaterialTheme.colorScheme.primary else TextGray,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
    )
}

/** 分类格（截图记账/自动记账确认表单用）：选中蓝色底，样式与记一笔页分类网格一致；长按弹二级分类。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RowScope.OcrCategoryCell(
    icon: ImageVector,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick ?: {},
            )
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) PrimaryGreen else BgGray),
            contentAlignment = Alignment.Center,
        ) {
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
