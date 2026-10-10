package com.cashtable.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * 数据仓库：按运行模式路由。
 *
 * - 本地模式：直接操作 App 自带 sqlite（LocalAccountDataSource），
 *   截图识别走 LlmClient（个人中心维护的 LLM Key 直连大模型）；
 * - 自定义/全功能模式：走云 API（全功能模式激活接口固定走公共服务器）。
 */
class Repository(private val prefs: Prefs, context: Context) {

    private val local = LocalAccountDataSource(context)
    private val llm = LlmClient(prefs, local)

    private val api: ApiService
        get() = ApiClient.getApi(prefs.serverUrl)

    /** 激活接口固定走公共服务器（激活时尚未切换模式，不能用模式路由地址）。 */
    private val publicApi: ApiService
        get() = ApiClient.getApi(Prefs.PUBLIC_SERVER)

    private val client by lazy { OkHttpClient() }

    init {
        // 全功能模式：所有业务请求携带激活码头，后端 deps.get_db 据此路由到
        // {激活码}_cashtable.db 租户库（数据按激活码隔离）；其他模式不加头。
        ApiClient.headerProvider = {
            if (prefs.mode == AppMode.Full && prefs.activationCode.isNotBlank()) mapOf(
                "X-Activate-Code" to prefs.activationCode,
                "X-Device-Mac" to prefs.deviceMac,
            ) else emptyMap()
        }
    }

    /** 本地模式在 IO 线程执行 sqlite 操作，其余模式走云 API。 */
    private suspend fun <T> route(localOp: () -> T, apiOp: suspend () -> T): T =
        if (prefs.mode == AppMode.Local) withContext(Dispatchers.IO) { localOp() }
        else apiOp()

    // ---------- 记账人 ----------

    suspend fun loadUsers(): List<UserDto> = route({ local.loadUsers() }, { api.users() })

    suspend fun createUser(name: String): UserDto = route(
        { local.createUser(name) },
        { api.createUser(UserCreateRequest(name = name)) },
    )

    suspend fun deleteUser(userId: Int) = route(
        { local.deleteUser(userId) },
        { api.deleteUser(userId) },
    )

    // ---------- 分类 ----------

    suspend fun loadCategories(kind: String? = null): List<CategoryDto> =
        route({ local.loadCategories(kind) }, { api.categories(kind) })

    suspend fun createCategory(name: String, kind: String, icon: String = "", parentId: Int? = null): CategoryDto =
        route(
            { local.createCategory(name, kind, icon, parentId) },
            { api.createCategory(CategoryCreateRequest(name = name, kind = kind, icon = icon, parent_id = parentId)) },
        )

    // ---------- 账单 ----------

    suspend fun loadTransactions(
        month: String? = null,
        categoryId: Int? = null,
        keyword: String? = null,
        createdBy: Int? = null,
        page: Int = 1,
        pageSize: Int = 200,
    ): TransactionListDto = route(
        { local.loadTransactions(month, categoryId, keyword, createdBy, page, pageSize) },
        { api.transactions(month, categoryId, keyword, createdBy, page, pageSize) },
    )

    suspend fun createTransaction(
        amount: Double,
        categoryId: Int?,
        happenedAt: String,
        content: String,
        createdBy: Int?,
    ): TransactionDto = route(
        {
            local.createTransaction(
                amount = amount, categoryId = categoryId, happenedAt = happenedAt,
                content = content, createdBy = createdBy, source = "manual", externalId = "",
            )
        },
        {
            api.createTransaction(
                TransactionCreateRequest(
                    amount = amount,
                    category_id = categoryId,
                    happened_at = happenedAt,
                    created_by = createdBy,
                    content = content,
                )
            )
        },
    )

    suspend fun updateTransaction(
        id: Int,
        amount: Double,
        categoryId: Int?,
        happenedAt: String,
        content: String,
    ): TransactionDto = route(
        {
            local.updateTransaction(id, amount, categoryId, happenedAt, content)
            TransactionDto(
                id = id, amount = amount, category_id = categoryId,
                category_name = null, happened_at = happenedAt, created_at = "",
                created_by = null, content = content, source = "", external_id = "",
            )
        },
        {
            api.updateTransaction(
                id,
                TransactionUpdateRequest(
                    amount = amount,
                    category_id = categoryId,
                    happened_at = happenedAt,
                    content = content,
                )
            )
        },
    )

    suspend fun deleteTransaction(id: Int) = route(
        { local.deleteTransaction(id) },
        { api.deleteTransaction(id) },
    )

    // ---------- 统计 ----------

    suspend fun loadMonthlyStats(year: String): List<MonthlyStatDto> =
        route({ local.loadMonthlyStats(year) }, { api.monthlyStat(year) })

    /** 导出区间内账单 CSV（关联记账人名与分类名），按当前模式路由。 */
    suspend fun exportTransactions(start: String, end: String): String = route(
        { local.exportTransactions(start, end) },
        {
            val resp = api.exportTransactions(start, end)
            if (resp.isSuccessful) {
                resp.body()?.string() ?: ""
            } else {
                throw RuntimeException("导出失败（HTTP ${resp.code()}）")
            }
        },
    )

    // ---------- App 配置（更新检查固定走云端公共服务器，与运行模式无关） ----------

    suspend fun loadAppConfig(): AppConfigDto = publicApi.appConfig()

    // ---------- 截图识别 ----------

    /** 截图识别：本地模式直连大模型；自定义/全功能模式走服务器 API。 */
    suspend fun extractFromImage(bytes: ByteArray, mime: String): VisionExtractDto =
        if (prefs.mode == AppMode.Local) {
            withContext(Dispatchers.IO) { llm.extract(bytes, mime) }
        } else {
            val mediaType = mime.toMediaTypeOrNull() ?: "image/jpeg".toMediaTypeOrNull()!!
            val part = MultipartBody.Part.createFormData(
                "file", "screenshot.jpg", bytes.toRequestBody(mediaType),
            )
            api.extractTransaction(part)
        }

    /** 测试个人中心配置的 LLM 是否连通（本地模式直连大模型）。 */
    suspend fun testLlmConnection(): String = withContext(Dispatchers.IO) { llm.testConnection() }

    // ---------- 全功能模式激活（固定走公共服务器） ----------

    suspend fun checkActivation(code: String, deviceMac: String): ActivationCheckResponse =
        publicApi.checkActivation(ActivationCheckRequest(code = code, device_mac = deviceMac))

    suspend fun activationStatus(code: String, deviceMac: String): ActivationCheckResponse =
        publicApi.activationStatus(code = code, deviceMac = deviceMac)

    /** 首次激活：关闭本地库保证落盘 → 上传 sqlite → 云端存为 {激活码}_cashtable.db（WAL）。 */
    suspend fun uploadActivationDb(code: String, deviceMac: String): ActivationUploadResponse {
        val path = local.dbPath()
        local.close()
        val file = File(path)
        return withContext(Dispatchers.IO) {
            val dbBody = file.readBytes().toRequestBody("application/octet-stream".toMediaTypeOrNull()!!)
            val part = MultipartBody.Part.createFormData("file", file.name, dbBody)
            publicApi.uploadActivationDb(
                code.toRequestBody("text/plain".toMediaTypeOrNull()!!),
                deviceMac.toRequestBody("text/plain".toMediaTypeOrNull()!!),
                part,
            )
        }
    }

    /** 全功能模式启动同步：下载云端该激活码的租户库并覆盖本地 sqlite。
     *  条件不满足或任一步失败均静默返回，不阻塞启动。 */
    suspend fun syncFromCloudDb(): Boolean = withContext(Dispatchers.IO) {
        val code = prefs.activationCode
        if (prefs.mode != AppMode.Full || code.isBlank()) return@withContext false
        val tmp = File.createTempFile("cloud-sync", ".db")
        try {
            val request = Request.Builder()
                .url("${Prefs.PUBLIC_SERVER}api/activate/download?code=$code")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                val body = resp.body ?: return@withContext false
                tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            // 校验为合法 sqlite 文件（文件头 16 字节）
            val head = ByteArray(16)
            tmp.inputStream().use { it.read(head) }
            if (!String(head, Charsets.US_ASCII).startsWith("SQLite format 3")) {
                return@withContext false
            }
            // 关闭本地库 → 清理 WAL/SHM → 覆盖（先取路径再关库，避免关库后重开）
            val dbFile = File(local.dbPath())
            local.close()
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            tmp.copyTo(dbFile, overwrite = true)
            true
        } catch (_: Exception) {
            false
        } finally {
            tmp.delete()
        }
    }

    /** 下载 APK 到目标文件（流式，进度回调 0~1），返回是否成功。 */
    suspend fun downloadApk(url: String, dest: File, onProgress: (Float) -> Unit): Boolean {
        return try {
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val body = resp.body ?: return false
                val total = body.contentLength()
                var read = 0L
                val buf = ByteArray(8192)
                dest.outputStream().use { out ->
                    body.byteStream().use { input ->
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0) onProgress(read.toFloat() / total)
                        }
                    }
                }
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
