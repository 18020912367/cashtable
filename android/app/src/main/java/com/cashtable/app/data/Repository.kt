package com.cashtable.app.data

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/** 数据仓库：封装 API 调用，页面层只与 Repository 交互。 */
class Repository(private val prefs: Prefs) {

    private val api: ApiService
        get() = ApiClient.getApi(prefs.serverUrl)

    private val client by lazy { OkHttpClient() }

    suspend fun loadUsers(): List<UserDto> = api.users()

    suspend fun loadCategories(kind: String? = null): List<CategoryDto> = api.categories(kind)

    suspend fun createCategory(name: String, kind: String, icon: String = "", parentId: Int? = null): CategoryDto =
        api.createCategory(CategoryCreateRequest(name = name, kind = kind, icon = icon, parent_id = parentId))

    suspend fun loadTransactions(
        month: String? = null,
        categoryId: Int? = null,
        keyword: String? = null,
        createdBy: Int? = null,
        page: Int = 1,
        pageSize: Int = 200,
    ): TransactionListDto = api.transactions(month, categoryId, keyword, createdBy, page, pageSize)

    suspend fun createTransaction(
        amount: Double,
        categoryId: Int?,
        happenedAt: String,
        content: String,
        createdBy: Int?,
    ): TransactionDto = api.createTransaction(
        TransactionCreateRequest(
            amount = amount,
            category_id = categoryId,
            happened_at = happenedAt,
            created_by = createdBy,
            content = content,
        )
    )

    suspend fun updateTransaction(
        id: Int,
        amount: Double,
        categoryId: Int?,
        happenedAt: String,
        content: String,
    ): TransactionDto = api.updateTransaction(
        id,
        TransactionUpdateRequest(
            amount = amount,
            category_id = categoryId,
            happened_at = happenedAt,
            content = content,
        )
    )

    suspend fun deleteTransaction(id: Int) = api.deleteTransaction(id)

    suspend fun loadMonthlyStats(year: String): List<MonthlyStatDto> = api.monthlyStat(year)

    suspend fun loadAppConfig(): AppConfigDto = api.appConfig()

    /** 截图识别：图片字节 → 结构化账单字段。 */
    suspend fun extractFromImage(bytes: ByteArray, mime: String): VisionExtractDto {
        val mediaType = mime.toMediaTypeOrNull() ?: "image/jpeg".toMediaTypeOrNull()!!
        val body = bytes.toRequestBody(mediaType)
        val part = MultipartBody.Part.createFormData("file", "screenshot.jpg", body)
        return api.extractTransaction(part)
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
