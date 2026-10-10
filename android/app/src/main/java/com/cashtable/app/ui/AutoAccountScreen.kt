package com.cashtable.app.ui

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.cashtable.app.data.CategoryDto
import com.cashtable.app.ui.components.categoryImage
import com.cashtable.app.ui.theme.BgGray
import com.cashtable.app.ui.theme.TextGray
import com.cashtable.app.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 自动记账（悬浮球入口，0.7.0 技术路线变更；悬浮球模式新增自动截屏）：
 * - 手动模式（capturePath == null）：用户先用系统截屏，再点击悬浮球跳入本页；
 *   页面自动读取相册中最新（第一张）图片 → 上传识别 → 预录表单（逻辑同截图记账页）。
 * - 自动模式（capturePath 非空）：悬浮球点击瞬间经 MediaProjection 自动截屏，
 *   携带应用私有截图路径进入本页直接识别，无需相册权限。
 * 保存成功后：删除对应截图 → 关闭 App 窗口，回到点击悬浮球前的界面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoAccountScreen(vm: AppViewModel, capturePath: String? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var recognizing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var screenshotUri by remember { mutableStateOf<Uri?>(null) }
    // 是否为自动读取的相册最新截图（保存后自动删除）；手动选图不删除
    var autoScreenshot by remember { mutableStateOf(true) }
    // 自动模式（MediaProjection）截取的应用私有截图：保存后直接删文件，无需系统确认
    var captureFile by remember { mutableStateOf<File?>(null) }

    // 识别结果表单（与截图记账页一致）
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

    // 保存成功后删除截屏：系统所有权限制时用 createDeleteRequest 系统弹窗（合规删除）
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        // 无论用户是否确认删除，记账已完成，回到点击悬浮球前的界面
        (context as? Activity)?.moveTaskToBack(true)
    }

    /** 对指定图片执行识别并预录表单（自动读取与手动选图共用）。 */
    fun recognizeUri(uri: Uri) {
        screenshotUri = uri
        error = null
        recognizing = true
        scope.launch {
            val (bytes, bmp) = withContext(Dispatchers.IO) { loadCompressed(context, uri) }
            imageBytes = bytes
            preview = bmp.asImageBitmap()
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

    // 兜底：自动读取失败时可手动从相册选择（同截图记账页的 Photo Picker，无需相册权限）
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            autoScreenshot = false
            recognizeUri(uri)
        }
    }

    /** 自动识别相册最新一张（系统截屏后默认位于第一张）。 */
    fun recognizeLatest() {
        val uri = getLatestImageUri(context)
        if (uri == null) {
            autoScreenshot = false
            error = "相册中未找到图片，请先使用系统截屏，或点击下方「从相册选择」"
            return
        }
        autoScreenshot = true
        recognizeUri(uri)
    }

    // 读取相册权限（Android 13+ 需 READ_MEDIA_IMAGES；Android 10 及以下需 READ_EXTERNAL_STORAGE；
    // Android 11/12 无需权限即可读媒体库）
    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) recognizeLatest() else error = "需要相册读取权限才能自动识别，请到系统设置授权，或点击下方「从相册选择」"
    }

    LaunchedEffect(Unit) {
        // 先确保分类已加载，保证识别结果能正确匹配分类
        if (vm.categories.isEmpty()) vm.loadCategories()
        // 自动模式：悬浮球已自动截屏（应用私有文件），无需相册权限，直接识别
        if (capturePath != null) {
            val f = File(capturePath)
            if (f.exists()) {
                captureFile = f
                autoScreenshot = true
                recognizeUri(Uri.fromFile(f))
            } else {
                autoScreenshot = false
                error = "自动截屏文件不存在，请重试"
            }
            return@LaunchedEffect
        }
        val permission = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                android.Manifest.permission.READ_MEDIA_IMAGES
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            else -> null
        }
        if (permission != null &&
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        ) {
            storageLauncher.launch(permission)
            return@LaunchedEffect
        }
        recognizeLatest()
    }

    /** 保存成功：自动读取的截图删除（系统合规方式），随后将 App 退到后台，回到点击悬浮球前的界面。 */
    fun deleteScreenshotAndExit() {
        val activity = context as? Activity
        // 自动模式私有截图：直接删文件（应用私有目录，无所有权限制）
        captureFile?.let { f ->
            try { f.delete() } catch (_: Exception) {}
            activity?.moveTaskToBack(true)
            return
        }
        if (!autoScreenshot) {
            // 手动从相册选择的图片：不自动删除
            activity?.moveTaskToBack(true)
            return
        }
        val uri = screenshotUri
        if (uri == null) {
            activity?.moveTaskToBack(true)
            return
        }
        try {
            context.contentResolver.delete(uri, null, null)
            activity?.moveTaskToBack(true)
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // 非本应用拥有的媒体（系统截屏）需系统确认弹窗后才能删除
                try {
                    deleteLauncher.launch(
                        IntentSenderRequest.Builder(
                            MediaStore.createDeleteRequest(context.contentResolver, listOf(uri)).intentSender
                        ).build()
                    )
                } catch (e2: Exception) {
                    activity?.moveTaskToBack(true)
                }
            } else {
                Toast.makeText(
                    context, "账单已保存，截图删除失败，请手动清理相册", Toast.LENGTH_SHORT,
                ).show()
                activity?.moveTaskToBack(true)
            }
        }
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
                text = "自动记账",
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
            // 截图预览（相册最新一张）
            if (preview != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF0F0F0)),
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
                    text = "自动识别相册中最新的一张截图",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (!recognizing && error == null) {
                Button(
                    onClick = { recognizeLatest() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("读取相册最新截图")
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
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = {
                            imageBytes?.let { bytes ->
                                error = null
                                recognizing = true
                                scope.launch {
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
                            } ?: recognizeLatest()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("重试") }
                    TextButton(
                        onClick = {
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("从相册选择") }
                }
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
                                    if (ok) {
                                        deleteScreenshotAndExit()
                                    } else {
                                        error = vm.serverError ?: "保存失败"
                                    }
                                }
                            }
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    Text(if (saving) "保存中…" else "保存账单并返回")
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = when {
                        captureFile != null -> "保存后自动清理截屏文件，并返回原界面"
                        autoScreenshot -> "保存后将自动删除相册中的对应截图，并返回原界面"
                        else -> "保存后返回原界面（手动选择的图片不会被删除）"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // 分类选择（行列式带图标，同截图记账页/记一笔页：仅一级分类，长按弹二级）
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

    // 二级分类列式菜单（长按一级分类图标触发）
    subMenuFor?.let { top ->
        val subs = vm.categories.filter { it.parent_id == top.id }
        AlertDialog(
            onDismissRequest = { subMenuFor = null },
            title = { Text("${top.name} · 二级分类") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
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
                                    selectedCategory = top
                                    subMenuFor = null
                                },
                            )
                            .padding(vertical = 10.dp),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    if (subs.isEmpty()) {
                        Text("暂无二级分类", color = TextGray, style = MaterialTheme.typography.bodySmall)
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
                                        selectedCategory = sub
                                        subMenuFor = null
                                    },
                                )
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { subMenuFor = null }) { Text("取消") } },
        )
    }

    // 日期/时间选择（同截图记账页）
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

/** 查询相册最新一张图片：优先匹配系统截屏目录，找不到则取最新图片。 */
private fun getLatestImageUri(context: Context): Uri? {
    val projection = if (Build.VERSION.SDK_INT >= 29) {
        arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATA,
        )
    } else {
        arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA)
    }
    var first: Uri? = null
    var screenshot: Uri? = null
    try {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val relCol = if (Build.VERSION.SDK_INT >= 29) {
                c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            } else -1
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            var count = 0
            while (c.moveToNext() && count < 30) {
                count++
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(idCol)
                )
                if (first == null) first = uri
                if (screenshot == null) {
                    val rel = if (relCol >= 0) c.getString(relCol) ?: "" else ""
                    val data = c.getString(dataCol) ?: ""
                    if ((rel + data).contains("creenshot", ignoreCase = true) ||
                        (rel + data).contains("截屏", ignoreCase = true)
                    ) screenshot = uri
                }
                if (screenshot != null && count >= 5) break
            }
        }
    } catch (_: Exception) {
        // 查询失败（如旧系统未授权）→ 返回 null，由页面提示
    }
    return screenshot ?: first
}
