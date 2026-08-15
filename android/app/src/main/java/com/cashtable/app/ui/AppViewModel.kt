package com.cashtable.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.cashtable.app.BuildConfig
import com.cashtable.app.data.AppConfigDto
import com.cashtable.app.data.CategoryDto
import com.cashtable.app.data.Prefs
import com.cashtable.app.data.Repository
import com.cashtable.app.data.TransactionDto
import com.cashtable.app.data.UserDto

/** 全局状态：服务器地址、记账人、分类缓存；数据加载与记账写入。 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val repository = Repository(prefs)

    // ---- 基础状态 ----
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

    fun updateServerUrl(url: String) {
        prefs.serverUrl = url
        serverUrl = prefs.serverUrl
    }

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

    /** 服务端版本号是否高于当前安装版本。 */
    fun hasNewVersion(): Boolean =
        appConfig?.let { it.latest_version_code > BuildConfig.VERSION_CODE } ?: false

    /** 用系统浏览器/下载器打开 APK 下载地址。 */
    fun openApkDownload() {
        val url = appConfig?.apk_url ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
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
