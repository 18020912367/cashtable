package com.cashtable.app.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cashtable.app.BuildConfig
import com.cashtable.app.data.ActivationCheckResponse
import com.cashtable.app.data.AppConfigDto
import com.cashtable.app.data.AppMode
import com.cashtable.app.data.CategoryDto
import com.cashtable.app.data.CrashLog
import com.cashtable.app.data.Prefs
import com.cashtable.app.data.Repository
import com.cashtable.app.data.TransactionDto
import com.cashtable.app.data.UserDto
import com.cashtable.app.data.VisionExtractDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 全局状态：运行模式、服务器地址、记账人、分类缓存；数据加载与记账写入。 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val repository = Repository(prefs, app)

    // ---- 基础状态 ----
    var mode by mutableStateOf(prefs.mode)
        private set

    var serverUrl by mutableStateOf(prefs.serverUrl)
        private set

    var currentUser by mutableStateOf<UserDto?>(
        prefs.currentUserId?.let { UserDto(id = it, name = prefs.currentUserName, created_at = "") }
    )
        private set

    var users by mutableStateOf<List<UserDto>>(emptyList())
        private set

    var categories by mutableStateOf<List<CategoryDto>>(emptyList())
        private set

    var appConfig by mutableStateOf<AppConfigDto?>(null)
        private set

    var serverError by mutableStateOf<String?>(null)

    var firstLoading by mutableStateOf(false)

    /** 记账数据版本号：保存成功后 +1，列表/统计页据此刷新。 */
    var transactionsVersion by mutableStateOf(0)
        private set

    // ---- 模式 / 服务器 / LLM / 激活 ----

    /** 切换运行模式（仅写入偏好；数据刷新由调用方触发 switchMode 后执行）。 */
    fun switchMode(newMode: AppMode) {
        prefs.mode = newMode
        mode = newMode
        serverUrl = prefs.serverUrl
    }

    /** 切换模式后刷新数据：重新拉取记账人与分类；当前记账人自动刷新为目标数据库的第一个记账人。 */
    suspend fun refreshForMode() {
        loadUsers()
        loadCategories()
        // 从本地模式切换至其他模式（或反向）时：自动刷新为对应数据库的第一个记账人
        val first = users.firstOrNull()
        if (first != null) {
            updateCurrentUser(first)
        } else {
            currentUser = null
            prefs.currentUserId = null
            prefs.currentUserName = ""
        }
    }

    /** 自定义模式：保存服务器地址 + sqlite 目录。 */
    fun updateCustomServer(url: String, dbPath: String) {
        prefs.customServerUrl = url
        prefs.customDbPath = dbPath
        serverUrl = prefs.serverUrl
    }

    // ---- LLM（个人中心维护，加密存储；本地模式截图识别直连大模型） ----

    /** 展示用：只显示头尾的掩码。 */
    fun llmKeyMasked(): String = Prefs.maskKey(prefs.llmApiKey)

    fun llmKeyConfigured(): Boolean = prefs.llmApiKey.isNotEmpty()

    /** 保存 LLM 配置（Key 加密存储；BaseUrl/模型普通存储）。 */
    fun saveLlmConfig(apiKey: String, baseUrl: String, model: String) {
        prefs.llmApiKey = apiKey
        prefs.llmBaseUrl = baseUrl
        prefs.llmModel = model
    }

    fun llmBaseUrl(): String = prefs.llmBaseUrl

    fun llmModel(): String = prefs.llmModel

    /** 保存时若 Key 留空则保留原值。 */
    fun llmApiKeyForSave(): String = prefs.llmApiKey

    /** 自定义模式：当前保存的服务器地址（供设置页展示）。 */
    fun customServerUrlForDisplay(): String = prefs.customServerUrl

    /** 自定义模式：当前保存的 sqlite 目录（供设置页展示）。 */
    fun customDbPathForDisplay(): String = prefs.customDbPath

    /** 测试 LLM 连通性，返回结果文案。 */
    suspend fun testLlmConnection(): String = withContext(Dispatchers.IO) {
        repository.testLlmConnection()
    }

    // ---- 全功能模式激活 ----

    /** 全功能模式：启动时拉取云端租户库同步到本地（覆盖式；失败静默不打扰启动）。 */
    suspend fun syncCloudDbOnLaunch() {
        try {
            repository.syncFromCloudDb()
        } catch (e: Exception) {
            // 同步失败不打扰启动，下次再试；记录异常日志（全功能模式上报云端）
            CrashLog.log(getApplication(), "SYNC_FAIL", "云端租户库同步失败: ${e.stackTraceToString()}")
        }
    }

    /** 设备标识：ANDROID_ID（Android 10+ 无法取真实 MAC，用系统唯一 ID 代替）。 */
    fun deviceMac(): String {
        prefs.deviceMac?.takeIf { it.isNotEmpty() }?.let { return it }
        val id = Settings.Secure.getString(
            getApplication<Application>().contentResolver,
            Settings.Secure.ANDROID_ID,
        ) ?: ""
        prefs.deviceMac = id
        return id
    }

    fun activationCode(): String = prefs.activationCode

    /** 查询激活状态（全功能模式入口/打开页面时调用）。 */
    suspend fun checkActivation(code: String): ActivationCheckResponse? = try {
        serverError = null
        repository.checkActivation(code, deviceMac())
    } catch (e: Exception) {
        serverError = e.message ?: "无法连接服务器"
        null
    }

    /** 激活：校验 + 首次激活上传本地库；成功返回 null，失败返回错误文案。 */
    suspend fun activate(code: String): String? {
        return try {
            serverError = null
            val result = repository.uploadActivationDb(code, deviceMac())
            if (result.ok) {
                prefs.activationCode = code
                prefs.mode = AppMode.Full
                mode = AppMode.Full
                serverUrl = prefs.serverUrl
                refreshForMode()
                null
            } else {
                result.message
            }
        } catch (e: Exception) {
            CrashLog.log(getApplication(), "ACTIVATE_FAIL", "激活流程异常: ${e.stackTraceToString()}")
            e.message ?: "激活失败，请重试"
        }
    }

    // ---- 记账人 ----

    fun updateCurrentUser(user: UserDto) {
        prefs.currentUserId = user.id
        prefs.currentUserName = user.name
        currentUser = user
    }

    /** 首次启动/重新连接时拉取记账人列表；成功返回 true。 */
    suspend fun loadUsers(): Boolean {
        firstLoading = true
        serverError = null
        return try {
            users = repository.loadUsers()
            true
        } catch (e: Exception) {
            serverError = e.message ?: "无法连接服务器"
            false
        } finally {
            firstLoading = false
        }
    }

    /** 新增记账人，成功返回新记账人并刷新列表。 */
    suspend fun createUser(name: String): UserDto? {
        return try {
            serverError = null
            val created = repository.createUser(name)
            loadUsers()
            created
        } catch (e: Exception) {
            serverError = e.message ?: "新增记账人失败"
            null
        }
    }

    /** 删除记账人（名下账单 created_by 置空）；若删除的是当前记账人则清空当前身份。 */
    suspend fun deleteUser(userId: Int): Boolean {
        return try {
            serverError = null
            repository.deleteUser(userId)
            if (currentUser?.id == userId) {
                currentUser = null
                prefs.currentUserId = null
                prefs.currentUserName = ""
            }
            loadUsers()
            true
        } catch (e: Exception) {
            serverError = e.message ?: "删除记账人失败"
            false
        }
    }

    /** 拉取分类缓存（记一笔页/列表图标使用）。 */
    suspend fun loadCategories() {
        try {
            categories = repository.loadCategories()
        } catch (_: Exception) {
            // 分类加载失败不阻塞主流程，记一笔页会提示
        }
    }

    /** 新增分类（一级/二级），成功返回新分类并刷新缓存。 */
    suspend fun createCategory(
        name: String,
        kind: String,
        icon: String = "",
        parentId: Int? = null,
    ): CategoryDto? {
        return try {
            val created = repository.createCategory(name, kind, icon, parentId)
            loadCategories()
            created
        } catch (e: Exception) {
            serverError = e.message ?: "新增分类失败"
            null
        }
    }

    /** 修改分类名称，成功返回 true 并刷新缓存。 */
    suspend fun updateCategory(id: Int, name: String): Boolean {
        return try {
            repository.updateCategory(id, name)
            loadCategories()
            true
        } catch (e: Exception) {
            serverError = e.message ?: "修改分类失败"
            false
        }
    }

    /** 删除分类（一级级联删二级，账单分类置空），成功返回 true 并刷新缓存。 */
    suspend fun deleteCategory(id: Int): Boolean {
        return try {
            repository.deleteCategory(id)
            loadCategories()
            true
        } catch (e: Exception) {
            serverError = e.message ?: "删除分类失败"
            false
        }
    }

    /** 动态分类（从"更多"加载的快捷分类）读写。 */
    fun dynamicCategoryId(kind: String): Int? = prefs.getDynamicCategoryId(kind)

    fun setDynamicCategory(kind: String, id: Int?) = prefs.setDynamicCategoryId(kind, id)

    /** 拉取 App 通用配置（版本检查 + 默认服务器地址）。 */
    suspend fun loadAppConfig() {
        try {
            appConfig = repository.loadAppConfig()
        } catch (_: Exception) {
            // 拉取失败不打扰，下次再试
        }
    }

    /** 导出区间内账单 CSV（关联记账人名与分类名）；失败返回 null 并置 serverError。 */
    suspend fun exportTransactions(start: String, end: String): String? {
        return try {
            serverError = null
            repository.exportTransactions(start, end)
        } catch (e: Exception) {
            serverError = e.message ?: "导出失败"
            null
        }
    }

    /** 服务端版本号是否高于当前安装版本。 */
    fun hasNewVersion(): Boolean =
        appConfig?.let { it.latest_version_code > BuildConfig.VERSION_CODE } ?: false

    // ---- App 内下载更新 ----
    var downloading by mutableStateOf(false)
        private set
    var downloadProgress by mutableStateOf(0f)
        private set
    var downloadError by mutableStateOf<String?>(null)

    /** App 内下载最新 APK：按线路顺序（GitHub → Gitee → 云端服务器）下载，失败或校验不符时自动切换下一条。 */
    fun startUpdateDownload() {
        val cfg = appConfig ?: return
        if (downloading) return
        val app = getApplication<Application>()
        val dest = File(app.cacheDir, "update/app-update.apk")
        dest.parentFile?.mkdirs()
        dest.delete() // 下载前清理旧包
        downloading = true
        downloadProgress = 0f
        downloadError = null

        // 候选线路：主链接在前，备用/云端链接（非空时）依次在后
        val urls = buildList {
            add(cfg.apk_url)
            if (cfg.apk_url_backup.isNotBlank()) add(cfg.apk_url_backup)
            if (cfg.apk_url_cloud.isNotBlank()) add(cfg.apk_url_cloud)
        }
        val expectedMd5 = cfg.apk_md5.trim().lowercase()

        viewModelScope.launch {
            var ok = false
            for ((index, url) in urls.withIndex()) {
                if (index > 0) {
                    // 切换备用线路：重置进度、清理半包
                    downloadProgress = 0f
                    dest.delete()
                }
                ok = withContext(Dispatchers.IO) {
                    repository.downloadApk(url, dest) { p -> downloadProgress = p }
                }
                if (ok) {
                    // 校验 MD5（服务端未提供时跳过）；不匹配视为下载失败，切下一线路
                    val actual = withContext(Dispatchers.IO) { repository.fileMd5(dest) }
                    if (expectedMd5.isNotEmpty() && actual != expectedMd5) {
                        ok = false
                    }
                }
                if (ok) break
            }
            downloading = false
            if (ok && dest.exists() && dest.length() > 0) {
                installApk(app, dest)
            } else {
                CrashLog.log(app, "UPDATE_FAIL", "三条线路下载均失败: $urls")
                downloadError = "下载失败，请检查网络后重试"
            }
        }
    }

    /** 通过系统安装器安装 APK（FileProvider 授权）。 */
    private fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            CrashLog.log(context, "INSTALL_FAIL", "启动 APK 安装器失败: ${e.stackTraceToString()}")
            downloadError = "无法启动安装，请在系统设置中允许安装未知应用"
        }
    }

    /** 截图识别（P3）：上传图片 → 结构化字段；失败返回 null 并置 serverError。 */
    suspend fun extractFromImage(bytes: ByteArray, mime: String): VisionExtractDto? {
        return try {
            serverError = null
            repository.extractFromImage(bytes, mime)
        } catch (e: Exception) {
            CrashLog.log(getApplication(), "OCR_FAIL", "截图识别失败: ${e.stackTraceToString()}")
            serverError = e.message ?: "识别失败，请重试"
            null
        }
    }

    /** 按条件拉取当月全部账单（分页聚合，最多 200/页）。 */
    suspend fun fetchTransactions(
        month: String?,
        categoryId: Int?,
        userId: Int?,
        keyword: String?,
    ): List<TransactionDto> {
        return try {
            val first = repository.loadTransactions(month, categoryId, keyword, userId, page = 1)
            var items = first.items
            val totalPages = (first.total + 199) / 200
            for (p in 2..totalPages) {
                items += repository.loadTransactions(month, categoryId, keyword, userId, page = p).items
            }
            serverError = null
            items
        } catch (e: Exception) {
            serverError = e.message ?: "网络错误"
            emptyList()
        }
    }

    /** 记一笔：金额带正负号（支出负、收入正）。 */
    suspend fun createTransaction(
        amount: Double,
        categoryId: Int?,
        happenedAt: String,
        content: String,
    ): Boolean {
        return try {
            repository.createTransaction(
                amount = amount,
                categoryId = categoryId,
                happenedAt = happenedAt,
                content = content,
                createdBy = currentUser?.id,
            )
            transactionsVersion++
            serverError = null
            true
        } catch (e: Exception) {
            serverError = e.message ?: "保存失败"
            false
        }
    }

    /** 修改账单：金额带正负号（支出负、收入正）。 */
    suspend fun updateTransaction(
        id: Int,
        amount: Double,
        categoryId: Int?,
        happenedAt: String,
        content: String,
    ): Boolean {
        return try {
            repository.updateTransaction(
                id = id,
                amount = amount,
                categoryId = categoryId,
                happenedAt = happenedAt,
                content = content,
            )
            transactionsVersion++
            serverError = null
            true
        } catch (e: Exception) {
            serverError = e.message ?: "保存失败"
            false
        }
    }

    /** 删除账单。 */
    suspend fun deleteTransaction(id: Int): Boolean {
        return try {
            repository.deleteTransaction(id)
            transactionsVersion++
            serverError = null
            true
        } catch (e: Exception) {
            serverError = e.message ?: "删除失败"
            false
        }
    }
}
